import { expect, test } from "bun:test"
import { fixCatalog, fixScripts } from "./transform-package-json"

test("fixScripts preserves Kilo-only root scripts from base", () => {
  const ours = {
    scripts: {
      "dev-setup": "kilo dev-setup",
      "postinstall": "bun run --cwd packages/opencode fix-node-pty && bun run script/setup-git.ts",
      "extension": "bun --cwd packages/kilo-vscode script/launch.ts",
    },
  }
  const pkg: Record<string, unknown> = {
    scripts: { postinstall: "bun run --cwd packages/opencode fix-node-pty" },
  }
  const changes: string[] = []
  fixScripts(pkg, "package.json", ours, changes)
  const scripts = pkg.scripts as Record<string, string>
  expect(scripts.postinstall).toBe(ours.scripts.postinstall)
  expect(scripts["dev-setup"]).toBe(ours.scripts["dev-setup"])
  expect(scripts.extension).toBe(ours.scripts.extension)
  expect(changes.some((c) => c.includes("postinstall"))).toBe(true)
  expect(changes.some((c) => c.includes("dev-setup"))).toBe(true)
})

test("fixScripts removes upstream-only dead scripts from root", () => {
  const pkg: Record<string, unknown> = {
    scripts: {
      "dev": "bun run --cwd packages/opencode src/index.ts",
      "dev:desktop": "bun --cwd packages/desktop-electron dev",
      "dev:web": "bun --cwd packages/app dev",
      "dev:console": "ulimit -n 10240 2>/dev/null; bun run --cwd packages/console/app dev",
    },
  }
  const changes: string[] = []
  fixScripts(pkg, "package.json", null, changes)
  const scripts = pkg.scripts as Record<string, string>
  expect(scripts.dev).toBeDefined()
  expect(scripts["dev:desktop"]).toBeUndefined()
  expect(scripts["dev:web"]).toBeUndefined()
  expect(scripts["dev:console"]).toBeUndefined()
  expect(changes.length).toBe(3)
})

test("fixScripts preserves opencode test scripts", () => {
  const ours = { scripts: { test: "bun test", "test:ci": "bun test --ci" } }
  const pkg: Record<string, unknown> = { scripts: { test: "vitest" } }
  const changes: string[] = []
  fixScripts(pkg, "packages/opencode/package.json", ours, changes)
  const scripts = pkg.scripts as Record<string, string>
  expect(scripts.test).toBe("bun test")
  expect(scripts["test:ci"]).toBe("bun test --ci")
})

test("fixScripts leaves unknown packages untouched", () => {
  const pkg: Record<string, unknown> = { scripts: { build: "tsc" } }
  const changes: string[] = []
  fixScripts(pkg, "packages/some-unknown/package.json", null, changes)
  expect((pkg.scripts as Record<string, string>).build).toBe("tsc")
  expect(changes.length).toBe(0)
})

test("fixCatalog removes upstream-only desktop sentry entries", () => {
  const pkg: Record<string, unknown> = {
    workspaces: {
      catalog: {
        "@sentry/solid": "10.36.0",
        "@sentry/vite-plugin": "4.6.0",
        "solid-js": "1.9.12",
      },
    },
  }
  const changes: string[] = []
  fixCatalog(pkg, "package.json", changes)
  const cat = (pkg.workspaces as { catalog: Record<string, string> }).catalog
  expect(cat["@sentry/solid"]).toBeUndefined()
  expect(cat["@sentry/vite-plugin"]).toBeUndefined()
  expect(cat["solid-js"]).toBe("1.9.12")
  expect(changes.length).toBe(2)
})

test("fixCatalog is a no-op when catalog is absent", () => {
  const pkg: Record<string, unknown> = {}
  const changes: string[] = []
  fixCatalog(pkg, "package.json", changes)
  expect(changes.length).toBe(0)
})
