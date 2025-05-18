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

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.ParcelFileDescriptor
import android.util.Log
import jackpal.androidterm.util.TermSettings
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * A terminal session, controlling the process attached to the session (usually
 * a shell). It keeps track of process PID and destroys its process group
 * upon stopping.
 */
class ShellTermSession(
    settings: TermSettings,
    private val mInitialCommand: String?
) : GenericTermSession(
    ParcelFileDescriptor.open(File("/dev/ptmx"), ParcelFileDescriptor.MODE_READ_WRITE),
    settings,
    false
) {
    private var mProcId: Int = 0
    private var mWatcherThread: Thread

    private val PROCESS_EXITED = 1
    private val mMsgHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (!isRunning) return
            if (msg.what == PROCESS_EXITED) {
                onProcessExit(msg.obj as Int)
            }
        }
    }

    init {
        initializeSession()
        termOut = ParcelFileDescriptor.AutoCloseOutputStream(mTermFd)
        termIn = ParcelFileDescriptor.AutoCloseInputStream(mTermFd)

        mWatcherThread = Thread {
            Log.i(TermDebug.LOG_TAG, "waiting for: $mProcId")
            val result = TermExec.waitFor(mProcId)
            Log.i(TermDebug.LOG_TAG, "Subprocess exited: $result")
            mMsgHandler.sendMessage(mMsgHandler.obtainMessage(PROCESS_EXITED, result))
        }
        mWatcherThread.name = "Process watcher"
    }

    @Throws(IOException::class)
    private fun initializeSession() {
        val settings = mSettings
        var path = System.getenv("PATH") ?: ""
        if (settings.doPathExtensions()) {
            val appendPath = settings.appendPath
            if (!appendPath.isNullOrEmpty()) {
                path = "$path:$appendPath"
            }
            if (settings.allowPathPrepend()) {
                val prependPath = settings.prependPath
                if (!prependPath.isNullOrEmpty()) {
                    path = "$prependPath:$path"
                }
            }
        }
        if (settings.verifyPath()) {
            path = checkPath(path)
        }
        val env = arrayOf(
            "TERM=" + settings.termType,
            "PATH=$path",
            "HOME=" + settings.homePath
        )
        mProcId = createSubprocess(settings.shell, env)
    }

    private fun checkPath(path: String): String {
        val dirs = path.split(":")
        val checkedPath = StringBuilder(path.length)
        for (dirname in dirs) {
            val dir = File(dirname)
            if (dir.isDirectory && dir.canExecute()) {
                checkedPath.append(dirname)
                checkedPath.append(":")
            }
        }
        return if (checkedPath.isNotEmpty()) checkedPath.substring(0, checkedPath.length - 1) else ""
    }

    override fun initializeEmulator(columns: Int, rows: Int) {
        super.initializeEmulator(columns, rows)
        mWatcherThread.start()
        sendInitialCommand(mInitialCommand)
    }

    private fun sendInitialCommand(initialCommand: String?) {
        if (!initialCommand.isNullOrEmpty()) {
            write(initialCommand + '\r')
        }
    }

    @Throws(IOException::class)
    private fun createSubprocess(shell: String, env: Array<String>): Int {
        var argList = parse(shell)
        var arg0: String
        var args: Array<String>
        try {
            arg0 = argList[0]
            val file = File(arg0)
            if (!file.exists()) {
                Log.e(TermDebug.LOG_TAG, "Shell $arg0 not found!")
                throw FileNotFoundException(arg0)
            } else if (!file.canExecute()) {
                Log.e(TermDebug.LOG_TAG, "Shell $arg0 not executable!")
                throw FileNotFoundException(arg0)
            }
            args = argList.toTypedArray()
        } catch (_: Exception) {
            argList = parse(mSettings.failsafeShell)
            arg0 = argList[0]
            args = argList.toTypedArray()
        }
        return TermExec.createSubprocess(mTermFd, arg0, args, env)
    }

    private fun parse(cmd: String): ArrayList<String> {
        val PLAIN = 0
        val WHITESPACE = 1
        val INQUOTE = 2
        var state = WHITESPACE
        val result = ArrayList<String>()
        val cmdLen = cmd.length
        val builder = StringBuilder()
        var i = 0
        while (i < cmdLen) {
            val c = cmd[i]
            when (state) {
                PLAIN -> {
                    if (c.isWhitespace()) {
                        result.add(builder.toString())
                        builder.clear()
                        state = WHITESPACE
                    } else if (c == '"') {
                        state = INQUOTE
                    } else {
                        builder.append(c)
                    }
                }
                WHITESPACE -> {
                    if (c.isWhitespace()) {
                        // do nothing
                    } else if (c == '"') {
                        state = INQUOTE
                    } else {
                        state = PLAIN
                        builder.append(c)
                    }
                }
                INQUOTE -> {
                    if (c == '\\') {
                        if (i + 1 < cmdLen) {
                            i += 1
                            builder.append(cmd[i])
                        }
                    } else if (c == '"') {
                        state = PLAIN
                    } else {
                        builder.append(c)
                    }
                }
            }
            i++
        }
        if (builder.isNotEmpty()) {
            result.add(builder.toString())
        }
        return result
    }

    private fun onProcessExit(result: Int) {
        onProcessExit()
    }

    override fun finish() {
        hangupProcessGroup()
        super.finish()
    }

    /**
     * Send SIGHUP to a process group, SIGHUP notifies a terminal client, that the terminal have been disconnected,
     * and usually results in client's death, unless it's process is a daemon or have been somehow else detached
     * from the terminal (for example, by the "nohup" utility).
     */
    fun hangupProcessGroup() {
        TermExec.sendSignal(-mProcId, 1)
    }
}

