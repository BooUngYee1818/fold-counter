package com.foldable.counter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foldable.counter.CounterState
import com.foldable.counter.CounterStore
import com.foldable.counter.DeviceNames
import com.foldable.counter.FoldService
import com.foldable.counter.HubStatus
import com.foldable.counter.Prefs
import com.foldable.counter.R
import com.foldable.counter.detection.HingeAngleDetector
import kotlinx.coroutines.launch

/** 첫 실행: 기기 이름 확인, 감지 방식 확인, 허브 연결 확인 후 카운터 화면으로. */
class SetupActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        CounterStore.init(this)
        if (prefs.setupDone) {
            startActivity(Intent(this, CounterActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_setup)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val etName = findViewById<EditText>(R.id.etName)
        val tvDetector = findViewById<TextView>(R.id.tvDetector)
        val tvDetectorNote = findViewById<TextView>(R.id.tvDetectorNote)
        val tvHub = findViewById<TextView>(R.id.tvHub)
        val dot = findViewById<View>(R.id.dot)
        val etHost = findViewById<EditText>(R.id.etHost)
        val etPort = findViewById<EditText>(R.id.etPort)

        findViewById<TextView>(R.id.tvCredit).text =
            prefs.creditText.ifBlank { getString(R.string.credit_default) }
        etName.setText(prefs.deviceName.ifBlank { DeviceNames.detect(this) })

        val hinge = HingeAngleDetector.isAvailable(this)
        tvDetector.text = getString(if (hinge) R.string.detector_hinge else R.string.detector_display) +
            "  ·  " + Build.MANUFACTURER + " " + Build.MODEL
        tvDetectorNote.visibility = if (hinge) View.GONE else View.VISIBLE

        etHost.setText(prefs.hubHost)
        etPort.setText(prefs.hubPort.toString())

        FoldService.start(this)

        findViewById<Button>(R.id.btnManual).setOnClickListener {
            val host = etHost.text.toString().trim()
            if (host.isEmpty()) return@setOnClickListener
            prefs.hubMode = Prefs.HUB_MANUAL
            prefs.hubHost = host
            prefs.hubPort = etPort.text.toString().toIntOrNull() ?: 8787
            FoldService.send(this, FoldService.ACTION_RECONNECT)
        }
        findViewById<Button>(R.id.btnAuto).setOnClickListener {
            prefs.hubMode = Prefs.HUB_AUTO
            FoldService.send(this, FoldService.ACTION_RECONNECT)
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            prefs.deviceName = etName.text.toString().trim().ifBlank { DeviceNames.detect(this) }
            prefs.setupDone = true
            FoldService.send(this, FoldService.ACTION_SETTINGS_CHANGED)
            startActivity(Intent(this, CounterActivity::class.java))
            finish()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CounterState.flow.collect { s ->
                    val (color, text) = when (s.hubStatus) {
                        HubStatus.CONNECTED -> R.color.ok to getString(R.string.hub_connected, s.hubUrl ?: "")
                        HubStatus.SEARCHING -> R.color.warn to getString(R.string.hub_searching)
                        HubStatus.ERROR -> R.color.bad to getString(R.string.hub_error)
                    }
                    (dot.background.mutate() as? GradientDrawable)?.setColor(ContextCompat.getColor(this@SetupActivity, color))
                    tvHub.text = text
                }
            }
        }
    }
}
