package app.workphotolog.android

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uniffi.proof_core.CaptureLocation
import uniffi.proof_core.CaptureMetadata
import uniffi.proof_core.inspectCapture

internal class LibraryBackup(private val app: Context, private val database: PhotoDatabase) {
    private val resolver = app.contentResolver
    private val preferences = app.getSharedPreferences("camera", Context.MODE_PRIVATE)
    private val logos = File(app.filesDir, "stamp-logos")

    suspend fun write(uri: Uri, projects: List<ProjectRow>, photos: List<Photo>, original: (Photo) -> File) {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val styles = readStyles(preferences.getString("stamp_library", null))
        val logoIds = (photos.mapNotNull { StampTemplate.fromJson(it.stampJson).logoId } +
            styles.values.mapNotNull { StampTemplate.fromJson(it).logoId })
            .toSortedSet().filter { validStampLogoId(it) && File(logos, "$it.png").isFile }
        val manifest = JSONObject()
            .put("format", "sitefinch-library")
            .put("version", 1)
            .put("projects", JSONArray(projects.map { JSONObject().put("id", it.id).put("name", it.name) }))
            .put("photos", JSONArray(photos.map(Photo::backupJson)))
            .put("styles", JSONObject(styles))
            .put("logos", JSONArray(logoIds))
            .toString().toByteArray()
        require(manifest.size.toLong() <= MAX_MANIFEST_BYTES) { "Library metadata is too large to back up." }
        val stream = checkNotNull(resolver.openOutputStream(uri, "wt")) { "Couldn't open the backup destination." }
        ZipOutputStream(stream.buffered()).use { zip ->
            // JPEG, MP4 and PNG are already compressed.
            zip.setLevel(0)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest)
            zip.closeEntry()
            photos.forEach { photo ->
                val file = original(photo)
                require(mediaMatches(photo, file)) { "An original is missing or has changed." }
                zip.writeFile(photo.backupPath, file)
            }
            logoIds.forEach { zip.writeFile("logos/$it.png", File(logos, "$it.png")) }
        }
    }

    suspend fun restore(uri: Uri, original: (Photo) -> File): Int = restoreLock.withLock {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val staging = Files.createTempDirectory(app.cacheDir.toPath(), RESTORE_PREFIX).toFile()
        try {
            val stream = checkNotNull(resolver.openInputStream(uri)) { "Couldn't read the selected backup." }
            val archive = ZipInputStream(stream.buffered()).use { zip ->
                require(zip.nextEntry?.name == MANIFEST) { "Choose a Sitefinch backup." }
                val buffer = java.io.ByteArrayOutputStream()
                zip.copyBounded(buffer, MAX_MANIFEST_BYTES)
                val archive = parseManifest(JSONObject(buffer.toString(Charsets.UTF_8.name())))
                val photosByPath = archive.photos.associateBy(Photo::backupPath)
                val logoPaths = archive.logoIds.map { "logos/$it.png" }.toSet()
                val expected = (photosByPath.keys + logoPaths).toMutableSet()
                var remaining = staging.usableSpace
                check(remaining > 0) { "Free up space before restoring." }
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && expected.remove(entry.name)) { "Backup contains an unexpected entry." }
                    val staged = File(staging, entry.name.substringAfterLast('/'))
                    val limit = if (entry.name in logoPaths) minOf(remaining, MAX_LOGO_BYTES) else remaining
                    staged.outputStream().use { output ->
                        remaining -= zip.copyBounded(output, limit)
                        output.fd.sync()
                    }
                    val photo = photosByPath[entry.name]
                    if (photo != null) {
                        require(mediaMatches(photo, staged)) { "An original in this backup failed its digest check." }
                    } else {
                        val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(staged.path, info)
                        require(info.outMimeType == "image/png" && info.outWidth in 1..640 && info.outHeight in 1..640) {
                            "Backup contains an invalid logo."
                        }
                    }
                }
                require(expected.isEmpty()) { "Backup is missing media." }
                archive
            }
            currentCoroutineContext().ensureActive()
            apply(archive, staging, original)
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun apply(archive: BackupManifest, staging: File, original: (Photo) -> File): Int =
        withContext(NonCancellable) {
            val moved = mutableListOf<File>()
            fun install(file: File) {
                check(!file.exists()) { "Backup would overwrite existing media." }
                Files.move(File(staging, file.name).toPath(), file.toPath(), ATOMIC_MOVE)
                moved += file
            }
            val imported = try {
                database.withTransaction {
                    val dao = database.photoDao()
                    val existing = dao.backupPhotos().associateBy { it.id }
                    archive.projects.forEach { if (!dao.hasProject(it.id)) dao.addProject(it) }
                    if (archive.logoIds.isNotEmpty()) check(logos.isDirectory || logos.mkdirs() || logos.isDirectory)
                    archive.logoIds.forEach { id ->
                        val logo = File(logos, "$id.png")
                        if (!logo.exists()) install(logo)
                    }
                    archive.photos.count { photo ->
                        val saved = existing[photo.id]
                        if (saved == null) {
                            install(original(photo))
                            dao.addPhoto(photo)
                            true
                        } else if (saved.sha256 == photo.sha256 && saved.isVideo == photo.isVideo &&
                            !original(saved).exists()
                        ) {
                            install(original(saved))
                            true
                        } else {
                            false
                        }
                    }
                }
            } catch (failure: Exception) {
                moved.forEach { it.delete() }
                throw failure
            }
            val styles = readStyles(preferences.getString("stamp_library", null))
            if (archive.styles.keys.any { it !in styles }) {
                preferences.edit().putString("stamp_library", JSONObject(archive.styles + styles).toString()).apply()
            }
            imported
        }

    companion object {
        private const val MANIFEST = "sitefinch-backup.json"
        private const val RESTORE_PREFIX = "library-restore-"
        private const val MAX_MANIFEST_BYTES = 32L * 1024 * 1024
        private const val MAX_LOGO_BYTES = 4L * 1024 * 1024
        private val restoreLock = Mutex()

        fun clearInterruptedRestores(cache: File) {
            cache.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith(RESTORE_PREFIX) }
                .forEach { it.deleteRecursively() }
        }
    }
}

private data class BackupManifest(
    val projects: List<ProjectRow>,
    val photos: List<Photo>,
    val styles: Map<String, String>,
    val logoIds: List<String>,
)

private fun parseManifest(json: JSONObject): BackupManifest {
    require(json.optString("format") == "sitefinch-library" && json.optInt("version") == 1) {
        "This backup format is unsupported."
    }
    val projects = json.getJSONArray("projects").read { index ->
        val row = getJSONObject(index)
        ProjectRow(row.getString("id"), row.getString("name")).also {
            require((it.id == "inbox" || validStampLogoId(it.id)) && it.name.isNotBlank() && it.name.length <= 64)
        }
    }
    val projectIds = projects.map { it.id }.toSet()
    require(projectIds.size == projects.size)
    val photos = json.getJSONArray("photos").read { index ->
        getJSONObject(index).backupPhoto().also {
            require(validStampLogoId(it.id) && it.projectId in projectIds)
        }
    }
    require(photos.distinctBy { it.id }.size == photos.size)
    val logos = json.getJSONArray("logos").read { index -> getString(index).also { require(validStampLogoId(it)) } }
    require(logos.toSet().size == logos.size)
    return BackupManifest(projects, photos, readStyles(json.getJSONObject("styles").toString()), logos)
}

private fun readStyles(data: String?): Map<String, String> {
    val styles = JSONObject(data ?: "{}")
    return styles.keys().asSequence().mapNotNull { name ->
        val template = StampTemplate.fromJson(styles.getString(name))
        if (name.isBlank() || name.length > 48 || template.preset == null) null else name to template.toJson()
    }.toMap()
}

private inline fun <T> JSONArray.read(element: JSONArray.(Int) -> T): List<T> =
    List(length()) { element(it) }

private fun Photo.backupJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("projectId", projectId)
    .put("takenAtMillis", takenAtMillis)
    .put("utcOffsetMinutes", utcOffsetMinutes)
    .put("note", note)
    .put("sha256", sha256)
    .put("stamp", stampJson)
    .put("video", isVideo)
    .put("heading", headingDegrees?.toDouble())
    .put("location", location?.let {
        JSONObject()
            .put("latitude", it.latitude)
            .put("longitude", it.longitude)
            .put("accuracy", it.accuracyMeters)
            .put("age", it.ageMillis)
            .put("precision", it.precision)
            .put("altitude", it.altitudeMeters)
    })

private fun JSONObject.backupPhoto(): Photo {
    val fix = optJSONObject("location")?.run {
        LocationFix(getDouble("latitude"), getDouble("longitude"), getDouble("accuracy"), getLong("age"),
            getString("precision"), optDouble("altitude").takeIf(Double::isFinite))
    }
    val stamp = optString("stamp").takeIf { it.isNotEmpty() }
    require(stamp == null || stamp.length <= 65_536)
    return Photo(
        getString("id"), getString("projectId"), getLong("takenAtMillis"), getInt("utcOffsetMinutes"),
        getString("note"), fix, getString("sha256"), stamp, getBoolean("video"),
        optDouble("heading").takeIf { it in 0.0..360.0 }?.toFloat(),
    )
}

private val Photo.backupPath: String get() = "originals/$id." + if (isVideo) "mp4" else "jpg"

private fun mediaMatches(photo: Photo, file: File): Boolean {
    if (!file.isFile) return false
    val location = photo.location?.let {
        CaptureLocation(it.latitude, it.longitude, it.accuracyMeters, it.ageMillis, it.precision)
    }
    return inspectCapture(
        CaptureMetadata(photo.id, photo.takenAtMillis, photo.utcOffsetMinutes, photo.note, location), file.absolutePath,
    ).sha256 == photo.sha256
}

private suspend fun ZipOutputStream.writeFile(path: String, file: File) {
    putNextEntry(ZipEntry(path))
    file.inputStream().use { input -> input.copyBounded(this, Long.MAX_VALUE) }
    closeEntry()
}

private suspend fun InputStream.copyBounded(output: OutputStream, limit: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    val coroutine = currentCoroutineContext()
    while (true) {
        coroutine.ensureActive()
        val count = read(buffer)
        if (count < 0) return total
        total += count
        require(total <= limit) { "Backup exceeds available space or the supported file size." }
        output.write(buffer, 0, count)
    }
}
