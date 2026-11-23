package app.workphotolog.android

import android.app.Application
import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Page { CAMERA, REVIEW, PROJECTS, ALBUM, PHOTO }

data class ShareRequest(val file: File, val mimeType: String)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkPhotoModel(private val app: Application) : AndroidViewModel(app) {
    val repository = PhotoRepository(app)
    private val storageAttempt = MutableStateFlow(0)
    var projectsFailed by mutableStateOf(false)
        private set
    val projects = storageAttempt.flatMapLatest {
        repository.projects.catch {
            projectsFailed = true
            emit(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val preferences = app.getSharedPreferences("camera", Context.MODE_PRIVATE)

    var page by mutableStateOf(Page.CAMERA)
        private set
    var selectedProjectId by mutableStateOf(preferences.getString("project", null))
        private set
    var useLocation by mutableStateOf(preferences.getBoolean("location", false))
        private set
    var draft by mutableStateOf<CaptureDraft?>(null)
        private set
    var activePhoto by mutableStateOf<Photo?>(null)
        private set
    var showDiscard by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var shareRequest by mutableStateOf<ShareRequest?>(null)
        private set
    var integrityResult by mutableStateOf<Boolean?>(null)
        private set

    private var pickerReturnPage = Page.CAMERA
    private var photoReturnPage = Page.ALBUM
    private var albumReturnPage = Page.CAMERA

    val project: Project?
        get() = projects.value.firstOrNull { it.id == selectedProjectId }
            ?: projects.value.firstOrNull()

    fun setLocation(enabled: Boolean) {
        useLocation = enabled
        preferences.edit { putBoolean("location", enabled) }
    }

    fun retryStorage() {
        if (!projectsFailed) return
        projectsFailed = false
        storageAttempt.value += 1
    }

    fun openProjects() {
        if (busy) return
        pickerReturnPage = page
        page = Page.PROJECTS
    }

    fun selectProject(target: Project) {
        if (busy) return
        rememberProject(target)
        draft = draft?.copy(projectId = target.id)
        page = pickerReturnPage
    }

    fun openAlbum(target: Project) {
        if (busy) return
        albumReturnPage = page
        rememberProject(target)
        page = Page.ALBUM
    }

    fun createProject(name: String) {
        perform("Couldn't create this project. Try another name.") {
            val created = repository.createProject(name)
            rememberProject(created)
            draft = draft?.copy(projectId = created.id)
            page = pickerReturnPage
        }
    }

    fun showPhoto(photo: Photo) {
        if (busy) return
        photoReturnPage = Page.ALBUM
        openPhoto(photo)
    }

    fun setDraftNote(note: String) {
        val limited = note.take(500)
        draft = draft?.copy(note = limited.dropLastWhile(Char::isHighSurrogate))
    }

    fun capture(imageCapture: ImageCapture) {
        val target = project ?: return
        val instant = Instant.now()
        val offset = ZoneId.systemDefault().rules.getOffset(instant).totalSeconds / 60
        perform("Couldn't capture a photo. Try again.") {
            val location = if (useLocation) async { currentFix(app) } else null
            val stagedFile = withContext(Dispatchers.IO) { repository.newCaptureFile() }
            try {
                imageCapture.captureFile(stagedFile, ContextCompat.getMainExecutor(app))
                draft = CaptureDraft(stagedFile, target.id, instant.toEpochMilli(), offset, "", location?.await())
                page = Page.REVIEW
            } catch (failure: Exception) {
                stagedFile.delete()
                throw failure
            }
        }
    }

    fun save() {
        val pending = draft ?: return
        perform("Couldn't save this photo. Your draft is still here.") {
            val saved = repository.saveCapture(pending)
            draft = null
            photoReturnPage = Page.CAMERA
            openPhoto(saved)
            notice = "Photo saved"
        }
    }

    fun dismissDiscard() {
        showDiscard = false
    }

    fun discard() {
        val file = draft?.file
        draft = null
        showDiscard = false
        page = Page.CAMERA
        viewModelScope.launch(Dispatchers.IO) { file?.delete() }
    }

    fun openCamera() {
        if (!busy) page = Page.CAMERA
    }

    fun back() {
        if (busy) return
        page = when (page) {
            Page.CAMERA -> Page.CAMERA
            Page.REVIEW -> {
                showDiscard = true
                Page.REVIEW
            }
            Page.PROJECTS -> pickerReturnPage
            Page.ALBUM -> albumReturnPage
            Page.PHOTO -> photoReturnPage
        }
    }

    fun checkIntegrity(photo: Photo) {
        perform("Couldn't check the original file.") {
            integrityResult = repository.checkOriginal(photo)
        }
    }

    fun shareOriginal(photo: Photo) {
        val file = repository.originalFile(photo)
        if (file.isFile) shareRequest = ShareRequest(file, "image/jpeg")
        else notice = "The original file is missing."
    }

    fun shareStamped(photo: Photo, projectName: String, includeLocation: Boolean) {
        perform("Couldn't prepare the stamped copy.") {
            val details = stampDetails(
                projectName, photo.note, photo.takenAtMillis, photo.utcOffsetMinutes,
                photo.location.takeIf { includeLocation },
            )
            val file = withContext(Dispatchers.IO) {
                createStampedCopy(app, repository.originalFile(photo), details)
            }
            shareRequest = ShareRequest(file, "image/jpeg")
        }
    }

    fun shareProject(target: Project, includeLocation: Boolean) {
        perform("Couldn't prepare this project's ZIP. Check free space and try again.") {
            shareRequest = ShareRequest(repository.exportProject(target, includeLocation), "application/zip")
        }
    }

    fun clearShareRequest() {
        shareRequest = null
    }

    fun message(message: String) {
        notice = message
    }

    fun clearNotice() {
        notice = null
    }

    private fun rememberProject(target: Project) {
        selectedProjectId = target.id
        preferences.edit { putString("project", target.id) }
    }

    private fun openPhoto(photo: Photo) {
        activePhoto = photo
        integrityResult = null
        page = Page.PHOTO
    }

    private fun perform(errorMessage: String, action: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notice = errorMessage
            } finally {
                busy = false
            }
        }
    }
}
