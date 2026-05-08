---
"@kilocode/cli": patch
"kilo-code": patch
---

Bump `simple-git` to 3.36.0 to pick up expanded blocking of exploitable git config keys (`core.fsmonitor`, `--template`, merge-related config) and the `GIT_CONFIG_COUNT` environment variable, hardening Kilo's git operations against argument-injection style vulnerabilities.
