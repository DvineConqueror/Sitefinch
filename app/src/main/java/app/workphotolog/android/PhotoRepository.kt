package app.workphotolog.android

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.proof_core.CaptureEntry
import uniffi.proof_core.CaptureLocation
import uniffi.proof_core.CaptureMetadata
import uniffi.proof_core.checkOriginal as rustCheckOriginal
import uniffi.proof_core.inspectCapture
import uniffi.proof_core.newPhotoId
import uniffi.proof_core.writeProjectZip

class PhotoRepository(context: Context) {
    private val app = context.applicationContext
    private val database = database(app)
    private val dao = database.photoDao()
    private val originals = File(app.filesDir, "photos")
    private val drafts = File(app.cacheDir, "drafts")
    private val shared = File(app.cacheDir, "share")

    val projects = flow {
        initialize()
        emitAll(dao.observeProjects().map { rows -> rows.map(ProjectRow::toProject) })
    }

    fun photos(projectId: String) = flow {
        initialize()
        emitAll(dao.observePhotos(projectId))
    }

    fun recent(projectId: String) = flow {
        initialize()
        emitAll(dao.observeRecent(projectId))
    }

    suspend fun createProject(name: String): Project = onDisk {
        val title = name.trim()
        require(title.isNotEmpty() && title.length <= 64) { "Enter a project name up to 64 characters." }
        database.withTransaction {
            require(!dao.hasProjectName(title)) { "A project with that name already exists." }
            ProjectRow(newPhotoId(), title).also { dao.addProject(it) }.toProject()
        }
    }

    fun newCaptureFile(isVideo: Boolean = false): File =
        File.createTempFile("capture-", if (isVideo) ".mp4" else ".jpg", drafts.ensureDirectory())

    fun originalFile(photo: Photo): File =
        File(originals, photo.id + if (photo.isVideo) ".mp4" else ".jpg")

    suspend fun saveCapture(draft: CaptureDraft): Photo = onDisk {
        check(dao.hasProject(draft.projectId)) { "Choose an existing project." }
        require(draft.file.canonicalFile.parentFile == drafts.canonicalFile && draft.file.isFile) {
            "This capture is missing. Take another photo."
        }

        val id = newPhotoId()
        val note = draft.note.trim()
        val entry = inspectCapture(
            CaptureMetadata(id, draft.takenAtMillis, draft.utcOffsetMinutes, note, draft.location?.toRust()),
            draft.file.absolutePath,
        )
        val photo = Photo(
            id, draft.projectId, draft.takenAtMillis, draft.utcOffsetMinutes, note, draft.location, entry.sha256,
            draft.stampJson, draft.isVideo, draft.headingDegrees,
        )
        val destination = originalFile(photo)
        // Keep the move and database write together if the screen closes during a save.
        // Startup can discard a final file left behind by an interrupted process.
        withContext(NonCancellable) {
            RandomAccessFile(draft.file, "rw").use { it.fd.sync() }
            Files.move(draft.file.toPath(), destination.toPath(), ATOMIC_MOVE)
            try {
                dao.addPhoto(photo)
            } catch (failure: Exception) {
                try {
                    Files.move(destination.toPath(), draft.file.toPath(), ATOMIC_MOVE)
                } catch (recoveryFailure: Exception) {
                    failure.addSuppressed(recoveryFailure)
                }
                throw failure
            }
        }
        photo
    }

    suspend fun checkOriginal(photo: Photo): Boolean = onDisk {
        val file = originalFile(photo)
        file.isFile && rustCheckOriginal(photo.toEntry(file))
    }

    suspend fun exportProject(project: Project, includeLocation: Boolean): File = onDisk {
        val photos = dao.projectPhotos(project.id)
        check(photos.isNotEmpty()) { "Take a photo before exporting this project." }
        val output = File(shared.ensureDirectory(), "work-photos-${newPhotoId()}.zip")
        writeProjectZip(
            project.id,
            project.name,
            photos.map { it.toEntry(originalFile(it)) },
            includeLocation,
            output.absolutePath,
        )
        output
    }

    suspend fun writeBackup(uri: Uri): Unit = onDisk {
        val (projects, photos) = database.withTransaction { dao.backupProjects() to dao.backupPhotos() }
        LibraryBackup(app, database).write(uri, projects, photos, ::originalFile)
    }

    suspend fun restoreBackup(uri: Uri): Int = onDisk {
        LibraryBackup(app, database).restore(uri, ::originalFile)
    }

    private fun Photo.toEntry(file: File) = CaptureEntry(
        CaptureMetadata(id, takenAtMillis, utcOffsetMinutes, note, location?.toRust()),
        sha256,
        file.absolutePath,
    )

    private fun LocationFix.toRust() = CaptureLocation(
        latitude, longitude, accuracyMeters, ageMillis, precision,
    )

    private suspend fun <T> onDisk(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        initialize()
        action()
    }

    private suspend fun initialize() = withContext(Dispatchers.IO) {
        initialization.withLock {
            if (initialized) return@withLock
            originals.ensureDirectory()
            database.withTransaction {
                if (!dao.hasProject(defaultProjectId)) {
                    dao.addProject(ProjectRow(defaultProjectId, "Inbox"))
                }
            }

            recoverAbandonedOriginals(originals, dao.photoIds().toHashSet())
            LibraryBackup.clearInterruptedRestores(app.cacheDir)
            val expiry = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            listOf(drafts, shared).forEach { directory ->
                directory.listFiles().orEmpty().filter { it.isFile && it.lastModified() < expiry }.forEach { it.delete() }
            }
            initialized = true
        }
    }

    private fun File.ensureDirectory(): File {
        check(isDirectory || mkdirs() || isDirectory) { "App storage is unavailable." }
        return this
    }

    private companion object {
        const val defaultProjectId = "inbox"
        val initialization = Mutex()
        var initialized = false
        private var instance: PhotoDatabase? = null

        @Synchronized
        fun database(context: Context): PhotoDatabase = instance
            ?: Room.databaseBuilder(context, PhotoDatabase::class.java, "work-photo-log.db")
                .build()
                .also { instance = it }
    }
}

private val originalName = Regex("""[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\.(jpg|mp4)""")

internal fun recoverAbandonedOriginals(directory: File, savedPhotoIds: Set<String>) {
    directory.listFiles().orEmpty().forEach { file ->
        if (file.isFile && file.name.matches(originalName) && file.nameWithoutExtension !in savedPhotoIds) {
            file.delete()
        }
    }
}
