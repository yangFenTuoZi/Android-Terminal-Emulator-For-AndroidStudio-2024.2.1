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
package jackpal.androidterm.emulatorview

import android.graphics.Canvas

/**
 * Text renderer interface
 */
interface TextRenderer {
    fun setReverseVideo(reverseVideo: Boolean)
    val characterWidth: Float
    val characterHeight: Int

    /** @return pixels above top row of text to avoid looking cramped.
     */
    val topMargin: Int

    /**
     * Draw a run of text
     * @param canvas The canvas to draw into.
     * @param x Canvas coordinate of the left edge of the whole line.
     * @param y Canvas coordinate of the bottom edge of the whole line.
     * @param lineOffset The screen character offset of this text run (0..length of line)
     * @param runWidth
     * @param text
     * @param index
     * @param count
     * @param selectionStyle True to draw the text using the "selected" style (for clipboard copy)
     * @param textStyle
     * @param cursorOffset The screen character offset of the cursor (or -1 if not on this line.)
     * @param cursorIndex The index of the cursor in text chars.
     * @param cursorIncr The width of the cursor in text chars. (1 or 2)
     * @param cursorWidth The width of the cursor in screen columns (1 or 2)
     * @param cursorMode The cursor mode (used to show state of shift/control/alt/fn locks.
     */
    fun drawTextRun(
        canvas: Canvas, x: Float, y: Float,
        lineOffset: Int, runWidth: Int, text: CharArray, index: Int, count: Int,
        selectionStyle: Boolean, textStyle: Int,
        cursorOffset: Int, cursorIndex: Int, cursorIncr: Int, cursorWidth: Int, cursorMode: Int
    )

    companion object {
        const val MODE_OFF: Int = 0
        const val MODE_ON: Int = 1
        const val MODE_LOCKED: Int = 2
        const val MODE_MASK: Int = 3

        const val MODE_SHIFT_SHIFT: Int = 0
        const val MODE_ALT_SHIFT: Int = 2
        const val MODE_CTRL_SHIFT: Int = 4
        const val MODE_FN_SHIFT: Int = 6
    }
}