package com.foldable.counter

import com.foldable.counter.detection.Posture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HubStatus { SEARCHING, CONNECTED, ERROR }

/** 서비스와 화면이 함께 보는 현재 상태 (메모리). */
object CounterState {
    data class Snapshot(
        val count: Int = 0,
        val deviceName: String = "",
        val posture: Posture = Posture.UNKNOWN,
        val detectorId: String = "",
        val detectorDetail: String = "",
        val windowPosture: String = "",
        val hubStatus: HubStatus = HubStatus.SEARCHING,
        val hubUrl: String? = null,
        val pending: Int = 0,
        val lastFoldAt: Long = 0L,
        val log: List<String> = emptyList(),
    )

    private val _flow = MutableStateFlow(Snapshot())
    val flow: StateFlow<Snapshot> get() = _flow
    val value: Snapshot get() = _flow.value

    fun update(block: Snapshot.() -> Snapshot) = _flow.update(block)

    fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        update { copy(log = (listOf("$ts  $msg") + log).take(40)) }
    }
}
