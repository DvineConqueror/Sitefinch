package app.workphotolog.android

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

private const val headingExpiryMillis = 2_000L
private const val headingExpiryNanos = headingExpiryMillis * 1_000_000
private val allowedHeadingErrorRadians = (35 * PI / 180).toFloat()

@Composable
internal fun rememberCaptureHeading(enabled: Boolean, frontFacing: Boolean = false): Float? {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val compass = remember(manager) {
        manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    }
    var heading by remember(enabled, frontFacing, compass) { mutableStateOf<Float?>(null) }

    if (enabled && manager != null && compass != null) {
        LifecycleResumeEffect(manager, compass, frontFacing) {
            val matrix = FloatArray(9)
            val handler = Handler(Looper.getMainLooper())
            val expire = Runnable { heading = null }
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    handler.removeCallbacks(expire)
                    val ageNanos = SystemClock.elapsedRealtimeNanos() - event.timestamp
                    val error = event.values.getOrNull(4)
                    if (event.accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ||
                        ageNanos !in 0L until headingExpiryNanos ||
                        (error != null && (!error.isFinite() || error > allowedHeadingErrorRadians))
                    ) {
                        heading = null
                        return
                    }
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    heading = cameraHeadingDegrees(matrix, frontFacing)
                    if (heading != null) {
                        handler.postDelayed(expire, headingExpiryMillis - ageNanos / 1_000_000L)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
                        handler.removeCallbacks(expire)
                        heading = null
                    }
                }
            }
            manager.registerListener(listener, compass, 200_000, handler)
            onPauseOrDispose {
                manager.unregisterListener(listener)
                handler.removeCallbacks(expire)
                heading = null
            }
        }
    }
    return heading
}

internal fun cameraHeadingDegrees(matrix: FloatArray, frontFacing: Boolean): Float? {
    // Display rotation moves around the lens axis, so camera bearing stays the same in landscape.
    val forward = if (frontFacing) 1 else -1
    val east = (forward * matrix[2]).toDouble()
    val north = (forward * matrix[5]).toDouble()
    if (!east.isFinite() || !north.isFinite() || hypot(east, north) < 0.2) return null
    return (Math.toDegrees(atan2(east, north)).toFloat() + 360f) % 360f
}
