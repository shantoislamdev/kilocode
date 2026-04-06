import { Hono } from "hono"
import { describeRoute, validator, resolver } from "hono-openapi"
import z from "zod"
import { PermissionNext } from "@/permission/next"
import { Session } from "@/session" // kilocode_change
import { Config } from "@/config/config" // kilocode_change
import { errors } from "../error"
import { lazy } from "../../util/lazy"

export const PermissionRoutes = lazy(() =>
  new Hono()
    .post(
      "/:requestID/reply",
      describeRoute({
        summary: "Respond to permission request",
        description: "Approve or deny a permission request from the AI assistant.",
        operationId: "permission.reply",
        responses: {
          200: {
            description: "Permission processed successfully",
            content: {
              "application/json": {
                schema: resolver(z.boolean()),
              },
            },
          },
          ...errors(400, 404),
        },
      }),
      validator(
        "param",
        z.object({
          requestID: z.string(),
        }),
      ),
      validator(
        "json",
        z.object({
          reply: PermissionNext.Reply,
          message: z.string().optional(),
        }),
      ),
      async (c) => {
        const params = c.req.valid("param")
        const json = c.req.valid("json")
        await PermissionNext.reply({
          requestID: params.requestID,
          reply: json.reply,
          message: json.message,
        })
        return c.json(true)
      },
    )
    // kilocode_change start
    .post(
      "/:requestID/always-rules",
      describeRoute({
        summary: "Save always-allow/deny permission rules",
        description: "Save approved/denied always-rules for a pending permission request.",
        operationId: "permission.saveAlwaysRules",
        responses: {
          200: {
            description: "Always rules saved successfully",
            content: {
              "application/json": {
                schema: resolver(z.boolean()),
              },
            },
          },
          ...errors(400, 404),
        },
      }),
      validator(
        "param",
        z.object({
          requestID: z.string(),
        }),
      ),
      validator(
        "json",
        z.object({
          approvedAlways: z.string().array().optional(),
          deniedAlways: z.string().array().optional(),
        }),
      ),
      async (c) => {
        const params = c.req.valid("param")
        const json = c.req.valid("json")
        await PermissionNext.saveAlwaysRules({
          requestID: params.requestID,
          approvedAlways: json.approvedAlways,
          deniedAlways: json.deniedAlways,
        })
        return c.json(true)
      },
    )
    // kilocode_change end
    // kilocode_change start
    .post(
      "/allow-everything",
      describeRoute({
        summary: "Allow everything",
        description: "Enable or disable allowing all permissions without prompts.",
        operationId: "permission.allowEverything",
        responses: {
          200: {
            description: "Success",
            content: {
              "application/json": {
                schema: resolver(z.boolean()),
              },
            },
          },
          ...errors(400, 404),
        },
      }),
      validator(
        "json",
        z.object({
          enable: z.boolean(),
          requestID: z.string().optional(),
          sessionID: z.string().optional(),
        }),
      ),
      async (c) => {
        const json = c.req.valid("json")
        const rules: PermissionNext.Ruleset = [{ permission: "*", pattern: "*", action: "allow" }]

        if (!json.enable) {
          if (json.sessionID) {
            const session = await Session.get(json.sessionID)
            await Session.setPermission({
              sessionID: json.sessionID,
              permission: (session.permission ?? []).filter(
                (rule) => !(rule.permission === "*" && rule.pattern === "*" && rule.action === "allow"),
              ),
            })
            await PermissionNext.allowEverything({ enable: false, sessionID: json.sessionID })
            return c.json(true)
          }

          await Config.updateGlobal({ permission: { "*": { "*": null } } }, { dispose: false })
          await PermissionNext.allowEverything({ enable: false })
          return c.json(true)
        }

        if (json.sessionID) {
          const session = await Session.get(json.sessionID)
          const existing = session.permission ?? []
          await Session.setPermission({
            sessionID: json.sessionID,
            permission: [...existing, ...rules],
          })
        } else {
          await Config.updateGlobal({ permission: PermissionNext.toConfig(rules) }, { dispose: false })
        }

        await PermissionNext.allowEverything({
          enable: true,
          requestID: json.requestID,
          sessionID: json.sessionID,
        })

        return c.json(true)
      },
    )
    // kilocode_change end
    .get(
      "/",
      describeRoute({
        summary: "List pending permissions",
        description: "Get all pending permission requests across all sessions.",
        operationId: "permission.list",
        responses: {
          200: {
            description: "List of pending permissions",
            content: {
              "application/json": {
                schema: resolver(PermissionNext.Request.array()),
              },
            },
          },
        },
      }),
      async (c) => {
        const permissions = await PermissionNext.list()
        return c.json(permissions)
      },
    ),
)
