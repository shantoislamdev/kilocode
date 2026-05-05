import { describe, expect, test } from "bun:test"
import { KiloSessionTuiSync } from "../../src/kilocode/session/tui-sync"

describe("KiloSessionTuiSync.model", () => {
  test("syncs normal user messages", () => {
    expect(KiloSessionTuiSync.model({ role: "user", parts: [{ type: "text" }] })).toBe(true)
  })

  test("skips compaction marker user messages", () => {
    expect(KiloSessionTuiSync.model({ role: "user", parts: [{ type: "compaction" }] })).toBe(false)
  })

  test("skips non-user messages", () => {
    expect(KiloSessionTuiSync.model({ role: "assistant", parts: [{ type: "text" }] })).toBe(false)
  })
})
