package jackpal.androidterm.emulatorview

import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_BREAK
import android.view.KeyEvent.KEYCODE_CAPS_LOCK
import android.view.KeyEvent.KEYCODE_CTRL_LEFT
import android.view.KeyEvent.KEYCODE_CTRL_RIGHT
import android.view.KeyEvent.KEYCODE_DEL
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_ESCAPE
import android.view.KeyEvent.KEYCODE_F1
import android.view.KeyEvent.KEYCODE_F10
import android.view.KeyEvent.KEYCODE_F11
import android.view.KeyEvent.KEYCODE_F12
import android.view.KeyEvent.KEYCODE_F2
import android.view.KeyEvent.KEYCODE_F3
import android.view.KeyEvent.KEYCODE_F4
import android.view.KeyEvent.KEYCODE_F5
import android.view.KeyEvent.KEYCODE_F6
import android.view.KeyEvent.KEYCODE_F7
import android.view.KeyEvent.KEYCODE_F8
import android.view.KeyEvent.KEYCODE_F9
import android.view.KeyEvent.KEYCODE_FORWARD_DEL
import android.view.KeyEvent.KEYCODE_FUNCTION
import android.view.KeyEvent.KEYCODE_INSERT
import android.view.KeyEvent.KEYCODE_MOVE_END
import android.view.KeyEvent.KEYCODE_MOVE_HOME
import android.view.KeyEvent.KEYCODE_NUMPAD_0
import android.view.KeyEvent.KEYCODE_NUMPAD_1
import android.view.KeyEvent.KEYCODE_NUMPAD_2
import android.view.KeyEvent.KEYCODE_NUMPAD_3
import android.view.KeyEvent.KEYCODE_NUMPAD_4
import android.view.KeyEvent.KEYCODE_NUMPAD_5
import android.view.KeyEvent.KEYCODE_NUMPAD_6
import android.view.KeyEvent.KEYCODE_NUMPAD_7
import android.view.KeyEvent.KEYCODE_NUMPAD_8
import android.view.KeyEvent.KEYCODE_NUMPAD_9
import android.view.KeyEvent.KEYCODE_NUMPAD_ADD
import android.view.KeyEvent.KEYCODE_NUMPAD_COMMA
import android.view.KeyEvent.KEYCODE_NUMPAD_DIVIDE
import android.view.KeyEvent.KEYCODE_NUMPAD_DOT
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_EQUALS
import android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY
import android.view.KeyEvent.KEYCODE_NUMPAD_SUBTRACT
import android.view.KeyEvent.KEYCODE_NUM_LOCK
import android.view.KeyEvent.KEYCODE_PAGE_DOWN
import android.view.KeyEvent.KEYCODE_PAGE_UP
import android.view.KeyEvent.KEYCODE_SPACE
import android.view.KeyEvent.KEYCODE_SYSRQ
import android.view.KeyEvent.KEYCODE_TAB
import android.view.KeyEvent.META_ALT_ON
import android.view.KeyEvent.META_CTRL_MASK
import android.view.KeyEvent.META_CTRL_ON
import android.view.KeyEvent.META_SHIFT_ON

import jackpal.androidterm.emulatorview.EmulatorDebug.bytesToString
import java.io.IOException

/**
 * An ASCII key listener. Supports control characters and escape. Keeps track of
 * the current state of the alt, shift, fn, and control keys.
 *
 */
class TermKeyListener(private val mTermSession: TermSession) {
    private val mKeyCodes = arrayOfNulls<String>(256)
    private val mAppKeyCodes = arrayOfNulls<String>(256)

    private fun initKeyCodes() {
        mKeyMap = HashMap<Int?, String?>()
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_DPAD_LEFT, "\u001b[1;2D")
        mKeyMap!!.put(KEYMOD_ALT or KEYCODE_DPAD_LEFT, "\u001b[1;3D")
        mKeyMap!!.put(KEYMOD_ALT or KEYMOD_SHIFT or KEYCODE_DPAD_LEFT, "\u001b[1;4D")
        mKeyMap!!.put(KEYMOD_CTRL or KEYCODE_DPAD_LEFT, "\u001b[1;5D")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_SHIFT or KEYCODE_DPAD_LEFT, "\u001b[1;6D")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_ALT or KEYCODE_DPAD_LEFT, "\u001b[1;7D")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_ALT or KEYMOD_SHIFT or KEYCODE_DPAD_LEFT, "\u001b[1;8D")

        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_DPAD_RIGHT, "\u001b[1;2C")
        mKeyMap!!.put(KEYMOD_ALT or KEYCODE_DPAD_RIGHT, "\u001b[1;3C")
        mKeyMap!!.put(KEYMOD_ALT or KEYMOD_SHIFT or KEYCODE_DPAD_RIGHT, "\u001b[1;4C")
        mKeyMap!!.put(KEYMOD_CTRL or KEYCODE_DPAD_RIGHT, "\u001b[1;5C")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_SHIFT or KEYCODE_DPAD_RIGHT, "\u001b[1;6C")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_ALT or KEYCODE_DPAD_RIGHT, "\u001b[1;7C")
        mKeyMap!!.put(
            KEYMOD_CTRL or KEYMOD_ALT or KEYMOD_SHIFT or KEYCODE_DPAD_RIGHT,
            "\u001b[1;8C"
        )

        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_DPAD_UP, "\u001b[1;2A")
        mKeyMap!!.put(KEYMOD_ALT or KEYCODE_DPAD_UP, "\u001b[1;3A")
        mKeyMap!!.put(KEYMOD_ALT or KEYMOD_SHIFT or KEYCODE_DPAD_UP, "\u001b[1;4A")
        mKeyMap!!.put(KEYMOD_CTRL or KEYCODE_DPAD_UP, "\u001b[1;5A")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_SHIFT or KEYCODE_DPAD_UP, "\u001b[1;6A")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_ALT or KEYCODE_DPAD_UP, "\u001b[1;7A")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_ALT or KEYMOD_SHIFT or KEYCODE_DPAD_UP, "\u001b[1;8A")

        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_DPAD_DOWN, "\u001b[1;2B")
        mKeyMap!!.put(KEYMOD_ALT or KEYCODE_DPAD_DOWN, "\u001b[1;3B")
        mKeyMap!!.put(KEYMOD_ALT or KEYMOD_SHIFT or KEYCODE_DPAD_DOWN, "\u001b[1;4B")
        mKeyMap!!.put(KEYMOD_CTRL or KEYCODE_DPAD_DOWN, "\u001b[1;5B")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_SHIFT or KEYCODE_DPAD_DOWN, "\u001b[1;6B")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_ALT or KEYCODE_DPAD_DOWN, "\u001b[1;7B")
        mKeyMap!!.put(KEYMOD_CTRL or KEYMOD_ALT or KEYMOD_SHIFT or KEYCODE_DPAD_DOWN, "\u001b[1;8B")

        //^[[3~
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_FORWARD_DEL, "\u001b[3;2~")
        mKeyMap!!.put(KEYMOD_ALT or KEYCODE_FORWARD_DEL, "\u001b[3;3~")
        mKeyMap!!.put(KEYMOD_CTRL or KEYCODE_FORWARD_DEL, "\u001b[3;5~")

        //^[[2~
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_INSERT, "\u001b[2;2~")
        mKeyMap!!.put(KEYMOD_ALT or KEYCODE_INSERT, "\u001b[2;3~")
        mKeyMap!!.put(KEYMOD_CTRL or KEYCODE_INSERT, "\u001b[2;5~")

        mKeyMap!!.put(KEYMOD_CTRL or KEYCODE_MOVE_HOME, "\u001b[1;5H")
        mKeyMap!!.put(KEYMOD_CTRL or KEYCODE_MOVE_END, "\u001b[1;5F")

        mKeyMap!!.put(KEYMOD_ALT or KEYCODE_ENTER, "\u001b\r")
        mKeyMap!!.put(KEYMOD_CTRL or KEYCODE_ENTER, "\n")
        // Duh, so special...
        mKeyMap!!.put(KEYMOD_CTRL or KEYCODE_SPACE, "\u0000")

        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_F1, "\u001b[1;2P")
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_F2, "\u001b[1;2Q")
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_F3, "\u001b[1;2R")
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_F4, "\u001b[1;2S")
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_F5, "\u001b[15;2~")
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_F6, "\u001b[17;2~")
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_F7, "\u001b[18;2~")
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_F8, "\u001b[19;2~")
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_F9, "\u001b[20;2~")
        mKeyMap!!.put(KEYMOD_SHIFT or KEYCODE_F10, "\u001b[21;2~")

        mKeyCodes[KEYCODE_DPAD_CENTER] = "\u000d"
        mKeyCodes[KEYCODE_DPAD_UP] = "\u001b[A"
        mKeyCodes[KEYCODE_DPAD_DOWN] = "\u001b[B"
        mKeyCodes[KEYCODE_DPAD_RIGHT] = "\u001b[C"
        mKeyCodes[KEYCODE_DPAD_LEFT] = "\u001b[D"
        setFnKeys("vt100")
        mKeyCodes[KEYCODE_SYSRQ] = "\u001b[32~" // Sys Request / Print
        // Is this Scroll lock? mKeyCodes[Cancel] = "\033[33~";
        mKeyCodes[KEYCODE_BREAK] = "\u001b[34~" // Pause/Break

        mKeyCodes[KEYCODE_TAB] = "\u0009"
        mKeyCodes[KEYCODE_ENTER] = "\u000d"
        mKeyCodes[KEYCODE_ESCAPE] = "\u001b"

        mKeyCodes[KEYCODE_INSERT] = "\u001b[2~"
        mKeyCodes[KEYCODE_FORWARD_DEL] = "\u001b[3~"
        // Home/End keys are set by setFnKeys()
        mKeyCodes[KEYCODE_PAGE_UP] = "\u001b[5~"
        mKeyCodes[KEYCODE_PAGE_DOWN] = "\u001b[6~"
        mKeyCodes[KEYCODE_DEL] = "\u007f"
        mKeyCodes[KEYCODE_NUM_LOCK] = "\u001bOP"
        mKeyCodes[KEYCODE_NUMPAD_DIVIDE] = "/"
        mKeyCodes[KEYCODE_NUMPAD_MULTIPLY] = "*"
        mKeyCodes[KEYCODE_NUMPAD_SUBTRACT] = "-"
        mKeyCodes[KEYCODE_NUMPAD_ADD] = "+"
        mKeyCodes[KEYCODE_NUMPAD_ENTER] = "\u000d"
        mKeyCodes[KEYCODE_NUMPAD_EQUALS] = "="
        mKeyCodes[KEYCODE_NUMPAD_COMMA] = ","
        /*
        mKeyCodes[KEYCODE_NUMPAD_DOT] = ".";
        mKeyCodes[KEYCODE_NUMPAD_0] = "0";
        mKeyCodes[KEYCODE_NUMPAD_1] = "1";
        mKeyCodes[KEYCODE_NUMPAD_2] = "2";
        mKeyCodes[KEYCODE_NUMPAD_3] = "3";
        mKeyCodes[KEYCODE_NUMPAD_4] = "4";
        mKeyCodes[KEYCODE_NUMPAD_5] = "5";
        mKeyCodes[KEYCODE_NUMPAD_6] = "6";
        mKeyCodes[KEYCODE_NUMPAD_7] = "7";
        mKeyCodes[KEYCODE_NUMPAD_8] = "8";
        mKeyCodes[KEYCODE_NUMPAD_9] = "9";
*/
        // Keypad is used for cursor/func keys
        mKeyCodes[KEYCODE_NUMPAD_DOT] = mKeyCodes[KEYCODE_FORWARD_DEL]
        mKeyCodes[KEYCODE_NUMPAD_0] = mKeyCodes[KEYCODE_INSERT]
        mKeyCodes[KEYCODE_NUMPAD_1] = mKeyCodes[KEYCODE_MOVE_END]
        mKeyCodes[KEYCODE_NUMPAD_2] = mKeyCodes[KEYCODE_DPAD_DOWN]
        mKeyCodes[KEYCODE_NUMPAD_3] = mKeyCodes[KEYCODE_PAGE_DOWN]
        mKeyCodes[KEYCODE_NUMPAD_4] = mKeyCodes[KEYCODE_DPAD_LEFT]
        mKeyCodes[KEYCODE_NUMPAD_5] = "5"
        mKeyCodes[KEYCODE_NUMPAD_6] = mKeyCodes[KEYCODE_DPAD_RIGHT]
        mKeyCodes[KEYCODE_NUMPAD_7] = mKeyCodes[KEYCODE_MOVE_HOME]
        mKeyCodes[KEYCODE_NUMPAD_8] = mKeyCodes[KEYCODE_DPAD_UP]
        mKeyCodes[KEYCODE_NUMPAD_9] = mKeyCodes[KEYCODE_PAGE_UP]


        //        mAppKeyCodes[KEYCODE_DPAD_UP] = "\033OA";
//        mAppKeyCodes[KEYCODE_DPAD_DOWN] = "\033OB";
//        mAppKeyCodes[KEYCODE_DPAD_RIGHT] = "\033OC";
//        mAppKeyCodes[KEYCODE_DPAD_LEFT] = "\033OD";
        mAppKeyCodes[KEYCODE_NUMPAD_DIVIDE] = "\u001bOo"
        mAppKeyCodes[KEYCODE_NUMPAD_MULTIPLY] = "\u001bOj"
        mAppKeyCodes[KEYCODE_NUMPAD_SUBTRACT] = "\u001bOm"
        mAppKeyCodes[KEYCODE_NUMPAD_ADD] = "\u001bOk"
        mAppKeyCodes[KEYCODE_NUMPAD_ENTER] = "\u001bOM"
        mAppKeyCodes[KEYCODE_NUMPAD_EQUALS] = "\u001bOX"
        mAppKeyCodes[KEYCODE_NUMPAD_DOT] = "\u001bOn"
        mAppKeyCodes[KEYCODE_NUMPAD_COMMA] = "\u001bOl"
        mAppKeyCodes[KEYCODE_NUMPAD_0] = "\u001bOp"
        mAppKeyCodes[KEYCODE_NUMPAD_1] = "\u001bOq"
        mAppKeyCodes[KEYCODE_NUMPAD_2] = "\u001bOr"
        mAppKeyCodes[KEYCODE_NUMPAD_3] = "\u001bOs"
        mAppKeyCodes[KEYCODE_NUMPAD_4] = "\u001bOt"
        mAppKeyCodes[KEYCODE_NUMPAD_5] = "\u001bOu"
        mAppKeyCodes[KEYCODE_NUMPAD_6] = "\u001bOv"
        mAppKeyCodes[KEYCODE_NUMPAD_7] = "\u001bOw"
        mAppKeyCodes[KEYCODE_NUMPAD_8] = "\u001bOx"
        mAppKeyCodes[KEYCODE_NUMPAD_9] = "\u001bOy"
    }

    fun setCursorKeysApplicationMode(`val`: Boolean) {
        if (LOG_MISC) {
            Log.d(EmulatorDebug.LOG_TAG, "CursorKeysApplicationMode=$`val`")
        }
        if (`val`) {
            mKeyCodes[KEYCODE_DPAD_UP] = "\u001bOA"
            mKeyCodes[KEYCODE_NUMPAD_8] = mKeyCodes[KEYCODE_DPAD_UP]
            mKeyCodes[KEYCODE_DPAD_DOWN] = "\u001bOB"
            mKeyCodes[KEYCODE_NUMPAD_2] = mKeyCodes[KEYCODE_DPAD_DOWN]
            mKeyCodes[KEYCODE_DPAD_RIGHT] = "\u001bOC"
            mKeyCodes[KEYCODE_NUMPAD_6] = mKeyCodes[KEYCODE_DPAD_RIGHT]
            mKeyCodes[KEYCODE_DPAD_LEFT] = "\u001bOD"
            mKeyCodes[KEYCODE_NUMPAD_4] = mKeyCodes[KEYCODE_DPAD_LEFT]
        } else {
            mKeyCodes[KEYCODE_DPAD_UP] = "\u001b[A"
            mKeyCodes[KEYCODE_NUMPAD_8] = mKeyCodes[KEYCODE_DPAD_UP]
            mKeyCodes[KEYCODE_DPAD_DOWN] = "\u001b[B"
            mKeyCodes[KEYCODE_NUMPAD_2] = mKeyCodes[KEYCODE_DPAD_DOWN]
            mKeyCodes[KEYCODE_DPAD_RIGHT] = "\u001b[C"
            mKeyCodes[KEYCODE_NUMPAD_6] = mKeyCodes[KEYCODE_DPAD_RIGHT]
            mKeyCodes[KEYCODE_DPAD_LEFT] = "\u001b[D"
            mKeyCodes[KEYCODE_NUMPAD_4] = mKeyCodes[KEYCODE_DPAD_LEFT]
        }
    }

    /**
     * The state engine for a modifier key. Can be pressed, released, locked,
     * and so on.
     *
     */
    private class ModifierKey {
        private var mState: Int

        /**
         * Construct a modifier key. UNPRESSED by default.
         *
         */
        init {
            mState = UNPRESSED
        }

        fun onPress() {
            when (mState) {
                PRESSED -> {}
                RELEASED -> mState = LOCKED
                USED -> {}
                LOCKED -> mState = UNPRESSED
                else -> mState = PRESSED
            }
        }

        fun onRelease() {
            when (mState) {
                USED -> mState = UNPRESSED
                PRESSED -> mState = RELEASED
                else -> {}
            }
        }

        fun adjustAfterKeypress() {
            when (mState) {
                PRESSED -> mState = USED
                RELEASED -> mState = UNPRESSED
                else -> {}
            }
        }

        val isActive: Boolean
            get() = mState != UNPRESSED

        val uIMode: Int
            get() = when (mState) {
                PRESSED, RELEASED, USED -> TextRenderer.MODE_ON
                LOCKED -> TextRenderer.MODE_LOCKED
                else -> TextRenderer.MODE_OFF
            }

        companion object {
            private const val UNPRESSED = 0

            private const val PRESSED = 1

            private const val RELEASED = 2

            private const val USED = 3

            private const val LOCKED = 4
        }
    }

    private val mAltKey = ModifierKey()

    private val mCapKey = ModifierKey()

    private val mControlKey = ModifierKey()

    private val mFnKey = ModifierKey()

    var cursorMode: Int = 0
        private set

    private var mHardwareControlKey = false

    private var mBackKeyCode = 0
    var altSendsEsc: Boolean = false

    var combiningAccent: Int = 0
        private set

    /**
     * Construct a term key listener.
     *
     */
    init {
        initKeyCodes()
        updateCursorMode()
    }

    fun setBackKeyCharacter(code: Int) {
        mBackKeyCode = code
    }

    fun handleHardwareControlKey(down: Boolean) {
        mHardwareControlKey = down
    }

    fun onPause() {
        // Ensure we don't have any left-over modifier state when switching
        // views.
        mHardwareControlKey = false
    }

    fun onResume() {
        // Nothing special.
    }

    fun handleControlKey(down: Boolean) {
        if (down) {
            mControlKey.onPress()
        } else {
            mControlKey.onRelease()
        }
        updateCursorMode()
    }

    fun handleFnKey(down: Boolean) {
        if (down) {
            mFnKey.onPress()
        } else {
            mFnKey.onRelease()
        }
        updateCursorMode()
    }

    fun setTermType(termType: String?) {
        setFnKeys(termType)
    }

    private fun setFnKeys(termType: String?) {
        // These key assignments taken from the debian squeeze terminfo database.
        if (termType == "xterm") {
            mKeyCodes[KEYCODE_MOVE_HOME] = "\u001bOH"
            mKeyCodes[KEYCODE_NUMPAD_7] = mKeyCodes[KEYCODE_MOVE_HOME]
            mKeyCodes[KEYCODE_MOVE_END] = "\u001bOF"
            mKeyCodes[KEYCODE_NUMPAD_1] = mKeyCodes[KEYCODE_MOVE_END]
        } else {
            mKeyCodes[KEYCODE_MOVE_HOME] = "\u001b[1~"
            mKeyCodes[KEYCODE_NUMPAD_7] = mKeyCodes[KEYCODE_MOVE_HOME]
            mKeyCodes[KEYCODE_MOVE_END] = "\u001b[4~"
            mKeyCodes[KEYCODE_NUMPAD_1] = mKeyCodes[KEYCODE_MOVE_END]
        }
        if (termType == "vt100") {
            mKeyCodes[KEYCODE_F1] = "\u001bOP" // VT100 PF1
            mKeyCodes[KEYCODE_F2] = "\u001bOQ" // VT100 PF2
            mKeyCodes[KEYCODE_F3] = "\u001bOR" // VT100 PF3
            mKeyCodes[KEYCODE_F4] = "\u001bOS" // VT100 PF4
            // the following keys are in the database, but aren't on a real vt100.
            mKeyCodes[KEYCODE_F5] = "\u001bOt"
            mKeyCodes[KEYCODE_F6] = "\u001bOu"
            mKeyCodes[KEYCODE_F7] = "\u001bOv"
            mKeyCodes[KEYCODE_F8] = "\u001bOl"
            mKeyCodes[KEYCODE_F9] = "\u001bOw"
            mKeyCodes[KEYCODE_F10] = "\u001bOx"
            // The following keys are not in database.
            mKeyCodes[KEYCODE_F11] = "\u001b[23~"
            mKeyCodes[KEYCODE_F12] = "\u001b[24~"
        } else if (termType?.startsWith("linux") == true) {
            mKeyCodes[KEYCODE_F1] = "\u001b[[A"
            mKeyCodes[KEYCODE_F2] = "\u001b[[B"
            mKeyCodes[KEYCODE_F3] = "\u001b[[C"
            mKeyCodes[KEYCODE_F4] = "\u001b[[D"
            mKeyCodes[KEYCODE_F5] = "\u001b[[E"
            mKeyCodes[KEYCODE_F6] = "\u001b[17~"
            mKeyCodes[KEYCODE_F7] = "\u001b[18~"
            mKeyCodes[KEYCODE_F8] = "\u001b[19~"
            mKeyCodes[KEYCODE_F9] = "\u001b[20~"
            mKeyCodes[KEYCODE_F10] = "\u001b[21~"
            mKeyCodes[KEYCODE_F11] = "\u001b[23~"
            mKeyCodes[KEYCODE_F12] = "\u001b[24~"
        } else {
            // default
            // screen, screen-256colors, xterm, anything new
            mKeyCodes[KEYCODE_F1] = "\u001bOP" // VT100 PF1
            mKeyCodes[KEYCODE_F2] = "\u001bOQ" // VT100 PF2
            mKeyCodes[KEYCODE_F3] = "\u001bOR" // VT100 PF3
            mKeyCodes[KEYCODE_F4] = "\u001bOS" // VT100 PF4
            mKeyCodes[KEYCODE_F5] = "\u001b[15~"
            mKeyCodes[KEYCODE_F6] = "\u001b[17~"
            mKeyCodes[KEYCODE_F7] = "\u001b[18~"
            mKeyCodes[KEYCODE_F8] = "\u001b[19~"
            mKeyCodes[KEYCODE_F9] = "\u001b[20~"
            mKeyCodes[KEYCODE_F10] = "\u001b[21~"
            mKeyCodes[KEYCODE_F11] = "\u001b[23~"
            mKeyCodes[KEYCODE_F12] = "\u001b[24~"
        }
    }

    fun mapControlChar(ch: Int): Int {
        return mapControlChar(mHardwareControlKey || mControlKey.isActive, mFnKey.isActive, ch)
    }

    fun mapControlChar(control: Boolean, fn: Boolean, ch: Int): Int {
        var result = ch
        if (control) {
            // Search is the control key.
            if (result >= 'a'.code && result <= 'z'.code) {
                result = (result - 'a'.code + '\u0001'.code).toChar().code
            } else if (result >= 'A'.code && result <= 'Z'.code) {
                result = (result - 'A'.code + '\u0001'.code).toChar().code
            } else if (result == ' '.code || result == '2'.code) {
                result = 0
            } else if (result == '['.code || result == '3'.code) {
                result = 27 // ^[ (Esc)
            } else if (result == '\\'.code || result == '4'.code) {
                result = 28
            } else if (result == ']'.code || result == '5'.code) {
                result = 29
            } else if (result == '^'.code || result == '6'.code) {
                result = 30 // control-^
            } else if (result == '_'.code || result == '7'.code) {
                result = 31
            } else if (result == '8'.code) {
                result = 127 // DEL
            } else if (result == '9'.code) {
                result = KEYCODE_OFFSET + KEYCODE_F11
            } else if (result == '0'.code) {
                result = KEYCODE_OFFSET + KEYCODE_F12
            }
        } else if (fn) {
            if (result == 'w'.code || result == 'W'.code) {
                result = KEYCODE_OFFSET + KEYCODE_DPAD_UP
            } else if (result == 'a'.code || result == 'A'.code) {
                result = KEYCODE_OFFSET + KEYCODE_DPAD_LEFT
            } else if (result == 's'.code || result == 'S'.code) {
                result = KEYCODE_OFFSET + KEYCODE_DPAD_DOWN
            } else if (result == 'd'.code || result == 'D'.code) {
                result = KEYCODE_OFFSET + KEYCODE_DPAD_RIGHT
            } else if (result == 'p'.code || result == 'P'.code) {
                result = KEYCODE_OFFSET + KEYCODE_PAGE_UP
            } else if (result == 'n'.code || result == 'N'.code) {
                result = KEYCODE_OFFSET + KEYCODE_PAGE_DOWN
            } else if (result == 't'.code || result == 'T'.code) {
                result = KEYCODE_OFFSET + KEYCODE_TAB
            } else if (result == 'l'.code || result == 'L'.code) {
                result = '|'.code
            } else if (result == 'u'.code || result == 'U'.code) {
                result = '_'.code
            } else if (result == 'e'.code || result == 'E'.code) {
                result = 27 // ^[ (Esc)
            } else if (result == '.'.code) {
                result = 28 // ^\
            } else if (result > '0'.code && result <= '9'.code) {
                // F1-F9
                result = (result + KEYCODE_OFFSET + KEYCODE_F1 - 1).toChar().code
            } else if (result == '0'.code) {
                result = KEYCODE_OFFSET + KEYCODE_F10
            } else if (result == 'i'.code || result == 'I'.code) {
                result = KEYCODE_OFFSET + KEYCODE_INSERT
            } else if (result == 'x'.code || result == 'X'.code) {
                result = KEYCODE_OFFSET + KEYCODE_FORWARD_DEL
            } else if (result == 'h'.code || result == 'H'.code) {
                result = KEYCODE_OFFSET + KEYCODE_MOVE_HOME
            } else if (result == 'f'.code || result == 'F'.code) {
                result = KEYCODE_OFFSET + KEYCODE_MOVE_END
            }
        }

        if (result > -1) {
            mAltKey.adjustAfterKeypress()
            mCapKey.adjustAfterKeypress()
            mControlKey.adjustAfterKeypress()
            mFnKey.adjustAfterKeypress()
            updateCursorMode()
        }

        return result
    }

    /**
     * Handle a keyDown event.
     *
     * @param keyCode the keycode of the keyDown event
     */
    @Throws(IOException::class)
    fun keyDown(
        keyCode: Int, event: KeyEvent, appMode: Boolean,
        allowToggle: Boolean
    ) {
        if (LOG_KEYS) {
            Log.i(TAG, "keyDown($keyCode,$event,$appMode,$allowToggle)")
        }
        if (handleKeyCode(keyCode, event, appMode)) {
            return
        }
        var result = -1
        var chordedCtrl = false
        var setHighBit = false
        when (keyCode) {
            KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_ALT_LEFT -> if (allowToggle) {
                mAltKey.onPress()
                updateCursorMode()
            }

            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> if (allowToggle) {
                mCapKey.onPress()
                updateCursorMode()
            }

            KEYCODE_CTRL_LEFT, KEYCODE_CTRL_RIGHT ->             // Ignore the control key.
                return

            KEYCODE_CAPS_LOCK ->             // Ignore the capslock key.
                return

            KEYCODE_FUNCTION ->             // Ignore the function key.
                return

            KeyEvent.KEYCODE_BACK -> result = mBackKeyCode
            else -> {
                val metaState = event.metaState
                chordedCtrl = ((META_CTRL_ON and metaState) != 0)
                val effectiveCaps = allowToggle &&
                        (mCapKey.isActive)
                var effectiveAlt = allowToggle && mAltKey.isActive
                var effectiveMetaState = metaState and (META_CTRL_MASK.inv())
                if (effectiveCaps) {
                    effectiveMetaState = effectiveMetaState or META_SHIFT_ON
                }
                if (!allowToggle && (effectiveMetaState and META_ALT_ON) != 0) {
                    effectiveAlt = true
                }
                if (effectiveAlt) {
                    if (this.altSendsEsc) {
                        mTermSession.write(byteArrayOf(0x1b), 0, 1)
                        effectiveMetaState = effectiveMetaState and KeyEvent.META_ALT_MASK.inv()
                    } else if (SUPPORT_8_BIT_META) {
                        setHighBit = true
                        effectiveMetaState = effectiveMetaState and KeyEvent.META_ALT_MASK.inv()
                    } else {
                        // Legacy behavior: Pass Alt through to allow composing characters.
                        effectiveMetaState = effectiveMetaState or META_ALT_ON
                    }
                }


                // Note: The Hacker keyboard IME key labeled Alt actually sends Meta.
                if ((metaState and KeyEvent.META_META_ON) != 0) {
                    if (this.altSendsEsc) {
                        mTermSession.write(byteArrayOf(0x1b), 0, 1)
                        effectiveMetaState = effectiveMetaState and KeyEvent.META_META_MASK.inv()
                    } else {
                        if (SUPPORT_8_BIT_META) {
                            setHighBit = true
                            effectiveMetaState =
                                effectiveMetaState and KeyEvent.META_META_MASK.inv()
                        }
                    }
                }
                result = event.getUnicodeChar(effectiveMetaState)

                if ((result and KeyCharacterMap.COMBINING_ACCENT) != 0) {
                    if (LOG_COMBINING_ACCENT) {
                        Log.i(TAG, "Got combining accent $result")
                    }
                    this.combiningAccent = result and KeyCharacterMap.COMBINING_ACCENT_MASK
                    return
                }
                if (this.combiningAccent != 0) {
                    val unaccentedChar = result
                    result = KeyCharacterMap.getDeadChar(this.combiningAccent, unaccentedChar)
                    if (LOG_COMBINING_ACCENT) {
                        Log.i(
                            TAG,
                            "getDeadChar(" + this.combiningAccent + ", " + unaccentedChar + ") -> " + result
                        )
                    }
                    this.combiningAccent = 0
                }
            }
        }

        val effectiveControl =
            chordedCtrl || mHardwareControlKey || (allowToggle && mControlKey.isActive)
        val effectiveFn = allowToggle && mFnKey.isActive

        result = mapControlChar(effectiveControl, effectiveFn, result)

        if (result >= KEYCODE_OFFSET) {
            handleKeyCode(result - KEYCODE_OFFSET, null, appMode)
        } else if (result >= 0) {
            if (setHighBit) {
                result = result or 0x80
            }
            mTermSession.write(result)
        }
    }

    private fun updateCursorMode() {
        this.cursorMode = (getCursorModeHelper(mCapKey, TextRenderer.MODE_SHIFT_SHIFT)
                or getCursorModeHelper(mAltKey, TextRenderer.MODE_ALT_SHIFT)
                or getCursorModeHelper(mControlKey, TextRenderer.MODE_CTRL_SHIFT)
                or getCursorModeHelper(mFnKey, TextRenderer.MODE_FN_SHIFT))
    }

    fun handleKeyCode(keyCode: Int, event: KeyEvent?, appMode: Boolean): Boolean {
        var code: String? = null
        if (event != null) {
            var keyMod = 0
            // META_CTRL_ON was added only in API 11, so don't use it,
            // use our own tracking of Ctrl key instead.
            // (event.getMetaState() & META_CTRL_ON) != 0
            if (mHardwareControlKey || mControlKey.isActive) {
                keyMod = keyMod or KEYMOD_CTRL
            }
            if ((event.metaState and META_ALT_ON) != 0) {
                keyMod = keyMod or KEYMOD_ALT
            }
            if ((event.metaState and META_SHIFT_ON) != 0) {
                keyMod = keyMod or KEYMOD_SHIFT
            }
            // First try to map scancode
            code = mKeyMap!![event.scanCode or KEYMOD_SCAN or keyMod]
            if (code == null) {
                code = mKeyMap!![keyCode or keyMod]
            }
        }

        if (code == null && keyCode >= 0 && keyCode < mKeyCodes.size) {
            if (appMode) {
                code = mAppKeyCodes[keyCode]
            }
            if (code == null) {
                code = mKeyCodes[keyCode]
            }
        }

        if (code != null) {
            if (EmulatorDebug.LOG_CHARACTERS_FLAG) {
                val bytes = code.toByteArray()
                Log.d(EmulatorDebug.LOG_TAG, "Out: '" + bytesToString(bytes, 0, bytes.size) + "'")
            }
            mTermSession.write(code)
            return true
        }
        return false
    }

    /**
     * Handle a keyUp event.
     *
     * @param keyCode the keyCode of the keyUp event
     */
    fun keyUp(keyCode: Int, event: KeyEvent) {
        val allowToggle = isEventFromToggleDevice(event)
        when (keyCode) {
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> if (allowToggle) {
                mAltKey.onRelease()
                updateCursorMode()
            }

            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> if (allowToggle) {
                mCapKey.onRelease()
                updateCursorMode()
            }

            KEYCODE_CTRL_LEFT, KEYCODE_CTRL_RIGHT -> {}
            else -> {}
        }
    }

    val isAltActive: Boolean
        get() = mAltKey.isActive

    val isCtrlActive: Boolean
        get() = mControlKey.isActive

    companion object {
        private const val TAG = "TermKeyListener"
        private const val LOG_MISC = false
        private const val LOG_KEYS = false
        private const val LOG_COMBINING_ACCENT = false

        /** Disabled for now because it interferes with ALT processing on phones with physical keyboards.  */
        private const val SUPPORT_8_BIT_META = false

        private const val KEYMOD_ALT = -0x80000000
        private const val KEYMOD_CTRL = 0x40000000
        private const val KEYMOD_SHIFT = 0x20000000

        /** Means this maps raw scancode  */
        private const val KEYMOD_SCAN = 0x10000000

        private var mKeyMap: MutableMap<Int?, String?>? = null

        // Map keycodes out of (above) the Unicode code point space.
        const val KEYCODE_OFFSET: Int = 0xA00000

        private fun getCursorModeHelper(key: ModifierKey, shift: Int): Int {
            return key.uIMode shl shift
        }

        fun isEventFromToggleDevice(event: KeyEvent): Boolean {
            return KeyCharacterMap.load(event.deviceId).modifierBehavior ==
                    KeyCharacterMap.MODIFIER_BEHAVIOR_CHORDED_OR_TOGGLED
        }
    }
}