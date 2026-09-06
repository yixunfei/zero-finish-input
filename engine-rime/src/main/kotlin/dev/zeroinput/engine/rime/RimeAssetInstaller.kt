package dev.zeroinput.engine.rime

import android.content.Context
import java.io.File

internal class RimeAssetInstaller(
    private val context: Context,
) {
    data class Directories(
        val shared: File,
        val user: File,
    )

    fun install(): Directories {
        val root = File(context.noBackupFilesDir, "rime")
        val shared = File(root, "shared-$ASSET_VERSION")
        val user = File(root, "user").apply(File::mkdirs)
        val marker = File(shared, ".installed")
        if (!marker.isFile) {
            shared.mkdirs()
            copyAssetTree(ASSET_ROOT, shared)
            marker.writeText(ASSET_VERSION)
        }
        return Directories(shared, user)
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                destination.outputStream().use(input::copyTo)
            }
            return
        }

        destination.mkdirs()
        children.forEach { child ->
            copyAssetTree("$assetPath/$child", File(destination, child))
        }
    }

    private companion object {
        const val ASSET_ROOT = "rime"
        const val ASSET_VERSION = "4"
    }
}
