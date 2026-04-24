import { describe, it, expect, afterEach } from "bun:test"
import * as vscode from "vscode"
import { buildProxyEnv } from "../../src/services/cli-backend/server-manager"

type WorkspaceStub = { getConfiguration: (section?: string) => { get: (key: string) => unknown } }

const workspace = vscode.workspace as unknown as WorkspaceStub
const originalGetConfiguration = workspace.getConfiguration

function stubHttpConfig(values: {
  proxy?: unknown
  noProxy?: unknown
  proxySupport?: unknown
}): void {
  workspace.getConfiguration = (section?: string) => {
    if (section === "http") {
      return {
        get: (key: string) => {
          if (key === "proxy") return values.proxy
          if (key === "noProxy") return values.noProxy
          if (key === "proxySupport") return values.proxySupport
          return undefined
        },
      }
    }
    return { get: () => undefined }
  }
}

afterEach(() => {
  workspace.getConfiguration = originalGetConfiguration
})

describe("buildProxyEnv", () => {
  it("returns an empty object when neither proxy nor noProxy is configured", () => {
    stubHttpConfig({ proxy: undefined, noProxy: undefined })

    expect(buildProxyEnv()).toEqual({})
  })

  it("forwards http.proxy as HTTP_PROXY and HTTPS_PROXY", () => {
    stubHttpConfig({ proxy: "http://proxy.corp.example:8080" })

    expect(buildProxyEnv()).toEqual({
      HTTP_PROXY: "http://proxy.corp.example:8080",
      HTTPS_PROXY: "http://proxy.corp.example:8080",
    })
  })

  it("joins http.noProxy into a comma-separated NO_PROXY value", () => {
    stubHttpConfig({ noProxy: ["localhost", "127.0.0.1", "*.internal"] })

    expect(buildProxyEnv()).toEqual({
      NO_PROXY: "localhost,127.0.0.1,*.internal",
    })
  })

  it("forwards both proxy and noProxy when both are configured", () => {
    stubHttpConfig({
      proxy: "http://proxy.corp.example:8080",
      noProxy: ["localhost", "*.internal"],
    })

    expect(buildProxyEnv()).toEqual({
      HTTP_PROXY: "http://proxy.corp.example:8080",
      HTTPS_PROXY: "http://proxy.corp.example:8080",
      NO_PROXY: "localhost,*.internal",
    })
  })

  it("ignores an http.proxy that is only whitespace", () => {
    stubHttpConfig({ proxy: "   " })

    expect(buildProxyEnv()).toEqual({})
  })

  it("ignores an empty http.noProxy array", () => {
    stubHttpConfig({ noProxy: [] })

    expect(buildProxyEnv()).toEqual({})
  })

  it("ignores a non-array http.noProxy value", () => {
    stubHttpConfig({ noProxy: "localhost" })

    expect(buildProxyEnv()).toEqual({})
  })

  it("explicitly clears env vars when http.proxySupport is off", () => {
    stubHttpConfig({ proxySupport: "off" })

    expect(buildProxyEnv()).toEqual({
      HTTP_PROXY: "",
      HTTPS_PROXY: "",
      NO_PROXY: "",
    })
  })

  it("http.proxySupport=off wins over a configured http.proxy/http.noProxy", () => {
    stubHttpConfig({
      proxy: "http://proxy.corp.example:8080",
      noProxy: ["localhost"],
      proxySupport: "off",
    })

    expect(buildProxyEnv()).toEqual({
      HTTP_PROXY: "",
      HTTPS_PROXY: "",
      NO_PROXY: "",
    })
  })
})
