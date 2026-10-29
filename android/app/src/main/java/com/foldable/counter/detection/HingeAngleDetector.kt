package com.foldable.counter.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import com.foldable.counter.Prefs
import java.util.Locale

/**
 * 1순위: 힌지 각도 센서 (Android 11+ 공식 센서, 또는 제조사가 "hinge" 이름으로 노출한 센서).
 * 0° = 완전히 접힘, 180° = 완전히 펼침. 서피스 듀오는 360° 까지 갑니다.
 * 접힘/펼침 기준 각도에 여유(히스테리시스)를 두어 경계에서 떨리는 값을 무시합니다.
 */
class HingeAngleDetector(
    context: Context,
    private val prefs: Prefs,
    private val listener: FoldListener,
) : FoldDetector, SensorEventListener {

    override val id: String = FoldDetector.HINGE
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensors: List<Sensor> = findHingeSensors(sm)
    private val angles = FloatArray(sensors.size) { Float.NaN }

    override fun start() {
        for (s in sensors) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    override fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val i = sensors.indexOf(event.sensor)
        if (i < 0 || event.values.isEmpty()) return
        angles[i] = event.values[0]
        evaluate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun evaluate() {
        val known = angles.filter { !it.isNaN() }
        if (known.isEmpty()) return
        val closedT = prefs.closedAngle
        val openT = prefs.openAngle
        val anyClosed = known.any { it <= closedT }
        val allClosed = known.all { it <= closedT }
        val allOpen = known.all { it >= openT }
        val closed = if (prefs.multiHinge == Prefs.MULTI_ALL) allClosed else anyClosed
        val posture = when {
            closed -> Posture.CLOSED
            allOpen -> Posture.OPEN
            else -> Posture.HALF
        }
        listener.onPosture(posture, known.joinToString(" / ") { String.format(Locale.US, "%.0f°", it) })
    }

    companion object {
        fun findHingeSensors(sm: SensorManager): List<Sensor> {
            val out = mutableListOf<Sensor>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                out += sm.getSensorList(Sensor.TYPE_HINGE_ANGLE)
            }
            if (out.isEmpty()) {
                out += sm.getSensorList(Sensor.TYPE_ALL).filter { s ->
                    s.name.contains("hinge", ignoreCase = true) || (s.stringType ?: "").contains("hinge", ignoreCase = true)
                }
            }
            return out.distinctBy { it.name + "#" + it.type }
        }

        fun isAvailable(context: Context): Boolean =
            findHingeSensors(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).isNotEmpty()
    }
}
