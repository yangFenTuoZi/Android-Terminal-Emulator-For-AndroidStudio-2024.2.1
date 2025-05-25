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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import android.text.TextUtils
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import jackpal.androidterm.emulatorview.TermSession
import jackpal.androidterm.libtermexec.v1.ITerminal
import jackpal.androidterm.util.SessionList
import jackpal.androidterm.util.TermSettings
import java.util.UUID

class TermService : Service(), TermSession.FinishCallback {
    private var mTermSessions: SessionList = SessionList()

    inner class TSBinder : Binder() {
        val service: TermService
            get() = this@TermService
    }

    private val mTSBinder: IBinder = TSBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return if (TermExec.SERVICE_ACTION_V1 == intent.action) {
            Log.i("TermService", "Outside process called onBind()")
            RBinder()
        } else {
            Log.i("TermService", "Activity called onBind()")
            mTSBinder
        }
    }

    override fun onCreate() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        prefs.edit {
            val defValue = getDir("HOME", MODE_PRIVATE).absolutePath
            val homePath = prefs.getString("home_path", defValue)
            putString("home_path", homePath)
        }

        mTermSessions = SessionList()

        val channelId = "term_service_channel"
        val channelName = getString(R.string.application_terminal)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)

        val notifyIntent = Intent(this, Term::class.java)
        notifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_service_notification_icon)
            .setContentTitle(getText(R.string.application_terminal))
            .setContentText(getText(R.string.service_notify_text))
            .setWhen(System.currentTimeMillis())
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
        Log.d(TermDebug.LOG_TAG, "TermService started")
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        for (session in mTermSessions) {
            session.setFinishCallback(null)
            session.finish()
        }
        mTermSessions.clear()
    }

    val sessions: SessionList
        get() = mTermSessions

    override fun onSessionFinish(session: TermSession?) {
        mTermSessions.remove(session)
    }

    private inner class RBinder : ITerminal.Stub() {
        override fun startSession(pseudoTerminalMultiplexerFd: ParcelFileDescriptor, callback: ResultReceiver): IntentSender? {
            val sessionHandle = UUID.randomUUID().toString()
            val switchIntent = Intent(RemoteInterface.PRIVACT_OPEN_NEW_WINDOW)
                .setData(sessionHandle.toUri())
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(RemoteInterface.PRIVEXTRA_TARGET_WINDOW, sessionHandle)
            val result = PendingIntent.getActivity(applicationContext, sessionHandle.hashCode(), switchIntent, PendingIntent.FLAG_IMMUTABLE)
            val pm = packageManager
            val pkgs = pm.getPackagesForUid(getCallingUid()) ?: return null
            for (packageName in pkgs) {
                try {
                    val pkgInfo = pm.getPackageInfo(packageName, 0)
                    val appInfo = pkgInfo.applicationInfo ?: continue
                    val label = pm.getApplicationLabel(appInfo)
                    if (!TextUtils.isEmpty(label)) {
                        val niceName = label.toString()
                        Handler(Looper.getMainLooper()).post {
                            var session: GenericTermSession? = null
                            try {
                                val settings = TermSettings(resources, PreferenceManager.getDefaultSharedPreferences(applicationContext))
                                session = BoundSession(pseudoTerminalMultiplexerFd, settings, niceName)
                                mTermSessions.add(session)
                                session.handle = sessionHandle
                                session.setFinishCallback(RBinderCleanupCallback(result, callback))
                                session.title = ""
                                session.initializeEmulator(80, 24)
                            } catch (e: Exception) {
                                Log.e("TermService", "Failed to bootstrap AIDL session: " + e.message)
                                session?.finish()
                            }
                        }
                        return result.intentSender
                    }
                } catch (_: PackageManager.NameNotFoundException) {
                }
            }
            return null
        }
    }

    private inner class RBinderCleanupCallback(private val result: PendingIntent, private val callback: ResultReceiver) : TermSession.FinishCallback {
        override fun onSessionFinish(session: TermSession?) {
            result.cancel()
            callback.send(0, Bundle())
            mTermSessions.remove(session)
        }
    }
}

