package com.foldable.counter.ui

import android.app.ActivityManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.foldable.counter.CounterState
import com.foldable.counter.CounterStore
import com.foldable.counter.FoldService
import com.foldable.counter.HubStatus
import com.foldable.counter.LogoLoader
import com.foldable.counter.Prefs
import com.foldable.counter.R
import kotlinx.coroutines.launch

private const val PIN_WAIT_MS = 15_000L

/**
 * 검은 화면에 숫자만. 실수로 나가지 않도록 뒤로가기와 음량 버튼은 무시합니다.
 * 메뉴는 화면을 빠르게 5번 탭하면 열립니다 (접는 동작으로는 열리지 않음).
 */
class CounterActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var tvName: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvHint: TextView
    private lateinit var dot: View
    private lateinit var creditPanel: View
    private lateinit var tvCreditBy: TextView
    private lateinit var ivLogo: ImageView

    private val taps = ArrayDeque<Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var idle: IdleWatcher? = null
    private var lastCount = -1
    private var pinPollStart = 0L
    private val pinPoll = object : Runnable {
        override fun run() {
            if (isPinned()) {
                Toast.makeText(this@CounterActivity, R.string.pin_started, Toast.LENGTH_LONG).show()
            } else if (SystemClock.elapsedRealtime() - pinPollStart < PIN_WAIT_MS) {
                handler.postDelayed(this, 500)
            } else {
                pinFailed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        CounterStore.init(this)
        if (!prefs.setupDone) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_counter)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        tvName = findViewById(R.id.tvName)
        tvCount = findViewById(R.id.tvCount)
        tvStatus = findViewById(R.id.tvStatus)
        tvHint = findViewById(R.id.tvHint)
        dot = findViewById(R.id.dot)
        creditPanel = findViewById(R.id.creditPanel)
        tvCreditBy = findViewById(R.id.tvCreditBy)
        ivLogo = findViewById(R.id.ivLogo)

        tvCreditBy.text = getString(R.string.credit_prefix) + " " + prefs.creditText.ifBlank { getString(R.string.credit_default) }
        LogoLoader.load(this)?.let {
            ivLogo.setImageBitmap(it)
            ivLogo.visibility = View.VISIBLE
        }

        FoldService.start(this)

        findViewById<View>(R.id.root).setOnClickListener { onTap() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* 실수로 나가지 않도록 무시 */ }
        })
        handler.postDelayed({ tvHint.animate().alpha(0f).setDuration(600).start() }, 6000)

        idle = IdleWatcher(this, { prefs.idleMinutes * 60_000L }, { showCredit(true) }, { showCredit(false) })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CounterState.flow.collect { render(it) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@CounterActivity)
                    .windowLayoutInfo(this@CounterActivity)
                    .collect { info ->
                        val ff = info.displayFeatures.filterIsInstance<FoldingFeature>()
                        val txt = if (ff.isEmpty()) "-" else ff.joinToString { "${it.state} ${it.orientation}" }
                        CounterState.update { copy(windowPosture = txt) }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        idle?.start()
    }

    override fun onPause() {
        super.onPause()
        idle?.stop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) idle?.activity()
        return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) true
        else super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) true
        else super.onKeyUp(keyCode, event)

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun render(s: CounterState.Snapshot) {
        tvCount.text = s.count.toString()
        if (s.count != lastCount) {
            if (lastCount >= 0) {
                tvCount.scaleX = 1.12f
                tvCount.scaleY = 1.12f
                tvCount.animate().scaleX(1f).scaleY(1f).setDuration(250).start()
                idle?.activity()
            }
            lastCount = s.count
        }
        tvName.text = if (prefs.showName) s.deviceName.ifBlank { prefs.deviceName } else ""
        val (color, text) = when (s.hubStatus) {
            HubStatus.CONNECTED -> R.color.ok to getString(R.string.hub_connected, s.hubUrl ?: "")
            HubStatus.SEARCHING -> R.color.warn to getString(R.string.hub_searching)
            HubStatus.ERROR -> R.color.bad to getString(R.string.hub_error)
        }
        (dot.background.mutate() as? GradientDrawable)?.setColor(ContextCompat.getColor(this, color))
        tvStatus.text = text + "  ·  " + FoldService.detectorLabel(this, s.detectorId) +
            (if (s.pending > 0) "  ·  +" + s.pending else "")
    }

    private fun showCredit(show: Boolean) {
        if (show) {
            tvName.animate().alpha(0f).setDuration(300).start() // 제작자 패널과 겹치지 않게 기기 이름은 잠시 숨김
            creditPanel.alpha = 0f
            creditPanel.visibility = View.VISIBLE
            creditPanel.animate().alpha(1f).setDuration(500).start()
        } else if (creditPanel.visibility == View.VISIBLE) {
            creditPanel.animate().alpha(0f).setDuration(300).withEndAction { creditPanel.visibility = View.GONE }.start()
            tvName.animate().alpha(1f).setDuration(300).start()
        }
    }

    private fun onTap() {
        val now = System.currentTimeMillis()
        taps.addLast(now)
        while (taps.size > 5) taps.removeFirst()
        if (taps.size == 5 && now - taps.first() < 1500) {
            taps.clear()
            showMenu()
        }
    }

    private fun showMenu() {
        val pinned = isPinned()
        val items = arrayOf(
            getString(if (pinned) R.string.menu_unpin else R.string.menu_pin),
            getString(R.string.menu_diagnostics),
            getString(R.string.menu_settings),
            getString(R.string.menu_test_fold),
            getString(R.string.menu_reset),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> togglePin()
                    1 -> startActivity(Intent(this, DiagnosticsActivity::class.java))
                    2 -> startActivity(Intent(this, SettingsActivity::class.java))
                    3 -> FoldService.send(this, FoldService.ACTION_SIMULATE)
                    4 -> confirmReset()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isPinned(): Boolean =
        getSystemService(ActivityManager::class.java).lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    private fun togglePin() {
        if (isPinned()) {
            stopLockTask()
            Toast.makeText(this, R.string.pin_stopped, Toast.LENGTH_SHORT).show()
            return
        }
        try { startLockTask() } catch (_: Exception) {}
        // 시스템의 "앱 고정" 확인창은 앱의 포커스를 바꾸지 않으므로 고정 상태를 잠시 폴링해서 결과를 확인합니다.
        // 시간이 지나도 고정되지 않으면(확인창이 안 떴거나 취소함) 설정 안내를 띄웁니다.
        handler.removeCallbacks(pinPoll)
        pinPollStart = SystemClock.elapsedRealtime()
        handler.postDelayed(pinPoll, 500)
    }

    private fun pinFailed() {
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_failed_title)
            .setMessage(R.string.pin_failed_msg)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                try { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_title)
            .setMessage(R.string.reset_msg)
            .setPositiveButton(R.string.yes) { _, _ -> FoldService.send(this, FoldService.ACTION_RESET) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
