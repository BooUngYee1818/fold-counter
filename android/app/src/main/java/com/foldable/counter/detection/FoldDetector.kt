package com.foldable.counter.detection

enum class Posture { UNKNOWN, OPEN, HALF, CLOSED }

fun interface FoldListener {
    fun onPosture(posture: Posture, detail: String)
}

/** 접힘 감지 방식. 결과는 모두 열림/반쯤/닫힘 셋 중 하나로 통일합니다. */
interface FoldDetector {
    val id: String
    fun start()
    fun stop()

    companion object {
        const val HINGE = "hinge"
        const val DISPLAY = "display"
    }
}
