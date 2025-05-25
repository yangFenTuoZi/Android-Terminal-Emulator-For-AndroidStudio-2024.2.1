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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.URLSpan
import android.text.util.Linkify
import android.text.util.Linkify.MatchFilter
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.Scroller
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat
import jackpal.androidterm.emulatorview.compat.KeycodeConstants
import jackpal.androidterm.emulatorview.compat.Patterns
import java.io.IOException
import java.util.Arrays
import java.util.Hashtable
import java.util.regex.Pattern

open class EmulatorView : View, GestureDetector.OnGestureListener {
    companion object {
        private const val TAG = "EmulatorView"
        private const val LOG_KEY_EVENTS = false
        private const val LOG_IME = false
        private const val CURSOR_BLINK_PERIOD = 1000
        private const val SELECT_TEXT_OFFSET_Y = -40

        private val sHttpMatchFilter = object : MatchFilter {
            override fun acceptMatch(s: CharSequence, start: Int, end: Int): Boolean {
                return startsWith(s, start, end, "http:") || startsWith(s, start, end, "https:")
            }

            private fun startsWith(s: CharSequence, start: Int, end: Int, prefix: String): Boolean {
                val prefixLen = prefix.length
                val fragmentLen = end - start
                if (prefixLen > fragmentLen) {
                    return false
                }
                for (i in 0 until prefixLen) {
                    if (s[start + i] != prefix[i]) {
                        return false
                    }
                }
                return true
            }
        }

        private val sTrapAltAndMeta = Build.MODEL.contains("Transformer TF101")
    }

    private var mKnownSize = false
    private var mDeferInit = false
    private var mVisibleWidth = 0
    private var mVisibleHeight = 0
    private var mTermSession: TermSession? = null
    private var mCharacterWidth = 0f
    private var mCharacterHeight = 0
    private var mTopOfScreenMargin = 0
    private var mTextRenderer: TextRenderer? = null
    private var mTextSize = 10
    private var mCursorBlink = 0
    private var mColorScheme: ColorScheme = BaseTextRenderer.defaultColorScheme
    private var mForegroundPaint = Paint()
    private var mBackgroundPaint = Paint()
    private var mUseCookedIme = false
    private var mEmulator: TerminalEmulator? = null
    private var mRows = 0
    private var mColumns = 0
    private var mVisibleColumns = 0
    private var mVisibleRows = 0
    private var mTopRow = 0
    private var mLeftColumn = 0
    private var mCursorVisible = true
    private var mIsSelectingText = false
    private var mBackKeySendsCharacter = false
    private var mControlKeyCode = 0
    private var mFnKeyCode = 0
    private var mIsControlKeySent = false
    private var mIsFnKeySent = false
    private var mMouseTracking = false
    private var mDensity = 0f
    private var mScaledDensity = 0f
    private var mSelXAnchor = -1
    private var mSelYAnchor = -1
    private var mSelX1 = -1
    private var mSelY1 = -1
    private var mSelX2 = -1
    private var mSelY2 = -1

    private val mBlinkCursor = object : Runnable {
        override fun run() {
            if (mCursorBlink != 0) {
                mCursorVisible = !mCursorVisible
                mHandler.postDelayed(this, CURSOR_BLINK_PERIOD.toLong())
            } else {
                mCursorVisible = true
            }
            invalidate()
        }
    }

    private var mGestureDetector: GestureDetector? = null
    private var mExtGestureListener: GestureDetector.OnGestureListener? = null
    private var mScroller: Scroller? = null
    private val mFlingRunner = object : Runnable {
        override fun run() {
            if (mScroller!!.isFinished) {
                return
            }
            if (isMouseTrackingActive) {
                return
            }

            val more = mScroller!!.computeScrollOffset()
            val newTopRow = mScroller!!.currY
            if (newTopRow != mTopRow) {
                mTopRow = newTopRow
                invalidate()
            }

            if (more) {
                post(this)
            }
        }
    }

    private val mLinkLayer = Hashtable<Int, Array<URLSpan?>>()

    private inner class MouseTrackingFlingRunner : Runnable {
        var mScroller: Scroller? = null
        var mLastY = 0
        var mMotionEvent: MotionEvent? = null

        fun fling(e: MotionEvent?, velocityX: Float, velocityY: Float) {
            val SCALE = 0.15f
            mScroller!!.fling(
                0,
                0,
                -(velocityX * SCALE).toInt(),
                -(velocityY * SCALE).toInt(),
                0,
                0,
                -100,
                100
            )
            mLastY = 0
            mMotionEvent = e
            post(this)
        }

        override fun run() {
            if (mScroller!!.isFinished) {
                return
            }
            if (!isMouseTrackingActive) {
                return
            }

            val more = mScroller!!.computeScrollOffset()
            val newY = mScroller!!.currY
            while (mLastY < newY) {
                mLastY++
                sendMouseEventCode(mMotionEvent!!, 65)
            }
            while (mLastY > newY) {
                mLastY--
                sendMouseEventCode(mMotionEvent!!, 64)
            }

            if (more) {
                post(this)
            }
        }
    }

    private val mMouseTrackingFlingRunner = MouseTrackingFlingRunner()
    private var mScrollRemainder = 0f
    private var mKeyListener: TermKeyListener? = null
    private var mImeBuffer = ""
    private val mHandler = Handler(HandlerThread("mThread").apply { start() }.looper)

    private val mUpdateNotify = object : UpdateCallback {
        override fun onUpdate() {
            if (mIsSelectingText) {
                val rowShift = mEmulator!!.scrollCounter
                mSelY1 -= rowShift
                mSelY2 -= rowShift
                mSelYAnchor -= rowShift
            }
            mEmulator!!.clearScrollCounter()
            ensureCursorVisible()
            invalidate()
        }
    }

    constructor(context: Context, session: TermSession, metrics: DisplayMetrics) : super(context) {
        attachSession(session)
        setDensity(metrics)
        commonConstructor(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        commonConstructor(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        commonConstructor(context)
    }

    private fun commonConstructor(context: Context) {
        mScroller = Scroller(context)
        mMouseTrackingFlingRunner.mScroller = Scroller(context)
    }

    fun attachSession(session: TermSession) {
        mTextRenderer = null
        mForegroundPaint = Paint()
        mBackgroundPaint = Paint()
        mTopRow = 0
        mLeftColumn = 0
        mGestureDetector = GestureDetector(this)
        isVerticalScrollBarEnabled = true
        isFocusable = true
        isFocusableInTouchMode = true

        mTermSession = session

        mKeyListener = TermKeyListener(session)
        session.setKeyListener(mKeyListener)

        if (mDeferInit) {
            mDeferInit = false
            mKnownSize = true
            initialize()
        }
    }

    fun setDensity(metrics: DisplayMetrics) {
        if (mDensity == 0f) {
            mTextSize = (mTextSize * metrics.density).toInt()
        }
        mDensity = metrics.density
        mScaledDensity = metrics.scaledDensity
    }

    fun onResume() {
        updateSize(false)
        if (mCursorBlink != 0) {
            mHandler.postDelayed(mBlinkCursor, CURSOR_BLINK_PERIOD.toLong())
        }
        mKeyListener?.onResume()
    }

    fun onPause() {
        if (mCursorBlink != 0) {
            mHandler.removeCallbacks(mBlinkCursor)
        }
        mKeyListener?.onPause()
    }

    fun setColorScheme(scheme: ColorScheme?) {
        mColorScheme = scheme ?: BaseTextRenderer.defaultColorScheme
        updateText()
    }

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = if (mUseCookedIme) EditorInfo.TYPE_CLASS_TEXT else EditorInfo.TYPE_NULL
        return object : BaseInputConnection(this, true) {
            private var mCursor = 0
            private var mComposingTextStart = 0
            private var mComposingTextEnd = 0
            private var mSelectedTextStart = 0
            private var mSelectedTextEnd = 0

            private fun sendText(text: CharSequence) {
                val n = text.length
                try {
                    var i = 0
                    while (i < n) {
                        val c = text[i]
                        if (Character.isHighSurrogate(c)) {
                            val codePoint = if (++i < n) {
                                Character.toCodePoint(c, text[i])
                            } else {
                                '\ufffd'.code
                            }
                            mapAndSend(codePoint)
                        } else {
                            mapAndSend(c.code)
                        }
                        i++
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "error writing ", e)
                }
            }

            @Throws(IOException::class)
            private fun mapAndSend(c: Int) {
                val result = mKeyListener!!.mapControlChar(c)
                if (result < TermKeyListener.KEYCODE_OFFSET) {
                    mTermSession!!.write(result)
                } else {
                    mKeyListener!!.handleKeyCode(
                        result - TermKeyListener.KEYCODE_OFFSET,
                        null,
                        getKeypadApplicationMode()
                    )
                }
                clearSpecialKeyStatus()
            }

            override fun beginBatchEdit(): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "beginBatchEdit")
                }
                setImeBuffer("")
                mCursor = 0
                mComposingTextStart = 0
                mComposingTextEnd = 0
                return true
            }

            override fun clearMetaKeyStates(states: Int): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "clearMetaKeyStates $states")
                }
                return false
            }

            override fun commitCompletion(text: CompletionInfo?): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "commitCompletion $text")
                }
                return false
            }

            override fun endBatchEdit(): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "endBatchEdit")
                }
                return true
            }

            override fun finishComposingText(): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "finishComposingText")
                }
                sendText(mImeBuffer)
                setImeBuffer("")
                mComposingTextStart = 0
                mComposingTextEnd = 0
                mCursor = 0
                return true
            }

            override fun getCursorCapsMode(reqModes: Int): Int {
                if (LOG_IME) {
                    Log.w(TAG, "getCursorCapsMode($reqModes)")
                }
                var mode = 0
                if (reqModes and TextUtils.CAP_MODE_CHARACTERS != 0) {
                    mode = mode or TextUtils.CAP_MODE_CHARACTERS
                }
                return mode
            }

            override fun getExtractedText(
                request: ExtractedTextRequest?,
                flags: Int
            ): ExtractedText? {
                if (LOG_IME) {
                    Log.w(TAG, "getExtractedText$request,$flags")
                }
                return null
            }

            override fun getTextAfterCursor(length: Int, flags: Int): CharSequence {
                if (LOG_IME) {
                    Log.w(TAG, "getTextAfterCursor($length,$flags)")
                }
                val len = length.coerceAtMost(mImeBuffer.length - mCursor)
                if (len <= 0 || mCursor < 0 || mCursor >= mImeBuffer.length) {
                    return ""
                }
                return mImeBuffer.substring(mCursor, mCursor + len)
            }

            override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence {
                if (LOG_IME) {
                    Log.w(TAG, "getTextBeforeCursor($length,$flags)")
                }
                val len = length.coerceAtMost(mCursor)
                if (len <= 0 || mCursor < 0 || mCursor >= mImeBuffer.length) {
                    return ""
                }
                return mImeBuffer.substring(mCursor - len, mCursor)
            }

            override fun performContextMenuAction(id: Int): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "performContextMenuAction$id")
                }
                return true
            }

            override fun performPrivateCommand(action: String?, data: Bundle?): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "performPrivateCommand$action,$data")
                }
                return true
            }

            override fun reportFullscreenMode(enabled: Boolean): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "reportFullscreenMode$enabled")
                }
                return true
            }

            override fun commitCorrection(info: CorrectionInfo?): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "commitCorrection")
                }
                return true
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "commitText(\"$text\", $newCursorPosition)")
                }
                clearComposingText()
                sendText(text!!)
                setImeBuffer("")
                mCursor = 0
                return true
            }

            private fun clearComposingText() {
                val len = mImeBuffer.length
                if (mComposingTextStart > len || mComposingTextEnd > len) {
                    mComposingTextEnd = 0
                    mComposingTextStart = 0
                    return
                }
                setImeBuffer(
                    mImeBuffer.substring(0, mComposingTextStart) + mImeBuffer.substring(
                        mComposingTextEnd
                    )
                )
                mCursor = when {
                    mCursor < mComposingTextStart -> mCursor
                    mCursor < mComposingTextEnd -> mComposingTextStart
                    else -> mCursor - (mComposingTextEnd - mComposingTextStart)
                }
                mComposingTextEnd = 0
                mComposingTextStart = 0
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "deleteSurroundingText($beforeLength,$afterLength)")
                }
                if (beforeLength > 0) {
                    for (i in 0 until beforeLength) {
                        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    }
                } else if (beforeLength == 0 && afterLength == 0) {
                    sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                }
                return true
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "performEditorAction($actionCode)")
                }
                if (actionCode == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    sendText("\r")
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "sendKeyEvent($event)")
                }
                dispatchKeyEvent(event!!)
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "setComposingText(\"$text\", $newCursorPosition)")
                }
                val len = mImeBuffer.length
                if (mComposingTextStart > len || mComposingTextEnd > len) {
                    return false
                }
                setImeBuffer(
                    mImeBuffer.substring(
                        0,
                        mComposingTextStart
                    ) + text + mImeBuffer.substring(mComposingTextEnd)
                )
                mComposingTextEnd = mComposingTextStart + text!!.length
                mCursor =
                    if (newCursorPosition > 0) mComposingTextEnd + newCursorPosition - 1 else mComposingTextStart - newCursorPosition
                return true
            }

            override fun setSelection(start: Int, end: Int): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "setSelection$start,$end")
                }
                val length = mImeBuffer.length
                if (start == end && start > 0 && start < length) {
                    mSelectedTextStart = 0
                    mSelectedTextEnd = 0
                    mCursor = start
                } else if (start < end && start > 0 && end < length) {
                    mSelectedTextStart = start
                    mSelectedTextEnd = end
                    mCursor = start
                }
                return true
            }

            override fun setComposingRegion(start: Int, end: Int): Boolean {
                if (LOG_IME) {
                    Log.w(TAG, "setComposingRegion $start,$end")
                }
                if (start < end && start > 0 && end < mImeBuffer.length) {
                    clearComposingText()
                    mComposingTextStart = start
                    mComposingTextEnd = end
                }
                return true
            }

            override fun getSelectedText(flags: Int): CharSequence {
                if (LOG_IME) {
                    Log.w(TAG, "getSelectedText $flags")
                }
                val len = mImeBuffer.length
                if (mSelectedTextEnd >= len || mSelectedTextStart > mSelectedTextEnd) {
                    return ""
                }
                return mImeBuffer.substring(mSelectedTextStart, mSelectedTextEnd + 1)
            }
        }
    }

    private fun setImeBuffer(buffer: String) {
        if (buffer != mImeBuffer) {
            invalidate()
        }
        mImeBuffer = buffer
    }

    fun getKeypadApplicationMode(): Boolean {
        return mEmulator!!.keypadApplicationMode
    }

    fun setExtGestureListener(listener: GestureDetector.OnGestureListener?) {
        mExtGestureListener = listener
    }

    override fun computeVerticalScrollRange(): Int {
        return mEmulator!!.screen!!.activeRows
    }

    override fun computeVerticalScrollExtent(): Int {
        return mRows
    }

    override fun computeVerticalScrollOffset(): Int {
        return mEmulator!!.screen!!.activeRows + mTopRow - mRows
    }

    private fun initialize() {
        val session = mTermSession ?: return

        updateText()

        mEmulator = session.emulator
        session.setUpdateCallback(mUpdateNotify)

        requestFocus()
    }

    val termSession: TermSession?
        get() = mTermSession

    val visibleWidth: Int
        get() = mVisibleWidth

    val visibleHeight: Int
        get() = mVisibleHeight

    fun getVisibleRows(): Int {
        return mVisibleRows
    }

    fun getVisibleColumns(): Int {
        return mVisibleColumns
    }

    fun page(delta: Int) {
        mTopRow =
            0.coerceAtMost((-mEmulator!!.screen!!.activeTranscriptRows).coerceAtLeast(mTopRow + mRows * delta))
        invalidate()
    }

    fun pageHorizontal(deltaColumns: Int) {
        mLeftColumn =
            0.coerceAtLeast((mLeftColumn + deltaColumns).coerceAtMost(mColumns - mVisibleColumns))
        invalidate()
    }

    fun setTextSize(fontSize: Int) {
        mTextSize = (fontSize * mDensity).toInt()
        updateText()
    }

    fun setUseCookedIME(useCookedIME: Boolean) {
        mUseCookedIme = useCookedIME
    }

    val isMouseTrackingActive: Boolean
        get() = mEmulator!!.mouseTrackingMode != 0 && mMouseTracking


    private fun sendMouseEventCode(e: MotionEvent, button_code: Int) {
        var x = (e.x / mCharacterWidth).toInt() + 1
        var y = ((e.y - mTopOfScreenMargin) / mCharacterHeight).toInt() + 1
        val out_of_bounds =
            x < 1 || y < 1 || x > mColumns || y > mRows || x > 255 - 32 || y > 255 - 32
        if (button_code < 0 || button_code > 255 - 32) {
            Log.e(TAG, "mouse button_code out of range: $button_code")
            return
        }
        if (!out_of_bounds) {
            val data = byteArrayOf(
                '\u001b'.code.toByte(), '['.code.toByte(), 'M'.code.toByte(),
                (32 + button_code).toByte(),
                (32 + x).toByte(),
                (32 + y).toByte()
            )
            mTermSession!!.write(data, 0, data.size)
        }
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        if (mExtGestureListener?.onSingleTapUp(e) == true) {
            return true
        }

        if (isMouseTrackingActive) {
            sendMouseEventCode(e, 0)
            sendMouseEventCode(e, 3)
        }

        requestFocus()
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        showContextMenu()
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (mExtGestureListener?.onScroll(e1, e2, distanceX, distanceY) == true) {
            return true
        }

        var deltaY = distanceY + mScrollRemainder
        var deltaRows = (deltaY / mCharacterHeight).toInt()
        mScrollRemainder = deltaY - deltaRows * mCharacterHeight

        if (isMouseTrackingActive) {
            e1?.let {
                while (deltaRows > 0) {
                    deltaRows--
                    sendMouseEventCode(it, 65)
                }
                while (deltaRows < 0) {
                    deltaRows++
                    sendMouseEventCode(it, 64)
                }
            }
            return true
        }

        mTopRow =
            0.coerceAtMost((-mEmulator!!.screen!!.activeTranscriptRows).coerceAtLeast(mTopRow + deltaRows))
        invalidate()

        return true
    }

    fun onSingleTapConfirmed(e: MotionEvent) {}

    fun onJumpTapDown(e1: MotionEvent, e2: MotionEvent): Boolean {
        mTopRow = 0
        invalidate()
        return true
    }

    fun onJumpTapUp(e1: MotionEvent, e2: MotionEvent): Boolean {
        mTopRow = -mEmulator!!.screen!!.activeTranscriptRows
        invalidate()
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (mExtGestureListener?.onFling(e1, e2, velocityX, velocityY) == true) {
            return true
        }

        mScrollRemainder = 0.0f
        if (isMouseTrackingActive) {
            mMouseTrackingFlingRunner.fling(e1, velocityX, velocityY)
        } else {
            val SCALE = 0.25f
            mScroller!!.fling(
                0, mTopRow,
                -(velocityX * SCALE).toInt(), -(velocityY * SCALE).toInt(),
                0, 0,
                -mEmulator!!.screen!!.activeTranscriptRows, 0
            )
            post(mFlingRunner)
        }
        return true
    }

    override fun onShowPress(e: MotionEvent) {
        mExtGestureListener?.onShowPress(e)
    }

    override fun onDown(e: MotionEvent): Boolean {
        if (mExtGestureListener?.onDown(e) == true) {
            return true
        }
        mScrollRemainder = 0.0f
        return true
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return if (mIsSelectingText) {
            onTouchEventWhileSelectingText(ev)
        } else {
            mGestureDetector?.onTouchEvent(ev) == true
        }
    }

    private fun onTouchEventWhileSelectingText(ev: MotionEvent): Boolean {
        val action = ev.action
        val cx = (ev.x / mCharacterWidth).toInt()
        val cy =
            0.coerceAtLeast(((ev.y + SELECT_TEXT_OFFSET_Y * mScaledDensity) / mCharacterHeight).toInt() + mTopRow)
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mSelXAnchor = cx
                mSelYAnchor = cy
                mSelX1 = cx
                mSelY1 = cy
                mSelX2 = mSelX1
                mSelY2 = mSelY1
            }

            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val minx = mSelXAnchor.coerceAtMost(cx)
                val maxx = mSelXAnchor.coerceAtLeast(cx)
                val miny = mSelYAnchor.coerceAtMost(cy)
                val maxy = mSelYAnchor.coerceAtLeast(cy)
                mSelX1 = minx
                mSelY1 = miny
                mSelX2 = maxx
                mSelY2 = maxy
                if (action == MotionEvent.ACTION_UP) {
                    val clip = ClipboardManagerCompat(context.applicationContext)
                    clip.text = getSelectedText()?.trim()
                    toggleSelectingText()
                }
                invalidate()
            }

            else -> {
                toggleSelectingText()
                invalidate()
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (LOG_KEY_EVENTS) {
            Log.w(TAG, "onKeyDown $keyCode")
        }
        return when {
            handleControlKey(keyCode, true) -> true
            handleFnKey(keyCode, true) -> true
            isSystemKey(keyCode, event) && !isInterceptedSystemKey(keyCode) -> super.onKeyDown(
                keyCode,
                event
            )

            else -> {
                try {
                    val oldCombiningAccent = mKeyListener?.combiningAccent ?: 0
                    val oldCursorMode = mKeyListener?.cursorMode ?: 0
                    mKeyListener?.keyDown(
                        keyCode, event, getKeypadApplicationMode(),
                        TermKeyListener.isEventFromToggleDevice(event)
                    )
                    if (mKeyListener?.combiningAccent != oldCombiningAccent || mKeyListener?.cursorMode != oldCursorMode) {
                        invalidate()
                    }
                } catch (e: IOException) {
                }
                true
            }
        }
    }

    private fun isInterceptedSystemKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BACK && mBackKeySendsCharacter
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (LOG_KEY_EVENTS) {
            Log.w(TAG, "onKeyUp $keyCode")
        }
        return when {
            handleControlKey(keyCode, false) -> true
            handleFnKey(keyCode, false) -> true
            isSystemKey(
                keyCode,
                event
            ) && !isInterceptedSystemKey(keyCode) -> super.onKeyUp(keyCode, event)

            else -> {
                mKeyListener?.keyUp(keyCode, event)
                clearSpecialKeyStatus()
                true
            }
        }
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (sTrapAltAndMeta) {
            val altEsc = mKeyListener?.altSendsEsc == true
            val altOn = event.metaState and KeyEvent.META_ALT_ON != 0
            val metaOn = event.metaState and KeyEvent.META_META_ON != 0
            val altPressed =
                keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT
            val altActive = mKeyListener?.isAltActive == true
            if (altEsc && (altOn || altPressed || altActive || metaOn)) {
                return if (event.action == KeyEvent.ACTION_DOWN) {
                    onKeyDown(keyCode, event)
                } else {
                    onKeyUp(keyCode, event)
                }
            }
        }

        if (handleHardwareControlKey(keyCode, event)) {
            return true
        }

        if (mKeyListener?.isCtrlActive == true) {
            return if (event.action == KeyEvent.ACTION_DOWN) {
                onKeyDown(keyCode, event)
            } else {
                onKeyUp(keyCode, event)
            }
        }

        return super.onKeyPreIme(keyCode, event)
    }

    private fun handleControlKey(keyCode: Int, down: Boolean): Boolean {
        if (keyCode == mControlKeyCode) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleControlKey $keyCode")
            }
            mKeyListener?.handleControlKey(down)
            invalidate()
            return true
        }
        return false
    }

    private fun handleHardwareControlKey(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeycodeConstants.KEYCODE_CTRL_LEFT || keyCode == KeycodeConstants.KEYCODE_CTRL_RIGHT) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleHardwareControlKey $keyCode")
            }
            val down = event.action == KeyEvent.ACTION_DOWN
            mKeyListener?.handleHardwareControlKey(down)
            invalidate()
            return true
        }
        return false
    }

    private fun handleFnKey(keyCode: Int, down: Boolean): Boolean {
        if (keyCode == mFnKeyCode) {
            if (LOG_KEY_EVENTS) {
                Log.w(TAG, "handleFnKey $keyCode")
            }
            mKeyListener?.handleFnKey(down)
            invalidate()
            return true
        }
        return false
    }

    private fun isSystemKey(keyCode: Int, event: KeyEvent): Boolean {
        return event.isSystem
    }

    private fun clearSpecialKeyStatus() {
        if (mIsControlKeySent) {
            mIsControlKeySent = false
            mKeyListener?.handleControlKey(false)
            invalidate()
        }
        if (mIsFnKeySent) {
            mIsFnKeySent = false
            mKeyListener?.handleFnKey(false)
            invalidate()
        }
    }

    private fun updateText() {
        val scheme = mColorScheme
        mTextRenderer = if (mTextSize > 0) {
            PaintRenderer(mTextSize, scheme)
        } else {
            Bitmap4x8FontRenderer(resources, scheme)
        }

        mForegroundPaint.color = scheme.foreColor
        mBackgroundPaint.color = scheme.backColor
        mCharacterWidth = mTextRenderer!!.characterWidth
        mCharacterHeight = mTextRenderer!!.characterHeight

        updateSize(true)
    }

    protected override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (mTermSession == null) {
            mDeferInit = true
            return
        }

        if (!mKnownSize) {
            mKnownSize = true
            initialize()
        } else {
            updateSize(false)
        }
    }

    private fun updateSize(w: Int, h: Int) {
        mColumns = (w / mCharacterWidth).coerceAtLeast(1f).toInt()
        mVisibleColumns = (mVisibleWidth / mCharacterWidth).coerceAtLeast(1f).toInt()

        mTopOfScreenMargin = mTextRenderer!!.topMargin
        mRows = ((h - mTopOfScreenMargin) / mCharacterHeight).coerceAtLeast(1)
        mVisibleRows = ((mVisibleHeight - mTopOfScreenMargin) / mCharacterHeight).coerceAtLeast(1)
        mTermSession?.updateSize(mColumns, mRows)

        mTopRow = 0
        mLeftColumn = 0

        invalidate()
    }

    fun updateSize(force: Boolean) {
        mLinkLayer.clear()
        if (mKnownSize) {
            val w = width
            val h = height
            if (force || w != mVisibleWidth || h != mVisibleHeight) {
                mVisibleWidth = w
                mVisibleHeight = h
                updateSize(mVisibleWidth, mVisibleHeight)
            }
        }
    }

    protected override fun onDraw(canvas: Canvas) {
        updateSize(false)

        if (mEmulator == null) {
            return
        }

        val w = width
        val h = height

        val reverseVideo = mEmulator!!.reverseVideo
        mTextRenderer?.setReverseVideo(reverseVideo)

        val backgroundPaint = if (reverseVideo) mForegroundPaint else mBackgroundPaint
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), backgroundPaint)
        var x = -mLeftColumn * mCharacterWidth
        var y = mCharacterHeight + mTopOfScreenMargin
        val endLine = mTopRow + mRows
        val cx = mEmulator!!.cursorCol
        val cy = mEmulator!!.cursorRow
        val cursorVisible = mCursorVisible && mEmulator!!.showCursor
        var effectiveImeBuffer = mImeBuffer
        val combiningAccent = mKeyListener?.combiningAccent ?: 0
        if (combiningAccent != 0) {
            effectiveImeBuffer += combiningAccent.toChar()
        }
        val cursorStyle = mKeyListener?.cursorMode ?: 0

        var linkLinesToSkip = 0

        for (i in mTopRow until endLine) {
            var cursorX = -1
            if (i == cy && cursorVisible) {
                cursorX = cx
            }
            var selx1 = -1
            var selx2 = -1
            if (i in mSelY1..mSelY2) {
                if (i == mSelY1) {
                    selx1 = mSelX1
                }
                selx2 = if (i == mSelY2) mSelX2 else mColumns
            }
            mEmulator!!.screen!!.drawText(
                i, canvas, x,
                y.toFloat(), mTextRenderer!!, cursorX, selx1, selx2, effectiveImeBuffer, cursorStyle
            )
            y += mCharacterHeight
            if (linkLinesToSkip == 0) {
                linkLinesToSkip = createLinks(i)
            }
            linkLinesToSkip--
        }
    }

    private fun ensureCursorVisible() {
        mTopRow = 0
        if (mVisibleColumns > 0) {
            val cx = mEmulator!!.cursorCol
            val visibleCursorX = mEmulator!!.cursorCol - mLeftColumn
            when {
                visibleCursorX < 0 -> mLeftColumn = cx
                visibleCursorX >= mVisibleColumns -> mLeftColumn = cx - mVisibleColumns + 1
            }
        }
    }

    fun toggleSelectingText() {
        mIsSelectingText = !mIsSelectingText
        isVerticalScrollBarEnabled = !mIsSelectingText
        if (!mIsSelectingText) {
            mSelX1 = -1
            mSelY1 = -1
            mSelX2 = -1
            mSelY2 = -1
        }
    }

    fun getSelectingText(): Boolean {
        return mIsSelectingText
    }

    fun getSelectedText(): String? {
        return mEmulator!!.getSelectedText(mSelX1, mSelY1, mSelX2, mSelY2)
    }

    fun sendControlKey() {
        mIsControlKeySent = true
        mKeyListener?.handleControlKey(true)
        invalidate()
    }

    fun sendFnKey() {
        mIsFnKeySent = true
        mKeyListener?.handleFnKey(true)
        invalidate()
    }

    fun setBackKeyCharacter(keyCode: Int) {
        mKeyListener?.setBackKeyCharacter(keyCode)
        mBackKeySendsCharacter = keyCode != 0
    }

    fun setAltSendsEsc(flag: Boolean) {
        mKeyListener?.altSendsEsc = flag
    }

    fun setControlKeyCode(keyCode: Int) {
        mControlKeyCode = keyCode
    }

    fun setFnKeyCode(keyCode: Int) {
        mFnKeyCode = keyCode
    }

    fun setTermType(termType: String?) {
        mKeyListener?.setTermType(termType)
    }

    fun setMouseTracking(flag: Boolean) {
        mMouseTracking = flag
    }

    fun getURLat(x: Float, y: Float): String? {
        val w = width.toFloat()
        val h = height.toFloat()

        if (w == 0f || h == 0f) {
            return null
        }

        val x_pos = x / w
        val y_pos = y / h

        val row = (y_pos * mRows).toInt()
        val col = (x_pos * mColumns).toInt()

        val linkRow = mLinkLayer[row]
        val link = linkRow?.get(col)

        return link?.url
    }

    private fun createLinks(row: Int): Int {
        val transcriptScreen = mEmulator?.screen ?: return 1
        val line = transcriptScreen.getScriptLine(row) ?: return 1
        var lineCount = 1

        var lineLen: Int
        val textIsBasic = transcriptScreen.isBasicLine(row)
        if (textIsBasic) {
            lineLen = line.size
        } else {
            lineLen = 0
            while (line[lineLen].code != 0) {
                lineLen++
            }
        }

        val textToLinkify = SpannableStringBuilder(String(line, 0, lineLen))

        var lineWrap = transcriptScreen.getScriptLineWrap(row)

        while (lineWrap) {
            val nextRow = row + lineCount
            val nextLine = transcriptScreen.getScriptLine(nextRow) ?: break

            val lineIsBasic = transcriptScreen.isBasicLine(nextRow)
            lineLen = if (lineIsBasic) {
                nextLine.size
            } else {
                var len = 0
                while (nextLine[len].code != 0) {
                    len++
                }
                len
            }

            textToLinkify.append(String(nextLine, 0, lineLen))

            lineWrap = transcriptScreen.getScriptLineWrap(nextRow)
            lineCount++
        }

        Linkify.addLinks(
            textToLinkify,
            Pattern.compile(Patterns.WEB_URL),
            null,
            sHttpMatchFilter,
            null
        )
        val urls = textToLinkify.getSpans(0, textToLinkify.length, URLSpan::class.java)
        if (urls.isNotEmpty()) {
            val columns = mColumns

            val screenRow = row - mTopRow

            val linkRows = Array(lineCount) { arrayOfNulls<URLSpan>(columns) }
            for (i in 0 until lineCount) {
                Arrays.fill(linkRows[i], null)
            }

            for (url in urls) {
                val spanStart = textToLinkify.getSpanStart(url)
                val spanEnd = textToLinkify.getSpanEnd(url)

                val (startRow, startCol, endRow, endCol) = if (textIsBasic) {
                    val spanLastPos = spanEnd - 1
                    val startRow = spanStart / mColumns
                    val startCol = spanStart % mColumns
                    val endRow = spanLastPos / mColumns
                    val endCol = spanLastPos % mColumns
                    Quad(startRow, startCol, endRow, endCol)
                } else {
                    var startRow = 0
                    var startCol = 0
                    var i = 0
                    while (i < spanStart) {
                        val c = textToLinkify[i]
                        startCol += if (Character.isHighSurrogate(c)) {
                            UnicodeTranscript.charWidth(c, textToLinkify[++i])
                        } else {
                            UnicodeTranscript.charWidth(c.code)
                        }
                        if (startCol >= columns) {
                            startRow++
                            startCol %= columns
                        }
                    }

                    var endRow = startRow
                    var endCol = startCol
                    i = spanStart
                    while (i < spanEnd) {
                        val c = textToLinkify[i]
                        endCol += if (Character.isHighSurrogate(c)) {
                            UnicodeTranscript.charWidth(c, textToLinkify[++i])
                        } else {
                            UnicodeTranscript.charWidth(c.code)
                        }
                        if (endCol >= columns) {
                            endRow++
                            endCol %= columns
                        }
                        i++
                    }
                    Quad(startRow, startCol, endRow, endCol)
                }

                for (i in startRow..endRow) {
                    val runStart = if (i == startRow) startCol else 0
                    val runEnd = if (i == endRow) endCol else columns - 1

                    Arrays.fill(linkRows[i], runStart, runEnd + 1, url)
                }
            }

            for (i in 0 until lineCount) {
                mLinkLayer[screenRow + i] = linkRows[i]
            }
        }
        return lineCount
    }

    private data class Quad(val first: Int, val second: Int, val third: Int, val fourth: Int)
}
