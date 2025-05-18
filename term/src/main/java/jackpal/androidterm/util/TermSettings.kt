package jackpal.androidterm.util

import android.content.SharedPreferences
import android.content.res.Resources
import android.view.KeyEvent
import jackpal.androidterm.R

/**
 * Terminal emulator settings
 */
class TermSettings(res: Resources, prefs: SharedPreferences) {
    private var mPrefs: SharedPreferences? = null

    private var mStatusBar: Int = 0
    private var mActionBarMode: Int = 0
    private var mOrientation: Int = 0
    private var mCursorStyle: Int = 0
    private var mCursorBlink: Int = 0
    private var mFontSize: Int = 0
    private var mColorId: Int = 0
    private var mUTF8ByDefault: Boolean = false
    private var mBackKeyAction: Int = 0
    private var mControlKeyId: Int = 0
    private var mFnKeyId: Int = 0
    private var mUseCookedIME: Int = 0
    private var mShell: String = ""
    private var mFailsafeShell: String = ""
    private var mInitialCommand: String = ""
    private var mTermType: String = ""
    private var mCloseOnExit: Boolean = false
    private var mVerifyPath: Boolean = false
    private var mDoPathExtensions: Boolean = false
    private var mAllowPathPrepend: Boolean = false
    private var mHomePath: String = ""
    private var mPrependPath: String? = null
    private var mAppendPath: String? = null
    private var mAltSendsEsc: Boolean = false
    private var mMouseTracking: Boolean = false
    private var mUseKeyboardShortcuts: Boolean = false

    companion object {
        private const val STATUSBAR_KEY = "statusbar"
        private const val ACTIONBAR_KEY = "actionbar"
        private const val ORIENTATION_KEY = "orientation"
        private const val FONTSIZE_KEY = "fontsize"
        private const val COLOR_KEY = "color"
        private const val UTF8_KEY = "utf8_by_default"
        private const val BACKACTION_KEY = "backaction"
        private const val CONTROLKEY_KEY = "controlkey"
        private const val FNKEY_KEY = "fnkey"
        private const val IME_KEY = "ime"
        private const val SHELL_KEY = "shell"
        private const val INITIALCOMMAND_KEY = "initialcommand"
        private const val TERMTYPE_KEY = "termtype"
        private const val CLOSEONEXIT_KEY = "close_window_on_process_exit"
        private const val VERIFYPATH_KEY = "verify_path"
        private const val PATHEXTENSIONS_KEY = "do_path_extensions"
        private const val PATHPREPEND_KEY = "allow_prepend_path"
        private const val HOMEPATH_KEY = "home_path"
        private const val ALT_SENDS_ESC = "alt_sends_esc"
        private const val MOUSE_TRACKING = "mouse_tracking"
        private const val USE_KEYBOARD_SHORTCUTS = "use_keyboard_shortcuts"

        const val WHITE = 0xffffffff.toInt()
        const val BLACK = 0xff000000.toInt()
        const val BLUE = 0xff344ebd.toInt()
        const val GREEN = 0xff00ff00.toInt()
        const val AMBER = 0xffffb651.toInt()
        const val RED = 0xffff0113.toInt()
        const val HOLO_BLUE = 0xff33b5e5.toInt()
        const val SOLARIZED_FG = 0xff657b83.toInt()
        const val SOLARIZED_BG = 0xfffdf6e3.toInt()
        const val SOLARIZED_DARK_FG = 0xff839496.toInt()
        const val SOLARIZED_DARK_BG = 0xff002b36.toInt()
        const val LINUX_CONSOLE_WHITE = 0xffaaaaaa.toInt()

        // foreground color, background color
        val COLOR_SCHEMES = arrayOf(
            intArrayOf(BLACK, WHITE),
            intArrayOf(WHITE, BLACK),
            intArrayOf(WHITE, BLUE),
            intArrayOf(GREEN, BLACK),
            intArrayOf(AMBER, BLACK),
            intArrayOf(RED, BLACK),
            intArrayOf(HOLO_BLUE, BLACK),
            intArrayOf(SOLARIZED_FG, SOLARIZED_BG),
            intArrayOf(SOLARIZED_DARK_FG, SOLARIZED_DARK_BG),
            intArrayOf(LINUX_CONSOLE_WHITE, BLACK)
        )

        const val ACTION_BAR_MODE_NONE = 0
        const val ACTION_BAR_MODE_ALWAYS_VISIBLE = 1
        const val ACTION_BAR_MODE_HIDES = 2
        private const val ACTION_BAR_MODE_MAX = 2

        const val ORIENTATION_UNSPECIFIED = 0
        const val ORIENTATION_LANDSCAPE = 1
        const val ORIENTATION_PORTRAIT = 2

        /** An integer not in the range of real key codes. */
        const val KEYCODE_NONE = -1

        const val CONTROL_KEY_ID_NONE = 7
        val CONTROL_KEY_SCHEMES = intArrayOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_AT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_CAMERA,
            KEYCODE_NONE
        )

        const val FN_KEY_ID_NONE = 7
        val FN_KEY_SCHEMES = intArrayOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_AT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_CAMERA,
            KEYCODE_NONE
        )

        const val BACK_KEY_STOPS_SERVICE = 0
        const val BACK_KEY_CLOSES_WINDOW = 1
        const val BACK_KEY_CLOSES_ACTIVITY = 2
        const val BACK_KEY_SENDS_ESC = 3
        const val BACK_KEY_SENDS_TAB = 4
        private const val BACK_KEY_MAX = 4
    }

    init {
        readDefaultPrefs(res)
        readPrefs(prefs)
    }

    private fun readDefaultPrefs(res: Resources) {
        mStatusBar = res.getString(R.string.pref_statusbar_default).toInt()
        mActionBarMode = res.getInteger(R.integer.pref_actionbar_default)
        mOrientation = res.getInteger(R.integer.pref_orientation_default)
        mCursorStyle = res.getString(R.string.pref_cursorstyle_default).toInt()
        mCursorBlink = res.getString(R.string.pref_cursorblink_default).toInt()
        mFontSize = res.getString(R.string.pref_fontsize_default).toInt()
        mColorId = res.getString(R.string.pref_color_default).toInt()
        mUTF8ByDefault = res.getBoolean(R.bool.pref_utf8_by_default_default)
        mBackKeyAction = res.getString(R.string.pref_backaction_default).toInt()
        mControlKeyId = res.getString(R.string.pref_controlkey_default).toInt()
        mFnKeyId = res.getString(R.string.pref_fnkey_default).toInt()
        mUseCookedIME = res.getString(R.string.pref_ime_default).toInt()
        mFailsafeShell = res.getString(R.string.pref_shell_default)
        mShell = mFailsafeShell
        mInitialCommand = res.getString(R.string.pref_initialcommand_default)
        mTermType = res.getString(R.string.pref_termtype_default)
        mCloseOnExit = res.getBoolean(R.bool.pref_close_window_on_process_exit_default)
        mVerifyPath = res.getBoolean(R.bool.pref_verify_path_default)
        mDoPathExtensions = res.getBoolean(R.bool.pref_do_path_extensions_default)
        mAllowPathPrepend = res.getBoolean(R.bool.pref_allow_prepend_path_default)
        mAltSendsEsc = res.getBoolean(R.bool.pref_alt_sends_esc_default)
        mMouseTracking = res.getBoolean(R.bool.pref_mouse_tracking_default)
        mUseKeyboardShortcuts = res.getBoolean(R.bool.pref_use_keyboard_shortcuts_default)
    }

    fun readPrefs(prefs: SharedPreferences) {
        mPrefs = prefs
        mStatusBar = readIntPref(STATUSBAR_KEY, mStatusBar, 1)
        mActionBarMode = readIntPref(ACTIONBAR_KEY, mActionBarMode, ACTION_BAR_MODE_MAX)
        mOrientation = readIntPref(ORIENTATION_KEY, mOrientation, 2)
        // mCursorStyle = readIntPref(CURSORSTYLE_KEY, mCursorStyle, 2)
        // mCursorBlink = readIntPref(CURSORBLINK_KEY, mCursorBlink, 1)
        mFontSize = readIntPref(FONTSIZE_KEY, mFontSize, 288)
        mColorId = readIntPref(COLOR_KEY, mColorId, COLOR_SCHEMES.size - 1)
        mUTF8ByDefault = readBooleanPref(UTF8_KEY, mUTF8ByDefault)
        mBackKeyAction = readIntPref(BACKACTION_KEY, mBackKeyAction, BACK_KEY_MAX)
        mControlKeyId = readIntPref(CONTROLKEY_KEY, mControlKeyId, CONTROL_KEY_SCHEMES.size - 1)
        mFnKeyId = readIntPref(FNKEY_KEY, mFnKeyId, FN_KEY_SCHEMES.size - 1)
        mUseCookedIME = readIntPref(IME_KEY, mUseCookedIME, 1)
        mShell = readStringPref(SHELL_KEY, mShell)
        mInitialCommand = readStringPref(INITIALCOMMAND_KEY, mInitialCommand)
        mTermType = readStringPref(TERMTYPE_KEY, mTermType)
        mCloseOnExit = readBooleanPref(CLOSEONEXIT_KEY, mCloseOnExit)
        mVerifyPath = readBooleanPref(VERIFYPATH_KEY, mVerifyPath)
        mDoPathExtensions = readBooleanPref(PATHEXTENSIONS_KEY, mDoPathExtensions)
        mAllowPathPrepend = readBooleanPref(PATHPREPEND_KEY, mAllowPathPrepend)
        mHomePath = readStringPref(HOMEPATH_KEY, mHomePath)
        mAltSendsEsc = readBooleanPref(ALT_SENDS_ESC, mAltSendsEsc)
        mMouseTracking = readBooleanPref(MOUSE_TRACKING, mMouseTracking)
        mUseKeyboardShortcuts = readBooleanPref(USE_KEYBOARD_SHORTCUTS, mUseKeyboardShortcuts)
        mPrefs = null // we leak a Context if we hold on to this
    }

    private fun readIntPref(key: String, defaultValue: Int, maxValue: Int): Int {
        val valStr = mPrefs?.getString(key, defaultValue.toString())
        val value = valStr?.toIntOrNull() ?: defaultValue
        return value.coerceIn(0, maxValue)
    }

    private fun readStringPref(key: String, defaultValue: String): String {
        return mPrefs?.getString(key, defaultValue) ?: defaultValue
    }

    private fun readBooleanPref(key: String, defaultValue: Boolean): Boolean {
        return mPrefs?.getBoolean(key, defaultValue) ?: defaultValue
    }

    fun showStatusBar(): Boolean = mStatusBar != 0
    fun actionBarMode(): Int = mActionBarMode
    val screenOrientation: Int get() = mOrientation
    val cursorStyle: Int get() = mCursorStyle
    val cursorBlink: Int get() = mCursorBlink
    val fontSize: Int get() = mFontSize
    val colorScheme: IntArray get() = COLOR_SCHEMES[mColorId]
    fun defaultToUTF8Mode(): Boolean = mUTF8ByDefault
    val backKeyAction: Int get() = mBackKeyAction
    fun backKeySendsCharacter(): Boolean = mBackKeyAction >= BACK_KEY_SENDS_ESC
    val altSendsEscFlag: Boolean get() = mAltSendsEsc
    val mouseTrackingFlag: Boolean get() = mMouseTracking
    val useKeyboardShortcutsFlag: Boolean get() = mUseKeyboardShortcuts
    val backKeyCharacter: Int
        get() = when (mBackKeyAction) {
            BACK_KEY_SENDS_ESC -> 27
            BACK_KEY_SENDS_TAB -> 9
            else -> 0
        }
    val controlKeyId: Int get() = mControlKeyId
    val fnKeyId: Int get() = mFnKeyId
    val controlKeyCode: Int get() = CONTROL_KEY_SCHEMES[mControlKeyId]
    val fnKeyCode: Int get() = FN_KEY_SCHEMES[mFnKeyId]
    fun useCookedIME(): Boolean = mUseCookedIME != 0
    val shell: String get() = mShell
    val failsafeShell: String get() = mFailsafeShell
    val initialCommand: String get() = mInitialCommand
    val termType: String get() = mTermType
    fun closeWindowOnProcessExit(): Boolean = mCloseOnExit
    fun verifyPath(): Boolean = mVerifyPath
    fun doPathExtensions(): Boolean = mDoPathExtensions
    fun allowPathPrepend(): Boolean = mAllowPathPrepend
    var prependPath: String?
        get() = mPrependPath
        set(value) { mPrependPath = value }
    var appendPath: String?
        get() = mAppendPath
        set(value) { mAppendPath = value }
    var homePath: String
        get() = mHomePath
        set(value) { mHomePath = value }
}

