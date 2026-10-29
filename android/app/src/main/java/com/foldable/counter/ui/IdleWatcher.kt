package com.foldable.counter.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

/**
 * 기기를 일정 시간 만지지도 움직이지도 않으면 onIdle, 다시 움직이거나 터치하면 onActive.
 * 움직임은 가속도 센서 변화량으로 판단합니다.
 */
class IdleWatcher(
    context: Context,
    private val timeoutMs: () -> Long,
    private val onIdle: () -> Unit,
    private val onActive: () -> Unit,
) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val handler = Handler(Looper.getMainLooper())
    private var last: FloatArray? = null
    private var idle = false
    private val fire = Runnable { idle = true; onIdle() }

    fun start() {
        accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        last = null
        reschedule()
    }

    fun stop() {
        sm.unregisterListener(this)
        handler.removeCallbacks(fire)
    }

    fun activity() {
        if (idle) {
            idle = false
            onActive()
        }
        reschedule()
    }

    private fun reschedule() {
        handler.removeCallbacks(fire)
        val t = timeoutMs()
        if (t > 0) handler.postDelayed(fire, t)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val l = last
        if (l != null) {
            val d = abs(e.values[0] - l[0]) + abs(e.values[1] - l[1]) + abs(e.values[2] - l[2])
            if (d > MOVE_THRESHOLD) activity()
        }
        last = e.values.clone()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val MOVE_THRESHOLD = 1.0f // m/s² 합산 변화량
    }
}
