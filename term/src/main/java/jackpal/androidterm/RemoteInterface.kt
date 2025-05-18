/*
 * Copyright (C) 2012 Steven Luo
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

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import jackpal.androidterm.util.TermSettings
import java.io.File
import java.io.IOException
import java.util.UUID

open class RemoteInterface : AppCompatActivity() {

    private lateinit var mSettings: TermSettings
    private var mTermService: TermService? = null
    private var mTSIntent: Intent? = null
    private var mTSConnection: ServiceConnection? = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TermService.TSBinder
            mTermService = binder.service
            handleIntent()
        }
        override fun onServiceDisconnected(className: ComponentName) {
            mTermService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        mSettings = TermSettings(resources, prefs)
        val tsIntent = Intent(this, TermService::class.java)
        mTSIntent = tsIntent
        startService(tsIntent)
        if (!bindService(tsIntent, mTSConnection!!, BIND_AUTO_CREATE)) {
            Log.e(TermDebug.LOG_TAG, "bind to service failed!")
            finish()
        }
    }

    override fun finish() {
        val conn = mTSConnection
        if (conn != null) {
            unbindService(conn)
            val service = mTermService
            if (service != null) {
                val sessions = service.sessions
                if (sessions.isEmpty()) {
                    stopService(mTSIntent)
                }
            }
            mTSConnection = null
            mTermService = null
        }
        super.finish()
    }

    protected val termService: TermService?
        get() = mTermService

    protected open fun handleIntent() {
        val service = termService
        if (service == null) {
            finish()
            return
        }
        val myIntent = intent
        val action = myIntent.action
        if (action == Intent.ACTION_SEND && myIntent.hasExtra(Intent.EXTRA_STREAM)) {
            val extraStream = myIntent.extras?.get(Intent.EXTRA_STREAM)
            if (extraStream is Uri) {
                val path = extraStream.path
                val file = File(path)
                val dirPath = if (file.isDirectory) path else file.parent
                openNewWindow("cd " + dirPath?.let { quoteForBash(it) })
            }
        } else {
            openNewWindow(null)
        }
        finish()
    }

    companion object {
        const val PRIVACT_OPEN_NEW_WINDOW = "jackpal.androidterm.private.OPEN_NEW_WINDOW"
        const val PRIVACT_SWITCH_WINDOW = "jackpal.androidterm.private.SWITCH_WINDOW"
        const val PRIVEXTRA_TARGET_WINDOW = "jackpal.androidterm.private.target_window"
        const val PRIVACT_ACTIVITY_ALIAS = "jackpal.androidterm.TermInternal"
        /**
         *  Quote a string so it can be used as a parameter in bash and similar shells.
         */
        @JvmStatic
        fun quoteForBash(s: String): String {
            val builder = StringBuilder()
            val specialChars = "\"\\$`!"
            builder.append('"')
            for (c in s) {
                if (specialChars.indexOf(c) >= 0) {
                    builder.append('\\')
                }
                builder.append(c)
            }
            builder.append('"')
            return builder.toString()
        }
    }

    protected fun openNewWindow(iInitialCommand: String?): String? {
        val service = termService
        var initialCommand = mSettings.initialCommand
        if (iInitialCommand != null) {
            initialCommand = if (initialCommand != null) {
                "$initialCommand\r$iInitialCommand"
            } else {
                iInitialCommand
            }
        }
        return try {
            val session = Term.createTermSession(this, mSettings, initialCommand)
            session.setFinishCallback(service)
            service?.sessions?.add(session)
            val handle = UUID.randomUUID().toString()
            (session as GenericTermSession).handle = handle
            val intent = Intent(PRIVACT_OPEN_NEW_WINDOW)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            handle
        } catch (_: IOException) {
            null
        }
    }

    protected fun appendToWindow(handle: String, iInitialCommand: String?): String? {
        val service = termService
        val sessions = service?.sessions
        var target: GenericTermSession? = null
        var index = 0
        if (sessions != null) {
            for (i in 0 until sessions.size) {
                val session = sessions[i] as GenericTermSession
                val h = session.handle
                if (h != null && h == handle) {
                    target = session
                    index = i
                    break
                }
            }
        }
        if (target == null) {
            return openNewWindow(iInitialCommand)
        }
        if (iInitialCommand != null) {
            target.write(iInitialCommand)
            target.write("\r")
        }
        val intent = Intent(PRIVACT_SWITCH_WINDOW)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra(PRIVEXTRA_TARGET_WINDOW, index)
        startActivity(intent)
        return handle
    }
}

