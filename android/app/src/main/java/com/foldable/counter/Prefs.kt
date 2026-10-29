package com.foldable.counter

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** 앱 설정과 카운트 저장. 모두 이 폰 안에만 저장됩니다. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("foldcounter", Context.MODE_PRIVATE)

    /** 앱 설치 시 만들어지는 무작위 ID. IMEI, ANDROID_ID 같은 기기 고유 식별자는 쓰지 않습니다. */
    val deviceId: String
        get() = sp.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("deviceId", it).apply()
        }

    var deviceName: String
        get() = sp.getString("deviceName", "") ?: ""
        set(v) = sp.edit().putString("deviceName", v).apply()

    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) = sp.edit().putBoolean("setupDone", v).apply()

    /** 카운트는 즉시 디스크에 기록(commit)해서 앱이 강제 종료돼도 남습니다. */
    var count: Int
        get() = sp.getInt("count", 0)
        set(v) { sp.edit().putInt("count", v).commit() }

    var pendingFolds: Int
        get() = sp.getInt("pendingFolds", 0)
        set(v) { sp.edit().putInt("pendingFolds", v).commit() }

    var hubMode: String
        get() = sp.getString("hubMode", HUB_AUTO) ?: HUB_AUTO
        set(v) = sp.edit().putString("hubMode", v).apply()

    var hubHost: String
        get() = sp.getString("hubHost", "") ?: ""
        set(v) = sp.edit().putString("hubHost", v).apply()

    var hubPort: Int
        get() = sp.getInt("hubPort", 8787)
        set(v) = sp.edit().putInt("hubPort", v).apply()

    var lastHubUrl: String?
        get() = sp.getString("lastHubUrl", null)
        set(v) = sp.edit().putString("lastHubUrl", v).apply()

    var detectorMode: String
        get() = sp.getString("detectorMode", DET_AUTO) ?: DET_AUTO
        set(v) = sp.edit().putString("detectorMode", v).apply()

    var closedAngle: Float
        get() = sp.getFloat("closedAngle", 30f)
        set(v) = sp.edit().putFloat("closedAngle", v).apply()

    var openAngle: Float
        get() = sp.getFloat("openAngle", 150f)
        set(v) = sp.edit().putFloat("openAngle", v).apply()

    var multiHinge: String
        get() = sp.getString("multiHinge", MULTI_ANY) ?: MULTI_ANY
        set(v) = sp.edit().putString("multiHinge", v).apply()

    var showName: Boolean
        get() = sp.getBoolean("showName", true)
        set(v) = sp.edit().putBoolean("showName", v).apply()

    var idleMinutes: Int
        get() = sp.getInt("idleMinutes", 3)
        set(v) = sp.edit().putInt("idleMinutes", v).apply()

    var creditText: String
        get() = sp.getString("creditText", "") ?: ""
        set(v) = sp.edit().putString("creditText", v).apply()

    /** 화면 상태 방식에서 학습한 내부 화면 폭(px) */
    var innerWidth: Int
        get() = sp.getInt("innerWidth", 0)
        set(v) = sp.edit().putInt("innerWidth", v).apply()

    companion object {
        const val HUB_AUTO = "auto"
        const val HUB_MANUAL = "manual"
        const val DET_AUTO = "auto"
        const val DET_HINGE = "hinge"
        const val DET_DISPLAY = "display"
        const val MULTI_ANY = "any"
        const val MULTI_ALL = "all"
    }
}
