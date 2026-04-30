package me.sukimon.miroot.util

import android.content.Context
import java.io.File

/**
 * Extracts all required assets from the APK to the app's filesDir.
 */
object AssetExtractor {

    private val ASSET_FILES = listOf(
        "busybox",
        "resetprop",
        "magisk.apk",
        "master.sh",
        "bootstrap.sh",
        "daemon_runner"
    )

    /**
     * Extract all assets to filesDir.
     * @return The filesDir path on success.
     */
    fun extractAll(context: Context): Result<File> {
        val filesDir = context.filesDir
        return try {
            for (assetName in ASSET_FILES) {
                val outFile = File(filesDir, assetName)
                context.assets.open(assetName).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                // chmod 755 via shell — Java's setExecutable doesn't reliably
                // set permissions for other UIDs
                Runtime.getRuntime().exec(arrayOf("chmod", "755", outFile.absolutePath)).waitFor()
            }
            Result.success(filesDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Make all files in the directory executable (chmod 755).
     */
    fun chmodAll(dir: File) {
        dir.listFiles()?.forEach { file ->
            Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath)).waitFor()
        }
    }
}
