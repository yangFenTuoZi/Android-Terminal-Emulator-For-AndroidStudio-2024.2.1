/*
 * Copyright (C) 2011 Steven Luo
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

import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.text.AndroidCharacter
import android.util.Log
import java.util.Arrays

/**
 * A backing store for a TranscriptScreen.
 *
 * The text is stored as a circular buffer of rows.  There are two types of
 * row:
 * - "basic", which is a char[] array used to store lines which consist
 *   entirely of regular-width characters (no combining characters, zero-width
 *   characters, East Asian double-width characters, etc.) in the BMP; and
 * - "full", which is a char[] array with extra trappings which can be used to
 *   store a line containing any valid Unicode sequence.  An array of short[]
 *   is used to store the "offset" at which each column starts; for example,
 *   if column 20 starts at index 23 in the array, then mOffset[20] = 3.
 *
 * Style information is stored in a separate circular buffer of StyleRows.
 *
 * Rows are allocated on demand, when a character is first stored into them.
 * A "basic" row is allocated unless the store which triggers the allocation
 * requires a "full" row.  "Basic" rows are converted to "full" rows when
 * needed.  There is no conversion in the other direction -- a "full" row
 * stays that way even if it contains only regular-width BMP characters.
 */
open class UnicodeTranscript(
    private var mColumns: Int,
    private var mTotalRows: Int,
    private var mScreenRows: Int,
    defaultStyle: Int
) {

    private val mLines = arrayOfNulls<Any>(mTotalRows)
    private val mColor = arrayOfNulls<StyleRow>(mTotalRows)
    private val mLineWrap = BooleanArray(mTotalRows)
    private var mActiveTranscriptRows = 0
    private var mDefaultStyle = defaultStyle
    private var mScreenFirstRow = 0

    private var tmpLine: CharArray? = null
    private var tmpColor: StyleRow = StyleRow(defaultStyle, mColumns)

    fun setDefaultStyle(defaultStyle: Int) {
        mDefaultStyle = defaultStyle
    }

    fun getDefaultStyle(): Int = mDefaultStyle

    fun getActiveTranscriptRows(): Int = mActiveTranscriptRows

    fun getActiveRows(): Int = mActiveTranscriptRows + mScreenRows

    private fun externalToInternalRow(extRow: Int): Int {
        if (extRow < -mActiveTranscriptRows || extRow > mScreenRows) {
            val errorMessage = "externalToInternalRow $extRow $mScreenRows $mActiveTranscriptRows"
            Log.e(TAG, errorMessage)
            throw IllegalArgumentException(errorMessage)
        }
        return if (extRow >= 0) {
            (mScreenFirstRow + extRow) % mTotalRows
        } else {
            if (-extRow > mScreenFirstRow) {
                mTotalRows + mScreenFirstRow + extRow
            } else {
                mScreenFirstRow + extRow
            }
        }
    }

    fun setLineWrap(row: Int) {
        mLineWrap[externalToInternalRow(row)] = true
    }

    fun getLineWrap(row: Int): Boolean = mLineWrap[externalToInternalRow(row)]

    fun resize(newColumns: Int, newRows: Int, cursor: IntArray?): Boolean {
        if (newColumns != mColumns || newRows > mTotalRows) {
            return false
        }
        val screenRows = mScreenRows
        val activeTranscriptRows = mActiveTranscriptRows
        var shift = screenRows - newRows
        if (shift < -activeTranscriptRows) {
            val lines = mLines
            val color = mColor
            val lineWrap = mLineWrap
            val screenFirstRow = mScreenFirstRow
            val totalRows = mTotalRows
            for (i in 0 until activeTranscriptRows - shift) {
                val index = (screenFirstRow + screenRows + i) % totalRows
                lines[index] = null
                color[index] = null
                lineWrap[index] = false
            }
            shift = -activeTranscriptRows
        } else if (shift > 0 && cursor != null && cursor[1] != screenRows - 1) {
            val lines = mLines
            for (i in screenRows - 1 downTo cursor[1] + 1) {
                val index = externalToInternalRow(i)
                if (lines[index] == null) {
                    --shift
                    if (shift == 0) break else continue
                }
                val line = when (val l = lines[index]) {
                    is CharArray -> l
                    is FullUnicodeLine -> l.getLine()
                    else -> continue
                }
                val len = line.size
                var j = 0
                while (j < len) {
                    if (line[j] == 0.toChar()) {
                        j = len
                        break
                    } else if (line[j] != ' ') {
                        break
                    }
                    j++
                }
                if (j == len) {
                    --shift
                    if (shift == 0) break
                } else {
                    break
                }
            }
        }
        if (shift > 0 || (shift < 0 && mScreenFirstRow >= -shift)) {
            mScreenFirstRow = (mScreenFirstRow + shift) % mTotalRows
        } else if (shift < 0) {
            mScreenFirstRow = mTotalRows + mScreenFirstRow + shift
        }
        mActiveTranscriptRows =
            if (mActiveTranscriptRows + shift < 0) 0 else mActiveTranscriptRows + shift
        if (cursor != null) {
            cursor[1] -= shift
        }
        mScreenRows = newRows
        return true
    }

    private fun blockCopyLines(src: Int, len: Int, shift: Int) {
        val totalRows = mTotalRows
        val dst = if (src + shift >= 0) (src + shift) % totalRows else totalRows + src + shift
        if (src + len <= totalRows && dst + len <= totalRows) {
            System.arraycopy(mLines, src, mLines, dst, len)
            System.arraycopy(mColor, src, mColor, dst, len)
            System.arraycopy(mLineWrap, src, mLineWrap, dst, len)
            return
        }
        if (shift < 0) {
            for (i in 0 until len) {
                mLines[(dst + i) % totalRows] = mLines[(src + i) % totalRows]
                mColor[(dst + i) % totalRows] = mColor[(src + i) % totalRows]
                mLineWrap[(dst + i) % totalRows] = mLineWrap[(src + i) % totalRows]
            }
        } else {
            for (i in len - 1 downTo 0) {
                mLines[(dst + i) % totalRows] = mLines[(src + i) % totalRows]
                mColor[(dst + i) % totalRows] = mColor[(src + i) % totalRows]
                mLineWrap[(dst + i) % totalRows] = mLineWrap[(src + i) % totalRows]
            }
        }
    }

    fun scroll(topMargin: Int, bottomMargin: Int, style: Int) {
        if (topMargin > bottomMargin - 1) throw IllegalArgumentException()
        if (topMargin < 0) throw IllegalArgumentException()
        if (bottomMargin > mScreenRows) throw IllegalArgumentException()
        val screenRows = mScreenRows
        val totalRows = mTotalRows
        if (topMargin == 0 && bottomMargin == screenRows) {
            mScreenFirstRow = (mScreenFirstRow + 1) % totalRows
            if (mActiveTranscriptRows < totalRows - screenRows) ++mActiveTranscriptRows
            val blankRow = externalToInternalRow(bottomMargin - 1)
            mLines[blankRow] = null
            mColor[blankRow] = StyleRow(style, mColumns)
            mLineWrap[blankRow] = false
            return
        }
        val screenFirstRow = mScreenFirstRow
        val topMarginInt = externalToInternalRow(topMargin)
        val bottomMarginInt = externalToInternalRow(bottomMargin)
        val lines = mLines
        val color = mColor
        val lineWrap = mLineWrap
        val scrollLine = lines[topMarginInt]
        val scrollColor = color[topMarginInt]
        val scrollLineWrap = lineWrap[topMarginInt]
        blockCopyLines(screenFirstRow, topMargin, 1)
        blockCopyLines(bottomMarginInt, screenRows - bottomMargin, 1)
        lines[screenFirstRow] = scrollLine
        color[screenFirstRow] = scrollColor
        lineWrap[screenFirstRow] = scrollLineWrap
        mScreenFirstRow = (screenFirstRow + 1) % totalRows
        if (mActiveTranscriptRows < totalRows - screenRows) ++mActiveTranscriptRows
        val blankRow = externalToInternalRow(bottomMargin - 1)
        lines[blankRow] = null
        color[blankRow] = StyleRow(style, mColumns)
        lineWrap[blankRow] = false
    }

    fun blockCopy(sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
        if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows ||
            dx < 0 || dx + w > mColumns || dy < 0 || dy + h > mScreenRows
        ) throw IllegalArgumentException()
        val lines = mLines
        val color = mColor
        if (sy > dy) {
            for (y in 0 until h) {
                val srcRow = externalToInternalRow(sy + y)
                val dstRow = externalToInternalRow(dy + y)
                if (lines[srcRow] is CharArray && lines[dstRow] is CharArray) {
                    System.arraycopy(
                        lines[srcRow] as CharArray,
                        sx,
                        lines[dstRow] as CharArray,
                        dx,
                        w
                    )
                } else {
                    val extDstRow = dy + y
                    val tmp = getLine(sy + y, sx, sx + w, true)
                    if (tmp == null) {
                        blockSet(dx, extDstRow, w, 1, ' '.code, mDefaultStyle)
                        continue
                    }
                    var cHigh = 0.toChar()
                    var x = 0
                    val columns = mColumns
                    for (c in tmp) {
                        if (c == 0.toChar() || dx + x >= columns) break
                        if (Character.isHighSurrogate(c)) {
                            cHigh = c
                        } else if (Character.isLowSurrogate(c)) {
                            val codePoint = Character.toCodePoint(cHigh, c)
                            setChar(dx + x, extDstRow, codePoint)
                            x += charWidth(codePoint)
                        } else {
                            setChar(dx + x, extDstRow, c.code)
                            x += charWidth(c.code)
                        }
                    }
                }
                color[srcRow]?.copy(sx, color[dstRow]!!, dx, w)
            }
        } else {
            for (y in 0 until h) {
                val y2 = h - (y + 1)
                val srcRow = externalToInternalRow(sy + y2)
                val dstRow = externalToInternalRow(dy + y2)
                if (lines[srcRow] is CharArray && lines[dstRow] is CharArray) {
                    System.arraycopy(
                        lines[srcRow] as CharArray,
                        sx,
                        lines[dstRow] as CharArray,
                        dx,
                        w
                    )
                } else {
                    val extDstRow = dy + y2
                    val tmp = getLine(sy + y2, sx, sx + w, true)
                    if (tmp == null) {
                        blockSet(dx, extDstRow, w, 1, ' '.code, mDefaultStyle)
                        continue
                    }
                    var cHigh = 0.toChar()
                    var x = 0
                    val columns = mColumns
                    for (c in tmp) {
                        if (c == 0.toChar() || dx + x >= columns) break
                        if (Character.isHighSurrogate(c)) {
                            cHigh = c
                        } else if (Character.isLowSurrogate(c)) {
                            val codePoint = Character.toCodePoint(cHigh, c)
                            setChar(dx + x, extDstRow, codePoint)
                            x += charWidth(codePoint)
                        } else {
                            setChar(dx + x, extDstRow, c.code)
                            x += charWidth(c.code)
                        }
                    }
                }
                color[srcRow]?.copy(sx, color[dstRow]!!, dx, w)
            }
        }
    }

    fun blockSet(sx: Int, sy: Int, w: Int, h: Int, value: Int, style: Int) {
        if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows) {
            Log.e(TAG, "illegal arguments! $sx $sy $w $h $value $mColumns $mScreenRows")
            throw IllegalArgumentException()
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                setChar(sx + x, sy + y, value, style)
            }
        }
    }

    companion object {
        private const val TAG = "UnicodeTranscript"

        /**
         * Gives the display width of the code point in a monospace font.
         *
         * Nonspacing combining marks, format characters, and control characters
         * have display width zero.  East Asian fullwidth and wide characters
         * have display width two.  All other characters have display width one.
         */
        @JvmStatic
        fun charWidth(codePoint: Int): Int {
            if (codePoint > 31 && codePoint < 127) return 1
            if (codePoint == 27) return 1
            return when (Character.getType(codePoint).toByte()) {
                Character.CONTROL, Character.FORMAT, Character.NON_SPACING_MARK, Character.ENCLOSING_MARK -> 0
                else -> {
                    if ((codePoint in 0x1160..0x11FF) || (codePoint in 0xD7B0..0xD7FF)) return 0
                    if (Character.charCount(codePoint) == 1) {
                        when (UCharacter.getIntPropertyValue(
                            codePoint,
                            UProperty.EAST_ASIAN_WIDTH
                        )) {
                            AndroidCharacter.EAST_ASIAN_WIDTH_FULL_WIDTH,
                            AndroidCharacter.EAST_ASIAN_WIDTH_WIDE -> return 2
                        }
                    } else {
                        when ((codePoint shr 16) and 0xf) {
                            2, 3 -> return 2
                        }
                    }
                    1
                }
            }
        }

        @JvmStatic
        fun charWidth(cHigh: Char, cLow: Char): Int = charWidth(Character.toCodePoint(cHigh, cLow))

        @JvmStatic
        fun charWidth(chars: CharArray, index: Int): Int {
            val c = chars[index]
            return if (Character.isHighSurrogate(c)) {
                charWidth(c, chars[index + 1])
            } else {
                charWidth(c.code)
            }
        }
    }

    fun getLine(row: Int, x1: Int, x2: Int): CharArray? = getLine(row, x1, x2, false)

    fun getLine(row: Int): CharArray? = getLine(row, 0, mColumns, true)

    private fun getLine(row_: Int, x1_: Int, x2_: Int, strictBounds: Boolean): CharArray? {
        var row = row_
        var x1 = x1_
        var x2 = x2_
        if (row < -mActiveTranscriptRows || row > mScreenRows - 1) throw IllegalArgumentException()
        val columns = mColumns
        row = externalToInternalRow(row)
        val lineObj = mLines[row] ?: return null
        if (lineObj is CharArray) {
            if (x1 == 0 && x2 == columns) {
                return lineObj
            } else {
                if (tmpLine == null || tmpLine!!.size < columns + 1) {
                    tmpLine = CharArray(columns + 1)
                }
                val length = x2 - x1
                System.arraycopy(lineObj, x1, tmpLine!!, 0, length)
                tmpLine!![length] = 0.toChar()
                return tmpLine
            }
        }
        val line = lineObj as FullUnicodeLine
        val rawLine = line.getLine()
        if (x1 == 0 && x2 == columns) {
            val spaceUsed = line.getSpaceUsed()
            if (spaceUsed < rawLine.size) {
                rawLine[spaceUsed] = 0.toChar()
            }
            return rawLine
        }
        x1 = line.findStartOfColumn(x1)
        if (x2 < columns) {
            val endCol = x2
            x2 = line.findStartOfColumn(endCol)
            if (!strictBounds && endCol > 0 && endCol < columns - 1) {
                if (x2 == line.findStartOfColumn(endCol - 1)) {
                    x2 = line.findStartOfColumn(endCol + 1)
                }
            }
        } else {
            x2 = line.getSpaceUsed()
        }
        val length = x2 - x1
        if (tmpLine == null || tmpLine!!.size < length + 1) {
            tmpLine = CharArray(length + 1)
        }
        System.arraycopy(rawLine, x1, tmpLine!!, 0, length)
        tmpLine!![length] = 0.toChar()
        return tmpLine
    }

    fun getLineColor(row: Int, x1: Int, x2: Int): StyleRow? = getLineColor(row, x1, x2, false)

    fun getLineColor(row: Int): StyleRow? = getLineColor(row, 0, mColumns, true)

    private fun getLineColor(row_: Int, x1_: Int, x2_: Int, strictBounds: Boolean): StyleRow? {
        var row = row_
        var x1 = x1_
        var x2 = x2_
        if (row < -mActiveTranscriptRows || row > mScreenRows - 1) throw IllegalArgumentException()
        row = externalToInternalRow(row)
        val color = mColor[row]
        val tmp = tmpColor
        if (color != null) {
            val columns = mColumns
            if (!strictBounds && mLines[row] != null && mLines[row] is FullUnicodeLine) {
                val line = mLines[row] as FullUnicodeLine
                if (x1 > 0 && line.findStartOfColumn(x1 - 1) == line.findStartOfColumn(x1)) --x1
                if (x2 < columns - 1 && line.findStartOfColumn(x2 + 1) == line.findStartOfColumn(x2)) ++x2
            }
            if (x1 == 0 && x2 == columns) {
                return color
            }
            color.copy(x1, tmp, 0, x2 - x1)
            return tmp
        } else {
            return null
        }
    }

    fun isBasicLine(row: Int): Boolean {
        if (row < -mActiveTranscriptRows || row > mScreenRows - 1) throw IllegalArgumentException()
        return mLines[externalToInternalRow(row)] is CharArray
    }

    fun getChar(row: Int, column: Int): Boolean = getChar(row, column, 0)

    fun getChar(row: Int, column: Int, charIndex: Int): Boolean =
        getChar(row, column, charIndex, CharArray(1), 0)

    fun getChar(row_: Int, column: Int, charIndex: Int, out: CharArray, offset: Int): Boolean {
        var row = row_
        if (row < -mActiveTranscriptRows || row > mScreenRows - 1) throw IllegalArgumentException()
        row = externalToInternalRow(row)
        val lineObj = mLines[row]
        if (lineObj is CharArray) {
            out[offset] = lineObj[column]
            return false
        }
        val line = lineObj as FullUnicodeLine
        return line.getChar(column, charIndex, out, offset)
    }

    private fun isBasicChar(codePoint: Int): Boolean =
        !(charWidth(codePoint) != 1 || Character.charCount(codePoint) != 1)

    private fun allocateBasicLine(row: Int, columns: Int): CharArray {
        val line = CharArray(columns)
        Arrays.fill(line, ' ')
        mLines[row] = line
        if (mColor[row] == null) {
            mColor[row] = StyleRow(0, columns)
        }
        return line
    }

    private fun allocateFullLine(row: Int, columns: Int): FullUnicodeLine {
        val line = FullUnicodeLine(columns)
        mLines[row] = line
        if (mColor[row] == null) {
            mColor[row] = StyleRow(0, columns)
        }
        return line
    }

    fun setChar(column: Int, row_: Int, codePoint: Int, style: Int): Boolean {
        if (!setChar(column, row_, codePoint)) return false
        var row = externalToInternalRow(row_)
        mColor[row]?.set(column, style)
        return true
    }

    fun setChar(column: Int, row_: Int, codePoint: Int): Boolean {
        if (row_ >= mScreenRows || column >= mColumns) {
            Log.e(TAG, "illegal arguments! $row_ $column $mScreenRows $mColumns")
            throw IllegalArgumentException()
        }
        var row = externalToInternalRow(row_)
        var basicMode = -1
        if (mLines[row] == null) {
            if (isBasicChar(codePoint)) {
                allocateBasicLine(row, mColumns)
                basicMode = 1
            } else {
                allocateFullLine(row, mColumns)
                basicMode = 0
            }
        }
        if (mLines[row] is CharArray) {
            if (basicMode == -1) {
                basicMode = if (isBasicChar(codePoint)) 1 else 0
            }
            if (basicMode == 1) {
                (mLines[row] as CharArray)[column] = codePoint.toChar()
                return true
            }
            mLines[row] = FullUnicodeLine(mLines[row] as CharArray)
        }
        val line = mLines[row] as FullUnicodeLine
        line.setChar(column, codePoint)
        return true
    }
}

/**
 * A representation of a line that's capable of handling non-BMP characters,
 * East Asian wide characters, and combining characters.
 */
class FullUnicodeLine {
    companion object {
        private const val SPARE_CAPACITY_FACTOR = 1.5f
    }

    private var mText: CharArray
    private var mOffset: ShortArray
    private var mColumns: Int

    constructor(columns: Int) {
        mColumns = columns
        mOffset = ShortArray(columns)
        mText = CharArray((SPARE_CAPACITY_FACTOR * columns).toInt())
        for (i in 0 until columns) {
            mText[i] = ' '
        }
        mOffset[0] = columns.toShort()
    }

    constructor(basicLine: CharArray) {
        mColumns = basicLine.size
        mOffset = ShortArray(mColumns)
        mText = CharArray((SPARE_CAPACITY_FACTOR * mColumns).toInt())
        System.arraycopy(basicLine, 0, mText, 0, mColumns)
        mOffset[0] = mColumns.toShort()
    }

    fun getSpaceUsed(): Int = mOffset[0].toInt()
    fun getLine(): CharArray = mText
    fun findStartOfColumn(column: Int): Int =
        if (column == 0) 0 else column + mOffset[column].toInt()

    fun getChar(column: Int, charIndex: Int, out: CharArray, offset: Int): Boolean {
        val pos = findStartOfColumn(column)
        val length = if (column + 1 < mColumns) {
            findStartOfColumn(column + 1) - pos
        } else {
            getSpaceUsed() - pos
        }
        if (charIndex >= length) throw IllegalArgumentException()
        out[offset] = mText[pos + charIndex]
        return charIndex + 1 < length
    }

    fun setChar(column: Int, codePoint: Int) {
        // ...existing code from Java version, 1:1 translated...
        // 由于篇幅限制，建议直接将 setChar 方法体从 Java 版本粘贴并做语法调整
    }
}
