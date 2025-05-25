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
import jackpal.androidterm.emulatorview.UnicodeTranscript.Companion.charWidth
import java.util.Arrays
import kotlin.math.min

/**
 * A TranscriptScreen is a screen that remembers data that's been scrolled. The
 * old data is stored in a ring buffer to minimize the amount of copying that
 * needs to be done. The transcript does its own drawing, to avoid having to
 * expose its internal data structures.
 */
class TranscriptScreen(
    columns: Int, totalRows: Int, screenRows: Int,
    scheme: ColorScheme?
) : Screen {
    /**
     * The width of the transcript, in characters. Fixed at initialization.
     */
    private var mColumns = 0

    /**
     * The total number of rows in the transcript and the screen. Fixed at
     * initialization.
     */
    private var mTotalRows = 0

    /**
     * The number of rows in the screen.
     */
    private var mScreenRows = 0

    private var mData: UnicodeTranscript? = null

    /**
     * Create a transcript screen.
     *
     * @param columns the width of the screen in characters.
     * @param totalRows the height of the entire text area, in rows of text.
     * @param screenRows the height of just the screen, not including the
     * transcript that holds lines that have scrolled off the top of the
     * screen.
     */
    init {
        init(columns, totalRows, screenRows, TextStyle.kNormalTextStyle)
    }

    private fun init(columns: Int, totalRows: Int, screenRows: Int, style: Int) {
        mColumns = columns
        mTotalRows = totalRows
        mScreenRows = screenRows

        mData = UnicodeTranscript(columns, totalRows, screenRows, style)
        mData!!.blockSet(0, 0, mColumns, mScreenRows, ' '.code, style)
    }

    fun setColorScheme(scheme: ColorScheme?) {
        mData!!.setDefaultStyle(TextStyle.kNormalTextStyle)
    }

    fun finish() {
        /*
         * The Android InputMethodService will sometimes hold a reference to
         * us for a while after the activity closes, which is expensive because
         * it means holding on to the now-useless mData array.  Explicitly
         * get rid of our references to this data to help keep the amount of
         * memory being leaked down.
         */
        mData = null
    }

    override fun setLineWrap(row: Int) {
        mData!!.setLineWrap(row)
    }

    /**
     * Store a Unicode code point into the screen at location (x, y)
     *
     * @param x X coordinate (also known as column)
     * @param y Y coordinate (also known as row)
     * @param codePoint Unicode codepoint to store
     * @param foreColor the foreground color
     * @param backColor the background color
     */
    override fun set(x: Int, y: Int, codePoint: Int, style: Int) {
        mData!!.setChar(x, y, codePoint, style)
    }

    override fun set(x: Int, y: Int, b: Byte, style: Int) {
        mData!!.setChar(x, y, b.toInt(), style)
    }

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line
     * screen, the arguments would be (0, 24).
     *
     * @param topMargin First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     * @param style the style for the newly exposed line.
     */
    override fun scroll(topMargin: Int, bottomMargin: Int, style: Int) {
        mData!!.scroll(topMargin, bottomMargin, style)
    }

    /**
     * Block copy characters from one position in the screen to another. The two
     * positions can overlap. All characters of the source and destination must
     * be within the bounds of the screen, or else an InvalidParemeterException
     * will be thrown.
     *
     * @param sx source X coordinate
     * @param sy source Y coordinate
     * @param w width
     * @param h height
     * @param dx destination X coordinate
     * @param dy destination Y coordinate
     */
    override fun blockCopy(sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
        mData!!.blockCopy(sx, sy, w, h, dx, dy)
    }

    /**
     * Block set characters. All characters must be within the bounds of the
     * screen, or else and InvalidParemeterException will be thrown. Typically
     * this is called with a "val" argument of 32 to clear a block of
     * characters.
     *
     * @param sx source X
     * @param sy source Y
     * @param w width
     * @param h height
     * @param val value to set.
     */
    override fun blockSet(
        sx: Int, sy: Int, w: Int, h: Int, `val`: Int,
        style: Int
    ) {
        mData!!.blockSet(sx, sy, w, h, `val`, style)
    }

    /**
     * Draw a row of text. Out-of-bounds rows are blank, not errors.
     *
     * @param row The row of text to draw.
     * @param canvas The canvas to draw to.
     * @param x The x coordinate origin of the drawing
     * @param y The y coordinate origin of the drawing
     * @param renderer The renderer to use to draw the text
     * @param cx the cursor X coordinate, -1 means don't draw it
     * @param selx1 the text selection start X coordinate
     * @param selx2 the text selection end X coordinate, if equals to selx1 don't draw selection
     * @param imeText current IME text, to be rendered at cursor
     * @param cursorMode the cursor mode. See TextRenderer.
     */
    fun drawText(
        row: Int, canvas: Canvas, x: Float, y: Float,
        renderer: TextRenderer, cx: Int, selx1: Int, selx2: Int, imeText: String, cursorMode: Int
    ) {
        val line: CharArray?
        val color: StyleRow?
        var cursorWidth = 1
        try {
            line = mData!!.getLine(row)
            color = mData!!.getLineColor(row)
        } catch (e: IllegalArgumentException) {
            // Out-of-bounds rows are blank.
            return
        } catch (e: NullPointerException) {
            // Attempt to draw on a finished transcript
            // XXX Figure out why this happens on Honeycomb
            return
        }
        val defaultStyle = mData!!.getDefaultStyle()

        if (line == null) {
            // Line is blank.
            if (selx1 != selx2) {
                // We need to draw a selection
                val blank = CharArray(selx2 - selx1)
                Arrays.fill(blank, ' ')
                renderer.drawTextRun(
                    canvas, x, y, selx1, selx2 - selx1,
                    blank, 0, 1, true, defaultStyle,
                    cx, 0, 1, 1, cursorMode
                )
            }
            if (cx != -1) {
                val blank = CharArray(1)
                Arrays.fill(blank, ' ')
                // We need to draw the cursor
                renderer.drawTextRun(
                    canvas, x, y, cx, 1,
                    blank, 0, 1, true, defaultStyle,
                    cx, 0, 1, 1, cursorMode
                )
            }

            return
        }

        val columns = mColumns
        val lineLen = line.size
        var lastStyle = 0
        var lastSelectionStyle = false
        var runWidth = 0
        var lastRunStart = -1
        var lastRunStartIndex = -1
        var forceFlushRun = false
        var column = 0
        var nextColumn = 0
        var displayCharWidth = 0
        var index = 0
        var cursorIndex = 0
        var cursorIncr = 0
        while (column < columns && index < lineLen && line[index] != '\u0000') {
            var incr = 1
            val width: Int
            if (Character.isHighSurrogate(line[index])) {
                width = charWidth(line, index)
                incr++
            } else {
                width = charWidth(line[index].code)
            }
            if (width > 0) {
                // We've moved on to the next column
                column = nextColumn
                displayCharWidth = width
            }
            val style = color!!.get(column)
            var selectionStyle = false
            if ((column >= selx1 || (displayCharWidth == 2 && column == selx1 - 1)) &&
                column <= selx2
            ) {
                // Draw selection:
                selectionStyle = true
            }
            if (style != lastStyle || selectionStyle != lastSelectionStyle || (width > 0 && forceFlushRun)) {
                if (lastRunStart >= 0) {
                    renderer.drawTextRun(
                        canvas, x, y, lastRunStart, runWidth,
                        line,
                        lastRunStartIndex, index - lastRunStartIndex,
                        lastSelectionStyle, lastStyle,
                        cx, cursorIndex, cursorIncr, cursorWidth, cursorMode
                    )
                }
                lastStyle = style
                lastSelectionStyle = selectionStyle
                runWidth = 0
                lastRunStart = column
                lastRunStartIndex = index
                forceFlushRun = false
            }
            if (cx == column) {
                if (width > 0) {
                    cursorIndex = index
                    cursorIncr = incr
                    cursorWidth = width
                } else {
                    // Combining char attaching to the char under the cursor
                    cursorIncr += incr
                }
            }
            runWidth += width
            nextColumn += width
            index += incr
            if (width > 1) {
                /* We cannot draw two or more East Asian wide characters in the
                   same run, because we need to make each wide character take
                   up two columns, which may not match the font's idea of the
                   character width */
                forceFlushRun = true
            }
        }
        if (lastRunStart >= 0) {
            renderer.drawTextRun(
                canvas, x, y, lastRunStart, runWidth,
                line,
                lastRunStartIndex, index - lastRunStartIndex,
                lastSelectionStyle, lastStyle,
                cx, cursorIndex, cursorIncr, cursorWidth, cursorMode
            )
        }

        if (cx >= 0 && !imeText.isEmpty()) {
            val imeLength = min(columns.toDouble(), imeText.length.toDouble()).toInt()
            val imeOffset = imeText.length - imeLength
            val imePosition = min(cx.toDouble(), (columns - imeLength).toDouble()).toInt()
            renderer.drawTextRun(
                canvas, x, y, imePosition, imeLength, imeText.toCharArray(),
                imeOffset, imeLength, true, TextStyle.encode(0x0f, 0x00, TextStyle.fxNormal),
                -1, 0, 0, 0, 0
            )
        }
    }

    override val activeRows: Int
        /**
         * Get the count of active rows.
         *
         * @return the count of active rows.
         */
        get() = mData!!.getActiveRows()

    val activeTranscriptRows: Int
        /**
         * Get the count of active transcript rows.
         *
         * @return the count of active transcript rows.
         */
        get() = mData!!.getActiveTranscriptRows()

    override val transcriptText: String
        get() = internalGetTranscriptText(
            null,
            0,
            -mData!!.getActiveTranscriptRows(),
            mColumns,
            mScreenRows
        )

    override fun getTranscriptText(colors: GrowableIntArray?): String {
        return internalGetTranscriptText(
            colors,
            0,
            -mData!!.getActiveTranscriptRows(),
            mColumns,
            mScreenRows
        )
    }

    override fun getSelectedText(selX1: Int, selY1: Int, selX2: Int, selY2: Int): String {
        return internalGetTranscriptText(null, selX1, selY1, selX2, selY2)
    }

    override fun getSelectedText(
        colors: GrowableIntArray?,
        selX1: Int,
        selY1: Int,
        selX2: Int,
        selY2: Int
    ): String {
        return internalGetTranscriptText(colors, selX1, selY1, selX2, selY2)
    }

    private fun internalGetTranscriptText(
        colors: GrowableIntArray?,
        selX1: Int,
        selY1: Int,
        selX2: Int,
        selY2: Int
    ): String {
        var selY1 = selY1
        var selY2 = selY2
        val builder = StringBuilder()
        val data = mData
        val columns = mColumns
        var line: CharArray?
        var rowColorBuffer: StyleRow? = null
        if (selY1 < -data!!.getActiveTranscriptRows()) {
            selY1 = -data.getActiveTranscriptRows()
        }
        if (selY2 >= mScreenRows) {
            selY2 = mScreenRows - 1
        }
        for (row in selY1..selY2) {
            var x1 = 0
            var x2: Int
            if (row == selY1) {
                x1 = selX1
            }
            if (row == selY2) {
                x2 = selX2 + 1
                if (x2 > columns) {
                    x2 = columns
                }
            } else {
                x2 = columns
            }
            line = data.getLine(row, x1, x2)
            if (colors != null) {
                rowColorBuffer = data.getLineColor(row, x1, x2)
            }
            if (line == null) {
                if (!data.getLineWrap(row) && row < selY2 && row < mScreenRows - 1) {
                    builder.append('\n')
                    colors?.append(0)
                }
                continue
            }
            val defaultColor = mData!!.getDefaultStyle()
            var lastPrintingChar = -1
            val lineLen = line.size
            var column = 0
            var i = 0
            while (i < lineLen) {
                val c = line[i]
                if (c.code == 0) {
                    break
                }

                var style = defaultColor
                try {
                    if (rowColorBuffer != null) {
                        style = rowColorBuffer.get(column)
                    }
                } catch (e: ArrayIndexOutOfBoundsException) {
                    // XXX This probably shouldn't happen ...
                }

                if (c != ' ' || style != defaultColor) {
                    lastPrintingChar = i
                }
                if (!Character.isLowSurrogate(c)) {
                    column += charWidth(line, i)
                }
                ++i
            }
            if (data.getLineWrap(row) && lastPrintingChar > -1 && x2 == columns) {
                // If the line was wrapped, we shouldn't lose trailing space
                lastPrintingChar = i - 1
            }
            builder.append(line, 0, lastPrintingChar + 1)
            if (colors != null) {
                if (rowColorBuffer != null) {
                    column = 0
                    var j = 0
                    while (j <= lastPrintingChar) {
                        colors.append(rowColorBuffer.get(column))
                        column += charWidth(line, j)
                        if (Character.isHighSurrogate(line[j])) {
                            ++j
                        }
                        ++j
                    }
                } else {
                    var j = 0
                    while (j <= lastPrintingChar) {
                        colors.append(defaultColor)
                        val c = line[j]
                        if (Character.isHighSurrogate(c)) {
                            ++j
                        }
                        ++j
                    }
                }
            }
            if (!data.getLineWrap(row) && row < selY2 && row < mScreenRows - 1) {
                builder.append('\n')
                colors?.append(0.toChar().code)
            }
        }
        return builder.toString()
    }

    override fun fastResize(columns: Int, rows: Int, cursor: IntArray?): Boolean {
        if (mData == null) {
            // XXX Trying to resize a finished TranscriptScreen?
            return true
        }
        if (mData!!.resize(columns, rows, cursor)) {
            mColumns = columns
            mScreenRows = rows
            return true
        } else {
            return false
        }
    }

    override fun resize(columns: Int, rows: Int, style: Int) {
        // Ensure backing store will be large enough to hold the whole screen 
        if (rows > mTotalRows) {
            mTotalRows = rows
        }
        init(columns, mTotalRows, rows, style)
    }

    /**
     *
     * Return the UnicodeTranscript line at this row index.
     * @param row The row index to be queried
     * @return The line of text at this row index
     */
    fun getScriptLine(row: Int): CharArray? {
        return try {
            mData!!.getLine(row)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: NullPointerException) {
            null
        }
    }

    /**
     * Get the line wrap status of the row provided.
     * @param row The row to check for line-wrap status
     * @return The line wrap status of the row provided
     */
    fun getScriptLineWrap(row: Int): Boolean {
        return mData!!.getLineWrap(row)
    }

    /**
     * Get whether the line at this index is "basic" (contains only BMP
     * characters of width 1).
     */
    fun isBasicLine(row: Int): Boolean {
        return if (mData != null) {
            mData!!.isBasicLine(row)
        } else {
            true
        }
    }
}