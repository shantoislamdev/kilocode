package ai.kilocode.client.session.history

/**
 * Resolves the origin remote URL for a given directory by running
 * `git remote get-url origin` as a subprocess.
 *
 * Returns null when the directory is not a git repo, has no origin remote,
 * or the command fails for any reason.
 *
 * Overridable in tests via [resolve] parameter.
 */
internal fun resolveGitRemoteUrl(dir: String): String? = runCatching {
    val proc = ProcessBuilder("git", "remote", "get-url", "origin")
        .directory(java.io.File(dir))
        .start()
    val out = proc.inputStream.bufferedReader().readText().trim()
    val code = proc.waitFor()
    if (code == 0 && out.isNotEmpty()) out else null
}.getOrNull()
