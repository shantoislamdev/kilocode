import { describe, expect, test } from "bun:test"
import path from "node:path"
import fs from "node:fs/promises"
import { tmpdir } from "../fixture/fixture"
import { Instance } from "../../src/project/instance"
import { Agent } from "../../src/agent/agent"

describe("agent {file:...} syntax in markdown body", () => {
  test("loads file content when {file:...} is used in agent body", async () => {
    await using tmp = await tmpdir({
      git: true,
      init: async (dir) => {
        await Bun.write(path.join(dir, "prompts", "guide.md"), "Guidelines content")

        const agentsDir = path.join(dir, ".kilo", "agents")
        await fs.mkdir(agentsDir, { recursive: true })
        await Bun.write(
          path.join(agentsDir, "test.md"),
          ["---", "description: Test Agent", "---", "Base prompt", "", "{file:../../prompts/guide.md}", ""].join("\n"),
        )
      },
    })

    await Instance.provide({
      directory: tmp.path,
      fn: async () => {
        const agent = await Agent.get("test")

        expect(agent).toBeDefined()
        expect(agent?.prompt).toContain("Base prompt")
        expect(agent?.prompt).toContain("Guidelines content")
      },
    })
  })

  test("replaces missing file reference with empty string", async () => {
    await using tmp = await tmpdir({
      git: true,
      init: async (dir) => {
        const agentsDir = path.join(dir, ".kilo", "agents")
        await fs.mkdir(agentsDir, { recursive: true })
        await Bun.write(
          path.join(agentsDir, "test.md"),
          ["---", "description: Test Agent", "---", "Base prompt", "", "{file:missing.txt}", ""].join("\n"),
        )
      },
    })

    await Instance.provide({
      directory: tmp.path,
      fn: async () => {
        const agent = await Agent.get("test")

        expect(agent).toBeDefined()
        expect(agent?.prompt).toContain("Base prompt")
        expect(agent?.prompt).not.toContain("{file:")
      },
    })
  })

  test("resolves multiple file references in same agent", async () => {
    await using tmp = await tmpdir({
      git: true,
      init: async (dir) => {
        await Bun.write(path.join(dir, "a.md"), "Content A")
        await Bun.write(path.join(dir, "b.md"), "Content B")

        const agentsDir = path.join(dir, ".kilo", "agents")
        await fs.mkdir(agentsDir, { recursive: true })
        await Bun.write(
          path.join(agentsDir, "test.md"),
          ["---", "description: Test", "---", "Base", "", "{file:../../a.md}", "{file:../../b.md}", ""].join("\n"),
        )
      },
    })

    await Instance.provide({
      directory: tmp.path,
      fn: async () => {
        const agent = await Agent.get("test")

        expect(agent).toBeDefined()
        expect(agent?.prompt).toContain("Content A")
        expect(agent?.prompt).toContain("Content B")
      },
    })
  })
})
