package org.rfisolns.checkpoint

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(val version: String, val apkUrl: String, val sha256Url: String)

sealed class DownloadResult {
    data class Success(val file: File) : DownloadResult()
    object ChecksumMismatch : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

// ponytail: plain HttpURLConnection, no networking library — one GitHub API
// call and two file downloads don't justify Retrofit/OkHttp.
object UpdateChecker {

    const val REPO = "r-fy/checkpoint-android"
    private const val APK_ASSET_NAME = "app-release.apk"
    private const val CHECKSUM_ASSET_NAME = "app-release.apk.sha256"

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "checkpoint-android")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val tag = json.getString("tag_name").removePrefix("v")
            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            var shaUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                when (asset.getString("name")) {
                    APK_ASSET_NAME -> apkUrl = asset.getString("browser_download_url")
                    CHECKSUM_ASSET_NAME -> shaUrl = asset.getString("browser_download_url")
                }
            }
            if (apkUrl == null || shaUrl == null) return@withContext null

            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
            if (!isNewer(tag, currentVersion)) return@withContext null

            UpdateInfo(version = tag, apkUrl = apkUrl, sha256Url = shaUrl)
        }.getOrNull()
    }

    suspend fun downloadAndVerify(context: Context, info: UpdateInfo): DownloadResult = withContext(Dispatchers.IO) {
        runCatching {
            val expectedSha = URL(info.sha256Url).openStream().bufferedReader().use { it.readText() }
                .trim().substringBefore(' ').lowercase()

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, APK_ASSET_NAME)
            URL(info.apkUrl).openStream().use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output) }
            }

            val digest = MessageDigest.getInstance("SHA-256")
            apkFile.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }

            if (actualSha != expectedSha) {
                apkFile.delete()
                DownloadResult.ChecksumMismatch
            } else {
                DownloadResult.Success(apkFile)
            }
        }.getOrElse { DownloadResult.Error(it.message ?: "Download failed") }
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(context: Context) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // ponytail: dotted-int compare, not full semver (no pre-release/build
    // metadata support) — this project only ever tags plain x.y.z.
    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
