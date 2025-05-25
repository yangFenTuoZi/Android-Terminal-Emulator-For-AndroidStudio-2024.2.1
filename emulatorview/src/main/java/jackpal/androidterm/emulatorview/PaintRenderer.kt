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

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.ceil

internal class PaintRenderer(fontSize: Int, scheme: ColorScheme?) : BaseTextRenderer(scheme) {
    override fun drawTextRun(
        canvas: Canvas, x: Float, y: Float, lineOffset: Int,
        runWidth: Int, text: CharArray, index: Int, count: Int,
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

        if (selectionStyle) {
            backColor = TextStyle.ciCursorBackground
        }

        val blink = (effect and TextStyle.fxBlink) != 0
        if (blink && backColor < 8) {
            backColor += 8
        }
        mTextPaint.setColor(mPalette[backColor])

        val left = x + lineOffset * mCharWidth
        canvas.drawRect(
            left, y + mCharAscent - mCharDescent,
            left + runWidth * mCharWidth, y,
            mTextPaint
        )

        val cursorVisible = lineOffset <= cursorOffset && cursorOffset < (lineOffset + runWidth)
        var cursorX = 0f
        if (cursorVisible) {
            cursorX = x + cursorOffset * mCharWidth
            drawCursorImp(
                canvas,
                cursorX.toInt().toFloat(),
                y,
                cursorWidth * mCharWidth,
                mCharHeight.toFloat(),
                cursorMode
            )
        }

        val invisible = (effect and TextStyle.fxInvisible) != 0
        if (!invisible) {
            val bold = (effect and TextStyle.fxBold) != 0
            val underline = (effect and TextStyle.fxUnderline) != 0
            if (bold) {
                mTextPaint.isFakeBoldText = true
            }
            if (underline) {
                mTextPaint.isUnderlineText = true
            }
            val textPaintColor: Int = if (foreColor < 8 && bold) {
                // In 16-color mode, bold also implies bright foreground colors
                mPalette[foreColor + 8]
            } else {
                mPalette[foreColor]
            }
            mTextPaint.setColor(textPaintColor)

            val textOriginY = y - mCharDescent

            if (cursorVisible) {
                // Text before cursor
                val countBeforeCursor = cursorIndex - index
                val countAfterCursor = count - (countBeforeCursor + cursorIncr)
                if (countBeforeCursor > 0) {
                    canvas.drawText(text, index, countBeforeCursor, left, textOriginY, mTextPaint)
                }
                // Text at cursor
                mTextPaint.setColor(mPalette[TextStyle.ciCursorForeground])
                canvas.drawText(
                    text, cursorIndex, cursorIncr, cursorX,
                    textOriginY, mTextPaint
                )
                // Text after cursor
                if (countAfterCursor > 0) {
                    mTextPaint.setColor(textPaintColor)
                    canvas.drawText(
                        text, cursorIndex + cursorIncr, countAfterCursor,
                        cursorX + cursorWidth * mCharWidth,
                        textOriginY, mTextPaint
                    )
                }
            } else {
                canvas.drawText(text, index, count, left, textOriginY, mTextPaint)
            }
            if (bold) {
                mTextPaint.isFakeBoldText = false
            }
            if (underline) {
                mTextPaint.isUnderlineText = false
            }
        }
    }

    override val characterHeight: Int
        get() = mCharHeight


    override val characterWidth: Float
        get() = mCharWidth


    override val topMargin: Int
        get() = mCharDescent


    private val mTextPaint: Paint = Paint()
    private val mCharWidth: Float
    private val mCharHeight: Int
    private val mCharAscent: Int
    private val mCharDescent: Int

    init {
        mTextPaint.setTypeface(Typeface.MONOSPACE)
        mTextPaint.isAntiAlias = true
        mTextPaint.textSize = fontSize.toFloat()

        mCharHeight = ceil(mTextPaint.fontSpacing.toDouble()).toInt()
        mCharAscent = ceil(mTextPaint.ascent().toDouble()).toInt()
        mCharDescent = mCharHeight + mCharAscent
        mCharWidth = mTextPaint.measureText(EXAMPLE_CHAR, 0, 1)
    }

    companion object {
        private val EXAMPLE_CHAR = charArrayOf('X')
    }
}