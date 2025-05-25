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
package jackpal.androidterm.emulatorview

import kotlin.math.abs

/**
 * A class describing a color scheme for an [EmulatorView].
 *
 *
 * `EmulatorView` supports changing its default foreground,
 * background, and cursor colors.  Passing a `ColorScheme` to
 * [setColorScheme][EmulatorView.setColorScheme] will cause the
 * `EmulatorView` to use the specified colors as its defaults.
 *
 *
 * Cursor colors can be omitted when specifying a color scheme; if no cursor
 * colors are specified, `ColorScheme` will automatically select
 * suitable cursor colors for you.
 *
 * @see EmulatorView.setColorScheme
 */
class ColorScheme {
    /**
     * @return This `ColorScheme`'s foreground color as an ARGB
     * hex value.
     */
    val foreColor: Int

    /**
     * @return This `ColorScheme`'s background color as an ARGB
     * hex value.
     */
    val backColor: Int

    /**
     * @return This `ColorScheme`'s cursor foreground color as an ARGB
     * hex value.
     */
    var cursorForeColor: Int = 0
        private set

    /**
     * @return This `ColorScheme`'s cursor background color as an ARGB
     * hex value.
     */
    var cursorBackColor: Int = 0
        private set

    private fun setDefaultCursorColors() {
        cursorBackColor = sDefaultCursorBackColor
        // Use the foreColor unless the foreColor is too similar to the cursorBackColor
        val foreDistance = distance(foreColor, cursorBackColor)
        val backDistance = distance(backColor, cursorBackColor)
        cursorForeColor = if (foreDistance * 2 >= backDistance) {
            foreColor
        } else {
            backColor
        }
    }

    /**
     * Creates a `ColorScheme` object.
     *
     * @param foreColor The foreground color as an ARGB hex value.
     * @param backColor The background color as an ARGB hex value.
     */
    constructor(foreColor: Int, backColor: Int) {
        this.foreColor = foreColor
        this.backColor = backColor
        setDefaultCursorColors()
    }

    /**
     * Creates a `ColorScheme` object.
     *
     * @param foreColor The foreground color as an ARGB hex value.
     * @param backColor The background color as an ARGB hex value.
     * @param cursorForeColor The cursor foreground color as an ARGB hex value.
     * @param cursorBackColor The cursor foreground color as an ARGB hex value.
     */
    constructor(foreColor: Int, backColor: Int, cursorForeColor: Int, cursorBackColor: Int) {
        this.foreColor = foreColor
        this.backColor = backColor
        this.cursorForeColor = cursorForeColor
        this.cursorBackColor = cursorBackColor
    }

    /**
     * Creates a `ColorScheme` object from an array.
     *
     * @param scheme An integer array `{ foreColor, backColor,
     * optionalCursorForeColor, optionalCursorBackColor }`.
     */
    constructor(scheme: IntArray) {
        val schemeLength = scheme.size
        require(!(schemeLength != 2 && schemeLength != 4))
        this.foreColor = scheme[0]
        this.backColor = scheme[1]
        if (schemeLength == 2) {
            setDefaultCursorColors()
        } else {
            this.cursorForeColor = scheme[2]
            this.cursorBackColor = scheme[3]
        }
    }

    companion object {
        private const val sDefaultCursorBackColor = -0x7f7f80

        private fun distance(a: Int, b: Int): Int {
            return (channelDistance(a, b, 0) * 3 + channelDistance(a, b, 1) * 5 + channelDistance(
                a,
                b,
                2
            ))
        }

        private fun channelDistance(a: Int, b: Int, channel: Int): Int {
            return abs((getChannel(a, channel) - getChannel(b, channel)).toDouble()).toInt()
        }

        private fun getChannel(color: Int, channel: Int): Int {
            return 0xff and (color shr ((2 - channel) * 8))
        }
    }
}