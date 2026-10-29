package com.foldable.counter.detection

import android.content.Context
import com.foldable.counter.Prefs

object FoldDetectorFactory {
    fun create(context: Context, prefs: Prefs, listener: FoldListener): FoldDetector {
        val hinge = HingeAngleDetector.isAvailable(context)
        return when (prefs.detectorMode) {
            Prefs.DET_DISPLAY -> DisplayStateDetector(context, prefs, listener)
            else -> if (hinge) HingeAngleDetector(context, prefs, listener) else DisplayStateDetector(context, prefs, listener)
        }
    }
}
