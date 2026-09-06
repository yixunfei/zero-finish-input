package dev.zeroinput.languagepack

data class LanguagePackManifest(
    val formatVersion: Int,
    val id: String,
    val displayName: String,
    val languageTag: String,
    val version: String,
    val engineId: String,
    val files: List<LanguagePackFile>,
)

data class LanguagePackFile(
    val path: String,
    val sha256: String,
    val size: Long,
)

data class InstalledLanguagePack(
    val manifest: LanguagePackManifest,
    val directory: java.io.File,
    val enabled: Boolean = true,
) {
    val key: String
        get() = "${manifest.id}@${manifest.version}"
}
