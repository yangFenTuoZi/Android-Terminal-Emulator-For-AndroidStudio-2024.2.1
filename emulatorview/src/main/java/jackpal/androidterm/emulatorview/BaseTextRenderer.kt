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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Matrix.ScaleToFit
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap

abstract class BaseTextRenderer(scheme: ColorScheme?) : TextRenderer {
    protected var mReverseVideo: Boolean = false

    protected var mPalette: IntArray = emptyArray<Int>().toIntArray()

    private val mCursorScreenPaint: Paint
    private val mCopyRedToAlphaPaint: Paint
    private val mCursorPaint: Paint
    private val mCursorStrokePaint: Paint
    private val mShiftCursor: Path
    private val mAltCursor: Path
    private val mCtrlCursor: Path
    private val mFnCursor: Path
    private val mTempSrc: RectF?
    private val mTempDst: RectF
    private val mScaleMatrix: Matrix
    private var mLastCharWidth = 0f
    private var mLastCharHeight = 0f
    private var mCursorBitmap: Bitmap? = null
    private var mWorkBitmap: Bitmap? = null
    private var mCursorBitmapCursorMode = -1

    init {
        var scheme = scheme
        if (scheme == null) {
            scheme = defaultColorScheme
        }
        setDefaultColors(scheme)

        mCursorScreenPaint = Paint()
        mCursorScreenPaint.setColor(scheme.cursorBackColor)

        // Cursor paint and cursor stroke paint are used to draw a grayscale mask that's converted
        // to an alpha8 texture. Only the red channel's value matters.
        mCursorPaint = Paint()
        mCursorPaint.setColor(-0x6f6f70) // Opaque lightgray
        mCursorPaint.isAntiAlias = true

        mCursorStrokePaint = Paint(mCursorPaint)
        mCursorStrokePaint.strokeWidth = 0.1f
        mCursorStrokePaint.style = Paint.Style.STROKE

        mCopyRedToAlphaPaint = Paint()
        val cm = ColorMatrix()
        cm.set(
            floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                1f, 0f, 0f, 0f, 0f
            )
        )
        mCopyRedToAlphaPaint.setColorFilter(ColorMatrixColorFilter(cm))

        mShiftCursor = Path()
        mShiftCursor.lineTo(0.5f, 0.33f)
        mShiftCursor.lineTo(1.0f, 0.0f)

        mAltCursor = Path()
        mAltCursor.moveTo(0.0f, 1.0f)
        mAltCursor.lineTo(0.5f, 0.66f)
        mAltCursor.lineTo(1.0f, 1.0f)

        mCtrlCursor = Path()
        mCtrlCursor.moveTo(0.0f, 0.25f)
        mCtrlCursor.lineTo(1.0f, 0.5f)
        mCtrlCursor.lineTo(0.0f, 0.75f)

        mFnCursor = Path()
        mFnCursor.moveTo(1.0f, 0.25f)
        mFnCursor.lineTo(0.0f, 0.5f)
        mFnCursor.lineTo(1.0f, 0.75f)

        // For creating the transform when the terminal resizes
        mTempSrc = RectF()
        mTempSrc.set(0.0f, 0.0f, 1.0f, 1.0f)
        mTempDst = RectF()
        mScaleMatrix = Matrix()
    }

    override fun setReverseVideo(reverseVideo: Boolean) {
        mReverseVideo = reverseVideo
    }

    private fun setDefaultColors(scheme: ColorScheme) {
        mPalette = cloneDefaultColors()
        mPalette[TextStyle.ciForeground] = scheme.foreColor
        mPalette[TextStyle.ciBackground] = scheme.backColor
        mPalette[TextStyle.ciCursorForeground] = scheme.cursorForeColor
        mPalette[TextStyle.ciCursorBackground] = scheme.cursorBackColor
    }

    protected fun drawCursorImp(
        canvas: Canvas, x: Float, y: Float, charWidth: Float, charHeight: Float,
        cursorMode: Int
    ) {
        if (cursorMode == 0) {
            canvas.drawRect(x, y - charHeight, x + charWidth, y, mCursorScreenPaint)
            return
        }

        // Fancy cursor. Draw an offscreen cursor shape, then blit it on screen.

        // Has the character size changed?
        if (charWidth != mLastCharWidth || charHeight != mLastCharHeight) {
            mLastCharWidth = charWidth
            mLastCharHeight = charHeight
            mTempDst.set(0.0f, 0.0f, charWidth, charHeight)
            mScaleMatrix.setRectToRect(mTempSrc, mTempDst, mScaleType)
            mCursorBitmap =
                createBitmap(charWidth.toInt(), charHeight.toInt(), Bitmap.Config.ALPHA_8)
            mWorkBitmap = createBitmap(charWidth.toInt(), charHeight.toInt())
            mCursorBitmapCursorMode = -1
        }

        // Has the cursor mode changed ?
        if (cursorMode != mCursorBitmapCursorMode) {
            mCursorBitmapCursorMode = cursorMode
            mWorkBitmap!!.eraseColor(-0x1)
            val workCanvas = Canvas(mWorkBitmap!!)
            workCanvas.concat(mScaleMatrix)
            drawCursorHelper(workCanvas, mShiftCursor, cursorMode, TextRenderer.MODE_SHIFT_SHIFT)
            drawCursorHelper(workCanvas, mAltCursor, cursorMode, TextRenderer.MODE_ALT_SHIFT)
            drawCursorHelper(workCanvas, mCtrlCursor, cursorMode, TextRenderer.MODE_CTRL_SHIFT)
            drawCursorHelper(workCanvas, mFnCursor, cursorMode, TextRenderer.MODE_FN_SHIFT)

            mCursorBitmap!!.eraseColor(0)
            val bitmapCanvas = Canvas(mCursorBitmap!!)
            bitmapCanvas.drawBitmap(mWorkBitmap!!, 0f, 0f, mCopyRedToAlphaPaint)
        }

        canvas.drawBitmap(mCursorBitmap!!, x, y - charHeight, mCursorScreenPaint)
    }

    private fun drawCursorHelper(canvas: Canvas, path: Path, mode: Int, shift: Int) {
        when ((mode shr shift) and TextRenderer.MODE_MASK) {
            TextRenderer.MODE_ON -> canvas.drawPath(path, mCursorStrokePaint)
            TextRenderer.MODE_LOCKED -> canvas.drawPath(path, mCursorPaint)
        }
    }

    companion object {
        protected val sXterm256Paint: IntArray = intArrayOf( // 16 original colors
            // First 8 are dim
            -0x1000000,  // black
            -0x330000,  // dim red
            -0xff3300,  // dim green
            -0x323300,  // dim yellow
            -0xffff12,  // dim blue
            -0x32ff33,  // dim magenta
            -0xff3233,  // dim cyan
            -0x1a1a1b,  // dim white
            // second 8 are bright
            -0x808081,  // medium grey
            -0x10000,  // bright red
            -0xff0100,  // bright green
            -0x100,  // bright yellow
            -0xa3a301,  // light blue
            -0xff01,  // bright magenta
            -0xff0001,  // bright cyan
            -0x1,  // bright white
            // 216 color cube, six shades of each color

            -0x1000000,
            -0xffffa1,
            -0xffff79,
            -0xffff51,
            -0xffff29,
            -0xffff01,
            -0xffa100,
            -0xffa0a1,
            -0xffa079,
            -0xffa051,
            -0xffa029,
            -0xffa001,
            -0xff7900,
            -0xff78a1,
            -0xff7879,
            -0xff7851,
            -0xff7829,
            -0xff7801,
            -0xff5100,
            -0xff50a1,
            -0xff5079,
            -0xff5051,
            -0xff5029,
            -0xff5001,
            -0xff2900,
            -0xff28a1,
            -0xff2879,
            -0xff2851,
            -0xff2829,
            -0xff2801,
            -0xff0100,
            -0xff00a1,
            -0xff0079,
            -0xff0051,
            -0xff0029,
            -0xff0001,
            -0xa10000,
            -0xa0ffa1,
            -0xa0ff79,
            -0xa0ff51,
            -0xa0ff29,
            -0xa0ff01,
            -0xa0a100,
            -0xa0a0a1,
            -0xa0a079,
            -0xa0a051,
            -0xa0a029,
            -0xa0a001,
            -0xa07900,
            -0xa078a1,
            -0xa07879,
            -0xa07851,
            -0xa07829,
            -0xa07801,
            -0xa05100,
            -0xa050a1,
            -0xa05079,
            -0xa05051,
            -0xa05029,
            -0xa05001,
            -0xa02900,
            -0xa028a1,
            -0xa02879,
            -0xa02851,
            -0xa02829,
            -0xa02801,
            -0xa00100,
            -0xa000a1,
            -0xa00079,
            -0xa00051,
            -0xa00029,
            -0xa00001,
            -0x790000,
            -0x78ffa1,
            -0x78ff79,
            -0x78ff51,
            -0x78ff29,
            -0x78ff01,
            -0x78a100,
            -0x78a0a1,
            -0x78a079,
            -0x78a051,
            -0x78a029,
            -0x78a001,
            -0x787900,
            -0x7878a1,
            -0x787879,
            -0x787851,
            -0x787829,
            -0x787801,
            -0x785100,
            -0x7850a1,
            -0x785079,
            -0x785051,
            -0x785029,
            -0x785001,
            -0x782900,
            -0x7828a1,
            -0x782879,
            -0x782851,
            -0x782829,
            -0x782801,
            -0x780100,
            -0x7800a1,
            -0x780079,
            -0x780051,
            -0x780029,
            -0x780001,
            -0x510000,
            -0x50ffa1,
            -0x50ff79,
            -0x50ff51,
            -0x50ff29,
            -0x50ff01,
            -0x50a100,
            -0x50a0a1,
            -0x50a079,
            -0x50a051,
            -0x50a029,
            -0x50a001,
            -0x507900,
            -0x5078a1,
            -0x507879,
            -0x507851,
            -0x507829,
            -0x507801,
            -0x505100,
            -0x5050a1,
            -0x505079,
            -0x505051,
            -0x505029,
            -0x505001,
            -0x502900,
            -0x5028a1,
            -0x502879,
            -0x502851,
            -0x502829,
            -0x502801,
            -0x500100,
            -0x5000a1,
            -0x500079,
            -0x500051,
            -0x500029,
            -0x500001,
            -0x290000,
            -0x28ffa1,
            -0x28ff79,
            -0x28ff51,
            -0x28ff29,
            -0x28ff01,
            -0x28a100,
            -0x28a0a1,
            -0x28a079,
            -0x28a051,
            -0x28a029,
            -0x28a001,
            -0x287900,
            -0x2878a1,
            -0x287879,
            -0x287851,
            -0x287829,
            -0x287801,
            -0x285100,
            -0x2850a1,
            -0x285079,
            -0x285051,
            -0x285029,
            -0x285001,
            -0x282900,
            -0x2828a1,
            -0x282879,
            -0x282851,
            -0x282829,
            -0x282801,
            -0x280100,
            -0x2800a1,
            -0x280079,
            -0x280051,
            -0x280029,
            -0x280001,
            -0x10000,
            -0xffa1,
            -0xff79,
            -0xff51,
            -0xff29,
            -0xff01,
            -0xa100,
            -0xa0a1,
            -0xa079,
            -0xa051,
            -0xa029,
            -0xa001,
            -0x7900,
            -0x78a1,
            -0x7879,
            -0x7851,
            -0x7829,
            -0x7801,
            -0x5100,
            -0x50a1,
            -0x5079,
            -0x5051,
            -0x5029,
            -0x5001,
            -0x2900,
            -0x28a1,
            -0x2879,
            -0x2851,
            -0x2829,
            -0x2801,
            -0x100,
            -0xa1,
            -0x79,
            -0x51,
            -0x29,
            -0x1,  // 24 grey scale ramp

            -0xf7f7f8,
            -0xededee,
            -0xe3e3e4,
            -0xd9d9da,
            -0xcfcfd0,
            -0xc5c5c6,
            -0xbbbbbc,
            -0xb1b1b2,
            -0xa7a7a8,
            -0x9d9d9e,
            -0x939394,
            -0x89898a,
            -0x7f7f80,
            -0x757576,
            -0x6b6b6c,
            -0x616162,
            -0x575758,
            -0x4d4d4e,
            -0x434344,
            -0x39393a,
            -0x2f2f30,
            -0x252526,
            -0x1b1b1c,
            -0x111112
        )

        val defaultColorScheme: ColorScheme = ColorScheme(-0x333334, -0x1000000)

        private val mScaleType = ScaleToFit.FILL

        private fun cloneDefaultColors(): IntArray {
            val length = sXterm256Paint.size
            val clone = IntArray(TextStyle.ciColorLength)
            System.arraycopy(sXterm256Paint, 0, clone, 0, length)
            return clone
        }
    }
}