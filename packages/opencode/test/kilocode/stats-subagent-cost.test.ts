// Verifies `kilo stats` does not double-count subagent cost. The task tool
// propagates each child session's total cost up to the parent's tool-wrapper
// assistant message. If stats summed every session indiscriminately, that
// propagated cost would appear in both the parent wrapper and the child's
// own messages. The aggregator is now filtered to root sessions (#6321).

import { afterEach, describe, expect, test } from "bun:test"
import { aggregateSessionStats } from "../../src/cli/cmd/stats"
import { MessageV2 } from "../../src/session/message-v2"
import { Instance } from "../../src/project/instance"
import { ProviderID, ModelID } from "../../src/provider/schema"
import { Session } from "../../src/session"
import { MessageID } from "../../src/session/schema"
import { Log } from "../../src/util"
import { tmpdir } from "../fixture/fixture"

Log.init({ print: false })

afterEach(async () => {
  await Instance.disposeAll()
})

const ref = {
  providerID: ProviderID.make("test"),
  modelID: ModelID.make("test-model"),
}

function assistant(sessionID: string, parentID: string, cost: number): MessageV2.Assistant {
  return {
    id: MessageID.ascending(),
    role: "assistant",
    parentID: parentID as any,
    sessionID: sessionID as any,
    mode: "build",
    agent: "build",
    cost,
    path: { cwd: "/tmp", root: "/tmp" },
    tokens: { input: 10, output: 5, reasoning: 0, cache: { read: 0, write: 0 } },
    modelID: ref.modelID,
    providerID: ref.providerID,
    time: { created: Date.now() },
  }
}

describe("stats subagent cost", () => {
  test("totalCost excludes children whose cost was propagated into the parent", async () => {
    await using tmp = await tmpdir({ git: true })
    await Instance.provide({
      directory: tmp.path,
      fn: async () => {
        const parent = await Session.create({ title: "root" })
        const child = await Session.create({ parentID: parent.id, title: "subagent" })

        // The parent's tool-wrapper assistant message shows the propagated total (own LLM + child).
        const userMsg = await Session.updateMessage({
          id: MessageID.ascending(),
          role: "user",
          sessionID: parent.id,
          agent: "build",
          model: ref,
          time: { created: Date.now() },
        } as any)
        await Session.updateMessage(assistant(parent.id, userMsg.id, 1.5))
        // The child session independently records its own LLM cost.
        const childUser = await Session.updateMessage({
          id: MessageID.ascending(),
          role: "user",
          sessionID: child.id,
          agent: "general",
          model: ref,
          time: { created: Date.now() },
        } as any)
        await Session.updateMessage(assistant(child.id, childUser.id, 0.5))

        const stats = await aggregateSessionStats()
        // Without the fix, totalCost would be 1.5 + 0.5 = 2.0 (child counted twice).
        // With the fix, only the parent (root) session contributes: 1.5 (which already
        // includes the 0.5 propagated from the child).
        expect(stats.totalCost).toBeCloseTo(1.5, 6)
        expect(stats.totalSessions).toBe(1)
      },
    })
  })
})
