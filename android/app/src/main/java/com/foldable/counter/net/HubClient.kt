package com.foldable.counter.net

import android.content.Context
import android.os.Build
import com.foldable.counter.BuildConfig
import com.foldable.counter.CounterState
import com.foldable.counter.CounterStore
import com.foldable.counter.HubStatus
import com.foldable.counter.Prefs
import com.foldable.counter.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * PC 허브와의 통신. 같은 네트워크 안에서만 동작하며 외부 서버는 없습니다.
 * - 자동 검색: UDP 브로드캐스트 → 허브가 자기 포트를 응답
 * - 등록 → 접힘 전송 → 5초마다 하트비트. 끊기면 접힘 횟수를 모아두었다가 재연결 시 전송.
 * - 카운트는 허브가 기준입니다 (관리 페이지에서 바꾼 값이 폰에도 반영).
 */
class HubClient(context: Context, private val prefs: Prefs) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val kicks = Channel<Unit>(Channel.CONFLATED)
    private val pending = AtomicInteger(prefs.pendingFolds)

    @Volatile private var baseUrl: String? = null
    @Volatile private var registered = false
    @Volatile private var pendingSetCount: Int? = null

    fun start() { scope.launch { loop() } }
    fun stop() { scope.cancel() }

    fun resetConnection() { baseUrl = null; registered = false; kick() }
    fun renamed() { registered = false; kick() }

    /** 접힘 1회. 허브와 끊겨 있어도 모아두었다가 연결되면 한꺼번에 보냅니다. */
    fun onFold() {
        val n = pending.incrementAndGet()
        prefs.pendingFolds = n
        CounterState.update { copy(pending = n) }
        kick()
    }

    /** 리셋 등 폰에서 정한 값을 허브에도 반영 */
    fun pushCount(n: Int) {
        pendingSetCount = n
        kick()
    }

    private fun kick() { kicks.trySend(Unit) }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            try {
                val url = baseUrl ?: discover()
                if (url == null) {
                    setStatus(HubStatus.SEARCHING, null)
                    waitKick(3000)
                    continue
                }
                baseUrl = url
                if (!registered) register(url)
                pendingSetCount?.let { n ->
                    http("POST", "$url/api/device/${prefs.deviceId}", JSONObject().put("count", n))
                    pendingSetCount = null
                }
                flush(url)
                heartbeat(url)
                setStatus(HubStatus.CONNECTED, url)
                waitKick(5000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CounterState.log(app.getString(R.string.log_hub_error, e.message ?: e.javaClass.simpleName))
                setStatus(HubStatus.ERROR, baseUrl)
                baseUrl = null
                registered = false
                waitKick(2000)
            }
        }
    }

    private suspend fun waitKick(ms: Long) { withTimeoutOrNull(ms) { kicks.receive() } }

    private fun setStatus(s: HubStatus, url: String?) {
        CounterState.update { copy(hubStatus = s, hubUrl = url) }
    }

    // ---------- 검색 ----------
    private fun discover(): String? {
        if (prefs.hubMode == Prefs.HUB_MANUAL && prefs.hubHost.isNotBlank()) {
            val u = "http://${prefs.hubHost.trim()}:${prefs.hubPort}"
            return if (ping(u)) u else null
        }
        prefs.lastHubUrl?.let { if (ping(it)) return it }
        return udpDiscover()?.also { prefs.lastHubUrl = it }
    }

    private fun ping(url: String): Boolean = try {
        JSONObject(http("GET", "$url/api/ping", null)).optString("hub") == "foldcounter"
    } catch (e: Exception) { false }

    private fun udpDiscover(): String? {
        try {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = 1500
                val payload = DISCOVERY_MAGIC.toByteArray()
                val targets = broadcastAddresses() + InetAddress.getByName("255.255.255.255")
                for (a in targets) {
                    try { sock.send(DatagramPacket(payload, payload.size, a, DISCOVERY_PORT)) } catch (_: Exception) {}
                }
                val buf = ByteArray(256)
                val deadline = System.currentTimeMillis() + 1500
                while (System.currentTimeMillis() < deadline) {
                    val pkt = DatagramPacket(buf, buf.size)
                    try { sock.receive(pkt) } catch (e: SocketTimeoutException) { break }
                    val msg = String(pkt.data, 0, pkt.length).trim()
                    if (msg.startsWith(DISCOVERY_REPLY)) {
                        val port = msg.substringAfter(' ', "").trim().toIntOrNull() ?: 8787
                        return "http://${pkt.address.hostAddress}:$port"
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun broadcastAddresses(): List<InetAddress> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
    } catch (e: Exception) { emptyList() }

    // ---------- API ----------
    private fun register(url: String) {
        val body = JSONObject()
            .put("deviceId", prefs.deviceId)
            .put("name", prefs.deviceName)
            .put("model", Build.MODEL)
            .put("manufacturer", Build.MANUFACTURER)
            .put("platform", "android")
            .put("osVersion", Build.VERSION.RELEASE)
            .put("detector", CounterState.value.detectorId)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("count", CounterStore.get())
        val r = JSONObject(http("POST", "$url/api/register", body))
        registered = true
        adopt(r.getJSONObject("device"))
        CounterState.log(app.getString(R.string.log_hub_connected, url))
    }

    private fun flush(url: String) {
        val n = pending.get()
        if (n <= 0) return
        val r = JSONObject(http("POST", "$url/api/fold", JSONObject().put("deviceId", prefs.deviceId).put("delta", n)))
        val left = pending.addAndGet(-n)
        prefs.pendingFolds = left
        CounterState.update { copy(pending = left) }
        adopt(r.getJSONObject("device"))
    }

    private fun heartbeat(url: String) {
        val s = CounterState.value
        val body = JSONObject().put("deviceId", prefs.deviceId).put("detector", s.detectorId).put("posture", s.posture.name)
        val r = JSONObject(http("POST", "$url/api/heartbeat", body))
        if (r.has("device")) adopt(r.getJSONObject("device"))
    }

    /** 허브 값을 받아들입니다. 단, 아직 못 보낸 접힘이 있으면 덮어쓰지 않습니다. */
    private fun adopt(d: JSONObject) {
        if (pending.get() > 0 || pendingSetCount != null) return
        val c = d.optInt("count", -1)
        if (c >= 0 && c != CounterStore.get()) CounterStore.set(c)
        val name = d.optString("displayName", "")
        if (name.isNotBlank()) CounterState.update { copy(deviceName = name) }
    }

    private fun http(method: String, url: String, body: JSONObject?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 2500
            conn.readTimeout = 3000
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code $text")
            return text
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DISCOVERY_PORT = 47777
        const val DISCOVERY_MAGIC = "FOLDCOUNTER_DISCOVER"
        const val DISCOVERY_REPLY = "FOLDCOUNTER_HUB"
    }
}
