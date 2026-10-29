package com.foldable.counter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.foldable.counter.detection.FoldDetector
import com.foldable.counter.detection.FoldDetectorFactory
import com.foldable.counter.detection.FoldListener
import com.foldable.counter.detection.Posture
import com.foldable.counter.net.HubClient
import com.foldable.counter.ui.CounterActivity

/**
 * 접힘 감지와 허브 통신을 맡는 포그라운드 서비스.
 * 화면이 꺼지거나 앱이 가려져도(접었을 때) 계속 동작합니다.
 * 접힘(CLOSED) 뒤에 펼침(OPEN)이 오면 1회로 셉니다. 반쯤 접힌 상태는 세지 않습니다.
 */
class FoldService : Service(), FoldListener {

    private lateinit var prefs: Prefs
    private lateinit var hub: HubClient
    private var detector: FoldDetector? = null
    private var armed = false
    private var lastCountAt = 0L
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        CounterStore.init(this)
        createChannel()
        startForegroundCompat()
        hub = HubClient(this, prefs)
        hub.start()
        startDetector()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESET -> {
                CounterStore.set(0)
                hub.pushCount(0)
                CounterState.log(getString(R.string.log_reset))
                updateNotification()
            }
            ACTION_SETTINGS_CHANGED -> {
                CounterState.update { copy(deviceName = prefs.deviceName) }
                startDetector()
                hub.renamed()
                hub.resetConnection()
            }
            ACTION_RECONNECT -> hub.resetConnection()
            ACTION_SIMULATE -> {
                val d = getString(R.string.detector_simulated)
                onPosture(Posture.CLOSED, d)
                main.postDelayed({ onPosture(Posture.OPEN, d) }, 400)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        detector?.stop()
        hub.stop()
        running = false
        super.onDestroy()
    }

    private fun startDetector() {
        detector?.stop()
        armed = false
        val d = FoldDetectorFactory.create(this, prefs, this)
        detector = d
        CounterState.update { copy(detectorId = d.id, posture = Posture.UNKNOWN, detectorDetail = "") }
        d.start()
        CounterState.log(getString(R.string.log_detector, detectorLabel(this, d.id)))
        updateNotification()
    }

    override fun onPosture(posture: Posture, detail: String) {
        CounterState.update { copy(posture = posture, detectorDetail = detail) }
        when (posture) {
            Posture.CLOSED -> if (!armed) {
                armed = true
                CounterState.log(getString(R.string.log_closed, detail))
            }
            Posture.OPEN -> if (armed) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastCountAt >= MIN_INTERVAL_MS) {
                    armed = false
                    lastCountAt = now
                    val n = CounterStore.increment()
                    hub.onFold()
                    CounterState.log(getString(R.string.log_unfold, n, detail))
                    updateNotification()
                }
            }
            else -> Unit
        }
    }

    // ---------- 알림 ----------
    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, CounterActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val s = CounterState.value
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text, s.count, detectorLabel(this, s.detectorId)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pi)
            .build()
    }

    private fun startForegroundCompat() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    companion object {
        const val CHANNEL = "fold"
        const val NOTIF_ID = 1
        const val MIN_INTERVAL_MS = 500L
        const val ACTION_RESET = "com.foldable.counter.RESET"
        const val ACTION_SETTINGS_CHANGED = "com.foldable.counter.SETTINGS_CHANGED"
        const val ACTION_RECONNECT = "com.foldable.counter.RECONNECT"
        const val ACTION_SIMULATE = "com.foldable.counter.SIMULATE"

        @Volatile var running = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FoldService::class.java))
        }

        fun send(context: Context, action: String) {
            ContextCompat.startForegroundService(context, Intent(context, FoldService::class.java).setAction(action))
        }

        fun detectorLabel(context: Context, id: String): String = when (id) {
            FoldDetector.HINGE -> context.getString(R.string.detector_hinge)
            FoldDetector.DISPLAY -> context.getString(R.string.detector_display)
            else -> id
        }

        fun postureLabel(context: Context, p: Posture): String = when (p) {
            Posture.OPEN -> context.getString(R.string.posture_open)
            Posture.HALF -> context.getString(R.string.posture_half)
            Posture.CLOSED -> context.getString(R.string.posture_closed)
            Posture.UNKNOWN -> context.getString(R.string.posture_unknown)
        }
    }
}
