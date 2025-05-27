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

import android.os.Handler
import android.os.Looper
import android.os.Message
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetEncoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * A terminal session, consisting of a VT100 terminal emulator and its
 * input and output streams.
 *
 *
 * You need to supply an [InputStream] and [OutputStream] to
 * provide input and output to the terminal.  For a locally running
 * program, these would typically point to a tty; for a telnet program
 * they might point to a network socket.  Reader and writer threads will be
 * spawned to do I/O to these streams.  All other operations, including
 * processing of input and output in [processInput][.processInput] and
 * [write][.write], will be performed on the main thread.
 *
 *
 * Call [.setTermIn] and [.setTermOut] to connect the input and
 * output streams to the emulator.  When all of your initialization is
 * complete, your initial screen size is known, and you're ready to
 * start VT100 emulation, call [.initializeEmulator] or [ ][.updateSize] with the number of rows and columns the terminal should
 * initially have.  (If you attach the session to an [EmulatorView],
 * the view will take care of setting the screen size and initializing the
 * emulator for you.)
 *
 *
 * When you're done with the session, you should call [.finish] on it.
 * This frees emulator data from memory, stops the reader and writer threads,
 * and closes the attached I/O streams.
 */
open class TermSession @JvmOverloads constructor(exitOnEOF: Boolean = false) {
    fun setKeyListener(l: TermKeyListener?) {
        mKeyListener = l
    }

    private var mKeyListener: TermKeyListener? = null

    private var mColorScheme: ColorScheme? = BaseTextRenderer.defaultColorScheme
    private var mNotify: UpdateCallback? = null

    private var mTermOut: OutputStream? = null
    private var mTermIn: InputStream? = null

    private var mTitle: String? = null

    var transcriptScreen: TranscriptScreen? = null
        private set
    var emulator: TerminalEmulator? = null
        private set

    private var mDefaultUTF8Mode = false

    private val mReaderThread: Thread
    private val mByteQueue: ByteQueue
    private val mReceiveBuffer: ByteArray

    private val mWriterThread: Thread
    private val mWriteQueue: ByteQueue
    private var mWriterHandler: Handler? = null

    private val mWriteCharBuffer: CharBuffer = CharBuffer.allocate(2)
    private val mWriteByteBuffer: ByteBuffer = ByteBuffer.allocate(4)
    private val mUTF8Encoder: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()

    /**
     * Callback to be invoked when a [TermSession] finishes.
     *
     * @see TermSession.setUpdateCallback
     */
    interface FinishCallback {
        /**
         * Callback function to be invoked when a [TermSession] finishes.
         *
         * @param session The `TermSession` which has finished.
         */
        fun onSessionFinish(session: TermSession?)
    }

    private var mFinishCallback: FinishCallback? = null

    /**
     * @return Whether the terminal emulation is currently running.
     */
    var isRunning: Boolean = false
        private set
    private val mMsgHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (!isRunning) {
                return
            }
            if (msg.what == NEW_INPUT) {
                readFromProcess()
            } else if (msg.what == EOF) {
                Handler(Looper.getMainLooper()).post { onProcessExit() }
            }
        }
    }

    private var mTitleChangedListener: UpdateCallback? = null

    init {
        mUTF8Encoder.onMalformedInput(CodingErrorAction.REPLACE)
        mUTF8Encoder.onUnmappableCharacter(CodingErrorAction.REPLACE)

        mReceiveBuffer = ByteArray(4 * 1024)
        mByteQueue = ByteQueue(4 * 1024)
        mReaderThread = object : Thread() {
            private val mBuffer = ByteArray(4096)

            override fun run() {
                try {
                    while (true) {
                        var read = mTermIn?.read(mBuffer)
                        if (read == -1 || read == null) {
                            // EOF -- process exited
                            break
                        }
                        var offset = 0
                        while (read > 0) {
                            val written = mByteQueue.write(
                                mBuffer,
                                offset, read
                            )
                            offset += written
                            read -= written
                            mMsgHandler.sendMessage(
                                mMsgHandler.obtainMessage(NEW_INPUT)
                            )
                        }
                    }
                } catch (_: IOException) {
                } catch (_: InterruptedException) {
                }

                if (exitOnEOF) mMsgHandler.sendMessage(mMsgHandler.obtainMessage(EOF))
            }
        }
        mReaderThread.setName("TermSession input reader")

        mWriteQueue = ByteQueue(4096)
        mWriterThread = object : Thread() {
            private val mBuffer = ByteArray(4096)

            override fun run() {
                Looper.prepare()

                mWriterHandler = object : Handler(Looper.myLooper()!!) {
                    override fun handleMessage(msg: Message) {
                        if (msg.what == NEW_OUTPUT) {
                            writeToOutput()
                        } else if (msg.what == FINISH) {
                            Looper.myLooper()?.quit()
                        }
                    }
                }

                // Drain anything in the queue from before we started
                writeToOutput()

                Looper.loop()
            }

            private fun writeToOutput() {
                val writeQueue = mWriteQueue
                val buffer = mBuffer
                val termOut: OutputStream = mTermOut!!

                val bytesAvailable = writeQueue.bytesAvailable
                val bytesToWrite = min(bytesAvailable.toDouble(), buffer.size.toDouble()).toInt()

                if (bytesToWrite == 0) {
                    return
                }

                try {
                    writeQueue.read(buffer, 0, bytesToWrite)
                    termOut.write(buffer, 0, bytesToWrite)
                    termOut.flush()
                } catch (e: IOException) {
                    // Ignore exception
                    // We don't really care if the receiver isn't listening.
                    // We just make a best effort to answer the query.
                    e.printStackTrace()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
        mWriterThread.setName("TermSession output writer")
    }

    protected open fun onProcessExit() {
        finish()
    }

    /**
     * Set the terminal emulator's window size and start terminal emulation.
     *
     * @param columns The number of columns in the terminal window.
     * @param rows The number of rows in the terminal window.
     */
    open fun initializeEmulator(columns: Int, rows: Int) {
        this.transcriptScreen = TranscriptScreen(columns, TRANSCRIPT_ROWS, rows, mColorScheme)
        this.emulator =
            this.transcriptScreen?.let { TerminalEmulator(this, it, columns, rows, mColorScheme) }
        emulator?.setDefaultUTF8Mode(mDefaultUTF8Mode)
        mKeyListener?.let { emulator?.setKeyListener(it) }

        this.isRunning = true
        mReaderThread.start()
        mWriterThread.start()
    }

    /**
     * Write data to the terminal output.  The written data will be consumed by
     * the emulation client as input.
     *
     *
     * `write` itself runs on the main thread.  The default
     * implementation writes the data into a circular buffer and signals the
     * writer thread to copy it from there to the [OutputStream].
     *
     *
     * Subclasses may override this method to modify the output before writing
     * it to the stream, but implementations in derived classes should call
     * through to this method to do the actual writing.
     *
     * @param data An array of bytes to write to the terminal.
     * @param offset The offset into the array at which the data starts.
     * @param count The number of bytes to be written.
     */
    fun write(data: ByteArray, offset: Int, count: Int) {
        var offset = offset
        var count = count
        try {
            while (count > 0) {
                val written = mWriteQueue.write(data, offset, count)
                offset += written
                count -= written
                notifyNewOutput()
            }
        } catch (_: InterruptedException) {
        }
    }

    /**
     * Write the UTF-8 representation of a String to the terminal output.  The
     * written data will be consumed by the emulation client as input.
     *
     *
     * This implementation encodes the String and then calls
     * [.write] to do the actual writing.  It should
     * therefore usually be unnecessary to override this method; override
     * [.write] instead.
     *
     * @param data The String to write to the terminal.
     */
    fun write(data: String) {
        try {
            val bytes = data.toByteArray(charset("UTF-8"))
            write(bytes, 0, bytes.size)
        } catch (_: UnsupportedEncodingException) {
        }
    }

    /**
     * Write the UTF-8 representation of a single Unicode code point to the
     * terminal output.  The written data will be consumed by the emulation
     * client as input.
     *
     *
     * This implementation encodes the code point and then calls
     * [.write] to do the actual writing.  It should
     * therefore usually be unnecessary to override this method; override
     * [.write] instead.
     *
     * @param codePoint The Unicode code point to write to the terminal.
     */
    fun write(codePoint: Int) {
        val byteBuf = mWriteByteBuffer
        if (codePoint < 128) {
            // Fast path for ASCII characters
            val buf = byteBuf.array()
            buf[0] = codePoint.toByte()
            write(buf, 0, 1)
            return
        }

        val charBuf = mWriteCharBuffer
        val encoder = mUTF8Encoder

        charBuf.clear()
        byteBuf.clear()
        Character.toChars(codePoint, charBuf.array(), 0)
        encoder.reset()
        encoder.encode(charBuf, byteBuf, true)
        encoder.flush(byteBuf)
        write(byteBuf.array(), 0, byteBuf.position() - 1)
    }

    /* Notify the writer thread that there's new output waiting */
    private fun notifyNewOutput() {
        val writerHandler = mWriterHandler
        if (writerHandler == null) {
            /* Writer thread isn't started -- will pick up data once it does */
            return
        }
        writerHandler.sendEmptyMessage(NEW_OUTPUT)
    }

    var termOut: OutputStream?
        /**
         * Get the [OutputStream] associated with this session.
         *
         * @return This session's [OutputStream].
         */
        get() = mTermOut
        /**
         * Set the [OutputStream] associated with this session.
         *
         * @param termOut This session's [OutputStream].
         */
        set(termOut) {
            mTermOut = termOut
        }

    var termIn: InputStream?
        /**
         * Get the [InputStream] associated with this session.
         *
         * @return This session's [InputStream].
         */
        get() = mTermIn
        /**
         * Set the [InputStream] associated with this session.
         *
         * @param termIn This session's [InputStream].
         */
        set(termIn) {
            mTermIn = termIn
        }

    /**
     * Set an [UpdateCallback] to be invoked when the terminal emulator's
     * screen is changed.
     *
     * @param notify The [UpdateCallback] to be invoked on changes.
     */
    fun setUpdateCallback(notify: UpdateCallback?) {
        mNotify = notify
    }

    /**
     * Notify the [UpdateCallback] registered by [ ][.setUpdateCallback] that the screen has changed.
     */
    protected fun notifyUpdate() {
        if (mNotify != null) {
            mNotify?.onUpdate()
        }
    }

    open var title: String?
        /**
         * Get the terminal session's title (may be null).
         */
        get() = mTitle
        /**
         * Change the terminal session's title.
         */
        set(title) {
            mTitle = title
            notifyTitleChanged()
        }

    /**
     * Set an [UpdateCallback] to be invoked when the terminal emulator's
     * title is changed.
     *
     * @param listener The [UpdateCallback] to be invoked on changes.
     */
    fun setTitleChangedListener(listener: UpdateCallback?) {
        mTitleChangedListener = listener
    }

    /**
     * Notify the UpdateCallback registered for title changes, if any, that the
     * terminal session's title has changed.
     */
    protected fun notifyTitleChanged() {
        val listener = mTitleChangedListener
        listener?.onUpdate()
    }

    /**
     * Change the terminal's window size.  Will call [.initializeEmulator]
     * if the emulator is not yet running.
     *
     *
     * You should override this method if your application needs to be notified
     * when the screen size changes (for example, if you need to issue
     * `TIOCSWINSZ` to a tty to adjust the window size).  *If you
     * do override this method, you must call through to the superclass
     * implementation.*
     *
     * @param columns The number of columns in the terminal window.
     * @param rows The number of rows in the terminal window.
     */
    open fun updateSize(columns: Int, rows: Int) {
        if (this.emulator == null) {
            initializeEmulator(columns, rows)
        } else {
            emulator?.updateSize(columns, rows)
        }
    }

    val transcriptText: String?
        /**
         * Retrieve the terminal's screen and scrollback buffer.
         *
         * @return A [String] containing the contents of the screen and
         * scrollback buffer.
         */
        get() = transcriptScreen?.transcriptText

    /**
     * Look for new input from the ptty, send it to the terminal emulator.
     */
    private fun readFromProcess() {
        val bytesAvailable = mByteQueue.bytesAvailable
        val bytesToRead = min(bytesAvailable.toDouble(), mReceiveBuffer.size.toDouble()).toInt()
        val bytesRead: Int
        try {
            bytesRead = mByteQueue.read(mReceiveBuffer, 0, bytesToRead)
        } catch (_: InterruptedException) {
            return
        }

        // Give subclasses a chance to process the read data
        processInput(mReceiveBuffer, 0, bytesRead)
        notifyUpdate()
    }

    /**
     * Process input and send it to the terminal emulator.  This method is
     * invoked on the main thread whenever new data is read from the
     * InputStream.
     *
     *
     * The default implementation sends the data straight to the terminal
     * emulator without modifying it in any way.  Subclasses can override it to
     * modify the data before giving it to the terminal.
     *
     * @param data A byte array containing the data read.
     * @param offset The offset into the buffer where the read data begins.
     * @param count The number of bytes read.
     */
    protected fun processInput(data: ByteArray, offset: Int, count: Int) {
        emulator?.append(data, offset, count)
    }

    /**
     * Write something directly to the terminal emulator input, bypassing the
     * emulation client, the session's [InputStream], and any processing
     * being done by [processInput][.processInput].
     *
     * @param data The data to be written to the terminal.
     * @param offset The starting offset into the buffer of the data.
     * @param count The length of the data to be written.
     */
    protected fun appendToEmulator(data: ByteArray, offset: Int, count: Int) {
        emulator?.append(data, offset, count)
    }

    /**
     * Set the terminal emulator's color scheme (default colors).
     *
     * @param scheme The [ColorScheme] to be used (use null for the
     * default scheme).
     */
    fun setColorScheme(scheme: ColorScheme?) {
        var scheme = scheme
        if (scheme == null) {
            scheme = BaseTextRenderer.defaultColorScheme
        }
        mColorScheme = scheme
        if (this.emulator == null) {
            return
        }
        emulator?.setColorScheme(scheme)
    }

    /**
     * Set whether the terminal emulator should be in UTF-8 mode by default.
     *
     *
     * In UTF-8 mode, the terminal will handle UTF-8 sequences, allowing the
     * display of text in most of the world's languages, but applications must
     * encode C1 control characters and graphics drawing characters as the
     * corresponding UTF-8 sequences.
     *
     * @param utf8ByDefault Whether the terminal emulator should be in UTF-8
     * mode by default.
     */
    fun setDefaultUTF8Mode(utf8ByDefault: Boolean) {
        mDefaultUTF8Mode = utf8ByDefault
        if (this.emulator == null) {
            return
        }
        emulator?.setDefaultUTF8Mode(utf8ByDefault)
    }

    val uTF8Mode: Boolean
        /**
         * Get whether the terminal emulator is currently in UTF-8 mode.
         *
         * @return Whether the emulator is currently in UTF-8 mode.
         */
        get() = if (this.emulator == null) {
            mDefaultUTF8Mode
        } else {
            emulator!!.uTF8Mode
        }

    /**
     * Set an [UpdateCallback] to be invoked when the terminal emulator
     * goes into or out of UTF-8 mode.
     *
     * @param utf8ModeNotify The [UpdateCallback] to be invoked.
     */
    fun setUTF8ModeUpdateCallback(utf8ModeNotify: UpdateCallback?) {
        if (this.emulator != null) {
            emulator?.setUTF8ModeUpdateCallback(utf8ModeNotify)
        }
    }

    /**
     * Reset the terminal emulator's state.
     */
    fun reset() {
        emulator?.reset()
        notifyUpdate()
    }

    /**
     * Set a [FinishCallback] to be invoked once this terminal session is
     * finished.
     *
     * @param callback The [FinishCallback] to be invoked on finish.
     */
    fun setFinishCallback(callback: FinishCallback?) {
        mFinishCallback = callback
    }

    /**
     * Finish this terminal session.  Frees resources used by the terminal
     * emulator and closes the attached `InputStream` and
     * `OutputStream`.
     */
    open fun finish() {
        this.isRunning = false
        emulator?.finish()
        if (this.transcriptScreen != null) {
            transcriptScreen?.finish()
        }

        // Stop the reader and writer threads, and close the I/O streams
        mWriterHandler?.sendEmptyMessage(FINISH)
        try {
            mTermIn?.close()
            mTermOut?.close()
        } catch (_: IOException) {
        }

        mFinishCallback?.onSessionFinish(this)
    }

    companion object {
        // Number of rows in the transcript
        private const val TRANSCRIPT_ROWS = 10000

        private const val NEW_INPUT = 1
        private const val NEW_OUTPUT = 2
        private const val FINISH = 3
        private const val EOF = 4
    }
}