/**
 * Normalize any http/https URLs in a string so that IDN/Unicode hostnames are
 * converted to their punycode ASCII form, preventing homograph attacks in
 * permission dialogs where visually identical Unicode characters (e.g. Cyrillic
 * 'а' U+0430) could impersonate trusted domains (e.g. 'apitest.com').
 *
 * Example: "curl https://аpitest.com/status" (Cyrillic а)
 *       → "curl https://xn--pitest-2nf.com/status"
 */
export function normalizeUrls(text: string) {
  return text.replace(/https?:\/\/\S+/g, (match) => {
    try {
      return new URL(match).href
    } catch {
      return match
    }
  })
}
