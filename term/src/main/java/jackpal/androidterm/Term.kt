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

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.net.toUri
import androidx.core.view.get
import androidx.core.view.size
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jackpal.androidterm.TermService.TSBinder
import jackpal.androidterm.emulatorview.EmulatorView
import jackpal.androidterm.emulatorview.TermSession
import jackpal.androidterm.emulatorview.UpdateCallback
import jackpal.androidterm.emulatorview.compat.ClipboardManagerCompat
import jackpal.androidterm.emulatorview.compat.KeycodeConstants
import jackpal.androidterm.util.SessionList
import jackpal.androidterm.util.TermSettings
import java.io.IOException
import java.text.Collator
import java.util.Arrays
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * A terminal emulator activity.
 */
open class Term : AppCompatActivity(), UpdateCallback, OnSharedPreferenceChangeListener {
    /**
     * The ViewFlipper which holds the collection of EmulatorView widgets.
     */
    private var mViewFlipper: TermViewFlipper? = null

    private var mTermSessions: SessionList? = null

    private var mSettings: TermSettings? = null

    private var mAlreadyStarted = false
    private var mStopServiceOnFinish = false

    private var tsIntent: Intent? = null

    private var onResumeSelectWindow = -1
    private var mPrivateAlias: ComponentName? = null

    private var mWakeLock: WakeLock? = null
    private var mWifiLock: WifiLock? = null
    private val mBackKeyPressed = false

    private var mPendingPathBroadcasts = 0
    private val mPathReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val path = makePathFromBundle(getResultExtras(false))
            if (ACTION_PATH_PREPEND_BROADCAST == intent.action) {
                mSettings?.prependPath = path
            } else {
                mSettings?.appendPath = path
            }
            mPendingPathBroadcasts--

            if (mPendingPathBroadcasts <= 0 && mTermService != null) {
                populateViewFlipper()
            }
        }
    }
    private var mTermService: TermService? = null
    private var mTSConnection: ServiceConnection? = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            Log.i(TermDebug.LOG_TAG, "Bound to TermService")
            val binder = service as TSBinder
            mTermService = binder.service
            if (mPendingPathBroadcasts <= 0) {
                populateViewFlipper()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName?) {
            mTermService = null
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        sharedPreferences?.let { mSettings?.readPrefs(it) }
    }

    private var mHaveFullHwKeyboard = false

    private inner class EmulatorViewGestureListener(private val view: EmulatorView) :
        SimpleOnGestureListener() {

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Let the EmulatorView handle taps if mouse tracking is active
            if (view.isMouseTrackingActive) return false

            //Check for link at tap location
            val link = view.getURLat(e.x, e.y)
            if (link != null) execURL(link)
            else doUIToggle(
                e.x.toInt(),
                e.y.toInt(),
                view.visibleWidth,
                view.visibleHeight
            )
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val absVelocityX = abs(velocityX.toDouble()).toFloat()
            val absVelocityY = abs(velocityY.toDouble()).toFloat()
            if (absVelocityX > max(1000.0, 2.0 * absVelocityY)) {
                // Assume user wanted side to side movement
                if (velocityX > 0) {
                    // Left to right swipe -- previous window
                    mViewFlipper?.showPrevious()
                } else {
                    // Right to left swipe -- next window
                    mViewFlipper?.showNext()
                }
                return true
            } else {
                return false
            }
        }
    }

    /**
     * Should we use keyboard shortcuts?
     */
    private var mUseKeyboardShortcuts = false

    /**
     * Intercepts keys before the view/terminal gets it.
     */
    private val mKeyListener: View.OnKeyListener = object : View.OnKeyListener {
        override fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
            return backkeyInterceptor(keyCode, event) || keyboardShortcuts(keyCode, event)
        }

        /**
         * Keyboard shortcuts (tab management, paste)
         */
        private fun keyboardShortcuts(keyCode: Int, event: KeyEvent): Boolean {
            if (event.action != KeyEvent.ACTION_DOWN) {
                return false
            }
            if (!mUseKeyboardShortcuts) {
                return false
            }
            val isCtrlPressed = (event.metaState and KeycodeConstants.META_CTRL_ON) != 0
            val isShiftPressed = (event.metaState and KeycodeConstants.META_SHIFT_ON) != 0

            if (keyCode == KeycodeConstants.KEYCODE_TAB && isCtrlPressed) {
                if (isShiftPressed) {
                    mViewFlipper?.showPrevious()
                } else {
                    mViewFlipper?.showNext()
                }

                return true
            } else if (keyCode == KeycodeConstants.KEYCODE_N && isCtrlPressed && isShiftPressed) {
                doCreateNewWindow()

                return true
            } else if (keyCode == KeycodeConstants.KEYCODE_V && isCtrlPressed && isShiftPressed) {
                doPaste()

                return true
            } else {
                return false
            }
        }

        /**
         * Make sure the back button always leaves the application.
         */
        private fun backkeyInterceptor(keyCode: Int, event: KeyEvent?): Boolean {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                /* We need to intercept the key event before the view sees it,
                   otherwise the view will handle it before we get it */
                onKeyUp(keyCode, event)
                return true
            } else {
                return false
            }
        }
    }

    private val mHandler = Handler(HandlerThread("mThread").apply { start() }.looper)

    private var mActionBarMode: Int = TermSettings.ACTION_BAR_MODE_NONE

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        Log.v(TermDebug.LOG_TAG, "onCreate")

        mPrivateAlias = ComponentName(this, RemoteInterface.PRIVACT_ACTIVITY_ALIAS)

        if (icicle == null) onNewIntent(intent)

        val mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        mSettings = TermSettings(getResources(), mPrefs)
        mPrefs.registerOnSharedPreferenceChangeListener(this)

        var broadcast = Intent(ACTION_PATH_BROADCAST)
        broadcast.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        mPendingPathBroadcasts++
        sendOrderedBroadcast(
            broadcast,
            PERMISSION_PATH_BROADCAST,
            mPathReceiver,
            null,
            RESULT_OK,
            null,
            null
        )

        broadcast = Intent(broadcast)
        broadcast.setAction(ACTION_PATH_PREPEND_BROADCAST)
        mPendingPathBroadcasts++
        sendOrderedBroadcast(
            broadcast,
            PERMISSION_PATH_PREPEND_BROADCAST,
            mPathReceiver,
            null,
            RESULT_OK,
            null,
            null
        )

        tsIntent = Intent(this, TermService::class.java)
        startService(tsIntent)

        setContentView(R.layout.term_activity)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        mViewFlipper = findViewById<TermViewFlipper?>(VIEW_FLIPPER)
        // 初始化 Spinner

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Term:wakelock")
        val wm = getSystemService(WIFI_SERVICE) as WifiManager
        mWifiLock = wm.createWifiLock(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "Term:wifilock"
        )

        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(getResources().configuration)

        updatePrefs()
        mActionBarMode = mSettings?.actionBarMode() ?: TermSettings.ACTION_BAR_MODE_NONE
        mAlreadyStarted = true
    }

    private fun makePathFromBundle(extras: Bundle?): String? {
        if (extras == null || extras.isEmpty) {
            return ""
        }

        val keys = extras.keySet().toTypedArray()
        val collator = Collator.getInstance(Locale.US)
        Arrays.sort(keys, collator)

        val path = StringBuilder()
        for (key in keys) {
            val dir = extras.getString(key)
            if (!dir.isNullOrEmpty()) {
                path.append(dir)
                path.append(":")
            }
        }

        return if (path.isNotEmpty()) path.substring(0, path.length - 1) else ""
    }

    override fun onStart() {
        super.onStart()

        tsIntent?.let {
            mTSConnection?.let { conn ->
                check(
                    bindService(
                        it,
                        conn,
                        BIND_AUTO_CREATE
                    )
                ) { "Failed to bind to TermService!" }
            }
        }
    }

    private fun populateViewFlipper() {
        if (mTermService != null) {
            mTermSessions = mTermService?.sessions

            if (mTermSessions?.isEmpty() == true) {
                try {
                    mTermSessions?.add(createTermSession())
                } catch (_: IOException) {
                    Toast.makeText(this, "Failed to start terminal session", Toast.LENGTH_LONG)
                        .show()
                    finish()
                    return
                }
            }

            mTermSessions?.addCallback(this)

            if (mTermSessions != null) {
                for (session in mTermSessions) {
                    val view: EmulatorView = createEmulatorView(session)
                    mViewFlipper?.addView(view)
                }
            }

            updatePrefs()

            if (onResumeSelectWindow >= 0) {
                mViewFlipper?.setDisplayedChild(onResumeSelectWindow)
                onResumeSelectWindow = -1
            }
            mViewFlipper?.onResume()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()

        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)

        if (mStopServiceOnFinish) {
            stopService(tsIntent)
        }
        mTermService = null
        mTSConnection = null
        if (mWakeLock?.isHeld == true) {
            mWakeLock?.release()
        }
        if (mWifiLock?.isHeld == true) {
            mWifiLock?.release()
        }
    }

    @Throws(IOException::class)
    private fun createTermSession(): TermSession {
        val settings: TermSettings = mSettings ?: throw IOException("No settings available")
        val session = createTermSession(this, settings, settings.initialCommand)
        session.setFinishCallback(mTermService)
        return session
    }

    private fun createEmulatorView(session: TermSession): TermView {
        val metrics = resources.displayMetrics
        val emulatorView = TermView(this, session, metrics)

        emulatorView.setExtGestureListener(EmulatorViewGestureListener(emulatorView))
        emulatorView.setOnKeyListener(mKeyListener)
        registerForContextMenu(emulatorView)

        return emulatorView
    }

    private val currentTermSession: TermSession?
        get() {
            val sessions = mTermSessions
            return if (sessions == null) {
                null
            } else {
                mViewFlipper?.displayedChild?.let { sessions[it] }
            }
        }

    private val currentEmulatorView: EmulatorView?
        get() = mViewFlipper?.currentView as EmulatorView?

    private fun updatePrefs() {
        mUseKeyboardShortcuts = mSettings?.useKeyboardShortcutsFlag == true

        val metrics = resources.displayMetrics

        mSettings?.let { mViewFlipper?.updatePrefs(it) }

        if (mViewFlipper != null) {
            for (v in mViewFlipper) {
                (v as EmulatorView).setDensity(metrics)
                mSettings?.let { (v as TermView).updatePrefs(it) }
            }
        }

        if (mTermSessions != null) {
            for (session in mTermSessions) {
                mSettings?.let { (session as GenericTermSession).updatePrefs(it) }
            }
        }

        run {
            val win = window
            if (mSettings?.showStatusBar() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    win.insetsController?.show(WindowInsets.Type.statusBars())
                } else {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    win.insetsController?.hide(WindowInsets.Type.statusBars())
                } else {
                    win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                }
            }
        }

        val orientation = mSettings?.screenOrientation
        var o = 0
        if (orientation == 0) {
            o = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else if (orientation == 1) {
            o = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else if (orientation == 2) {
            o = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            /* Shouldn't be happened. */
        }
        setRequestedOrientation(o)
    }

    public override fun onPause() {
        super.onPause()

        /* Explicitly close the input method
           Otherwise, the soft keyboard could cover up whatever activity takes
           our place */
        val token = mViewFlipper?.windowToken
        Thread(Runnable {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(token, 0)
        }).start()
    }

    override fun onStop() {
        mViewFlipper?.onPause()
        if (mTermSessions != null) {
            mTermSessions?.removeCallback(this)
        }

        mViewFlipper?.removeAllViews()

        mTSConnection?.let { unbindService(it) }

        super.onStop()
    }

    private fun checkHaveFullHwKeyboard(c: Configuration): Boolean {
        return (c.keyboard == Configuration.KEYBOARD_QWERTY) &&
                (c.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        mHaveFullHwKeyboard = checkHaveFullHwKeyboard(newConfig)

        val v = mViewFlipper?.currentView as EmulatorView?
        v?.updateSize(false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        menu.findItem(R.id.menu_new_window).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.findItem(R.id.menu_close_window).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.menu_preferences) {
            doPreferences()
        } else if (id == R.id.menu_new_window) {
            doCreateNewWindow()
        } else if (id == R.id.menu_close_window) {
            confirmCloseWindow()
        } else if (id == R.id.menu_window_list) {
            windowList()
        } else if (id == R.id.menu_reset) {
            doResetTerminal()
            val toast = Toast.makeText(this, R.string.reset_toast_notification, Toast.LENGTH_LONG)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        } else if (id == R.id.menu_send_email) {
            doEmailTranscript()
        } else if (id == R.id.menu_special_keys) {
            doDocumentKeys()
        } else if (id == R.id.menu_toggle_soft_keyboard) {
            doToggleSoftKeyboard()
        } else if (id == R.id.menu_toggle_wakelock) {
            doToggleWakeLock()
        } else if (id == R.id.menu_toggle_wifilock) {
            doToggleWifiLock()
        } else if (id == R.id.action_help) {
            val openHelp = Intent(
                Intent.ACTION_VIEW,
                getString(R.string.help_url).toUri()
            )
            startActivity(openHelp)
        }
        return super.onOptionsItemSelected(item)
    }

    private fun doCreateNewWindow() {
        if (mTermSessions == null) {
            Log.w(TermDebug.LOG_TAG, "Couldn't create new window because mTermSessions == null")
            return
        }

        try {
            val session = createTermSession()

            mTermSessions?.add(session)

            val view = createEmulatorView(session)
            mSettings?.let { view.updatePrefs(it) }

            mViewFlipper?.addView(view)
            mViewFlipper?.size?.let { mViewFlipper?.setDisplayedChild(it - 1) }
        } catch (_: IOException) {
            Toast.makeText(this, "Failed to create a session", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmCloseWindow() {
        val b = AlertDialog.Builder(this)
        b.setIcon(android.R.drawable.ic_dialog_alert)
        b.setMessage(R.string.confirm_window_close_message)
        val closeWindow = Runnable { this.doCloseWindow() }
        b.setPositiveButton(
            android.R.string.ok,
            DialogInterface.OnClickListener { dialog: DialogInterface?, id: Int ->
                dialog?.dismiss()
                mHandler.post(closeWindow)
            })
        b.setNegativeButton(android.R.string.cancel, null)
        b.show()
    }

    private fun doCloseWindow() {
        if (mTermSessions == null) {
            return
        }

        val view = this.currentEmulatorView
        if (view == null) {
            return
        }
        val session = mViewFlipper?.displayedChild?.let { mTermSessions?.removeAt(it) }
        view.onPause()
        session?.finish()
        mViewFlipper?.removeView(view)
        mTermSessions?.isEmpty()?.let {
            if (!it) {
                mViewFlipper?.showNext()
            }
        }
    }

    private fun windowList() {
        val adapter = WindowListAdapter(mTermSessions)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.window_list)
            .setAdapter(adapter) { _, which ->
                mViewFlipper?.setDisplayedChild(which)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    class CloseButton : AppCompatImageView {
        constructor(context: Context) : super(context)
        constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
        constructor(context: Context, attrs: AttributeSet, style: Int) : super(context, attrs, style)

        override fun setPressed(pressed: Boolean) {
            if (pressed && (parent as View).isPressed) {
                return
            }
            super.setPressed(pressed)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            // Don't repeat action if intent comes from history
            return
        }

        val action = intent.action
        if (TextUtils.isEmpty(action) || mPrivateAlias != intent.component) {
            return
        }

        // huge number simply opens new window
        // TODO: add a way to restrict max number of windows per caller (possibly via reusing BoundSession)
        when (action) {
            RemoteInterface.PRIVACT_OPEN_NEW_WINDOW -> onResumeSelectWindow =
                Int.Companion.MAX_VALUE

            RemoteInterface.PRIVACT_SWITCH_WINDOW -> {
                val target = intent.getIntExtra(RemoteInterface.PRIVEXTRA_TARGET_WINDOW, -1)
                if (target >= 0) {
                    onResumeSelectWindow = target
                }
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val wakeLockItem = menu.findItem(R.id.menu_toggle_wakelock)
        val wifiLockItem = menu.findItem(R.id.menu_toggle_wifilock)
        if (mWakeLock?.isHeld == true) {
            wakeLockItem.setTitle(R.string.disable_wakelock)
        } else {
            wakeLockItem.setTitle(R.string.enable_wakelock)
        }
        if (mWifiLock?.isHeld == true) {
            wifiLockItem.setTitle(R.string.disable_wifilock)
        } else {
            wifiLockItem.setTitle(R.string.enable_wifilock)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateContextMenu(
        menu: ContextMenu, v: View?,
        menuInfo: ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menu.setHeaderTitle(R.string.edit_text)
        menu.add(0, SELECT_TEXT_ID, 0, R.string.select_text)
        menu.add(0, COPY_ALL_ID, 0, R.string.copy_all)
        menu.add(0, PASTE_ID, 0, R.string.paste)
        menu.add(0, SEND_CONTROL_KEY_ID, 0, R.string.send_control_key)
        menu.add(0, SEND_FN_KEY_ID, 0, R.string.send_fn_key)
        if (!canPaste()) {
            menu[PASTE_ID].isEnabled = false
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            SELECT_TEXT_ID -> {
                this.currentEmulatorView?.toggleSelectingText()
                true
            }

            COPY_ALL_ID -> {
                doCopyAll()
                true
            }

            PASTE_ID -> {
                doPaste()
                true
            }

            SEND_CONTROL_KEY_ID -> {
                doSendControlKey()
                true
            }

            SEND_FN_KEY_ID -> {
                doSendFnKey()
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        /* The pre-Eclair default implementation of onKeyDown() would prevent
           our handling of the Back key in onKeyUp() from taking effect, so
           ignore it here */
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                when (mSettings?.backKeyAction) {
                    TermSettings.BACK_KEY_STOPS_SERVICE -> {
                        mStopServiceOnFinish = true
                        finish()
                        return true
                    }

                    TermSettings.BACK_KEY_CLOSES_ACTIVITY -> {
                        finish()
                        return true
                    }

                    TermSettings.BACK_KEY_CLOSES_WINDOW -> {
                        doCloseWindow()
                        return true
                    }

                    else -> return false
                }
            }

            KeyEvent.KEYCODE_MENU -> return super.onKeyUp(keyCode, event)

            else -> return super.onKeyUp(keyCode, event)
        }
    }

    // Called when the list of sessions changes
    override fun onUpdate() {
        val sessions = mTermSessions
        if (sessions == null) {
            return
        }

        if (sessions.isEmpty()) {
            mStopServiceOnFinish = true
            finish()
        } else mViewFlipper?.size?.let {
            if (sessions.size < it) {
                var i = 0
                while (i < it) {
                    val v = mViewFlipper?.getChildAt(i) as EmulatorView
                    if (!sessions.contains(v.termSession)) {
                        v.onPause()
                        mViewFlipper?.removeView(v)
                        --i
                    }
                    ++i
                }
            }
        }
    }

    private fun canPaste(): Boolean {
        val clip = ClipboardManagerCompat(applicationContext)
        return clip.hasText()
    }

    private fun doPreferences() {
        startActivity(Intent(this, TermPreferences::class.java))
    }

    private fun doResetTerminal() {
        val session = this.currentTermSession
        session?.reset()
    }

    private fun doEmailTranscript() {
        val session = this.currentTermSession
        if (session != null) {
            // Don't really want to supply an address, but
            // currently it's required, otherwise nobody
            // wants to handle the intent.
            val addr = "user@example.com"
            val intent =
                Intent(
                    Intent.ACTION_SENDTO, ("mailto:$addr").toUri()
                )

            var subject: String? = getString(R.string.email_transcript_subject)
            val title = session.title
            if (title != null) {
                subject = "$subject - $title"
            }
            intent.putExtra(Intent.EXTRA_SUBJECT, subject)
            intent.putExtra(
                Intent.EXTRA_TEXT,
                session.transcriptText?.trim { it <= ' ' })
            try {
                startActivity(
                    Intent.createChooser(
                        intent,
                        getString(R.string.email_transcript_chooser_title)
                    )
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    this,
                    R.string.email_transcript_no_email_activity_found,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun doCopyAll() {
        val clip = ClipboardManagerCompat(applicationContext)
        this.currentTermSession?.transcriptText?.let { clip.text = it.trim { it <= ' ' } }
    }

    private fun doPaste() {
        if (!canPaste()) {
            return
        }
        val clip = ClipboardManagerCompat(applicationContext)
        val paste = clip.text
        this.currentTermSession?.write(paste.toString())
    }

    private fun doSendControlKey() {
        this.currentEmulatorView?.sendControlKey()
    }

    private fun doSendFnKey() {
        this.currentEmulatorView?.sendFnKey()
    }

    private fun doDocumentKeys() {
        val dialog = AlertDialog.Builder(this)
        val r = getResources()
        dialog.setTitle(r.getString(R.string.control_key_dialog_title))
        mSettings?.controlKeyId?.let {
            dialog.setMessage(
                (formatMessage(
                    it, TermSettings.CONTROL_KEY_ID_NONE,
                    r, R.array.control_keys_short_names,
                    R.string.control_key_dialog_control_text,
                    R.string.control_key_dialog_control_disabled_text, "CTRLKEY"
                )
                        + "\n\n" +
                        mSettings?.fnKeyId?.let {
                            formatMessage(
                                it, TermSettings.FN_KEY_ID_NONE,
                                r, R.array.fn_keys_short_names,
                                R.string.control_key_dialog_fn_text,
                                R.string.control_key_dialog_fn_disabled_text, "FNKEY"
                            )
                        })
            )
        }
        dialog.show()
    }

    private fun formatMessage(
        keyId: Int, disabledKeyId: Int,
        r: Resources, arrayId: Int,
        enabledId: Int,
        disabledId: Int, regex: String
    ): String {
        if (keyId == disabledKeyId) {
            return r.getString(disabledId)
        }
        val keyNames = r.getStringArray(arrayId)
        val keyName = keyNames[keyId]
        val template = r.getString(enabledId)
        return template.replace(regex.toRegex(), keyName)
    }

    private fun doToggleSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun doToggleWakeLock() {
        if (mWakeLock?.isHeld == true) {
            mWakeLock?.release()
        } else {
            mWakeLock?.acquire()
        }
        invalidateOptionsMenu()
    }

    private fun doToggleWifiLock() {
        if (mWifiLock?.isHeld == true) {
            mWifiLock?.release()
        } else {
            mWifiLock?.acquire()
        }
        invalidateOptionsMenu()
    }

    private fun doUIToggle(x: Int, y: Int, width: Int, height: Int) {
        when (mActionBarMode) {
            TermSettings.ACTION_BAR_MODE_NONE -> if (mHaveFullHwKeyboard || y < height / 2) {
                openOptionsMenu()
                return
            } else {
                doToggleSoftKeyboard()
            }

            TermSettings.ACTION_BAR_MODE_ALWAYS_VISIBLE -> if (!mHaveFullHwKeyboard) {
                doToggleSoftKeyboard()
            }

            TermSettings.ACTION_BAR_MODE_HIDES -> if (mHaveFullHwKeyboard || y < height / 2) {
                return
            } else {
                doToggleSoftKeyboard()
            }
        }
        this.currentEmulatorView?.requestFocus()
    }

    /**
     *
     * Send a URL up to Android to be handled by a browser.
     * @param link The URL to be opened.
     */
    private fun execURL(link: String?) {
        val webLink = link?.toUri()
        val openLink = Intent(Intent.ACTION_VIEW, webLink)
        val pm = packageManager
        val handlers = pm.queryIntentActivities(openLink, 0)
        if (!handlers.isEmpty()) startActivity(openLink)
    }

    companion object {
        /**
         * The name of the ViewFlipper in the resources.
         */
        private val VIEW_FLIPPER = R.id.view_flipper

        private const val SELECT_TEXT_ID = 0
        private const val COPY_ALL_ID = 1
        private const val PASTE_ID = 2
        private const val SEND_CONTROL_KEY_ID = 3
        private const val SEND_FN_KEY_ID = 4

        private const val ACTION_PATH_BROADCAST = "jackpal.androidterm.broadcast.APPEND_TO_PATH"
        private const val ACTION_PATH_PREPEND_BROADCAST =
            "jackpal.androidterm.broadcast.PREPEND_TO_PATH"
        private const val PERMISSION_PATH_BROADCAST =
            "jackpal.androidterm.permission.APPEND_TO_PATH"
        private const val PERMISSION_PATH_PREPEND_BROADCAST =
            "jackpal.androidterm.permission.PREPEND_TO_PATH"

        @Throws(IOException::class)
        fun createTermSession(
            context: Context,
            settings: TermSettings,
            initialCommand: String?
        ): TermSession {
            val session: GenericTermSession = ShellTermSession(settings, initialCommand)
            // XXX We should really be able to fetch this from within TermSession
            session.setProcessExitMessage(context.getString(R.string.process_exit_message))

            return session
        }
    }
}
