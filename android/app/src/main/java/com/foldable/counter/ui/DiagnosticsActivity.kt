package com.foldable.counter.ui

import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foldable.counter.CounterState
import com.foldable.counter.FoldService
import com.foldable.counter.HubStatus
import com.foldable.counter.Prefs
import com.foldable.counter.R
import kotlinx.coroutines.launch

/** 새 기기에서 어떤 감지 방식이 잡혔는지, 각도가 실시간으로 바뀌는지 바로 확인하는 화면. */
class DiagnosticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        val prefs = Prefs(this)
        val tvBody = findViewById<TextView>(R.id.tvBody)

        findViewById<Button>(R.id.btnReconnect).setOnClickListener { FoldService.send(this, FoldService.ACTION_RECONNECT) }
        findViewById<Button>(R.id.btnTest).setOnClickListener { FoldService.send(this, FoldService.ACTION_SIMULATE) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CounterState.flow.collect { s ->
                    val hub = when (s.hubStatus) {
                        HubStatus.CONNECTED -> getString(R.string.hub_connected, s.hubUrl ?: "")
                        HubStatus.SEARCHING -> getString(R.string.hub_searching)
                        HubStatus.ERROR -> getString(R.string.hub_error)
                    }
                    tvBody.text = getString(
                        R.string.diag_body,
                        FoldService.detectorLabel(this@DiagnosticsActivity, s.detectorId),
                        s.detectorDetail.ifBlank { "-" },
                        FoldService.postureLabel(this@DiagnosticsActivity, s.posture),
                        s.windowPosture.ifBlank { "-" },
                        hub,
                        s.pending,
                        s.count,
                        prefs.deviceId,
                        Build.MANUFACTURER + " " + Build.MODEL,
                        Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
                        s.log.joinToString("\n").ifBlank { "-" },
                    )
                }
            }
        }
    }
}
