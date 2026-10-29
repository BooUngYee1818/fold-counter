package com.foldable.counter.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.foldable.counter.FoldService
import com.foldable.counter.Prefs
import com.foldable.counter.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val prefs = Prefs(this)

        val etName = findViewById<EditText>(R.id.etName)
        val rgHub = findViewById<RadioGroup>(R.id.rgHub)
        val etHost = findViewById<EditText>(R.id.etHost)
        val etPort = findViewById<EditText>(R.id.etPort)
        val rgDetector = findViewById<RadioGroup>(R.id.rgDetector)
        val etClosed = findViewById<EditText>(R.id.etClosed)
        val etOpen = findViewById<EditText>(R.id.etOpen)
        val rgMulti = findViewById<RadioGroup>(R.id.rgMulti)
        val swShowName = findViewById<SwitchCompat>(R.id.swShowName)
        val etIdle = findViewById<EditText>(R.id.etIdle)
        val etCredit = findViewById<EditText>(R.id.etCredit)

        etName.setText(prefs.deviceName)
        rgHub.check(if (prefs.hubMode == Prefs.HUB_MANUAL) R.id.rbHubManual else R.id.rbHubAuto)
        etHost.setText(prefs.hubHost)
        etPort.setText(prefs.hubPort.toString())
        rgDetector.check(
            when (prefs.detectorMode) {
                Prefs.DET_HINGE -> R.id.rbDetHinge
                Prefs.DET_DISPLAY -> R.id.rbDetDisplay
                else -> R.id.rbDetAuto
            },
        )
        etClosed.setText(prefs.closedAngle.toInt().toString())
        etOpen.setText(prefs.openAngle.toInt().toString())
        rgMulti.check(if (prefs.multiHinge == Prefs.MULTI_ALL) R.id.rbMultiAll else R.id.rbMultiAny)
        swShowName.isChecked = prefs.showName
        etIdle.setText(prefs.idleMinutes.toString())
        etCredit.setText(prefs.creditText.ifBlank { getString(R.string.credit_default) })

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.deviceName = etName.text.toString().trim().ifBlank { prefs.deviceName }
            prefs.hubMode = if (rgHub.checkedRadioButtonId == R.id.rbHubManual) Prefs.HUB_MANUAL else Prefs.HUB_AUTO
            prefs.hubHost = etHost.text.toString().trim()
            prefs.hubPort = etPort.text.toString().toIntOrNull()?.coerceIn(1, 65535) ?: 8787
            prefs.detectorMode = when (rgDetector.checkedRadioButtonId) {
                R.id.rbDetHinge -> Prefs.DET_HINGE
                R.id.rbDetDisplay -> Prefs.DET_DISPLAY
                else -> Prefs.DET_AUTO
            }
            val closed = etClosed.text.toString().toFloatOrNull()?.coerceIn(0f, 179f) ?: 30f
            val open = etOpen.text.toString().toFloatOrNull()?.coerceIn(1f, 360f) ?: 150f
            prefs.closedAngle = closed
            prefs.openAngle = if (open > closed) open else closed + 1f
            prefs.multiHinge = if (rgMulti.checkedRadioButtonId == R.id.rbMultiAll) Prefs.MULTI_ALL else Prefs.MULTI_ANY
            prefs.showName = swShowName.isChecked
            prefs.idleMinutes = etIdle.text.toString().toIntOrNull()?.coerceIn(0, 600) ?: 3
            prefs.creditText = etCredit.text.toString().trim()
            FoldService.send(this, FoldService.ACTION_SETTINGS_CHANGED)
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
