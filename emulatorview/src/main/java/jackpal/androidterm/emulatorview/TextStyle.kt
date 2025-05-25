package jackpal.androidterm.emulatorview

internal class TextStyle private constructor() {
    init {
        // Prevent instantiation
        throw UnsupportedOperationException()
    }

    companion object {
        // Effect bitmasks:
        const val fxNormal: Int = 0
        const val fxBold: Int = 1 // Originally Bright

        //final static int fxFaint = 2;
        const val fxItalic: Int = 1 shl 1
        const val fxUnderline: Int = 1 shl 2
        const val fxBlink: Int = 1 shl 3
        const val fxInverse: Int = 1 shl 4
        const val fxInvisible: Int = 1 shl 5

        // Special color indices
        const val ciForeground: Int = 256 // VT100 text foreground color
        const val ciBackground: Int = 257 // VT100 text background color
        const val ciCursorForeground: Int = 258 // VT100 text cursor foreground color
        const val ciCursorBackground: Int = 259 // VT100 text cursor background color

        val ciColorLength: Int = ciCursorBackground + 1

        val kNormalTextStyle: Int = encode(ciForeground, ciBackground, fxNormal)

        fun encode(foreColor: Int, backColor: Int, effect: Int): Int {
            return ((effect and 0x3f) shl 18) or ((foreColor and 0x1ff) shl 9) or (backColor and 0x1ff)
        }

        fun decodeForeColor(encodedColor: Int): Int {
            return (encodedColor shr 9) and 0x1ff
        }

        fun decodeBackColor(encodedColor: Int): Int {
            return encodedColor and 0x1ff
        }

        fun decodeEffect(encodedColor: Int): Int {
            return (encodedColor shr 18) and 0x3f
        }
    }
}