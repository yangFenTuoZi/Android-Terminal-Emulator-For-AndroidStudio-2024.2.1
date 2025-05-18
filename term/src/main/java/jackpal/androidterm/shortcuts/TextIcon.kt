package jackpal.androidterm.shortcuts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlin.math.abs
import kotlin.math.ceil

object TextIcon {
    @JvmStatic
    fun getTextIcon(text: String, color: Int, width: Int, height: Int): Bitmap {
        val trimmedText = text.trim()
        val lines = trimmedText.split("\\s*\n\\s*".toRegex()).toTypedArray()
        val nLines = lines.size
        val rect = Rect()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.setShadowLayer(2f, 10f, 10f, 0xFF000000.toInt())
        paint.color = color
        paint.isSubpixelText = true
        paint.textSize = 256f
        paint.textAlign = Align.CENTER
        val HH = FloatArray(nLines)
        var H = 0f
        var W = 0f
        for (i in 0 until nLines) {
            paint.getTextBounds(lines[i], 0, lines[i].length, rect)
            var h = abs(rect.top - rect.bottom).toFloat()
            var w = abs(rect.right - rect.left).toFloat()
            if (nLines > 1) h += 0.1f * h // Add space between lines.
            HH[i] = h
            H += h
            if (w > W) W = w
        }
        val f = width.toFloat() * H / height.toFloat()
        var hBitmap = H.toInt()
        var wBitmap = W.toInt()
        if (W < f) {
            wBitmap = ceil(f.toDouble()).toInt()
            hBitmap = ceil(H.toDouble()).toInt()
        } else {
            wBitmap = ceil(W.toDouble()).toInt()
            hBitmap = (height * wBitmap / width.toFloat()).toInt()
        }
        val b = createBitmap(wBitmap, hBitmap)
        b.density = Bitmap.DENSITY_NONE
        val c = Canvas(b)
        val centerW = wBitmap / 2f
        var top = hBitmap / 2f - H / 2f + HH[0] / 2f
        for (i in 0 until nLines) {
            top += HH[i] / 2f
            c.drawText(lines[i], centerW, top, paint)
            top += HH[i] / 2f
        }
        return b.scale(width, height)
    }
}

