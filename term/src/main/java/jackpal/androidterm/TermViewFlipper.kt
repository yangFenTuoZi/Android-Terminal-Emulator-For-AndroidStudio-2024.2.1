/*
 * Copyright (C) 2011 Steven Luo
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

package jackpal.androidterm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.core.view.isEmpty
import jackpal.androidterm.emulatorview.EmulatorView
import jackpal.androidterm.emulatorview.UpdateCallback
import jackpal.androidterm.util.TermSettings
import java.util.LinkedList

class TermViewFlipper : ViewFlipper, Iterable<View> {
    private var contextRef: Context
    private var mToast: Toast? = null
    private val callbacks = LinkedList<UpdateCallback>()
    private var mStatusBarVisible = false
    private var mCurWidth = 0
    private var mCurHeight = 0
    private val mVisibleRect = Rect()
    private val mWindowRect = Rect()
    private var mChildParams: LayoutParams? = null
    private var mRedoLayout = false
    private val mbPollForWindowSizeChange = false
    private val mHandler = Handler(Looper.getMainLooper())
    private val SCREEN_CHECK_PERIOD = 1000
    private val mCheckSize = object : Runnable {
        override fun run() {
            adjustChildSize()
            mHandler.postDelayed(this, SCREEN_CHECK_PERIOD.toLong())
        }
    }

    constructor(context: Context) : super(context) {
        contextRef = context
        commonConstructor(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        contextRef = context
        commonConstructor(context)
    }

    private fun commonConstructor(context: Context) {
        updateVisibleRect()
        val visible = mVisibleRect
        mChildParams = LayoutParams(visible.width(), visible.height(), Gravity.TOP or Gravity.START)
    }

    fun updatePrefs(settings: TermSettings) {
        val statusBarVisible = settings.showStatusBar()
        val colorScheme = settings.colorScheme
        setBackgroundColor(colorScheme[1])
        mStatusBarVisible = statusBarVisible
    }

    override fun iterator(): Iterator<View> {
        return object : Iterator<View> {
            var pos = 0
            override fun hasNext(): Boolean = pos < childCount
            override fun next(): View = getChildAt(pos++)
        }
    }

    fun addCallback(callback: UpdateCallback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: UpdateCallback) {
        callbacks.remove(callback)
    }

    private fun notifyChange() {
        for (callback in callbacks) {
            callback.onUpdate()
        }
    }

    fun onPause() {
        if (mbPollForWindowSizeChange) {
            mHandler.removeCallbacks(mCheckSize)
        }
        pauseCurrentView()
    }

    fun onResume() {
        if (mbPollForWindowSizeChange) {
            mCheckSize.run()
        }
        resumeCurrentView()
    }

    fun pauseCurrentView() {
        val view = currentView as? EmulatorView ?: return
        view.onPause()
    }

    fun resumeCurrentView() {
        val view = currentView as? EmulatorView ?: return
        view.onResume()
        view.requestFocus()
    }

    private fun showTitle() {
        if (isEmpty()) return
        val view = currentView as? EmulatorView ?: return
        val session = view.termSession ?: return
        var title = contextRef.getString(R.string.window_title, displayedChild + 1)
        if (session is GenericTermSession) {
            title = session.getTitle(title)
        }
        mToast?.cancel()
        mToast = Toast.makeText(contextRef, title, Toast.LENGTH_SHORT).apply { show() }
    }

    override fun showPrevious() {
        pauseCurrentView()
        super.showPrevious()
        showTitle()
        resumeCurrentView()
        notifyChange()
    }

    override fun showNext() {
        pauseCurrentView()
        super.showNext()
        showTitle()
        resumeCurrentView()
        notifyChange()
    }

    override fun setDisplayedChild(position: Int) {
        pauseCurrentView()
        super.setDisplayedChild(position)
        showTitle()
        resumeCurrentView()
        notifyChange()
    }

    override fun addView(v: View, index: Int) {
        super.addView(v, index, mChildParams)
    }

    override fun addView(v: View) {
        super.addView(v, mChildParams)
    }

    private fun updateVisibleRect() {
        val visible = mVisibleRect
        val window = mWindowRect
        getGlobalVisibleRect(visible)
        getWindowVisibleDisplayFrame(window)
        if (!mStatusBarVisible) {
            window.top = 0
        }
        if (visible.width() == 0 && visible.height() == 0) {
            visible.left = window.left
            visible.top = window.top
        } else {
            if (visible.left < window.left) {
                visible.left = window.left
            }
            if (visible.top < window.top) {
                visible.top = window.top
            }
        }
        visible.right = window.right
        visible.bottom = window.bottom
    }

    private fun adjustChildSize() {
        updateVisibleRect()
        val visible = mVisibleRect
        val width = visible.width()
        val height = visible.height()
        if (mCurWidth != width || mCurHeight != height) {
            mCurWidth = width
            mCurHeight = height
            val params = mChildParams
            if (params != null) {
                params.width = width
                params.height = height
                for (v in this) {
                    updateViewLayout(v, params)
                }
            }
            mRedoLayout = true
            val currentView = currentView as? EmulatorView
            currentView?.updateSize(false)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        adjustChildSize()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        if (mRedoLayout) {
            requestLayout()
            mRedoLayout = false
        }
        super.onDraw(canvas)
    }
}

