package com.foldable.counter

import android.content.Context

/** 카운트의 단일 진입점. 디스크(Prefs)와 화면 상태(CounterState)를 함께 갱신합니다. */
object CounterStore {
    @Volatile private var prefs: Prefs? = null

    fun init(context: Context) {
        if (prefs == null) synchronized(this) {
            if (prefs == null) {
                val p = Prefs(context)
                prefs = p
                CounterState.update { copy(count = p.count, pending = p.pendingFolds, deviceName = p.deviceName) }
            }
        }
    }

    private fun p(): Prefs = prefs ?: error("CounterStore.init() must be called first")

    @Synchronized fun get(): Int = p().count

    @Synchronized fun increment(): Int {
        val n = p().count + 1
        p().count = n
        CounterState.update { copy(count = n, lastFoldAt = System.currentTimeMillis()) }
        return n
    }

    @Synchronized fun set(n: Int) {
        p().count = n
        CounterState.update { copy(count = n) }
    }
}
