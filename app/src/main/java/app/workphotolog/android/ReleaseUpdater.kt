package app.workphotolog.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class ReleaseUpdate(
    val version: String,
    val notes: String,
    val size: Long,
    val sha256: String,
)

internal class ReleaseUpdater(context: Context) {
    private val app = context.applicationContext
    private val packages = app.packageManager

    @Suppress("DEPRECATION")
    private val installed = packages.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    private val preferences = app.getSharedPreferences("release_updates", Context.MODE_PRIVATE)
    private val downloadDirectory = File(app.cacheDir, "updates")

    val installedVersion: String = installed.versionName.orEmpty()
    val updatesSupported: Boolean = app.packageName == RELEASE_PACKAGE && "arm64-v8a" in Build.SUPPORTED_ABIS

    suspend fun check(): ReleaseUpdate? = withContext(Dispatchers.IO) {
        if (!updatesSupported) return@withContext null
        val json = readGitHub(LATEST_RELEASE, allowNotFound = true) { input ->
            val bytes = ByteArray(MAX_RELEASE_JSON + 1)
            var used = 0
            while (used < bytes.size) {
                val count = input.read(bytes, used, bytes.size - used)
                if (count < 0) break
                used += count
            }
            if (used > MAX_RELEASE_JSON) throw IOException("GitHub sent too much release information.")
            JSONObject(String(bytes, 0, used, Charsets.UTF_8))
        } ?: return@withContext null
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext null
        val tag = json.optString("tag_name")
        val version = tag.removePrefix("v")
        if (!tag.startsWith('v') || !isNewerRelease(version, installedVersion)) return@withContext null

        val name = assetName(version)
        val assets = json.getJSONArray("assets")
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            if (asset.optString("name") != name || asset.optString("state") != "uploaded") continue
            val size = asset.optLong("size")
            val digest = SHA256.matchEntire(asset.optString("digest"))?.groupValues?.get(1)
            if (digest == null || size !in 1..MAX_APK_BYTES) {
                throw IOException("GitHub didn’t provide valid update verification details.")
            }
            return@withContext ReleaseUpdate(version, json.optString("body").take(600), size, digest)
        }
        throw IOException("The latest release is missing the Android update.")
    }

    fun isDismissed(update: ReleaseUpdate): Boolean =
        preferences.getString("dismissed_version", null) == update.version

    fun dismiss(update: ReleaseUpdate) {
        preferences.edit().putString("dismissed_version", update.version).apply()
    }

    suspend fun download(update: ReleaseUpdate, onProgress: (Float) -> Unit): File = coroutineScope {
        require(updatesSupported && isNewerRelease(update.version, installedVersion))
        val caller = coroutineContext.minusKey(Job)
        withContext(Dispatchers.IO) {
            if (!downloadDirectory.isDirectory && !downloadDirectory.mkdirs()) {
                throw IOException("Update storage isn’t available.")
            }
            val destination = File(downloadDirectory, assetName(update.version))
            val scratch = File.createTempFile("download-", ".apk", app.cacheDir)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                var reported = 0
                scratch.outputStream().buffered().use { output ->
                    readGitHub("$GITHUB_REPO/releases/download/v${update.version}/${destination.name}") { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > update.size) throw IOException("The update download is larger than expected.")
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            val percent = (total * 100 / update.size).toInt().coerceAtMost(99)
                            if (percent > reported) {
                                reported = percent
                                withContext(caller) { onProgress(percent / 100f) }
                            }
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (total != update.size || actual != update.sha256) {
                    throw IOException("The downloaded update didn’t pass its SHA-256 check.")
                }
                verifyPackage(scratch, update.version)
                if (!scratch.renameTo(destination)) throw IOException("Couldn’t save the verified update.")
                downloadDirectory.listFiles()?.filter { it != destination }?.forEach { it.delete() }
                withContext(caller) { onProgress(1f) }
                destination
            } finally {
                scratch.delete()
            }
        }
    }

    fun canInstall(): Boolean = updatesSupported && packages.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))

    fun installIntent(apk: File): Intent {
        require(canInstall() && apk.parentFile == downloadDirectory && apk.isFile)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(apk: File, version: String) {
        val candidate = packages.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        if (candidate == null || candidate.packageName != app.packageName || candidate.versionName != version ||
            candidate.longVersionCode <= installed.longVersionCode ||
            (candidate.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT
        ) {
            throw IOException("This update doesn’t match the installed Sitefinch app or Android version.")
        }
        val currentSigners = installed.signingInfo?.apkContentsSigners.orEmpty().toSet()
        val candidateSigners = candidate.signingInfo?.apkContentsSigners.orEmpty().toSet()
        if (currentSigners.isEmpty() || candidateSigners != currentSigners) {
            throw IOException("The update was signed with a different key.")
        }
    }
}

private const val REPO = "DvineConqueror/Sitefinch"
private const val GITHUB_REPO = "https://github.com/$REPO"
private const val LATEST_RELEASE = "https://api.github.com/repos/$REPO/releases/latest"
private const val RELEASE_PACKAGE = "app.workphotolog.android"
private const val MAX_RELEASE_JSON = 256 * 1024
private const val MAX_APK_BYTES = 250L * 1024 * 1024
private val SHA256 = Regex("sha256:([a-f0-9]{64})")
private val STABLE_VERSION = Regex("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})")
private val GITHUB_HOSTS = setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

private fun assetName(version: String) = "sitefinch-v$version-arm64-v8a.apk"

internal fun isNewerRelease(candidate: String, installed: String): Boolean {
    if (!STABLE_VERSION.matches(candidate) || !STABLE_VERSION.matches(installed)) return false
    val current = installed.split('.').map(String::toInt)
    for ((index, part) in candidate.split('.').withIndex()) {
        val difference = part.toInt().compareTo(current[index])
        if (difference != 0) return difference > 0
    }
    return false
}

private suspend fun <T> readGitHub(
    endpoint: String,
    allowNotFound: Boolean = false,
    consume: suspend (InputStream) -> T,
): T? {
    var url = URL(endpoint)
    repeat(5) {
        if (url.protocol != "https" || url.host !in GITHUB_HOSTS || url.userInfo != null || url.port !in listOf(-1, 443)) {
            throw IOException("GitHub redirected the update to an unexpected address.")
        }
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "Sitefinch-Android")
            if (url.host == "api.github.com") connection.setRequestProperty("Accept", "application/vnd.github+json")
            when (val status = connection.responseCode) {
                200 -> return connection.inputStream.buffered().use { consume(it) }
                301, 302, 303, 307, 308 -> {
                    val next = connection.getHeaderField("Location") ?: throw IOException("GitHub sent an invalid redirect.")
                    url = URL(url, next)
                }
                404 -> if (allowNotFound) return null else throw IOException("GitHub couldn’t find the update file.")
                else -> throw IOException("GitHub couldn’t deliver the update (HTTP $status).")
            }
        } finally {
            connection.disconnect()
        }
    }
    throw IOException("GitHub redirected the update too many times.")
}
