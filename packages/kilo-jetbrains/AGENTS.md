# AGENTS.md — Kilo JetBrains Plugin

## Skills

- Use `packages/kilo-jetbrains/.kilo/skills/jetbrains-ui-style/SKILL.md` when creating, modifying, or reviewing Kotlin/Swing UI code for this plugin. It covers IntelliJ UI conventions, platform components, theme-aware colors/fonts, spacing, manual Swing vs Kotlin UI DSL, session transcript styling, and retained Swing component-state patterns for hover/expand/update flows.

## Architecture (Split Mode)

- **Split-mode plugin** with three Gradle modules: `shared/`, `frontend/`, `backend/`. The module descriptors are `kilo.jetbrains.shared.xml`, `kilo.jetbrains.frontend.xml`, `kilo.jetbrains.backend.xml` — these must stay in sync with `plugin.xml`'s `<content>` block.
- Reference template for the split-mode structure: https://github.com/JetBrains/intellij-platform-modular-plugin-template
- Official docs: https://plugins.jetbrains.com/docs/intellij/split-mode-for-remote-development.html
- The JetBrains reference template mirrors our overall structure well: root project assembles the final plugin, `shared` holds contracts, `frontend` holds UI, and `backend` holds project-local logic. Copy its split-mode wiring and RPC layout, but **do not** copy its Compose UI approach.
- Kotlin source goes under `{module}/src/main/kotlin/ai/kilocode/jetbrains/`. Package name is `ai.kilocode.jetbrains` (matches `group` in root `build.gradle.kts`).
- **Module placement rules**: backend modules host project model, indexing, analysis, execution, and CLI process management. Frontend modules host UI, typing assistance, and latency-sensitive features. Shared modules define RPC interfaces and data types used by both sides.
- In monolithic IDE mode (non-remote), all three modules load in one process — split plugins work fine without remote dev.
- Frontend ↔ backend communication uses RPC interfaces defined in `shared/`. Data sent over RPC must use `kotlinx.serialization`. In monolithic mode RPC is just an in-process suspend call.
- **Testing split mode**: run `./gradlew generateSplitModeRunConfigurations` to create a "Run IDE (Split Mode)" config that starts both frontend and backend processes locally. Emulate latency via the Split Mode widget (requires internal mode: `-Didea.is.internal=true`).
- The root `plugin.xml` is wiring only: keep plugin metadata and the `<content>` block there. Register services, extensions, listeners, and actions in the module XML descriptors, not in root `plugin.xml`.
- Module descriptor files must live directly in `{module}/src/main/resources/`, not in `META-INF/`.
- Module XMLs use `<dependencies>`, not `<depends>`. The allowed top-level registration tags are limited; keep module XMLs focused on `<resource-bundle>`, `<extensions>`, `<extensionPoints>`, `<actions>`, `<applicationListeners>`, and `<projectListeners>`.
- Module dependencies determine where code loads. In monolith mode both frontend and backend dependencies are satisfied, so both modules load together.
- Run inspection `Plugin DevKit | Code | Frontend and Backend API Usage` when adding or moving split-mode code.

## Split Feature Development

- For any new split feature, follow this flow: put UI in `frontend`, heavy/project-local logic in `backend`, and shared contracts in `shared`.
- Shared cross-process payloads must be `@Serializable`. Keep `shared` lightweight and avoid pulling frontend-only or backend-only APIs into it.
- Define RPC APIs in `shared` with `@Rpc`, `RemoteApi<Unit>`, and `suspend` methods only.
- Implement RPC providers in `backend` and register them via `com.intellij.platform.rpc.backend.remoteApiProvider` when RPC is introduced.
- Call RPC from `frontend` coroutines only. Never call RPC on the EDT; do not paper over this with blocking wrappers.
- Wrap long-lived RPC calls and flows in `durable {}` so they survive reconnects and backend restarts.
- For backend -> frontend push events, prefer Remote Topics over ad-hoc polling.
- Render empty state immediately and progressively fill data from the backend. Do not block first paint on backend state.
- Avoid chatty RPC. Debounce UI events, batch requests, cache results where appropriate, and page large datasets instead of sending everything at once.
- If a new split feature requires RPC support similar to the JetBrains template, mirror the template's wiring: `shared` and `frontend` use the RPC/serialization plugins, and the backend adds the required backend RPC platform modules.

## CLI Integration

- CLI process spawning, extraction, and lifecycle belong in `backend`.
- Detect architecture with `com.intellij.util.system.CpuArch.CURRENT`, not `System.getProperty("os.arch")`.
- Detect OS with `com.intellij.openapi.util.SystemInfo.isMac` / `isLinux` / `isWindows`.
- For packaging/build plumbing, see `script/build.ts` and `backend/build.gradle.kts`.

## Dependencies

- **Always bundle third-party libraries with the plugin.** Do not rely on libraries bundled with the IntelliJ platform (e.g. OkHttp, Gson, Guava, kotlinx-serialization-json). The IDE's bundled versions change across releases without notice and can cause version collisions, classloader conflicts, or silent API breakage. Declare all third-party dependencies as `implementation` in the relevant `build.gradle.kts` so they ship inside the plugin JAR and load from the plugin's own classloader.
- `kotlinx.coroutines` is the one mandatory exception — it is provided by the platform and must not be bundled (the IntelliJ Platform Gradle plugin enforces this automatically).
- Pin exact versions in `gradle/libs.versions.toml` and reference them via the version catalog (`libs.*`) in `build.gradle.kts`. Never hardcode version strings in `build.gradle.kts`.

## Services and Coroutines

- Official docs: https://plugins.jetbrains.com/docs/intellij/plugin-services.html and https://plugins.jetbrains.com/docs/intellij/launching-coroutines.html
- **Prefer light services**: annotate with `@Service` (or `@Service(Service.Level.PROJECT)`) instead of registering in XML when the service won't be overridden or exposed as API. Light services must be `final` in Java (no `open` in Kotlin), cannot use constructor injection of other services, and don't support `os`/`client`/`overrides` attributes.
- Non-light services that need XML registration go in `kilo.jetbrains.backend.xml` under `<extensions defaultExtensionNs="com.intellij"><applicationService>` (or `<projectService>`).
- **Constructor-injected `CoroutineScope`**: the recommended way to launch coroutines. Each service gets its own scope (child of an intersection scope). The scope is cancelled on app/project shutdown or plugin unload. Supported signatures: `MyService(CoroutineScope)` for app services, `MyService(Project, CoroutineScope)` for project services.
- The injected scope's context contains `Dispatchers.Default` and `CoroutineName(serviceClass)`. Switch to `Dispatchers.IO` for blocking I/O.
- **Avoid heavy constructor work** — defer initialization to methods. Never cache service instances in fields; always retrieve via `service<T>()` at the call site.
- `runBlockingCancellable` exists but is **not recommended** — use service scopes instead. For actions, use `currentThreadCoroutineScope()` which lets the Action System cancel the coroutine.
- No extra coroutines dependency is needed — `kotlinx.coroutines` is bundled by the IntelliJ platform and available transitively.

## CLI Server Protocol

- The plugin spawns `kilo serve --port 0` (OS assigns random port) and reads stdout for `listening on http://...:(\d+)` to discover the port.
- A random 32-byte hex password is passed via `KILO_SERVER_PASSWORD` env var for Basic Auth.
- Key env vars: `KILO_CLIENT=jetbrains`, `KILO_PLATFORM=jetbrains`, `KILO_APP_NAME=kilo-code`, `KILO_ENABLE_QUESTION_TOOL=true`.
- This is the same protocol used by the VS Code extension (`packages/kilo-vscode/src/services/cli-backend/server-manager.ts`).

## Build

- **Full build**: `bun run build` from `packages/kilo-jetbrains/` (builds CLI + Gradle plugin).
- **Gradle only**: `./gradlew buildPlugin` from `packages/kilo-jetbrains/` (requires CLI binaries already present).
- **Via Turbo**: `bun turbo build --filter=@kilocode/kilo-jetbrains` from repo root.
- **Run in sandbox**: `./gradlew runIde` — launches sandboxed IntelliJ with the plugin. Does NOT build CLI binaries.

## Files That Must Change Together

- `plugin.xml` `<content>` entries ↔ module XML descriptors (`kilo.jetbrains.{shared,frontend,backend}.xml`)
- Service classes ↔ `<applicationService>`/`<projectService>` entries in the corresponding module XML
- `script/build.ts` platform list ↔ `backend/build.gradle.kts` `requiredPlatforms` list

## Session Component

The chat session feature uses a three-layer Model / Controller / View architecture. All files live under
`frontend/src/main/kotlin/ai/kilocode/client/session/`.

### Layers

**`SessionModel`** (`model/SessionModel.kt`)

- Single source of truth for session content and runtime state.
- **EDT-only access** — no synchronisation. `SessionController` guarantees all reads and writes happen on the EDT.
- State is mutated only through dedicated methods (`setState`, `upsertMessage`, `setDiff`, etc.), never via direct field assignment from outside the model.
- Every mutation fires a sealed `SessionModelEvent` that carries the data needed for rendering — UI never needs to read back from the model after receiving an event.
- Each event overrides `toString()` with a compact, stable label (e.g. `"MessageAdded msg1"`, `"DiffUpdated files=2"`). Tests assert events by comparing joined `toString()` output.
- `loadHistory()` and `clear()` reset all state fields — diff, todos, compactionCount, messages, and `SessionState.Idle`. Call them when opening or clearing a session.

**`SessionController`** (`SessionController.kt`)

- Owns one `SessionModel`. UIs read from `model` and subscribe to `SessionModelEvent` via `model.addListener()`.
- Accepts an optional `id` at construction.
  - `id = null` → lazily creates a new session on the first `prompt()` call. This guarantees events are subscribed before the prompt is sent, eliminating race conditions.
  - `id != null` → immediately loads history and subscribes to SSE events on construction.
- After history load, `recoverPending()` seeds state in this priority order: (1) pending permission, (2) pending question, (3) current session status (`busy`/`retry`/`offline` from `KiloSessionService.statuses`), (4) `Idle`.
- All SSE events are filtered by `sessionID` before being handled. `session.error` events with `null` sessionID are treated as global and pass through.
- Publishes coarser lifecycle updates (app/workspace changes, view switching) via `SessionControllerEvent` to registered listeners — keep these separate from the fine-grained `SessionModelEvent` stream.

**View** (UI classes under `ui/`)

- Listens to `SessionModelEvent` via `model.addListener(parent) { event -> when(event) { ... } }`.
- The `when` block must be exhaustive — add `-> Unit` branches for events the view intentionally ignores so new events surface as compile errors.
- Views call `SessionController` actions (`prompt()`, `replyPermission()`, etc.) on the EDT; the controller dispatches RPC calls to a coroutine scope.
- Views must never access RPC or services directly — everything goes through the controller.

### Adding a New Event

1. Add a subclass to `SessionModelEvent` with a stable `toString()`.
2. Add the corresponding state field and mutation method to `SessionModel`. Reset the field in both `loadHistory()` and `clear()`.
3. Handle the new `ChatEventDto` in `SessionController.handle()` by calling the model mutation method.
4. Add `-> Unit` stubs for the new event in any existing exhaustive `when` blocks in view code.

### Testing

Controller tests extend `SessionControllerTestBase` (`test/…/session/SessionControllerTestBase.kt`),
which provides a real IntelliJ Application and EDT via `BasePlatformTestCase`, real frontend services
wired to `FakeSessionRpcApi`, and a set of shared helpers.

**Two setups:**

| Setup | When to use |
|---|---|
| `val (m, events, modelEvents) = prompted()` | New-session flow — sets app/workspace to ready, creates a controller with no ID, sends an initial prompt. `model.showMessages` is `true`. Start all event-driven tests from here. |
| `controller("ses_test")` + manual `appRpc`/`projectRpc` setup + `flush()` | Existing-session flow — opens a specific session, triggers history load and `recoverPending()`. `model.showMessages` is `false`. Use for recovery and history tests. Pass `show = false` to `assertSession`. |

**Core assertion helpers:**

```kotlin
// Full controller state — includes model transcript + status line.
// show=true is the default; pass show=false for existing-session tests.
assertSession("""
    assistant#msg1
    text#prt1:
      hello

    [code] [kilo/gpt-5] [idle]
""", m)

// Just the model transcript (no status line)
assertModel("diff: src/A.kt src/B.kt", m)

// Model event stream — one event per line via event.toString()
assertModelEvents("""
    MessageAdded msg1
    ContentAdded msg1/prt1
""", modelEvents)

// Controller lifecycle events
assertControllerEvents("WorkspaceReady", events)
```

**Emitting events and flushing:**

```kotlin
emit(ChatEventDto.TurnOpen("ses_test"))         // emits + flushes by default
emit(ChatEventDto.PartDelta(…), flush = false)  // batch without intermediate flush
flush()                                          // settle coroutines + drain EDT
```

**`FakeSessionRpcApi` configurable state:**

| Field | Purpose |
|---|---|
| `rpc.events` (`MutableSharedFlow`) | Emit `ChatEventDto` events the controller will receive |
| `rpc.statuses` (`MutableStateFlow<Map<String, SessionStatusDto>>`) | Seed the status map read during `recoverPending()` |
| `rpc.history` | Messages returned by `messages()` (history load) |
| `rpc.pendingPermissionList` | Permissions returned during recovery |
| `rpc.pendingQuestionList` | Questions returned during recovery |
| `rpc.prompts`, `rpc.permissionReplies`, etc. | Call tracking for RPC side-effects |

**String format of `model.toString()`** (used by `assertModel` / `assertSession`):

```
role#msgId
text#partId:
  line one
  line two
---
tool#partId toolName [STATE] optional title
---
question#id
tool: msgId/callId
header: …
prompt: …
option: label - description
multiple: false
custom: true
---
diff: file1 file2
---
todo: [status] content
---
compacted: N
```

Sections are separated by `---`. Only non-empty sections appear. The status line appended by `SessionController.toString()` is:

```
[agentName] [provider/modelId] [idle|busy|retry|offline|error|awaiting-question|awaiting-permission] [optional detail]
```

## UI Design Guidelines

For Kotlin/Swing UI work — components, layouts, dialogs, tool windows, theming, icons, popups, lists, trees, validation, session styling — load `packages/kilo-jetbrains/.kilo/skills/jetbrains-ui-style/SKILL.md`. It is the single source of truth for plugin UI conventions and is more comprehensive than the previous inline guide that lived here.

The non-negotiables (kept inline so they apply even without loading the skill):

- Do not use Kotlin Compose or `intellij.platform.compose`.
- Do not use JCEF (`JBCefBrowser`) — it does not work in split-mode remote dev.
- Prefer Kotlin UI DSL v2 (`com.intellij.ui.dsl.builder`) for dialogs, settings, forms, and structured layouts. Use manual Swing only for surfaces the DSL cannot express.
- Use IntelliJ platform components (`JBLabel`, `JBTextField`, `JBList`, `Tree`, etc.) instead of raw Swing.
- Never hardcode colors, fonts, or pixel sizes — derive from `JBUI.CurrentTheme`, `UIUtil`, `JBFont`, `JBColor.namedColor`, and `JBUI.scale`.
