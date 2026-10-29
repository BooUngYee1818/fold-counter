package com.foldable.counter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * assets/logo.png 가 있으면 읽어서 녹색(크로마키) 배경을 투명하게 만든 뒤 돌려줍니다.
 * 파일이 없으면 null (글자만 표시).
 */
object LogoLoader {
    @Volatile private var cached: Bitmap? = null
    @Volatile private var tried = false

    fun load(context: Context): Bitmap? {
        if (tried) return cached
        tried = true
        cached = try {
            context.assets.open("logo.png").use { BitmapFactory.decodeStream(it) }?.let { keyOutGreen(it) }
        } catch (e: Exception) {
            null
        }
        return cached
    }

    fun keyOutGreen(src: Bitmap): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        bmp.setHasAlpha(true)
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            val greenness = g - maxOf(r, b)
            if (greenness > 60) {
                px[i] = 0
            } else if (greenness > 20) {
                val a = ((1f - (greenness - 20) / 40f) * 255).toInt().coerceIn(0, 255)
                px[i] = (a shl 24) or (c and 0xffffff)
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }
}
