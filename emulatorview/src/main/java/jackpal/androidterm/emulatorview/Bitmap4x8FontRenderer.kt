/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jackpal.androidterm.emulatorview

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

internal class Bitmap4x8FontRenderer(resources: Resources?, scheme: ColorScheme?) :
    BaseTextRenderer(scheme) {
    private val mFont: Bitmap
    private var mCurrentForeColor = 0
    private var mCurrentBackColor = 0
    private var mColorMatrix: FloatArray? = null
    private val mPaint: Paint

    init {
        val fontResource = R.drawable.atari_small_nodpi
        mFont = BitmapFactory.decodeResource(resources, fontResource)
        mPaint = Paint()
        mPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    override val characterWidth: Float
        get() = kCharacterWidth.toFloat()

    override val characterHeight: Int
        get() = kCharacterHeight


    override val topMargin: Int
        get() = 0


    override fun drawTextRun(
        canvas: Canvas, x: Float, y: Float,
        lineOffset: Int, runWidth: Int, text: CharArray, index: Int, count: Int,
        selectionStyle: Boolean, textStyle: Int,
        cursorOffset: Int, cursorIndex: Int, cursorIncr: Int, cursorWidth: Int, cursorMode: Int
    ) {
        var foreColor = TextStyle.decodeForeColor(textStyle)
        var backColor = TextStyle.decodeBackColor(textStyle)
        val effect = TextStyle.decodeEffect(textStyle)

        val inverse = mReverseVideo xor
                ((effect and (TextStyle.fxInverse or TextStyle.fxItalic)) != 0)
        if (inverse) {
            val temp = foreColor
            foreColor = backColor
            backColor = temp
        }

        val bold = ((effect and TextStyle.fxBold) != 0)
        if (bold && foreColor < 8) {
            // In 16-color mode, bold also implies bright foreground colors
            foreColor += 8
        }
        val blink = ((effect and TextStyle.fxBlink) != 0)
        if (blink && backColor < 8) {
            // In 16-color mode, blink also implies bright background colors
            backColor += 8
        }

        if (selectionStyle) {
            backColor = TextStyle.ciCursorBackground
        }

        val invisible = (effect and TextStyle.fxInvisible) != 0

        if (invisible) {
            foreColor = backColor
        }

        drawTextRunHelper(canvas, x, y, lineOffset, text, index, count, foreColor, backColor)

        // The cursor is too small to show the cursor mode.
        if (lineOffset <= cursorOffset && cursorOffset < (lineOffset + count)) {
            drawTextRunHelper(
                canvas, x, y, cursorOffset, text, cursorOffset - lineOffset, 1,
                TextStyle.ciCursorForeground, TextStyle.ciCursorBackground
            )
        }
    }

    private fun drawTextRunHelper(
        canvas: Canvas, x: Float, y: Float, lineOffset: Int, text: CharArray,
        index: Int, count: Int, foreColor: Int, backColor: Int
    ) {
        setColorMatrix(mPalette[foreColor], mPalette[backColor])
        var destX = x.toInt() + kCharacterWidth * lineOffset
        val destY = y.toInt()
        val srcRect = Rect()
        val destRect = Rect()
        destRect.top = (destY - kCharacterHeight)
        destRect.bottom = destY
        val drawSpaces = mPalette[backColor] != mPalette[TextStyle.ciBackground]
        for (i in 0..<count) {
            // XXX No Unicode support in bitmap font
            val c = text[i + index]
            if ((c.code < 128) && ((c.code != 32) || drawSpaces)) {
                val cellX = c.code and 31
                val cellY = (c.code shr 5) and 3
                val srcX = cellX * kCharacterWidth
                val srcY = cellY * kCharacterHeight
                srcRect.set(
                    srcX, srcY,
                    srcX + kCharacterWidth, srcY + kCharacterHeight
                )
                destRect.left = destX
                destRect.right = destX + kCharacterWidth
                canvas.drawBitmap(mFont, srcRect, destRect, mPaint)
            }
            destX += kCharacterWidth
        }
    }

    private fun setColorMatrix(foreColor: Int, backColor: Int) {
        if ((foreColor != mCurrentForeColor)
            || (backColor != mCurrentBackColor)
            || (mColorMatrix == null)
        ) {
            mCurrentForeColor = foreColor
            mCurrentBackColor = backColor
            if (mColorMatrix == null) {
                mColorMatrix = FloatArray(20)
                mColorMatrix!![18] = 1.0f // Just copy Alpha
            }
            for (component in 0..2) {
                val rightShift = (2 - component) shl 3
                val fore = 0xff and (foreColor shr rightShift)
                val back = 0xff and (backColor shr rightShift)
                val delta = back - fore
                mColorMatrix!![component * 6] = delta * BYTE_SCALE
                mColorMatrix!![component * 5 + 4] = fore.toFloat()
            }
            mPaint.setColorFilter(ColorMatrixColorFilter(mColorMatrix!!))
        }
    }

    companion object {
        private const val kCharacterWidth = 4
        private const val kCharacterHeight = 8
        private const val BYTE_SCALE = 1.0f / 255.0f
    }
}