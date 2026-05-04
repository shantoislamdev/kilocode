import { afterEach, beforeEach, expect, test } from "bun:test"
import { mkdtemp, rm } from "node:fs/promises"
import { join } from "node:path"
import { tmpdir } from "node:os"
import { $ } from "bun"
import { createMergeCommit, findLatestCompatCommit, getCommitHash, updateBranch, writeTree } from "./git"

const cwd = process.cwd()
let dir = ""

async function commit(message: string) {
  await $`git add -A`.quiet()
  await $`git -c user.name=Test -c user.email=test@example.com commit -m ${message}`.quiet()
  return getCommitHash("HEAD")
}

beforeEach(async () => {
  dir = await mkdtemp(join(tmpdir(), "kilo-upstream-git-"))
  process.chdir(dir)
  await $`git init -b upstream`.quiet()
  await $`git config user.name Test`.quiet()
  await $`git config user.email test@example.com`.quiet()
})

afterEach(async () => {
  process.chdir(cwd)
  await rm(dir, { recursive: true, force: true })
})

test("finds previous compatibility commit for transformed merge base", async () => {
  await Bun.write("brand.txt", "opencode A\n")
  const old = await commit("release: v1.0.0")

  await Bun.write("brand.txt", "opencode B\n")
  const target = await commit("release: v1.0.1")

  await $`git checkout -b main ${old}`.quiet()
  await Bun.write("brand.txt", "kilo A\n")
  const prior = await commit("refactor: kilo compat for v1.0.0")

  const found = await findLatestCompatCommit("main", target)
  expect(found?.commit).toBe(prior)
  expect(found?.upstream).toBe(old)

  await $`git checkout ${target}`.quiet()
  await $`git checkout -b opencode-v1.0.1`.quiet()
  await Bun.write("brand.txt", "kilo B\n")
  await $`git add -A`.quiet()
  const tree = await writeTree()
  const next = await createMergeCommit(tree, "refactor: kilo compat for v1.0.1", target, prior)
  await updateBranch("opencode-v1.0.1", next)

  const base = (await $`git merge-base main opencode-v1.0.1`.text()).trim()
  expect(base).toBe(prior)
})
