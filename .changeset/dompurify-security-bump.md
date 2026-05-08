---
"@kilocode/cli": patch
---

Bump DOMPurify to 3.4.2 to pick up upstream security fixes for markdown HTML sanitization (mXSS via re-contextualization, prototype pollution in `CUSTOM_ELEMENT_HANDLING` / `USE_PROFILES`, `ADD_ATTR` URI validation bypass, and related advisories).
