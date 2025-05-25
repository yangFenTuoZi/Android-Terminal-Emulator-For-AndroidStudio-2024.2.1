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

package jackpal.androidterm

import android.os.ParcelFileDescriptor
import android.util.Log
import jackpal.androidterm.emulatorview.ColorScheme
import jackpal.androidterm.emulatorview.TermSession
import jackpal.androidterm.emulatorview.UpdateCallback
import jackpal.androidterm.util.TermSettings
import java.io.FileDescriptor
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.lang.reflect.Field

/**
 * A terminal session, consisting of a TerminalEmulator, a TranscriptScreen,
 * and the I/O streams used to talk to the process.
 */
open class GenericTermSession(
    val mTermFd: ParcelFileDescriptor,
    var mSettings: TermSettings,
    exitOnEOF: Boolean
) : TermSession(exitOnEOF) {
    companion object {
        private const val VTTEST_MODE = false
        private var descriptorField: Field? = null
        const val PROCESS_EXIT_FINISHES_SESSION = 0
        const val PROCESS_EXIT_DISPLAYS_MESSAGE = 1
        private fun cacheDescField() {
            if (descriptorField != null) return
            descriptorField = FileDescriptor::class.java.getDeclaredField("descriptor")
            descriptorField!!.isAccessible = true
        }
        @Throws(IOException::class)
        private fun getIntFd(parcelFd: ParcelFileDescriptor): Int {
            return FdHelperHoneycomb.getFd(parcelFd)
        }
    }

    private val createdAt: Long = System.currentTimeMillis()
    private var mHandle: String? = null
    private var mProcessExitMessage: String? = null
    private val mUTF8ModeNotify = UpdateCallback { setPtyUTF8Mode(uTF8Mode) }

    init {
        updatePrefs(mSettings)
    }

    fun updatePrefs(settings: TermSettings) {
        mSettings = settings
        setColorScheme(ColorScheme(settings.colorScheme))
        setDefaultUTF8Mode(settings.defaultToUTF8Mode())
    }

    override fun initializeEmulator(columns: Int, rows: Int) {
        var cols = columns
        var rws = rows
        if (VTTEST_MODE) {
            cols = 80
            rws = 24
        }
        super.initializeEmulator(cols, rws)
        setPtyUTF8Mode(uTF8Mode)
        setUTF8ModeUpdateCallback(mUTF8ModeNotify)
    }

    override fun updateSize(columns: Int, rows: Int) {
        var cols = columns
        var rws = rows
        if (VTTEST_MODE) {
            cols = 80
            rws = 24
        }
        setPtyWindowSize(rws, cols, 0, 0)
        super.updateSize(cols, rws)
    }

    fun setProcessExitMessage(message: String) {
        mProcessExitMessage = message
    }

    override fun onProcessExit() {
        if (mSettings.closeWindowOnProcessExit()) {
            finish()
        } else if (mProcessExitMessage != null) {
            try {
                val msg = "\r\n[${mProcessExitMessage}]".toByteArray(Charsets.UTF_8)
                appendToEmulator(msg, 0, msg.size)
                notifyUpdate()
            } catch (_: UnsupportedEncodingException) {
                // Never happens
            }
        }
    }

    override fun finish() {
        try {
            mTermFd.close()
        } catch (_: IOException) {
            // ok
        }
        super.finish()
    }

    /**
     * Gets the terminal session's title.  Unlike the superclass's getTitle(),
     * if the title is null or an empty string, the provided default title will
     * be returned instead.
     *
     * @param defaultTitle The default title to use if this session's title is
     *     unset or an empty string.
     */
    fun getTitle(defaultTitle: String): String {
        val title = super.title
        return if (!title.isNullOrEmpty()) title else defaultTitle
    }

    var handle: String?
        get() = mHandle
        set(it) {
            if (mHandle != null) {
                throw IllegalStateException("Cannot change handle once set")
            }
            mHandle = it
        }

    override fun toString(): String {
        return "${javaClass.simpleName}($createdAt,$mHandle)"
    }

    /**
     * Set the widow size for a given pty. Allows programs
     * connected to the pty learn how large their screen is.
     */
    fun setPtyWindowSize(row: Int, col: Int, xpixel: Int, ypixel: Int) {
        if (!mTermFd.fileDescriptor.valid()) return
        try {
            Exec.setPtyWindowSizeInternal(getIntFd(mTermFd), row, col, xpixel, ypixel)
        } catch (e: IOException) {
            Log.e("exec", "Failed to set window size: ${e.message}")
            if (isFailFast()) throw IllegalStateException(e)
        }
    }

    /**
     * Set or clear UTF-8 mode for a given pty.  Used by the terminal driver
     * to implement correct erase behavior in cooked mode (Linux >= 2.6.4).
     */
    fun setPtyUTF8Mode(utf8Mode: Boolean) {
        if (!mTermFd.fileDescriptor.valid()) return
        try {
            Exec.setPtyUTF8ModeInternal(getIntFd(mTermFd), utf8Mode)
        } catch (e: IOException) {
            Log.e("exec", "Failed to set UTF mode: ${e.message}")
            if (isFailFast()) throw IllegalStateException(e)
        }
    }

    /**
     * @return true, if failing to operate on file descriptor deserves an exception (never the case for ATE own shell)
     */
    open fun isFailFast(): Boolean = false
}

