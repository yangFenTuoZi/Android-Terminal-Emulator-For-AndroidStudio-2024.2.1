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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.ResultReceiver
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import jackpal.androidterm.emulatorview.TermSession
import jackpal.androidterm.libtermexec.v1.ITerminal
import jackpal.androidterm.util.SessionList
import jackpal.androidterm.util.TermSettings
import java.util.UUID

class TermService : Service(), TermSession.FinishCallback {

    companion object {
        const val ACTION_CLOSE_ALL_SESSIONS = "terminal.action.CLOSE_ALL_SESSIONS"
        const val ACTION_ENABLE_WAKE_LOCK = "terminal.action.ENABLE_WAKE_LOCK"
        const val ACTION_DISABLE_WAKE_LOCK = "terminal.action.DISABLE_WAKE_LOCK"
    }

    val channelId = "term_service_channel"

    private var mTermSessions: SessionList = SessionList()
    private lateinit var mWakeLock: WakeLock

    inner class TSBinder : Binder() {
        val service: TermService
            get() = this@TermService
    }

    private val mTSBinder: IBinder = TSBinder()


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return if (intent != null) {
            when (intent.action) {
                ACTION_CLOSE_ALL_SESSIONS -> {
                    Log.i("TermService", "Closing all sessions")
                    for (session in mTermSessions) {
                        session.setFinishCallback(null)
                        session.finish()
                    }
                    mTermSessions.clear()
                    stopSelf()
                    START_NOT_STICKY
                }

                ACTION_ENABLE_WAKE_LOCK -> {
                    Log.i("TermService", "Enabling wake lock")
                    if (!mWakeLock.isHeld) {
                        mWakeLock.acquire()
                    }
                    updateNotification()
                    START_NOT_STICKY
                }

                ACTION_DISABLE_WAKE_LOCK -> {
                    Log.i("TermService", "Disabling wake lock")
                    if (mWakeLock.isHeld) {
                        mWakeLock.release()
                    }
                    updateNotification()
                    START_NOT_STICKY
                }

                else -> {
                    Log.w("TermService", "Unknown action: ${intent.action}")
                    START_STICKY
                }
            }
        } else START_STICKY
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

    fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, buildNotification().build())
    }

    fun buildNotification(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, channelId).apply {
            setSmallIcon(R.drawable.ic_stat_service_notification_icon)
            setContentTitle(getString(R.string.application_terminal))
            setContentText(getString(R.string.service_notify_text, mTermSessions.size))
            setWhen(System.currentTimeMillis())
            setOngoing(true)
            setContentIntent(
                PendingIntent.getActivity(
                    this@TermService, 0, Intent(this@TermService, Term::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE
                )
            )
            addAction(
                NotificationCompat.Action.Builder(
                    null,
                    getString(R.string.exit),
                    PendingIntent.getService(
                        this@TermService, 0,
                        Intent().apply {
                            setClass(this@TermService, TermService::class.java)
                            action = ACTION_CLOSE_ALL_SESSIONS
                            `package` = BuildConfig.APPLICATION_ID
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )
            val hasWakeLock = mWakeLock.isHeld
            addAction(
                NotificationCompat.Action.Builder(
                    null,
                    getString(if (hasWakeLock) R.string.disable_wakelock else R.string.enable_wakelock),
                    PendingIntent.getService(
                        this@TermService, 0, Intent().apply {
                            setClass(this@TermService, TermService::class.java)
                            action =
                                if (hasWakeLock) ACTION_DISABLE_WAKE_LOCK else ACTION_ENABLE_WAKE_LOCK
                            `package` = BuildConfig.APPLICATION_ID
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )
        }
    }

    override fun onCreate() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (prefs.getString("home_path", null) == null)
            prefs.edit {
                putString("home_path", getDir("HOME", MODE_PRIVATE).absolutePath)
            }

        mWakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Term:wakelock"
        )

        mTermSessions = SessionList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel =
                NotificationChannel(
                    channelId,
                    getString(R.string.application_terminal),
                    NotificationManager.IMPORTANCE_LOW
                )
            nm.createNotificationChannel(channel)
        }

        startForeground(1, buildNotification().build())
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
        updateNotification()
    }

    private inner class RBinder : ITerminal.Stub() {
        override fun startSession(
            pseudoTerminalMultiplexerFd: ParcelFileDescriptor,
            callback: ResultReceiver
        ): IntentSender? {
            val sessionHandle = UUID.randomUUID().toString()
            val switchIntent = Intent(RemoteInterface.PRIVACT_OPEN_NEW_WINDOW)
                .setData(sessionHandle.toUri())
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(RemoteInterface.PRIVEXTRA_TARGET_WINDOW, sessionHandle)
            val result = PendingIntent.getActivity(
                applicationContext,
                sessionHandle.hashCode(),
                switchIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
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
                                val settings = TermSettings(
                                    resources,
                                    PreferenceManager.getDefaultSharedPreferences(
                                        applicationContext
                                    )
                                )
                                session =
                                    BoundSession(
                                        pseudoTerminalMultiplexerFd,
                                        settings,
                                        niceName
                                    )
                                mTermSessions.add(session)
                                session.handle = sessionHandle
                                session.setFinishCallback(
                                    RBinderCleanupCallback(
                                        result,
                                        callback
                                    )
                                )
                                session.title = ""
                                session.initializeEmulator(80, 24)
                            } catch (e: Exception) {
                                Log.e(
                                    "TermService",
                                    "Failed to bootstrap AIDL session: " + e.message
                                )
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

    private inner class RBinderCleanupCallback(
        private val result: PendingIntent,
        private val callback: ResultReceiver
    ) : TermSession.FinishCallback {
        override fun onSessionFinish(session: TermSession?) {
            result.cancel()
            callback.send(0, Bundle())
            mTermSessions.remove(session)
        }
    }
}

