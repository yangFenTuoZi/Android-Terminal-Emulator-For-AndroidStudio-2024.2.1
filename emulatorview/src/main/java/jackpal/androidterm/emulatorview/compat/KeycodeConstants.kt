package jackpal.androidterm.emulatorview.compat

/**
 * Keycode constants and modifier masks for use with keyboard event listeners.
 *
 * The Meta masks (ctrl, alt, shift, and meta) are used as follows:
 * KeyEvent keyEvent = ...;
 * boolean isCtrlPressed = (keyEvent.getMetaState() & META_CTRL_ON) != 0
 *
 * Contains the complete set of Android key codes that were defined as of the 2.3 API.
 * We could pull in the constants from the 2.3 API, but then we would need to raise the
 * SDK minVersion in the manifest. We want to keep compatibility with Android 1.6,
 * and raising this level could result in the accidental use of a newer API.
 */
object KeycodeConstants {
    /** Key code constant: Unknown key code.  */
    const val KEYCODE_UNKNOWN: Int = 0

    /** Key code constant: Soft Left key.
     * Usually situated below the display on phones and used as a multi-function
     * feature key for selecting a software defined function shown on the bottom left
     * of the display.  */
    const val KEYCODE_SOFT_LEFT: Int = 1

    /** Key code constant: Soft Right key.
     * Usually situated below the display on phones and used as a multi-function
     * feature key for selecting a software defined function shown on the bottom right
     * of the display.  */
    const val KEYCODE_SOFT_RIGHT: Int = 2

    /** Key code constant: Home key.
     * This key is handled by the framework and is never delivered to applications.  */
    const val KEYCODE_HOME: Int = 3

    /** Key code constant: Back key.  */
    const val KEYCODE_BACK: Int = 4

    /** Key code constant: Call key.  */
    const val KEYCODE_CALL: Int = 5

    /** Key code constant: End Call key.  */
    const val KEYCODE_ENDCALL: Int = 6

    /** Key code constant: '0' key.  */
    const val KEYCODE_0: Int = 7

    /** Key code constant: '1' key.  */
    const val KEYCODE_1: Int = 8

    /** Key code constant: '2' key.  */
    const val KEYCODE_2: Int = 9

    /** Key code constant: '3' key.  */
    const val KEYCODE_3: Int = 10

    /** Key code constant: '4' key.  */
    const val KEYCODE_4: Int = 11

    /** Key code constant: '5' key.  */
    const val KEYCODE_5: Int = 12

    /** Key code constant: '6' key.  */
    const val KEYCODE_6: Int = 13

    /** Key code constant: '7' key.  */
    const val KEYCODE_7: Int = 14

    /** Key code constant: '8' key.  */
    const val KEYCODE_8: Int = 15

    /** Key code constant: '9' key.  */
    const val KEYCODE_9: Int = 16

    /** Key code constant: '*' key.  */
    const val KEYCODE_STAR: Int = 17

    /** Key code constant: '#' key.  */
    const val KEYCODE_POUND: Int = 18

    /** Key code constant: Directional Pad Up key.
     * May also be synthesized from trackball motions.  */
    const val KEYCODE_DPAD_UP: Int = 19

    /** Key code constant: Directional Pad Down key.
     * May also be synthesized from trackball motions.  */
    const val KEYCODE_DPAD_DOWN: Int = 20

    /** Key code constant: Directional Pad Left key.
     * May also be synthesized from trackball motions.  */
    const val KEYCODE_DPAD_LEFT: Int = 21

    /** Key code constant: Directional Pad Right key.
     * May also be synthesized from trackball motions.  */
    const val KEYCODE_DPAD_RIGHT: Int = 22

    /** Key code constant: Directional Pad Center key.
     * May also be synthesized from trackball motions.  */
    const val KEYCODE_DPAD_CENTER: Int = 23

    /** Key code constant: Volume Up key.
     * Adjusts the speaker volume up.  */
    const val KEYCODE_VOLUME_UP: Int = 24

    /** Key code constant: Volume Down key.
     * Adjusts the speaker volume down.  */
    const val KEYCODE_VOLUME_DOWN: Int = 25

    /** Key code constant: Power key.  */
    const val KEYCODE_POWER: Int = 26

    /** Key code constant: Camera key.
     * Used to launch a camera application or take pictures.  */
    const val KEYCODE_CAMERA: Int = 27

    /** Key code constant: Clear key.  */
    const val KEYCODE_CLEAR: Int = 28

    /** Key code constant: 'A' key.  */
    const val KEYCODE_A: Int = 29

    /** Key code constant: 'B' key.  */
    const val KEYCODE_B: Int = 30

    /** Key code constant: 'C' key.  */
    const val KEYCODE_C: Int = 31

    /** Key code constant: 'D' key.  */
    const val KEYCODE_D: Int = 32

    /** Key code constant: 'E' key.  */
    const val KEYCODE_E: Int = 33

    /** Key code constant: 'F' key.  */
    const val KEYCODE_F: Int = 34

    /** Key code constant: 'G' key.  */
    const val KEYCODE_G: Int = 35

    /** Key code constant: 'H' key.  */
    const val KEYCODE_H: Int = 36

    /** Key code constant: 'I' key.  */
    const val KEYCODE_I: Int = 37

    /** Key code constant: 'J' key.  */
    const val KEYCODE_J: Int = 38

    /** Key code constant: 'K' key.  */
    const val KEYCODE_K: Int = 39

    /** Key code constant: 'L' key.  */
    const val KEYCODE_L: Int = 40

    /** Key code constant: 'M' key.  */
    const val KEYCODE_M: Int = 41

    /** Key code constant: 'N' key.  */
    const val KEYCODE_N: Int = 42

    /** Key code constant: 'O' key.  */
    const val KEYCODE_O: Int = 43

    /** Key code constant: 'P' key.  */
    const val KEYCODE_P: Int = 44

    /** Key code constant: 'Q' key.  */
    const val KEYCODE_Q: Int = 45

    /** Key code constant: 'R' key.  */
    const val KEYCODE_R: Int = 46

    /** Key code constant: 'S' key.  */
    const val KEYCODE_S: Int = 47

    /** Key code constant: 'T' key.  */
    const val KEYCODE_T: Int = 48

    /** Key code constant: 'U' key.  */
    const val KEYCODE_U: Int = 49

    /** Key code constant: 'V' key.  */
    const val KEYCODE_V: Int = 50

    /** Key code constant: 'W' key.  */
    const val KEYCODE_W: Int = 51

    /** Key code constant: 'X' key.  */
    const val KEYCODE_X: Int = 52

    /** Key code constant: 'Y' key.  */
    const val KEYCODE_Y: Int = 53

    /** Key code constant: 'Z' key.  */
    const val KEYCODE_Z: Int = 54

    /** Key code constant: ',' key.  */
    const val KEYCODE_COMMA: Int = 55

    /** Key code constant: '.' key.  */
    const val KEYCODE_PERIOD: Int = 56

    /** Key code constant: Left Alt modifier key.  */
    const val KEYCODE_ALT_LEFT: Int = 57

    /** Key code constant: Right Alt modifier key.  */
    const val KEYCODE_ALT_RIGHT: Int = 58

    /** Key code constant: Left Shift modifier key.  */
    const val KEYCODE_SHIFT_LEFT: Int = 59

    /** Key code constant: Right Shift modifier key.  */
    const val KEYCODE_SHIFT_RIGHT: Int = 60

    /** Key code constant: Tab key.  */
    const val KEYCODE_TAB: Int = 61

    /** Key code constant: Space key.  */
    const val KEYCODE_SPACE: Int = 62

    /** Key code constant: Symbol modifier key.
     * Used to enter alternate symbols.  */
    const val KEYCODE_SYM: Int = 63

    /** Key code constant: Explorer special function key.
     * Used to launch a browser application.  */
    const val KEYCODE_EXPLORER: Int = 64

    /** Key code constant: Envelope special function key.
     * Used to launch a mail application.  */
    const val KEYCODE_ENVELOPE: Int = 65

    /** Key code constant: Enter key.  */
    const val KEYCODE_ENTER: Int = 66

    /** Key code constant: Backspace key.
     * Deletes characters before the insertion point, unlike [.KEYCODE_FORWARD_DEL].  */
    const val KEYCODE_DEL: Int = 67

    /** Key code constant: '`' (backtick) key.  */
    const val KEYCODE_GRAVE: Int = 68

    /** Key code constant: '-'.  */
    const val KEYCODE_MINUS: Int = 69

    /** Key code constant: '=' key.  */
    const val KEYCODE_EQUALS: Int = 70

    /** Key code constant: '[' key.  */
    const val KEYCODE_LEFT_BRACKET: Int = 71

    /** Key code constant: ']' key.  */
    const val KEYCODE_RIGHT_BRACKET: Int = 72

    /** Key code constant: '\' key.  */
    const val KEYCODE_BACKSLASH: Int = 73

    /** Key code constant: ';' key.  */
    const val KEYCODE_SEMICOLON: Int = 74

    /** Key code constant: ''' (apostrophe) key.  */
    const val KEYCODE_APOSTROPHE: Int = 75

    /** Key code constant: '/' key.  */
    const val KEYCODE_SLASH: Int = 76

    /** Key code constant: '@' key.  */
    const val KEYCODE_AT: Int = 77

    /** Key code constant: Number modifier key.
     * Used to enter numeric symbols.
     * This key is not Num Lock; it is more like [.KEYCODE_ALT_LEFT] and is
     * interpreted as an ALT key by [android.text.method.MetaKeyKeyListener].  */
    const val KEYCODE_NUM: Int = 78

    /** Key code constant: Headset Hook key.
     * Used to hang up calls and stop media.  */
    const val KEYCODE_HEADSETHOOK: Int = 79

    /** Key code constant: Camera Focus key.
     * Used to focus the camera.  */
    const val KEYCODE_FOCUS: Int = 80 // *Camera* focus

    /** Key code constant: '+' key.  */
    const val KEYCODE_PLUS: Int = 81

    /** Key code constant: Menu key.  */
    const val KEYCODE_MENU: Int = 82

    /** Key code constant: Notification key.  */
    const val KEYCODE_NOTIFICATION: Int = 83

    /** Key code constant: Search key.  */
    const val KEYCODE_SEARCH: Int = 84

    /** Key code constant: Play/Pause media key.  */
    const val KEYCODE_MEDIA_PLAY_PAUSE: Int = 85

    /** Key code constant: Stop media key.  */
    const val KEYCODE_MEDIA_STOP: Int = 86

    /** Key code constant: Play Next media key.  */
    const val KEYCODE_MEDIA_NEXT: Int = 87

    /** Key code constant: Play Previous media key.  */
    const val KEYCODE_MEDIA_PREVIOUS: Int = 88

    /** Key code constant: Rewind media key.  */
    const val KEYCODE_MEDIA_REWIND: Int = 89

    /** Key code constant: Fast Forward media key.  */
    const val KEYCODE_MEDIA_FAST_FORWARD: Int = 90

    /** Key code constant: Mute key.
     * Mutes the microphone, unlike [.KEYCODE_VOLUME_MUTE].  */
    const val KEYCODE_MUTE: Int = 91

    /** Key code constant: Page Up key.  */
    const val KEYCODE_PAGE_UP: Int = 92

    /** Key code constant: Page Down key.  */
    const val KEYCODE_PAGE_DOWN: Int = 93

    /** Key code constant: Picture Symbols modifier key.
     * Used to switch symbol sets (Emoji, Kao-moji).  */
    const val KEYCODE_PICTSYMBOLS: Int = 94 // switch symbol-sets (Emoji,Kao-moji)

    /** Key code constant: Switch Charset modifier key.
     * Used to switch character sets (Kanji, Katakana).  */
    const val KEYCODE_SWITCH_CHARSET: Int = 95 // switch char-sets (Kanji,Katakana)

    /** Key code constant: A Button key.
     * On a game controller, the A button should be either the button labeled A
     * or the first button on the upper row of controller buttons.  */
    const val KEYCODE_BUTTON_A: Int = 96

    /** Key code constant: B Button key.
     * On a game controller, the B button should be either the button labeled B
     * or the second button on the upper row of controller buttons.  */
    const val KEYCODE_BUTTON_B: Int = 97

    /** Key code constant: C Button key.
     * On a game controller, the C button should be either the button labeled C
     * or the third button on the upper row of controller buttons.  */
    const val KEYCODE_BUTTON_C: Int = 98

    /** Key code constant: X Button key.
     * On a game controller, the X button should be either the button labeled X
     * or the first button on the lower row of controller buttons.  */
    const val KEYCODE_BUTTON_X: Int = 99

    /** Key code constant: Y Button key.
     * On a game controller, the Y button should be either the button labeled Y
     * or the second button on the lower row of controller buttons.  */
    const val KEYCODE_BUTTON_Y: Int = 100

    /** Key code constant: Z Button key.
     * On a game controller, the Z button should be either the button labeled Z
     * or the third button on the lower row of controller buttons.  */
    const val KEYCODE_BUTTON_Z: Int = 101

    /** Key code constant: L1 Button key.
     * On a game controller, the L1 button should be either the button labeled L1 (or L)
     * or the top left trigger button.  */
    const val KEYCODE_BUTTON_L1: Int = 102

    /** Key code constant: R1 Button key.
     * On a game controller, the R1 button should be either the button labeled R1 (or R)
     * or the top right trigger button.  */
    const val KEYCODE_BUTTON_R1: Int = 103

    /** Key code constant: L2 Button key.
     * On a game controller, the L2 button should be either the button labeled L2
     * or the bottom left trigger button.  */
    const val KEYCODE_BUTTON_L2: Int = 104

    /** Key code constant: R2 Button key.
     * On a game controller, the R2 button should be either the button labeled R2
     * or the bottom right trigger button.  */
    const val KEYCODE_BUTTON_R2: Int = 105

    /** Key code constant: Left Thumb Button key.
     * On a game controller, the left thumb button indicates that the left (or only)
     * joystick is pressed.  */
    const val KEYCODE_BUTTON_THUMBL: Int = 106

    /** Key code constant: Right Thumb Button key.
     * On a game controller, the right thumb button indicates that the right
     * joystick is pressed.  */
    const val KEYCODE_BUTTON_THUMBR: Int = 107

    /** Key code constant: Start Button key.
     * On a game controller, the button labeled Start.  */
    const val KEYCODE_BUTTON_START: Int = 108

    /** Key code constant: Select Button key.
     * On a game controller, the button labeled Select.  */
    const val KEYCODE_BUTTON_SELECT: Int = 109

    /** Key code constant: Mode Button key.
     * On a game controller, the button labeled Mode.  */
    const val KEYCODE_BUTTON_MODE: Int = 110

    /** Key code constant: Escape key.  */
    const val KEYCODE_ESCAPE: Int = 111

    /** Key code constant: Forward Delete key.
     * Deletes characters ahead of the insertion point, unlike [.KEYCODE_DEL].  */
    const val KEYCODE_FORWARD_DEL: Int = 112

    /** Key code constant: Left Control modifier key.  */
    const val KEYCODE_CTRL_LEFT: Int = 113

    /** Key code constant: Right Control modifier key.  */
    const val KEYCODE_CTRL_RIGHT: Int = 114

    /** Key code constant: Caps Lock modifier key.  */
    const val KEYCODE_CAPS_LOCK: Int = 115

    /** Key code constant: Scroll Lock key.  */
    const val KEYCODE_SCROLL_LOCK: Int = 116

    /** Key code constant: Left Meta modifier key.  */
    const val KEYCODE_META_LEFT: Int = 117

    /** Key code constant: Right Meta modifier key.  */
    const val KEYCODE_META_RIGHT: Int = 118

    /** Key code constant: Function modifier key.  */
    const val KEYCODE_FUNCTION: Int = 119

    /** Key code constant: System Request / Print Screen key.  */
    const val KEYCODE_SYSRQ: Int = 120

    /** Key code constant: Break / Pause key.  */
    const val KEYCODE_BREAK: Int = 121

    /** Key code constant: Home Movement key.
     * Used for scrolling or moving the cursor around to the start of a line
     * or to the top of a list.  */
    const val KEYCODE_MOVE_HOME: Int = 122

    /** Key code constant: End Movement key.
     * Used for scrolling or moving the cursor around to the end of a line
     * or to the bottom of a list.  */
    const val KEYCODE_MOVE_END: Int = 123

    /** Key code constant: Insert key.
     * Toggles insert / overwrite edit mode.  */
    const val KEYCODE_INSERT: Int = 124

    /** Key code constant: Forward key.
     * Navigates forward in the history stack.  Complement of [.KEYCODE_BACK].  */
    const val KEYCODE_FORWARD: Int = 125

    /** Key code constant: Play media key.  */
    const val KEYCODE_MEDIA_PLAY: Int = 126

    /** Key code constant: Pause media key.  */
    const val KEYCODE_MEDIA_PAUSE: Int = 127

    /** Key code constant: Close media key.
     * May be used to close a CD tray, for example.  */
    const val KEYCODE_MEDIA_CLOSE: Int = 128

    /** Key code constant: Eject media key.
     * May be used to eject a CD tray, for example.  */
    const val KEYCODE_MEDIA_EJECT: Int = 129

    /** Key code constant: Record media key.  */
    const val KEYCODE_MEDIA_RECORD: Int = 130

    /** Key code constant: F1 key.  */
    const val KEYCODE_F1: Int = 131

    /** Key code constant: F2 key.  */
    const val KEYCODE_F2: Int = 132

    /** Key code constant: F3 key.  */
    const val KEYCODE_F3: Int = 133

    /** Key code constant: F4 key.  */
    const val KEYCODE_F4: Int = 134

    /** Key code constant: F5 key.  */
    const val KEYCODE_F5: Int = 135

    /** Key code constant: F6 key.  */
    const val KEYCODE_F6: Int = 136

    /** Key code constant: F7 key.  */
    const val KEYCODE_F7: Int = 137

    /** Key code constant: F8 key.  */
    const val KEYCODE_F8: Int = 138

    /** Key code constant: F9 key.  */
    const val KEYCODE_F9: Int = 139

    /** Key code constant: F10 key.  */
    const val KEYCODE_F10: Int = 140

    /** Key code constant: F11 key.  */
    const val KEYCODE_F11: Int = 141

    /** Key code constant: F12 key.  */
    const val KEYCODE_F12: Int = 142

    /** Key code constant: Num Lock modifier key.
     * This is the Num Lock key; it is different from [.KEYCODE_NUM].
     * This key generally modifies the behavior of other keys on the numeric keypad.  */
    const val KEYCODE_NUM_LOCK: Int = 143

    /** Key code constant: Numeric keypad '0' key.  */
    const val KEYCODE_NUMPAD_0: Int = 144

    /** Key code constant: Numeric keypad '1' key.  */
    const val KEYCODE_NUMPAD_1: Int = 145

    /** Key code constant: Numeric keypad '2' key.  */
    const val KEYCODE_NUMPAD_2: Int = 146

    /** Key code constant: Numeric keypad '3' key.  */
    const val KEYCODE_NUMPAD_3: Int = 147

    /** Key code constant: Numeric keypad '4' key.  */
    const val KEYCODE_NUMPAD_4: Int = 148

    /** Key code constant: Numeric keypad '5' key.  */
    const val KEYCODE_NUMPAD_5: Int = 149

    /** Key code constant: Numeric keypad '6' key.  */
    const val KEYCODE_NUMPAD_6: Int = 150

    /** Key code constant: Numeric keypad '7' key.  */
    const val KEYCODE_NUMPAD_7: Int = 151

    /** Key code constant: Numeric keypad '8' key.  */
    const val KEYCODE_NUMPAD_8: Int = 152

    /** Key code constant: Numeric keypad '9' key.  */
    const val KEYCODE_NUMPAD_9: Int = 153

    /** Key code constant: Numeric keypad '/' key (for division).  */
    const val KEYCODE_NUMPAD_DIVIDE: Int = 154

    /** Key code constant: Numeric keypad '*' key (for multiplication).  */
    const val KEYCODE_NUMPAD_MULTIPLY: Int = 155

    /** Key code constant: Numeric keypad '-' key (for subtraction).  */
    const val KEYCODE_NUMPAD_SUBTRACT: Int = 156

    /** Key code constant: Numeric keypad '+' key (for addition).  */
    const val KEYCODE_NUMPAD_ADD: Int = 157

    /** Key code constant: Numeric keypad '.' key (for decimals or digit grouping).  */
    const val KEYCODE_NUMPAD_DOT: Int = 158

    /** Key code constant: Numeric keypad ',' key (for decimals or digit grouping).  */
    const val KEYCODE_NUMPAD_COMMA: Int = 159

    /** Key code constant: Numeric keypad Enter key.  */
    const val KEYCODE_NUMPAD_ENTER: Int = 160

    /** Key code constant: Numeric keypad '=' key.  */
    const val KEYCODE_NUMPAD_EQUALS: Int = 161

    /** Key code constant: Numeric keypad '(' key.  */
    const val KEYCODE_NUMPAD_LEFT_PAREN: Int = 162

    /** Key code constant: Numeric keypad ')' key.  */
    const val KEYCODE_NUMPAD_RIGHT_PAREN: Int = 163

    /** Key code constant: Volume Mute key.
     * Mutes the speaker, unlike [.KEYCODE_MUTE].
     * This key should normally be implemented as a toggle such that the first press
     * mutes the speaker and the second press restores the original volume.  */
    const val KEYCODE_VOLUME_MUTE: Int = 164

    /** Key code constant: Info key.
     * Common on TV remotes to show additional information related to what is
     * currently being viewed.  */
    const val KEYCODE_INFO: Int = 165

    /** Key code constant: Channel up key.
     * On TV remotes, increments the television channel.  */
    const val KEYCODE_CHANNEL_UP: Int = 166

    /** Key code constant: Channel down key.
     * On TV remotes, decrements the television channel.  */
    const val KEYCODE_CHANNEL_DOWN: Int = 167

    /** Key code constant: Zoom in key.  */
    const val KEYCODE_ZOOM_IN: Int = 168

    /** Key code constant: Zoom out key.  */
    const val KEYCODE_ZOOM_OUT: Int = 169

    /** Key code constant: TV key.
     * On TV remotes, switches to viewing live TV.  */
    const val KEYCODE_TV: Int = 170

    /** Key code constant: Window key.
     * On TV remotes, toggles picture-in-picture mode or other windowing functions.  */
    const val KEYCODE_WINDOW: Int = 171

    /** Key code constant: Guide key.
     * On TV remotes, shows a programming guide.  */
    const val KEYCODE_GUIDE: Int = 172

    /** Key code constant: DVR key.
     * On some TV remotes, switches to a DVR mode for recorded shows.  */
    const val KEYCODE_DVR: Int = 173

    /** Key code constant: Bookmark key.
     * On some TV remotes, bookmarks content or web pages.  */
    const val KEYCODE_BOOKMARK: Int = 174

    /** Key code constant: Toggle captions key.
     * Switches the mode for closed-captioning text, for example during television shows.  */
    const val KEYCODE_CAPTIONS: Int = 175

    /** Key code constant: Settings key.
     * Starts the system settings activity.  */
    const val KEYCODE_SETTINGS: Int = 176

    /** Key code constant: TV power key.
     * On TV remotes, toggles the power on a television screen.  */
    const val KEYCODE_TV_POWER: Int = 177

    /** Key code constant: TV input key.
     * On TV remotes, switches the input on a television screen.  */
    const val KEYCODE_TV_INPUT: Int = 178

    /** Key code constant: Set-top-box power key.
     * On TV remotes, toggles the power on an external Set-top-box.  */
    const val KEYCODE_STB_POWER: Int = 179

    /** Key code constant: Set-top-box input key.
     * On TV remotes, switches the input mode on an external Set-top-box.  */
    const val KEYCODE_STB_INPUT: Int = 180

    /** Key code constant: A/V Receiver power key.
     * On TV remotes, toggles the power on an external A/V Receiver.  */
    const val KEYCODE_AVR_POWER: Int = 181

    /** Key code constant: A/V Receiver input key.
     * On TV remotes, switches the input mode on an external A/V Receiver.  */
    const val KEYCODE_AVR_INPUT: Int = 182

    /** Key code constant: Red "programmable" key.
     * On TV remotes, acts as a contextual/programmable key.  */
    const val KEYCODE_PROG_RED: Int = 183

    /** Key code constant: Green "programmable" key.
     * On TV remotes, actsas a contextual/programmable key.  */
    const val KEYCODE_PROG_GREEN: Int = 184

    /** Key code constant: Yellow "programmable" key.
     * On TV remotes, acts as a contextual/programmable key.  */
    const val KEYCODE_PROG_YELLOW: Int = 185

    /** Key code constant: Blue "programmable" key.
     * On TV remotes, acts as a contextual/programmable key.  */
    const val KEYCODE_PROG_BLUE: Int = 186

    const val LAST_KEYCODE: Int = KEYCODE_PROG_BLUE

    const val META_ALT_ON: Int = 2
    const val META_CAPS_LOCK_ON: Int = 0x00100000
    const val META_CTRL_ON: Int = 0x1000
    const val META_SHIFT_ON: Int = 1
    const val META_CTRL_MASK: Int = 0x7000
    const val META_META_ON: Int = 0x00010000
    const val META_META_MASK: Int = 0x00070000
}