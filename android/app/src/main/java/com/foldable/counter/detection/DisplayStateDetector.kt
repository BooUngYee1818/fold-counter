package com.foldable.counter.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.content.ContextCompat
import com.foldable.counter.Prefs
import kotlin.math.min

/**
 * 2순위 (힌지 센서가 없는 구형 기기용) 화면 상태 감지.
 * - 폴드형: 접으면 기본 디스플레이가 커버 화면(작은 폭)으로 바뀌거나 화면이 꺼짐 → 접힘
 * - 플립형: 접으면 메인 화면이 꺼짐 → 접힘
 * 한계: 전원 버튼으로 화면을 껐다 켜도 1회로 셉니다. 앱 고정 + 화면 켜짐 유지 상태에서 쓰세요.
 */
class DisplayStateDetector(
    private val context: Context,
    private val prefs: Prefs,
    private val listener: FoldListener,
) : FoldDetector {

    override val id: String = FoldDetector.DISPLAY
    private val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val handler = Handler(Looper.getMainLooper())
    private var screenOn = true
    private var maxWidth = prefs.innerWidth

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                Intent.ACTION_SCREEN_ON -> screenOn = true
            }
            evaluate()
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = evaluate()
        override fun onDisplayRemoved(displayId: Int) = evaluate()
        override fun onDisplayChanged(displayId: Int) = evaluate()
    }

    override fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        dm.registerDisplayListener(displayListener, handler)
        evaluate()
    }

    override fun stop() {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        dm.unregisterDisplayListener(displayListener)
    }

    private fun evaluate() {
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        d.getRealMetrics(metrics)
        val w = min(metrics.widthPixels, metrics.heightPixels)
        if (w > maxWidth) {
            maxWidth = w
            prefs.innerWidth = w
        }
        val off = !screenOn || d.state != Display.STATE_ON
        val small = maxWidth > 0 && w < maxWidth * 0.8f
        val posture = if (off || small) Posture.CLOSED else Posture.OPEN
        listener.onPosture(posture, "screen ${if (off) "off" else "on"}, width ${w}px / max ${maxWidth}px")
    }
}
