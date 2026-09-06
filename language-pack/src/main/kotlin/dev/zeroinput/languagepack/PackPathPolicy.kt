package dev.zeroinput.languagepack

import java.io.File

object PackPathPolicy {
    private val allowedExtensions = setOf(
        "yaml",
        "txt",
        "json",
        "bin",
        "ocd2",
    )

    fun validate(rawPath: String): String {
        val path = rawPath.replace('\\', '/')
        require(path.length in 1..MAX_PATH_LENGTH) { "Invalid language pack path length" }
        require(!path.startsWith('/') && !WINDOWS_DRIVE.matches(path)) { "Absolute paths are forbidden" }
        require(path.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Unsafe language pack path"
        }
        require(path.substringAfterLast('.', "").lowercase() in allowedExtensions) {
            "Executable or unsupported language pack file"
        }
        return path
    }

    fun resolveInside(root: File, rawPath: String): File {
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, validate(rawPath)).canonicalFile
        require(target.path.startsWith(canonicalRoot.path + File.separator)) {
            "Language pack path escapes its destination"
        }
        return target
    }

    private val WINDOWS_DRIVE = Regex("^[A-Za-z]:.*")
    private const val MAX_PATH_LENGTH = 240
}

