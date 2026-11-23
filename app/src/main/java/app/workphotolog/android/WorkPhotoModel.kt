package app.workphotolog.android

import android.app.Application
import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recording
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

enum class Page { CAMERA, REVIEW, PROJECTS, ALBUM, PHOTO }

internal enum class CaptureMode { PHOTO, VIDEO }

data class ShareRequest(val file: File, val mimeType: String)

internal data class QuickPhoto(
    val photo: Photo,
    val file: File?,
    val stamped: Boolean,
    val gallerySaved: Boolean? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkPhotoModel(private val app: Application) : AndroidViewModel(app) {
    val repository = PhotoRepository(app)
    private val gallery = GalleryPhotos(app)
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
    var autoSaveToGallery by mutableStateOf(preferences.getBoolean("auto_save_to_gallery", true))
        private set
    var stampTemplate by mutableStateOf(StampTemplate.fromJson(preferences.getString("stamp_template", null)))
        private set
    var stampLibrary by mutableStateOf(readStampLibrary())
        private set
    var nextCaptureNote by mutableStateOf("")
        private set
    internal var captureMode by mutableStateOf(CaptureMode.PHOTO)
        private set
    internal var recordingStatus by mutableStateOf<VideoRecordingStatus?>(null)
        private set
    internal var recordingFinishing by mutableStateOf(false)
        private set
    internal val captureLocked: Boolean
        get() = busy || recordingStatus != null || recordingFinishing
    private var recording: Recording? = null
    private var prepareRecording: Job? = null
    private var disposed = false
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
    var photoInGallery by mutableStateOf(false)
        private set
    private var galleryLookup: Job? = null
    internal var quickPhoto by mutableStateOf<QuickPhoto?>(null)
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

    fun updateAutoSaveToGallery(enabled: Boolean) {
        autoSaveToGallery = enabled
        preferences.edit { putBoolean("auto_save_to_gallery", enabled) }
    }

    fun applyStampTemplate(template: StampTemplate) {
        stampTemplate = template.copy(recordedAddress = emptyMap())
        preferences.edit { putString("stamp_template", stampTemplate.toJson()) }
    }

    fun applyDraftStamp(template: StampTemplate) {
        val pending = draft ?: return
        if (!pending.isVideo) {
            val recorded = StampTemplate.fromJson(pending.stampJson).recordedAddress
            draft = pending.copy(stampJson = template.copy(recordedAddress = recorded).toJson())
        }
    }

    fun saveStamp(name: String, template: StampTemplate) {
        val title = name.trim().take(48)
        if (title.isBlank() || template.preset == null) return
        updateStampLibrary(stampLibrary + (title to template.copy(recordedAddress = emptyMap())))
    }

    fun deleteStamp(name: String) = updateStampLibrary(stampLibrary - name)

    private fun readStampLibrary(): Map<String, StampTemplate> {
        val saved = preferences.getString("stamp_library", null) ?: return emptyMap()
        return try {
            val library = JSONObject(saved)
            library.keys().asSequence().mapNotNull { name ->
                val template = StampTemplate.fromJson(library.optString(name))
                if (template.preset == null) null else name to template
            }.sortedBy { it.first.lowercase() }.toMap()
        } catch (_: JSONException) {
            emptyMap()
        }
    }

    private fun updateStampLibrary(library: Map<String, StampTemplate>) {
        val saved = JSONObject()
        library.forEach { (name, template) -> saved.put(name, template.toJson()) }
        preferences.edit { putString("stamp_library", saved.toString()) }
        stampLibrary = library.toSortedMap(compareBy<String> { it.lowercase() }.thenBy { it })
    }

    fun updateNextCaptureNote(note: String) {
        nextCaptureNote = limitNote(note)
    }

    internal fun setCaptureMode(mode: CaptureMode) {
        if (!captureLocked) captureMode = mode
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
        draft = draft?.copy(note = limitNote(note))
    }

    private fun limitNote(note: String) = note.take(500).dropLastWhile(Char::isHighSurrogate)

    fun capture(imageCapture: ImageCapture, headingDegrees: Float? = null) {
        val target = project ?: return
        val selectedStamp = stampTemplate
        val note = nextCaptureNote
        val instant = Instant.now()
        val offset = ZoneId.systemDefault().rules.getOffset(instant).totalSeconds / 60
        perform("Couldn't capture a photo. Try again.") {
            val location = if (useLocation) async { currentFix(app) } else null
            val address = if (location != null && selectedStamp.preset != null && selectedStamp.showAddress) {
                async { resolveStampAddress(app, location.await()) }
            } else null
            val stagedFile = withContext(Dispatchers.IO) { repository.newCaptureFile() }
            try {
                imageCapture.captureFile(stagedFile, ContextCompat.getMainExecutor(app))
                draft = CaptureDraft(
                    stagedFile, target.id, instant.toEpochMilli(), offset, note, location?.await(),
                    stampJson = selectedStamp.copy(recordedAddress = address?.await().orEmpty()).toJson(),
                    headingDegrees = headingDegrees.takeIf { selectedStamp.showCompass },
                )
            } catch (failure: Exception) {
                stagedFile.delete()
                throw failure
            }
            nextCaptureNote = ""
            val pending = checkNotNull(draft)
            val saved = try {
                repository.saveCapture(pending)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                page = Page.REVIEW
                notice = "Couldn't save the photo. You can retry from review."
                return@perform
            }
            draft = null
            finishPhoto(saved, target.name)
        }
    }

    internal fun startVideo(camera: VideoCamera, withAudio: Boolean, headingDegrees: Float? = null) {
        val target = project ?: return
        if (captureLocked) return
        val template = stampTemplate
        val note = nextCaptureNote
        val includeFix = useLocation
        busy = true
        prepareRecording = viewModelScope.launch {
            var staged: File? = null
            try {
                val fix = if (includeFix) currentFix(app) else null
                val instant = Instant.now()
                val offset = ZoneId.systemDefault().rules.getOffset(instant).totalSeconds / 60
                val selected = if (template.preset != null && template.showAddress) {
                    template.copy(recordedAddress = resolveStampAddress(app, fix))
                } else template
                val details = selected.preset?.let {
                    stampDetails(target.name, note, instant.toEpochMilli(), offset, fix, selected,
                        logo = if (selected.showLogo) StampLogoStore(app).loadLogo(selected.logoId) else null,
                        headingDegrees = headingDegrees,
                        map = if (selected.showMap) renderStampMap(app, fix) else null,
                    )
                }
                val output = withContext(Dispatchers.IO) { repository.newCaptureFile(isVideo = true) }
                staged = output
                val pending = CaptureDraft(
                    output, target.id, instant.toEpochMilli(), offset, note, fix,
                    stampJson = selected.toJson(), isVideo = true,
                    headingDegrees = headingDegrees.takeIf { selected.showCompass },
                )
                recording = camera.start(app, output, details, withAudio, { recordingStatus = it }) { result ->
                    recording = null
                    recordingStatus = null
                    recordingFinishing = false
                    if (disposed) {
                        output.delete()
                    } else {
                        result.onSuccess {
                            draft = pending
                            nextCaptureNote = ""
                            page = Page.REVIEW
                        }.onFailure {
                            output.delete()
                            notice = "Couldn't record this clip. Try again."
                        }
                    }
                }
                recordingStatus = VideoRecordingStatus(0, false)
            } catch (failure: Exception) {
                staged?.delete()
                if (failure is CancellationException) throw failure
                notice = "Couldn't start recording."
            } finally {
                busy = false
            }
        }
    }

    internal fun toggleVideoPause() {
        if (recordingFinishing) return
        val active = recording ?: return
        if (recordingStatus?.paused == true) active.resume() else active.pause()
    }

    internal fun stopVideo() {
        prepareRecording?.cancel()
        val active = recording ?: return
        if (!recordingFinishing) {
            recordingFinishing = true
            active.stop()
        }
    }

    override fun onCleared() {
        disposed = true
        stopVideo()
        super.onCleared()
    }

    fun save() {
        val pending = draft ?: return
        perform("Couldn't save this photo. Your draft is still here.") {
            val saved = repository.saveCapture(pending)
            draft = null
            if (saved.isVideo) {
                photoReturnPage = Page.CAMERA
                openPhoto(saved)
                notice = "Clip saved"
            } else {
                page = Page.CAMERA
                finishPhoto(saved, project?.name ?: "Project")
            }
        }
    }

    fun savePhotoToGallery(photo: Photo, projectName: String) {
        if (photo.isVideo) return
        perform("Couldn't add this photo to Gallery.") {
            val template = StampTemplate.fromJson(photo.stampJson)
            val stamped = template.preset != null
            val file = prepareCaptureCopy(photo, projectName, template)
            galleryLookup?.cancel()
            gallery.publish(photo, file, stamped)
            photoInGallery = true
            notice = "Photo saved to Gallery"
        }
    }

    private suspend fun finishPhoto(photo: Photo, projectName: String) {
        val template = StampTemplate.fromJson(photo.stampJson)
        val stamped = template.preset != null
        val file = try {
            prepareCaptureCopy(photo, projectName, template)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val gallerySaved = if (autoSaveToGallery && file != null) {
            try {
                gallery.publish(photo, file, stamped)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        } else null
        quickPhoto = QuickPhoto(photo, file, stamped, gallerySaved)
    }

    private suspend fun prepareCaptureCopy(photo: Photo, projectName: String, template: StampTemplate): File {
        val original = repository.originalFile(photo)
        if (template.preset == null) return original
        val map = if (template.showMap) renderStampMap(app, photo.location) else null
        return withContext(Dispatchers.IO) {
            try {
                createStampedCopy(app, original, stampDetails(
                    projectName, photo.note, photo.takenAtMillis, photo.utcOffsetMinutes, photo.location, template,
                    photoCode = photo.id, headingDegrees = photo.headingDegrees, map = map,
                ))
            } finally {
                map?.recycle()
            }
        }
    }

    internal fun dismissQuickPhoto() {
        quickPhoto = null
    }

    internal fun shareQuickPhoto() {
        val file = quickPhoto?.file ?: return
        dismissQuickPhoto()
        shareRequest = ShareRequest(file, "image/jpeg")
    }

    internal fun openQuickPhotoDetails() {
        val photo = quickPhoto?.photo ?: return
        dismissQuickPhoto()
        photoReturnPage = Page.CAMERA
        openPhoto(photo)
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
        if (file.isFile) {
            shareRequest = ShareRequest(file, if (photo.isVideo) "video/mp4" else "image/jpeg")
        } else notice = "The original file is missing."
    }

    internal fun sharePhotoCopy(photo: Photo, details: StampDetails?, marks: List<PhotoMark>) {
        perform("Couldn't prepare the photo copy.") {
            val file = withContext(Dispatchers.IO) {
                createStampedCopy(app, repository.originalFile(photo), details) { bitmap ->
                    drawPhotoMarks(android.graphics.Canvas(bitmap), bitmap.width, bitmap.height, marks)
                }
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
        photoInGallery = false
        galleryLookup?.cancel()
        if (!photo.isVideo) {
            galleryLookup = viewModelScope.launch {
                try {
                    val stamped = StampTemplate.fromJson(photo.stampJson).preset != null
                    photoInGallery = gallery.find(photo, stamped) != null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    photoInGallery = false
                }
            }
        }
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
