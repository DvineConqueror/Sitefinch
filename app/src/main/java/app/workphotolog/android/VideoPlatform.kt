package app.workphotolog.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import androidx.annotation.MainThread
import androidx.camera.core.CameraEffect
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class VideoRecordingStatus(val elapsedSeconds: Long, val paused: Boolean)

internal data class VideoClip(val file: File, val durationMillis: Long)

/** Owns the video use case and its overlay thread. The caller holds the active Recording. */
internal class VideoCamera(private val onEffectError: (Throwable) -> Unit) : Closeable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlayThread = HandlerThread("work-photo-video-overlay").apply { start() }

    @Volatile
    private var closed = false

    @Volatile
    private var active: RecordingOverlay? = null

    private val recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)),
        )
        .build()

    val capture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    val effect = OverlayEffect(
        CameraEffect.VIDEO_CAPTURE,
        0,
        Handler(overlayThread.looper),
        ::reportEffectFailure,
    )

    private var nextRotation: Int? = null
    private var previousStamp: RecordingOverlay? = null
    private var previousFrame: FrameGeometry? = null

    init {
        effect.setOnDrawListener { frame ->
            try {
                renderOverlay(frame)
                true
            } catch (failure: RuntimeException) {
                reportEffectFailure(failure)
                false
            }
        }
    }

    @MainThread
    fun updateRotation(rotation: Int) {
        if (closed) return
        nextRotation = rotation
        if (active == null) capture.targetRotation = rotation
    }

    @MainThread
    fun start(
        context: Context,
        output: File,
        stamp: StampDetails?,
        withAudio: Boolean = false,
        onUpdate: (VideoRecordingStatus) -> Unit,
        onFinalized: (Result<VideoClip>) -> Unit,
    ): Recording {
        check(!closed) { "Camera is closed" }
        check(active == null) { "A video recording is already in progress" }
        val overlay = RecordingOverlay(stamp?.takeIf { it.template.preset != null })
        active = overlay
        var paused = false
        var previousStatus: VideoRecordingStatus? = null

        try {
            val options = FileOutputOptions.Builder(output).build()
            val pendingRecording = recorder.prepareRecording(context.applicationContext, options)
            val configuredRecording = if (withAudio) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    throw SecurityException("Microphone access is required to record sound.")
                }
                pendingRecording.withAudioEnabled(false)
            } else {
                pendingRecording
            }
            return configuredRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                val elapsed = event.recordingStats.recordedDurationNanos
                when (event) {
                    is VideoRecordEvent.Finalize -> {
                        active = null
                        if (!closed) nextRotation?.let { capture.targetRotation = it }
                        val result = completeClip(output, overlay, event, elapsed)
                        if (result.isFailure) output.delete()
                        onFinalized(result)
                    }
                    else -> {
                        if (event is VideoRecordEvent.Pause) paused = true
                        if (event is VideoRecordEvent.Resume) paused = false
                        val status = VideoRecordingStatus(TimeUnit.NANOSECONDS.toSeconds(elapsed), paused)
                        if (status != previousStatus) {
                            previousStatus = status
                            onUpdate(status)
                        }
                    }
                }
            }
        } catch (failure: RuntimeException) {
            active = null
            output.delete()
            throw failure
        }
    }

    @MainThread
    override fun close() {
        if (closed) return
        closed = true
        effect.close()
        overlayThread.quitSafely()
    }

    private fun reportEffectFailure(failure: Throwable) {
        if (closed) return
        val recording = active
        if (recording?.failure != null) return
        recording?.failure = failure
        mainHandler.post {
            if (!closed && (recording == null || active === recording)) onEffectError(failure)
        }
    }

    private fun renderOverlay(frame: Frame) {
        val overlay = active?.takeIf { it.details != null }
        val crop = frame.cropRect
        val rotation = frame.rotationDegrees
        val mirrored = frame.isMirroring
        if (overlay == null && previousStamp == null) return
        if (overlay === previousStamp && previousFrame?.matches(frame.size, crop, rotation, mirrored) == true) return
        val canvas = frame.overlayCanvas
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        if (overlay != null) {
            canvas.withVideoFrameCoordinates(crop, rotation, mirrored) { width, height ->
                drawStamp(this, width, height, checkNotNull(overlay.details))
                overlay.drawn = true
            }
        }
        previousStamp = overlay
        previousFrame = FrameGeometry(frame.size, Rect(crop), rotation, mirrored)
    }

    private fun completeClip(
        output: File,
        overlay: RecordingOverlay,
        event: VideoRecordEvent.Finalize,
        durationNanos: Long,
    ): Result<VideoClip> {
        overlay.failure?.let { return Result.failure(IOException("Couldn't render the video.", it)) }
        val allowedError = event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE
        val duration = TimeUnit.NANOSECONDS.toMillis(durationNanos)
        if ((event.hasError() && !allowedError) || output.length() == 0L || duration <= 0L) {
            val message = if (event.error == VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE) {
                "Not enough storage for this clip."
            } else {
                "Couldn't save this clip (camera error ${event.error})."
            }
            return Result.failure(IOException(message, event.cause))
        }
        if (overlay.details != null && !overlay.drawn) {
            return Result.failure(IOException("Couldn't apply the video stamp."))
        }
        return Result.success(VideoClip(output, duration))
    }

    private class RecordingOverlay(val details: StampDetails?) {
        @Volatile var drawn = false
        @Volatile var failure: Throwable? = null
    }

    private class FrameGeometry(
        val size: Size,
        val crop: Rect,
        val rotation: Int,
        val mirrored: Boolean,
    ) {
        fun matches(size: Size, crop: Rect, rotation: Int, mirrored: Boolean): Boolean =
            this.size == size && this.crop == crop && this.rotation == rotation && this.mirrored == mirrored
    }
}

internal fun Canvas.withVideoFrameCoordinates(
    crop: Rect,
    rotation: Int,
    mirrored: Boolean,
    draw: Canvas.(width: Int, height: Int) -> Unit,
) {
    if (crop.isEmpty) return
    val swapDimensions = rotation % 180 != 0
    val width = if (swapDimensions) crop.height() else crop.width()
    val height = if (swapDimensions) crop.width() else crop.height()
    val saveCount = save()
    try {
        clipRect(crop)
        // Map upright overlay coordinates back to CameraX's cropped input buffer.
        translate(crop.exactCenterX(), crop.exactCenterY())
        rotate(-rotation.toFloat())
        if (mirrored) scale(-1f, 1f)
        translate(-width / 2f, -height / 2f)
        draw(width, height)
    } finally {
        restoreToCount(saveCount)
    }
}
