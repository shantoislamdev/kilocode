// kilocode_change - new file
import type { Argv } from "yargs"
import { Instance } from "../../project/instance"
import { Provider } from "../../provider/provider"
import { ProviderTransform } from "../../provider/transform"
import { cmd } from "./cmd"
import { UI } from "../ui"
import { generateText } from "ai"
import { randomUUID } from "crypto"

const HEADERS = ["Model", "Access", "Snippet", "Latency"]
const SEPARATOR_PADDING = 9

const isTTY = process.stderr.isTTY ?? false

function color(style: string): string {
  return isTTY ? style : ""
}

function sanitize(text: string): string {
  return text.replace(/\x1b\[[0-9;]*m/g, "").replace(/[\x00-\x1f\x7f]/g, "")
}

function truncate(text: string, maxLen: number): string {
  if (maxLen < 4) return text.substring(0, maxLen)
  return text.length > maxLen ? text.substring(0, maxLen - 3) + "..." : text
}

export function formatTable(
  rows: string[][],
  terminalWidth: number,
): { header: string; separator: string; rows: string[] } {
  const sanitizedRows = rows.map((row) => row.map((cell) => sanitize(cell ?? "")))

  const widths = HEADERS.map((h, i) => Math.max(h.length, ...sanitizedRows.map((r) => r[i].length)))

  const totalWidth = widths.reduce((a, b) => a + b, 0) + SEPARATOR_PADDING

  const minSnippetWidth = HEADERS[2].length + 3
  if (totalWidth > terminalWidth && widths[2] > minSnippetWidth) {
    const overflow = totalWidth - terminalWidth
    widths[2] = Math.max(minSnippetWidth, widths[2] - overflow)
  }

  const header = HEADERS.map((h, i) => h.padEnd(widths[i])).join(" | ")
  const separator = "-".repeat(header.length)

  const formattedRows = sanitizedRows.map((row) => {
    const truncatedRow = [row[0], row[1], row[2] ? truncate(row[2], widths[2]) : row[2], row[3]]
    return truncatedRow.map((c, i) => c.padEnd(widths[i])).join(" | ")
  })

  return { header, separator, rows: formattedRows }
}

export function formatMarkdown(rows: string[][]): string {
  const sanitized = rows.map((row) => row.map((cell) => sanitize(cell ?? "")))
  const widths = HEADERS.map((h, i) => Math.max(h.length, ...sanitized.map((r) => r[i].length)))
  const pad = (text: string, i: number) => text.padEnd(widths[i])
  const header = "| " + HEADERS.map((h, i) => pad(h, i)).join(" | ") + " |"
  const separator = "| " + widths.map((w) => "-".repeat(w)).join(" | ") + " |"
  const body = sanitized.map((row) => "| " + row.map((c, i) => pad(c, i)).join(" | ") + " |")
  return [header, separator, ...body].join("\n")
}

export function isTextModel(model: Provider.Model): boolean {
  return model.capabilities.input.text && model.capabilities.output.text
}

export const RollCallCommand = cmd({
  command: "roll-call <filter>",
  describe: "batch-test text models matching a filter for connectivity and latency",
  builder: (yargs: Argv) => {
    return yargs
      .positional("filter", {
        type: "string",
        describe: "regex to filter models by provider/modelID (required)",
        demandOption: true,
      })
      .option("prompt", {
        type: "string",
        default: "Hello",
        describe: "Prompt to send to each model",
      })
      .option("timeout", {
        type: "number",
        default: 25000,
        describe: "Timeout for each model call in milliseconds",
      })
      .option("parallel", {
        type: "number",
        default: 5,
        describe: "Number of parallel model calls",
      })
      .option("verbose", {
        type: "boolean",
        default: false,
        describe: "Show verbose output",
      })
      .option("quiet", {
        type: "boolean",
        default: false,
        describe: "Suppress progress and decoration",
      })
      .option("output", {
        type: "string",
        choices: ["table", "json", "md"],
        default: "table",
        describe: "Output format (table, json, or md)",
      })
  },
  handler: async (args) => {
    await rollCallHandler(args)
  },
})

interface RollCallResult {
  model: string
  access: boolean
  snippet: string
  latency: number | null
  errorType: string | null
  errorMessage: string | null
}

export async function rollCallHandler(args: any) {
  const { prompt, timeout, filter, parallel, output, verbose, quiet } = args

  if (parallel < 1) {
    UI.error("--parallel must be at least 1")
    process.exitCode = 1
    return
  }

  if (!filter || !filter.trim()) {
    UI.error("filter is required and cannot be empty")
    process.exitCode = 1
    return
  }

  const json = output === "json"

  if (!quiet && !json) {
    UI.println(
      `${color(UI.Style.TEXT_INFO)}Starting roll call for models with prompt: "${prompt}"${color(UI.Style.TEXT_NORMAL)}`,
    )
    UI.println(
      `${color(UI.Style.TEXT_INFO)}Timeout per model: ${timeout}ms, Parallel calls: ${parallel}${color(UI.Style.TEXT_NORMAL)}`,
    )
  }

  await Instance.provide({
    directory: process.cwd(),
    async fn() {
      const providers = await Provider.list()
      const modelsToTest: { providerID: string; modelID: string; model: Provider.Model }[] = []

      for (const [providerID, provider] of Object.entries(providers)) {
        for (const [modelID, model] of Object.entries(provider.models)) {
          const fullName = `${providerID}/${modelID}`
          try {
            const regex = new RegExp(filter, "i")
            if (!regex.test(fullName)) continue
          } catch (e) {
            UI.error(`Invalid filter regex: ${filter}`)
            return
          }
          if (!isTextModel(model)) continue
          modelsToTest.push({ providerID, modelID, model })
        }
      }

      if (modelsToTest.length === 0) {
        if (!quiet && !json)
          UI.println(`${color(UI.Style.TEXT_WARNING)}No models to test after filtering.${color(UI.Style.TEXT_NORMAL)}`)
        if (json) console.log(JSON.stringify([], null, 2))
        process.exitCode = 1
        return
      }

      if (!quiet && !json) {
        UI.println(
          `${color(UI.Style.TEXT_INFO)}Prompting ${modelsToTest.length} models...${color(UI.Style.TEXT_NORMAL)}`,
        )
      }

      const results: RollCallResult[] = []
      const queue = [...modelsToTest]
      const activePromises: Promise<void>[] = []

      const processModel = async (item: (typeof modelsToTest)[0]) => {
        const { providerID, modelID, model } = item
        const fullName = `${providerID}/${modelID}`
        const startTime = Date.now()
        let access = false
        let snippet = ""
        let latency: number | null = null
        let errorType: string | null = null
        let errorMessage: string | null = null

        try {
          const languageModel = await Provider.getLanguage(model)
          const sessionID = randomUUID()
          const baseOptions = ProviderTransform.options({ model, sessionID })
          const providerOptions = ProviderTransform.providerOptions(model, baseOptions)
          const maxOutputTokens = ProviderTransform.maxOutputTokens(model)
          const temperature = ProviderTransform.temperature(model)
          const topP = ProviderTransform.topP(model)
          const topK = ProviderTransform.topK(model)

          const messages: Parameters<typeof generateText>[0]["messages"] = [{ role: "user", content: prompt }]
          const transformedMessages = ProviderTransform.message(messages, model, baseOptions)

          const { text } = await generateText({
            model: languageModel,
            messages: transformedMessages,
            abortSignal: AbortSignal.timeout(timeout),
            maxOutputTokens,
            temperature,
            topP,
            topK,
            providerOptions,
          })
          access = true
          snippet = text.replace(/\n/g, " ")
          latency = Date.now() - startTime
        } catch (e: any) {
          latency = Date.now() - startTime
          if (e.name === "AbortError" || e.message?.includes("abort") || e.message?.includes("timeout")) {
            errorType = "timeout"
            errorMessage = "The operation timed out."
          } else if (e.error?.type || e.error?.message) {
            errorType = e.error?.type || "api_error"
            errorMessage = e.error?.message || e.message
          } else {
            errorType = "unknown"
            errorMessage = e.message || "An unknown error occurred"
          }
        }

        results.push({
          model: fullName,
          access,
          snippet,
          latency,
          errorType,
          errorMessage,
        })

        if (verbose && !quiet && !json) {
          if (access) {
            UI.println(`${color(UI.Style.TEXT_SUCCESS)}✔${color(UI.Style.TEXT_NORMAL)} ${fullName} - ${latency}ms`)
          } else {
            UI.println(
              `${color(UI.Style.TEXT_DANGER)}✘${color(UI.Style.TEXT_NORMAL)} ${fullName} - ${errorType}: ${errorMessage}`,
            )
          }
        }
      }

      while (queue.length > 0 || activePromises.length > 0) {
        while (queue.length > 0 && activePromises.length < parallel) {
          const item = queue.shift()!
          const promise = processModel(item).finally(() => {
            const index = activePromises.indexOf(promise)
            if (index > -1) {
              activePromises.splice(index, 1)
            }
          })
          activePromises.push(promise)
        }
        if (activePromises.length > 0) {
          await Promise.race(activePromises)
        }
      }

      if (json) {
        console.log(JSON.stringify(results, null, 2))
        return
      }

      const rows = results.map((r) => [
        r.model,
        r.access ? "YES" : "NO",
        r.access ? r.snippet : r.errorMessage ? `(${r.errorMessage})` : "",
        r.latency !== null ? `${r.latency}ms` : "N/A",
      ])

      if (output === "md") {
        console.log(formatMarkdown(rows))
        return
      }

      const terminalWidth = parseInt(process.env.COLUMNS || "", 10) || process.stdout.columns || 80
      const table = formatTable(rows, terminalWidth)

      UI.println(table.header)
      UI.println(table.separator)
      table.rows.forEach((line, idx) => {
        const rowColor = results[idx].access ? UI.Style.TEXT_SUCCESS : UI.Style.TEXT_DANGER
        UI.println(color(rowColor) + line + color(UI.Style.TEXT_NORMAL))
      })

      if (!quiet) {
        const successful = results.filter((r) => r.access).length
        const failed = results.length - successful
        UI.println("")
        UI.println(
          `${color(UI.Style.TEXT_SUCCESS)}${successful} accessible${color(UI.Style.TEXT_NORMAL)}, ${color(UI.Style.TEXT_DANGER)}${failed} failed${color(UI.Style.TEXT_NORMAL)}`,
        )
      }
    },
  })
}
