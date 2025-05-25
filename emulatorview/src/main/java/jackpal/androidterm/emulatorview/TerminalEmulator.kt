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

import android.util.Log
import jackpal.androidterm.emulatorview.EmulatorDebug.bytesToString
import jackpal.androidterm.emulatorview.UnicodeTranscript.Companion.charWidth
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Renders text into a screen. Contains all the terminal-specific knowledge and
 * state. Emulates a subset of the X Window System xterm terminal, which in turn
 * is an emulator for a subset of the Digital Equipment Corporation vt100
 * terminal. Missing functionality: text attributes (bold, underline, reverse
 * video, color) alternate screen cursor key and keypad escape sequences.
 */
class TerminalEmulator(
    /**
     * The terminal session this emulator is bound to.
     */
    private val mSession: TermSession,
    /**
     * Stores the characters that appear on the screen of the emulated terminal.
     */
    private val mMainBuffer: TranscriptScreen, columns: Int, rows: Int, scheme: ColorScheme?
) {
    fun setKeyListener(l: TermKeyListener) {
        mKeyListener = l
    }

    private var mKeyListener: TermKeyListener? = null

    /**
     * The cursor row. Numbered 0..mRows-1.
     */
    private var mCursorRow = 0

    /**
     * The cursor column. Numbered 0..mColumns-1.
     */
    private var mCursorCol = 0

    /**
     * The number of character rows in the terminal screen.
     */
    private var mRows: Int

    /**
     * The number of character columns in the terminal screen.
     */
    private var mColumns: Int

    private var mAltBuffer: TranscriptScreen?
    var screen: TranscriptScreen?
        private set

    /**
     * Keeps track of the current argument of the current escape sequence.
     * Ranges from 0 to MAX_ESCAPE_PARAMETERS-1. (Typically just 0 or 1.)
     */
    private var mArgIndex = 0

    /**
     * Holds the arguments of the current escape sequence.
     */
    private val mArgs = IntArray(MAX_ESCAPE_PARAMETERS)

    /**
     * Holds OSC arguments, which can be strings.
     */
    private val mOSCArg = ByteArray(MAX_OSC_STRING_LENGTH)

    private var mOSCArgLength = 0

    private var mOSCArgTokenizerIndex = 0

    /**
     * True if the current escape sequence should continue, false if the current
     * escape sequence should be terminated. Used when parsing a single
     * character.
     */
    private var mContinueSequence = false

    /**
     * The current state of the escape sequence state machine.
     */
    private var mEscapeState = 0

    /**
     * Saved state of the cursor row, Used to implement the save/restore cursor
     * position escape sequences.
     */
    private var mSavedCursorRow = 0

    /**
     * Saved state of the cursor column, Used to implement the save/restore
     * cursor position escape sequences.
     */
    private var mSavedCursorCol = 0

    private var mSavedEffect = 0

    private var mSavedDecFlags_DECSC_DECRC = 0


    /**
     * Holds multiple DECSET flags. The data is stored this way, rather than in
     * separate booleans, to make it easier to implement the save-and-restore
     * semantics. The various k*ModeMask masks can be used to extract and modify
     * the individual flags current states.
     */
    private var mDecFlags = 0

    /**
     * Saves away a snapshot of the DECSET flags. Used to implement save and
     * restore escape sequences.
     */
    private var mSavedDecFlags = 0

    /**
     * Get the current DECSET mouse tracking mode, zero for no mouse tracking.
     *
     * @return the current DECSET mouse tracking mode.
     */
    /**
     * The current DECSET mouse tracking mode, zero for no mouse tracking.
     */
    var mouseTrackingMode: Int = 0
        private set

    // Modes set with Set Mode / Reset Mode
    /**
     * True if insert mode (as opposed to replace mode) is active. In insert
     * mode new characters are inserted, pushing existing text to the right.
     */
    private var mInsertMode = false

    /**
     * An array of tab stops. mTabStop[i] is true if there is a tab stop set for
     * column i.
     */
    private var mTabStop: BooleanArray

    // The margins allow portions of the screen to be locked.
    /**
     * The top margin of the screen, for scrolling purposes. Ranges from 0 to
     * mRows-2.
     */
    private var mTopMargin = 0

    /**
     * The bottom margin of the screen, for scrolling purposes. Ranges from
     * mTopMargin + 2 to mRows. (Defines the first row after the scrolling
     * region.
     */
    private var mBottomMargin = 0

    /**
     * True if the next character to be emitted will be automatically wrapped to
     * the next line. Used to disambiguate the case where the cursor is
     * positioned on column mColumns-1.
     */
    private var mAboutToAutoWrap = false

    /**
     * The width of the last emitted spacing character.  Used to place
     * combining characters into the correct column.
     */
    private var mLastEmittedCharWidth = 0

    /**
     * True if we just auto-wrapped and no character has been emitted on this
     * line yet.  Used to ensure combining characters following a character
     * at the edge of the screen are stored in the proper place.
     */
    private var mJustWrapped = false

    /**
     * Used for debugging, counts how many chars have been processed.
     */
    private var mProcessedCharCount = 0

    /**
     * Foreground color, 0..255
     */
    private var foreColor = 0
    private var mDefaultForeColor = 0

    /**
     * Background color, 0..255
     */
    private var backColor = 0
    private var mDefaultBackColor = 0

    /**
     * Current TextStyle effect
     */
    private var effect = 0

    var keypadApplicationMode: Boolean = false
        private set

    /** false == G0, true == G1  */
    private var mAlternateCharSet = false

    /** What is the current graphics character set. [0] == G0, [1] == G1  */
    private val mCharSet = IntArray(2)

    /** Derived from mAlternateCharSet and mCharSet.
     * True if we're supposed to be drawing the special graphics.
     */
    private var mUseAlternateCharSet = false

    /**
     * Used for moving selection up along with the scrolling text
     */
    var scrollCounter: Int = 0
        private set

    private var mDefaultUTF8Mode = false
    private var mUTF8Mode = false
    private var mUTF8EscapeUsed = false
    private var mUTF8ToFollow = 0
    private val mUTF8ByteBuffer: ByteBuffer
    private val mInputCharBuffer: CharBuffer
    private val mUTF8Decoder: CharsetDecoder
    private var mUTF8ModeNotify: UpdateCallback? = null

    /**
     * Construct a terminal emulator that uses the supplied screen
     *
     * @param mSession the terminal session the emulator is attached to
     * @param mMainBuffer the screen to render characters into.
     * @param columns the number of columns to emulate
     * @param rows the number of rows to emulate
     * @param scheme the default color scheme of this emulator
     */
    init {
        this.screen = mMainBuffer
        mAltBuffer = TranscriptScreen(columns, rows, rows, scheme)
        mRows = rows
        mColumns = columns
        mTabStop = BooleanArray(mColumns)

        setColorScheme(scheme)

        mUTF8ByteBuffer = ByteBuffer.allocate(4)
        mInputCharBuffer = CharBuffer.allocate(2)
        mUTF8Decoder = StandardCharsets.UTF_8.newDecoder()
        mUTF8Decoder.onMalformedInput(CodingErrorAction.REPLACE)
        mUTF8Decoder.onUnmappableCharacter(CodingErrorAction.REPLACE)

        reset()
    }

    fun updateSize(columns: Int, rows: Int) {
        if (mRows == rows && mColumns == columns) {
            return
        }
        require(columns > 0) { "rows:$columns" }

        require(rows > 0) { "rows:$rows" }

        val screen = this.screen
        val altScreen = if (screen !== mMainBuffer) {
            mMainBuffer
        } else {
            mAltBuffer
        }

        // Try to resize the screen without getting the transcript
        val cursor = intArrayOf(mCursorCol, mCursorRow)
        val fastResize = screen!!.fastResize(columns, rows, cursor)

        var cursorColor: GrowableIntArray? = null
        var charAtCursor: String? = null
        var colors: GrowableIntArray? = null
        var transcriptText: String? = null
        if (!fastResize) {
            /* Save the character at the cursor (if one exists) and store an
             * ASCII ESC character at the cursor's location
             * This is an epic hack that lets us restore the cursor later...
             */
            cursorColor = GrowableIntArray(1)
            charAtCursor = screen.getSelectedText(
                cursorColor,
                mCursorCol,
                mCursorRow,
                mCursorCol,
                mCursorRow
            )
            screen.set(mCursorCol, mCursorRow, 27, 0)

            colors = GrowableIntArray(1024)
            transcriptText = screen.getTranscriptText(colors)
            screen.resize(columns, rows, this.style)
        }

        var altFastResize = true
        var altColors: GrowableIntArray? = null
        var altTranscriptText: String? = null
        if (altScreen != null) {
            altFastResize = altScreen.fastResize(columns, rows, null)

            if (!altFastResize) {
                altColors = GrowableIntArray(1024)
                altTranscriptText = altScreen.getTranscriptText(altColors)
                altScreen.resize(columns, rows, this.style)
            }
        }

        if (mRows != rows) {
            mRows = rows
            mTopMargin = 0
            mBottomMargin = mRows
        }
        if (mColumns != columns) {
            val oldColumns = mColumns
            mColumns = columns
            val oldTabStop = mTabStop
            mTabStop = BooleanArray(mColumns)
            val toTransfer = min(oldColumns.toDouble(), columns.toDouble()).toInt()
            System.arraycopy(oldTabStop, 0, mTabStop, 0, toTransfer)
        }

        if (!altFastResize) {
            val wasAboutToAutoWrap = mAboutToAutoWrap

            // Restore the contents of the inactive screen's buffer
            this.screen = altScreen
            mCursorRow = 0
            mCursorCol = 0
            mAboutToAutoWrap = false

            val end = altTranscriptText!!.length - 1
            /* Unlike for the main transcript below, don't trim off trailing
             * newlines -- the alternate transcript lacks a cursor marking, so
             * we might introduce an unwanted vertical shift in the screen
             * contents this way */
            var c: Char
            var cLow: Char
            var colorOffset = 0
            var i = 0
            while (i <= end) {
                c = altTranscriptText[i]
                val style = altColors!!.at(i - colorOffset)
                if (Character.isHighSurrogate(c)) {
                    cLow = altTranscriptText[++i]
                    emit(Character.toCodePoint(c, cLow), style)
                    ++colorOffset
                } else if (c == '\n') {
                    this.cursorCol = 0
                    doLinefeed()
                } else {
                    emit(c.code, style)
                }
                i++
            }

            this.screen = screen
            mAboutToAutoWrap = wasAboutToAutoWrap
        }

        if (fastResize) {
            // Only need to make sure the cursor is in the right spot
            if (cursor[0] >= 0 && cursor[1] >= 0) {
                mCursorCol = cursor[0]
                mCursorRow = cursor[1]
            } else {
                // Cursor scrolled off screen, reset the cursor to top left
                mCursorCol = 0
                mCursorRow = 0
            }

            return
        }

        mCursorRow = 0
        mCursorCol = 0
        mAboutToAutoWrap = false

        var newCursorRow = -1
        var newCursorCol = -1
        var newCursorTranscriptPos = -1
        var end = transcriptText!!.length - 1
        while ((end >= 0) && transcriptText[end] == '\n') {
            end--
        }
        var c: Char
        var cLow: Char
        var colorOffset = 0
        var i = 0
        while (i <= end) {
            c = transcriptText[i]
            val style = colors!!.at(i - colorOffset)
            if (Character.isHighSurrogate(c)) {
                cLow = transcriptText[++i]
                emit(Character.toCodePoint(c, cLow), style)
                ++colorOffset
            } else if (c == '\n') {
                this.cursorCol = 0
                doLinefeed()
            } else if (c.code == 27) {
                /* We marked the cursor location with ESC earlier, so this
                   is the place to restore the cursor to */
                newCursorRow = mCursorRow
                newCursorCol = mCursorCol
                newCursorTranscriptPos = screen.activeRows
                if (charAtCursor != null && !charAtCursor.isEmpty()) {
                    // Emit the real character that was in this spot
                    val encodedCursorColor = cursorColor!!.at(0)
                    emit(charAtCursor.toCharArray(), 0, charAtCursor.length, encodedCursorColor)
                }
            } else {
                emit(c.code, style)
            }
            i++
        }

        // If we marked a cursor location, move the cursor there now
        if (newCursorRow != -1 && newCursorCol != -1) {
            mCursorRow = newCursorRow
            mCursorCol = newCursorCol

            /* Adjust for any scrolling between the time we marked the cursor
               location and now */
            val scrollCount = screen.activeRows - newCursorTranscriptPos
            if (scrollCount > 0 && scrollCount <= newCursorRow) {
                mCursorRow -= scrollCount
            } else if (scrollCount > newCursorRow) {
                // Cursor scrolled off screen -- reset to top left corner
                mCursorRow = 0
                mCursorCol = 0
            }
        }
    }

    var cursorRow: Int
        /**
         * Get the cursor's current row.
         *
         * @return the cursor's current row.
         */
        get() = mCursorRow
        private set(row) {
            mCursorRow = row
            mAboutToAutoWrap = false
        }

    var cursorCol: Int
        /**
         * Get the cursor's current column.
         *
         * @return the cursor's current column.
         */
        get() = mCursorCol
        private set(col) {
            mCursorCol = col
            mAboutToAutoWrap = false
        }

    val reverseVideo: Boolean
        get() = (mDecFlags and K_REVERSE_VIDEO_MASK) != 0

    val showCursor: Boolean
        get() = (mDecFlags and K_SHOW_CURSOR_MASK) != 0

    private fun setDefaultTabStops() {
        for (i in 0..<mColumns) {
            mTabStop[i] = (i and 7) == 0 && i != 0
        }
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param base the first index of the array to process
     * @param length the number of bytes in the array to process
     */
    fun append(buffer: ByteArray, base: Int, length: Int) {
        if (EmulatorDebug.LOG_CHARACTERS_FLAG) {
            Log.d(EmulatorDebug.LOG_TAG, "In: '" + bytesToString(buffer, base, length) + "'")
        }
        for (i in 0..<length) {
            val b = buffer[base + i]
            try {
                process(b)
                mProcessedCharCount++
            } catch (e: Exception) {
                Log.e(
                    EmulatorDebug.LOG_TAG, ("Exception while processing character "
                            + mProcessedCharCount + " code "
                            + b.toInt().toString()), e
                )
            }
        }
    }

    private fun process(b: Byte, doUTF8: Boolean = true) {
        // Let the UTF-8 decoder try to handle it if we're in UTF-8 mode
        if (doUTF8 && mUTF8Mode && handleUTF8Sequence(b)) {
            return
        }

        // Handle C1 control characters
        if ((b.toInt() and 0x80) == 0x80 && (b.toInt() and 0x7f) <= 0x1f) {
            /* ESC ((code & 0x7f) + 0x40) is the two-byte escape sequence
               corresponding to a particular C1 code */
            process(27.toByte(), false)
            process(((b.toInt() and 0x7f) + 0x40).toByte(), false)
            return
        }

        when (b.toInt()) {
            0 -> {}
            7 ->             /* If in an OSC sequence, BEL may terminate a string; otherwise do
             * nothing */
                if (mEscapeState == ESC_RIGHT_SQUARE_BRACKET) {
                    doEscRightSquareBracket(b)
                }

            8 -> this.cursorCol = max(0.0, (mCursorCol - 1).toDouble()).toInt()
            9 ->             // Move to next tab stop, but not past edge of screen
                this.cursorCol = nextTabStop(mCursorCol)

            13 -> this.cursorCol = 0
            10, 11, 12 -> doLinefeed()
            14 -> setAltCharSet(true)
            15 -> setAltCharSet(false)
            24, 26 -> if (mEscapeState != ESC_NONE) {
                mEscapeState = ESC_NONE
                emit(127.toByte())
            }

            27 ->             // Starts an escape sequence unless we're parsing a string
                if (mEscapeState != ESC_RIGHT_SQUARE_BRACKET) {
                    startEscapeSequence(ESC)
                } else {
                    doEscRightSquareBracket(b)
                }

            else -> {
                mContinueSequence = false
                when (mEscapeState) {
                    ESC_NONE -> if (b >= 32) {
                        emit(b)
                    }

                    ESC -> doEsc(b)
                    ESC_POUND -> doEscPound(b)
                    ESC_SELECT_LEFT_PAREN -> doEscSelectLeftParen(b)
                    ESC_SELECT_RIGHT_PAREN -> doEscSelectRightParen(b)
                    ESC_LEFT_SQUARE_BRACKET -> doEscLeftSquareBracket(b) // CSI
                    ESC_LEFT_SQUARE_BRACKET_QUESTION_MARK -> doEscLSBQuest(b) // CSI ?
                    ESC_PERCENT -> doEscPercent(b)
                    ESC_RIGHT_SQUARE_BRACKET -> doEscRightSquareBracket(b)
                    ESC_RIGHT_SQUARE_BRACKET_ESC -> doEscRightSquareBracketEsc(b)
                    else -> unknownSequence(b)
                }
                if (!mContinueSequence) {
                    mEscapeState = ESC_NONE
                }
            }
        }
    }

    private fun handleUTF8Sequence(b: Byte): Boolean {
        if (mUTF8ToFollow == 0 && (b.toInt() and 0x80) == 0) {
            // ASCII character -- we don't need to handle this
            return false
        }

        if (mUTF8ToFollow > 0) {
            if ((b.toInt() and 0xc0) != 0x80) {
                /* Not a UTF-8 continuation byte (doesn't begin with 0b10)
                   Replace the entire sequence with the replacement char */
                mUTF8ToFollow = 0
                mUTF8ByteBuffer.clear()
                emit(UNICODE_REPLACEMENT_CHAR)

                /* The Unicode standard (section 3.9, definition D93) requires
                 * that we now attempt to process this byte as though it were
                 * the beginning of another possibly-valid sequence */
                return handleUTF8Sequence(b)
            }

            mUTF8ByteBuffer.put(b)
            if (--mUTF8ToFollow == 0) {
                // Sequence complete -- decode and emit it
                val byteBuf = mUTF8ByteBuffer
                val charBuf = mInputCharBuffer
                val decoder = mUTF8Decoder

                byteBuf.rewind()
                decoder.reset()
                decoder.decode(byteBuf, charBuf, true)
                decoder.flush(charBuf)

                val chars = charBuf.array()
                if (chars[0].code >= 0x80 && chars[0].code <= 0x9f) {
                    /* Sequence decoded to a C1 control character which needs
                       to be sent through process() again */
                    process(chars[0].code.toByte(), false)
                } else {
                    emit(chars)
                }

                byteBuf.clear()
                charBuf.clear()
            }
        } else {
            mUTF8ToFollow = if ((b.toInt() and 0xe0) == 0xc0) { // 0b110 -- two-byte sequence
                1
            } else if ((b.toInt() and 0xf0) == 0xe0) { // 0b1110 -- three-byte sequence
                2
            } else if ((b.toInt() and 0xf8) == 0xf0) { // 0b11110 -- four-byte sequence
                3
            } else {
                // Not a valid UTF-8 sequence start -- replace this char
                emit(UNICODE_REPLACEMENT_CHAR)
                return true
            }

            mUTF8ByteBuffer.put(b)
        }

        return true
    }

    private fun setAltCharSet(alternateCharSet: Boolean) {
        mAlternateCharSet = alternateCharSet
        computeEffectiveCharSet()
    }

    private fun computeEffectiveCharSet() {
        val charSet = mCharSet[if (mAlternateCharSet) 1 else 0]
        mUseAlternateCharSet = charSet == CHAR_SET_SPECIAL_GRAPHICS
    }

    private fun nextTabStop(cursorCol: Int): Int {
        for (i in cursorCol + 1..<mColumns) {
            if (mTabStop[i]) {
                return i
            }
        }
        return mColumns - 1
    }

    private fun prevTabStop(cursorCol: Int): Int {
        for (i in cursorCol - 1 downTo 0) {
            if (mTabStop[i]) {
                return i
            }
        }
        return 0
    }

    private fun doEscPercent(b: Byte) {
        when (b.toInt().toChar()) {
            '@' -> {
                this.uTF8Mode = false
                mUTF8EscapeUsed = true
            }

            'G' -> {
                this.uTF8Mode = true
                mUTF8EscapeUsed = true
            }

            else -> {}
        }
    }

    private fun doEscLSBQuest(b: Byte) {
        val arg = getArg0(0)
        val mask = getDecFlagsMask(arg)
        val oldFlags = mDecFlags
        when (b.toInt().toChar()) {
            'h' -> {
                mDecFlags = mDecFlags or mask
                when (arg) {
                    1 -> mKeyListener!!.setCursorKeysApplicationMode(true)
                    47, 1047, 1049 -> if (mAltBuffer != null) {
                        this.screen = mAltBuffer
                    }
                }
                if (arg >= 1000 && arg <= 1003) {
                    this.mouseTrackingMode = arg
                }
            }

            'l' -> {
                mDecFlags = mDecFlags and mask.inv()
                when (arg) {
                    1 -> mKeyListener!!.setCursorKeysApplicationMode(false)
                    47, 1047, 1049 -> this.screen = mMainBuffer
                }
                if (arg >= 1000 && arg <= 1003) {
                    this.mouseTrackingMode = 0
                }
            }

            'r' -> mDecFlags = (mDecFlags and mask.inv()) or (mSavedDecFlags and mask)
            's' -> mSavedDecFlags = (mSavedDecFlags and mask.inv()) or (mDecFlags and mask)
            else -> parseArg(b)
        }

        val newlySetFlags = (oldFlags.inv()) and mDecFlags
        val changedFlags = oldFlags xor mDecFlags

        // 132 column mode
        if ((changedFlags and K_132_COLUMN_MODE_MASK) != 0) {
            // We don't actually set/reset 132 cols, but we do want the
            // side effect of clearing the screen and homing the cursor.
            blockClear(0, 0, mColumns, mRows)
            setCursorRowCol(0, 0)
        }

        // origin mode
        if ((newlySetFlags and K_ORIGIN_MODE_MASK) != 0) {
            // Home the cursor.
            setCursorPosition(0, 0)
        }
    }

    private fun getDecFlagsMask(argument: Int): Int {
        if (argument >= 1 && argument <= 32) {
            return (1 shl argument)
        }

        return 0
    }

    private fun startEscapeSequence(escapeState: Int) {
        mEscapeState = escapeState
        mArgIndex = 0
        Arrays.fill(mArgs, -1)
    }

    private fun doLinefeed() {
        var newCursorRow = mCursorRow + 1
        if (newCursorRow >= mBottomMargin) {
            scroll()
            newCursorRow = mBottomMargin - 1
        }
        this.cursorRow = newCursorRow
    }

    private fun continueSequence() {
        mContinueSequence = true
    }

    private fun continueSequence(state: Int) {
        mEscapeState = state
        mContinueSequence = true
    }

    private fun doEscSelectLeftParen(b: Byte) {
        doSelectCharSet(0, b)
    }

    private fun doEscSelectRightParen(b: Byte) {
        doSelectCharSet(1, b)
    }

    private fun doSelectCharSet(charSetIndex: Int, b: Byte) {
        val charSet: Int = when (b.toInt().toChar()) {
            'A' -> CHAR_SET_UK
            'B' -> CHAR_SET_ASCII
            '0' -> CHAR_SET_SPECIAL_GRAPHICS
            '1' -> CHAR_SET_ALT_STANDARD
            '2' -> CHAR_SET_ALT_SPECIAL_GRAPICS
            else -> {
                unknownSequence(b)
                return
            }
        }
        mCharSet[charSetIndex] = charSet
        computeEffectiveCharSet()
    }

    private fun doEscPound(b: Byte) {
        if (b == '8'.code.toByte()) { // Esc # 8 - DECALN alignment test
            screen!!.blockSet(
                0, 0, mColumns, mRows, 'E'.code,
                this.style
            )
        } else {
            unknownSequence(b)
        }
    }

    private fun doEsc(b: Byte) {
        when (b.toInt().toChar()) {
            '#' -> continueSequence(ESC_POUND)
            '(' -> continueSequence(ESC_SELECT_LEFT_PAREN)
            ')' -> continueSequence(ESC_SELECT_RIGHT_PAREN)
            '7' -> {
                mSavedCursorRow = mCursorRow
                mSavedCursorCol = mCursorCol
                mSavedEffect = this.effect
                mSavedDecFlags_DECSC_DECRC = mDecFlags and K_DECSC_DECRC_MASK
            }

            '8' -> {
                setCursorRowCol(mSavedCursorRow, mSavedCursorCol)
                this.effect = mSavedEffect
                mDecFlags = ((mDecFlags and K_DECSC_DECRC_MASK.inv())
                        or mSavedDecFlags_DECSC_DECRC)
            }

            'D' -> doLinefeed()
            'E' -> {
                this.cursorCol = 0
                doLinefeed()
            }

            'F' -> setCursorRowCol(0, mBottomMargin - 1)
            'H' -> mTabStop[mCursorCol] = true
            'M' -> if (mCursorRow <= mTopMargin) {
                screen!!.blockCopy(
                    0, mTopMargin, mColumns, mBottomMargin
                            - (mTopMargin + 1), 0, mTopMargin + 1
                )
                blockClear(0, mTopMargin, mColumns)
            } else {
                mCursorRow--
            }

            'N' -> unimplementedSequence(b)
            '0' -> unimplementedSequence(b)
            'P' -> unimplementedSequence(b)
            'Z' -> sendDeviceAttributes()
            '[' -> continueSequence(ESC_LEFT_SQUARE_BRACKET)
            '=' -> this.keypadApplicationMode = true
            ']' -> {
                startCollectingOSCArgs()
                continueSequence(ESC_RIGHT_SQUARE_BRACKET)
            }

            '>' -> this.keypadApplicationMode = false
            else -> unknownSequence(b)
        }
    }

    private fun doEscLeftSquareBracket(b: Byte) {
        // CSI
        when (b.toInt().toChar()) {
            '@' -> {
                val charsAfterCursor = mColumns - mCursorCol
                val charsToInsert = min(getArg0(1).toDouble(), charsAfterCursor.toDouble()).toInt()
                val charsToMove = charsAfterCursor - charsToInsert
                screen!!.blockCopy(
                    mCursorCol, mCursorRow, charsToMove, 1,
                    mCursorCol + charsToInsert, mCursorRow
                )
                blockClear(mCursorCol, mCursorRow, charsToInsert)
            }

            'A' -> this.cursorRow =
                max(mTopMargin.toDouble(), (mCursorRow - getArg0(1)).toDouble()).toInt()

            'B' -> this.cursorRow = min(
                (mBottomMargin - 1).toDouble(),
                (mCursorRow + getArg0(1)).toDouble()
            ).toInt()

            'C' -> this.cursorCol =
                min((mColumns - 1).toDouble(), (mCursorCol + getArg0(1)).toDouble())
                    .toInt()

            'D' -> this.cursorCol =
                max(0.0, (mCursorCol - getArg0(1)).toDouble()).toInt()

            'G' -> this.cursorCol = (min(
                max(1.0, getArg0(1).toDouble()),
                mColumns.toDouble()
            ) - 1).toInt()

            'H' -> setHorizontalVerticalPosition()
            'J' ->             // ED ignores the scrolling margins.
                when (getArg0(0)) {
                    0 -> {
                        blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol)
                        blockClear(
                            0, mCursorRow + 1, mColumns,
                            mRows - (mCursorRow + 1)
                        )
                    }

                    1 -> {
                        blockClear(0, 0, mColumns, mCursorRow)
                        blockClear(0, mCursorRow, mCursorCol + 1)
                    }

                    2 -> blockClear(0, 0, mColumns, mRows)
                    else -> unknownSequence(b)
                }

            'K' -> when (getArg0(0)) {
                0 -> blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol)
                1 -> blockClear(0, mCursorRow, mCursorCol + 1)
                2 -> blockClear(0, mCursorRow, mColumns)
                else -> unknownSequence(b)
            }

            'L' -> {
                val linesAfterCursor = mBottomMargin - mCursorRow
                val linesToInsert = min(getArg0(1).toDouble(), linesAfterCursor.toDouble()).toInt()
                val linesToMove = linesAfterCursor - linesToInsert
                screen!!.blockCopy(
                    0, mCursorRow, mColumns, linesToMove, 0,
                    mCursorRow + linesToInsert
                )
                blockClear(0, mCursorRow, mColumns, linesToInsert)
            }

            'M' -> {
                val linesAfterCursor = mBottomMargin - mCursorRow
                val linesToDelete = min(getArg0(1).toDouble(), linesAfterCursor.toDouble()).toInt()
                val linesToMove = linesAfterCursor - linesToDelete
                screen!!.blockCopy(
                    0, mCursorRow + linesToDelete, mColumns,
                    linesToMove, 0, mCursorRow
                )
                blockClear(0, mCursorRow + linesToMove, mColumns, linesToDelete)
            }

            'P' -> {
                val charsAfterCursor = mColumns - mCursorCol
                val charsToDelete = min(getArg0(1).toDouble(), charsAfterCursor.toDouble()).toInt()
                val charsToMove = charsAfterCursor - charsToDelete
                screen!!.blockCopy(
                    mCursorCol + charsToDelete, mCursorRow,
                    charsToMove, 1, mCursorCol, mCursorRow
                )
                blockClear(mCursorCol + charsToMove, mCursorRow, charsToDelete)
            }

            'T' -> unimplementedSequence(b)
            'X' -> blockClear(mCursorCol, mCursorRow, getArg0(0))
            'Z' -> this.cursorCol = prevTabStop(mCursorCol)
            '?' -> continueSequence(ESC_LEFT_SQUARE_BRACKET_QUESTION_MARK)
            'c' -> sendDeviceAttributes()
            'd' -> this.cursorRow =
                (min(max(1.0, getArg0(1).toDouble()), mRows.toDouble()) - 1).toInt()

            'f' -> setHorizontalVerticalPosition()
            'g' -> when (getArg0(0)) {
                0 -> mTabStop[mCursorCol] = false
                3 -> {
                    var i = 0
                    while (i < mColumns) {
                        mTabStop[i] = false
                        i++
                    }
                }

                else -> {}
            }

            'h' -> doSetMode(true)
            'l' -> doSetMode(false)
            'm' ->                   // (can have up to 16 numerical arguments)
                selectGraphicRendition()

            'n' ->             //sendDeviceAttributes()
                when (getArg0(0)) {
                    5 -> {
                        // Answer is ESC [ 0 n (Terminal OK).
                        val dsr = byteArrayOf(
                            27.toByte(),
                            '['.code.toByte(),
                            '0'.code.toByte(),
                            'n'.code.toByte()
                        )
                        mSession.write(dsr, 0, dsr.size)
                    }

                    6 -> {
                        // Answer is ESC [ y ; x R, where x,y is
                        // the cursor location.
                        val cpr = String.format(
                            Locale.US, "\u001b[%d;%dR",
                            mCursorRow + 1, mCursorCol + 1
                        ).toByteArray()
                        mSession.write(cpr, 0, cpr.size)
                    }

                    else -> {}
                }

            'r' -> {
                // The top margin defaults to 1, the bottom margin
                // (unusually for arguments) defaults to mRows.
                //
                // The escape sequence numbers top 1..23, but we
                // number top 0..22.
                // The escape sequence numbers bottom 2..24, and
                // so do we (because we use a zero based numbering
                // scheme, but we store the first line below the
                // bottom-most scrolling line.
                // As a result, we adjust the top line by -1, but
                // we leave the bottom line alone.
                //
                // Also require that top + 2 <= bottom
                val top = max(0.0, min((getArg0(1) - 1).toDouble(), (mRows - 2).toDouble())).toInt()
                val bottom = max(
                    (top + 2).toDouble(),
                    min(getArg1(mRows).toDouble(), mRows.toDouble())
                ).toInt()
                mTopMargin = top
                mBottomMargin = bottom

                // The cursor is placed in the home position
                setCursorRowCol(mTopMargin, 0)
            }

            else -> parseArg(b)
        }
    }

    private fun selectGraphicRendition() {
        // SGR
        var i = 0
        while (i <= mArgIndex) {
            var code = mArgs[i]
            if (code < 0) {
                if (mArgIndex > 0) {
                    i++
                    continue
                } else {
                    code = 0
                }
            }

            // See http://en.wikipedia.org/wiki/ANSI_escape_code#graphics
            if (code == 0) { // reset
                this.foreColor = mDefaultForeColor
                this.backColor = mDefaultBackColor
                this.effect = TextStyle.fxNormal
            } else if (code == 1) { // bold
                this.effect = this.effect or TextStyle.fxBold
            } else if (code == 3) { // italics, but rarely used as such; "standout" (inverse colors) with TERM=screen
                this.effect = this.effect or TextStyle.fxItalic
            } else if (code == 4) { // underscore
                this.effect = this.effect or TextStyle.fxUnderline
            } else if (code == 5) { // blink
                this.effect = this.effect or TextStyle.fxBlink
            } else if (code == 7) { // inverse
                this.effect = this.effect or TextStyle.fxInverse
            } else if (code == 8) { // invisible
                this.effect = this.effect or TextStyle.fxInvisible
            } else if (code == 10) { // exit alt charset (TERM=linux)
                setAltCharSet(false)
            } else if (code == 11) { // enter alt charset (TERM=linux)
                setAltCharSet(true)
            } else if (code == 22) { // Normal color or intensity, neither bright, bold nor faint
                //mEffect &= ~(TextStyle.fxBold | TextStyle.fxFaint);
                this.effect = this.effect and TextStyle.fxBold.inv()
            } else if (code == 23) { // not italic, but rarely used as such; clears standout with TERM=screen
                this.effect = this.effect and TextStyle.fxItalic.inv()
            } else if (code == 24) { // underline: none
                this.effect = this.effect and TextStyle.fxUnderline.inv()
            } else if (code == 25) { // blink: none
                this.effect = this.effect and TextStyle.fxBlink.inv()
            } else if (code == 27) { // image: positive
                this.effect = this.effect and TextStyle.fxInverse.inv()
            } else if (code == 28) { // invisible
                this.effect = this.effect and TextStyle.fxInvisible.inv()
            } else if (code >= 30 && code <= 37) { // foreground color
                this.foreColor = code - 30
            } else if (code == 38 && i + 2 <= mArgIndex && mArgs[i + 1] == 5) { // foreground 256 color
                val color = mArgs[i + 2]
                if (checkColor(color)) {
                    this.foreColor = color
                }
                i += 2
            } else if (code == 39) { // set default text color
                this.foreColor = mDefaultForeColor
            } else if (code >= 40 && code <= 47) { // background color
                this.backColor = code - 40
            } else if (code == 48 && i + 2 <= mArgIndex && mArgs[i + 1] == 5) { // background 256 color
                this.backColor = mArgs[i + 2]
                val color = mArgs[i + 2]
                if (checkColor(color)) {
                    this.backColor = color
                }
                i += 2
            } else if (code == 49) { // set default background color
                this.backColor = mDefaultBackColor
            } else if (code >= 90 && code <= 97) { // bright foreground color
                this.foreColor = code - 90 + 8
            } else if (code >= 100 && code <= 107) { // bright background color
                this.backColor = code - 100 + 8
            } else {
                if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
                    Log.w(EmulatorDebug.LOG_TAG, String.format("SGR unknown code %d", code))
                }
            }
            i++
        }
    }

    private fun checkColor(color: Int): Boolean {
        val result = isValidColor(color)
        if (!result) {
            if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
                Log.w(
                    EmulatorDebug.LOG_TAG,
                    String.format("Invalid color %d", color)
                )
            }
        }
        return result
    }

    private fun isValidColor(color: Int): Boolean {
        return color >= 0 && color < TextStyle.ciColorLength
    }

    private fun doEscRightSquareBracket(b: Byte) {
        when (b.toInt()) {
            0x7 -> doOSC()
            0x1b -> continueSequence(ESC_RIGHT_SQUARE_BRACKET_ESC)
            else -> collectOSCArgs(b)
        }
    }

    private fun doEscRightSquareBracketEsc(b: Byte) {
        if (b == '\\'.code.toByte()) {
            doOSC()
        } else { // The ESC character was not followed by a \, so insert the ESC and
            // the current character in arg buffer.
            collectOSCArgs(0x1b.toByte())
            collectOSCArgs(b)
            continueSequence(ESC_RIGHT_SQUARE_BRACKET)
        }
    }

    private fun doOSC() { // Operating System Controls
        startTokenizingOSC()
        val ps = nextOSCInt(';'.code)
        when (ps) {
            0, 1, 2 -> changeTitle(ps, nextOSCString(-1))
            else -> unknownParameter(ps)
        }
        finishSequence()
    }

    private fun changeTitle(parameter: Int, title: String?) {
        if (parameter == 0 || parameter == 2) {
            mSession.title = title
        }
    }

    private fun blockClear(sx: Int, sy: Int, w: Int, h: Int = 1) {
        screen!!.blockSet(sx, sy, w, h, ' '.code, this.style)
    }

    private val style: Int
        get() = TextStyle.encode(
            this.foreColor,
            this.backColor,
            this.effect
        )

    private fun doSetMode(newValue: Boolean) {
        val modeBit = getArg0(0)
        if (modeBit == 4) {
            mInsertMode = newValue
        } else {
            unknownParameter(modeBit)
        }
    }

    private fun setHorizontalVerticalPosition() {
        // Parameters are Row ; Column

        setCursorPosition(getArg1(1) - 1, getArg0(1) - 1)
    }

    private fun setCursorPosition(x: Int, y: Int) {
        var effectiveTopMargin = 0
        var effectiveBottomMargin = mRows
        if ((mDecFlags and K_ORIGIN_MODE_MASK) != 0) {
            effectiveTopMargin = mTopMargin
            effectiveBottomMargin = mBottomMargin
        }
        val newRow = max(
            effectiveTopMargin.toDouble(), min(
                (effectiveTopMargin + y).toDouble(),
                (effectiveBottomMargin - 1).toDouble()
            )
        ).toInt()
        val newCol = max(0.0, min(x.toDouble(), (mColumns - 1).toDouble())).toInt()
        setCursorRowCol(newRow, newCol)
    }

    private fun sendDeviceAttributes() {
        // This identifies us as a DEC vt100 with advanced
        // video options. This is what the xterm terminal
        // emulator sends.
        val attributes =
            byteArrayOf( /* VT100 */27.toByte(),
                '['.code.toByte(),
                '?'.code.toByte(),
                '1'.code.toByte(),
                ';'.code.toByte(),
                '2'.code.toByte(),
                'c'.code.toByte() /* VT220
                (byte) 27, (byte) '[', (byte) '?', (byte) '6',
                (byte) '0',  (byte) ';',
                (byte) '1',  (byte) ';',
                (byte) '2',  (byte) ';',
                (byte) '6',  (byte) ';',
                (byte) '8',  (byte) ';',
                (byte) '9',  (byte) ';',
                (byte) '1',  (byte) '5', (byte) ';',
                (byte) 'c'
                */

            )

        mSession.write(attributes, 0, attributes.size)
    }

    private fun scroll() {
        //System.out.println("Scroll(): mTopMargin " + mTopMargin + " mBottomMargin " + mBottomMargin);
        this.scrollCounter++
        screen!!.scroll(mTopMargin, mBottomMargin, this.style)
    }

    /**
     * Process the next ASCII character of a parameter.
     *
     * @param b The next ASCII character of the paramater sequence.
     */
    private fun parseArg(b: Byte) {
        if (b >= '0'.code.toByte() && b <= '9'.code.toByte()) {
            if (mArgIndex < mArgs.size) {
                val oldValue = mArgs[mArgIndex]
                val thisDigit = b - '0'.code.toByte()
                val value: Int = if (oldValue >= 0) {
                    oldValue * 10 + thisDigit
                } else {
                    thisDigit
                }
                mArgs[mArgIndex] = value
            }
            continueSequence()
        } else if (b == ';'.code.toByte()) {
            if (mArgIndex < mArgs.size) {
                mArgIndex++
            }
            continueSequence()
        } else {
            unknownSequence(b)
        }
    }

    private fun getArg0(defaultValue: Int): Int {
        return getArg(0, defaultValue, true)
    }

    private fun getArg1(defaultValue: Int): Int {
        return getArg(1, defaultValue, true)
    }

    private fun getArg(
        index: Int, defaultValue: Int,
        treatZeroAsDefault: Boolean
    ): Int {
        var result = mArgs[index]
        if (result < 0 || (result == 0 && treatZeroAsDefault)) {
            result = defaultValue
        }
        return result
    }

    private fun startCollectingOSCArgs() {
        mOSCArgLength = 0
    }

    private fun collectOSCArgs(b: Byte) {
        if (mOSCArgLength < MAX_OSC_STRING_LENGTH) {
            mOSCArg[mOSCArgLength++] = b
            continueSequence()
        } else {
            unknownSequence(b)
        }
    }

    private fun startTokenizingOSC() {
        mOSCArgTokenizerIndex = 0
    }

    private fun nextOSCString(delimiter: Int): String {
        val start = mOSCArgTokenizerIndex
        var end = start
        while (mOSCArgTokenizerIndex < mOSCArgLength) {
            val b = mOSCArg[mOSCArgTokenizerIndex++]
            if (b.toInt() == delimiter) {
                break
            }
            end++
        }
        if (start == end) {
            return ""
        }
        return String(mOSCArg, start, end - start, StandardCharsets.UTF_8)
    }

    private fun nextOSCInt(delimiter: Int): Int {
        var value = -1
        while (mOSCArgTokenizerIndex < mOSCArgLength) {
            val b = mOSCArg[mOSCArgTokenizerIndex++]
            if (b.toInt() == delimiter) {
                break
            } else if (b >= '0'.code.toByte() && b <= '9'.code.toByte()) {
                if (value < 0) {
                    value = 0
                }
                value = value * 10 + b - '0'.code
            } else {
                unknownSequence(b)
            }
        }
        return value
    }

    private fun unimplementedSequence(b: Byte) {
        if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            logError("unimplemented", b)
        }
        finishSequence()
    }

    private fun unknownSequence(b: Byte) {
        if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            logError("unknown", b)
        }
        finishSequence()
    }

    private fun unknownParameter(parameter: Int) {
        if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            val buf = StringBuilder()
            buf.append("Unknown parameter")
            buf.append(parameter)
            logError(buf.toString())
        }
    }

    private fun logError(errorType: String?, b: Byte) {
        if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            val buf = StringBuilder()
            buf.append(errorType)
            buf.append(" sequence ")
            buf.append(" EscapeState: ")
            buf.append(mEscapeState)
            buf.append(" char: '")
            buf.append(Char(b.toUShort()))
            buf.append("' (")
            buf.append(b.toInt())
            buf.append(")")
            var firstArg = true
            for (i in 0..mArgIndex) {
                val value = mArgs[i]
                if (value >= 0) {
                    if (firstArg) {
                        firstArg = false
                        buf.append("args = ")
                    }
                    buf.append(String.format("%d; ", value))
                }
            }
            logError(buf.toString())
        }
    }

    private fun logError(error: String) {
        if (EmulatorDebug.LOG_UNKNOWN_ESCAPE_SEQUENCES) {
            Log.e(EmulatorDebug.LOG_TAG, error)
        }
        finishSequence()
    }

    private fun finishSequence() {
        mEscapeState = ESC_NONE
    }

    private fun autoWrapEnabled(): Boolean {
        return (mDecFlags and K_WRAPAROUND_MODE_MASK) != 0
    }

    /**
     * Send a Unicode code point to the screen.
     *
     * @param c The code point of the character to display
     * @param foreColor The foreground color of the character
     * @param backColor The background color of the character
     */
    private fun emit(c: Int, style: Int = this.style) {
        val autoWrap = autoWrapEnabled()
        val width = charWidth(c)

        if (autoWrap) {
            if (mCursorCol == mColumns - 1 && (mAboutToAutoWrap || width == 2)) {
                screen!!.setLineWrap(mCursorRow)
                mCursorCol = 0
                mJustWrapped = true
                if (mCursorRow + 1 < mBottomMargin) {
                    mCursorRow++
                } else {
                    scroll()
                }
            }
        }

        if (mInsertMode and (width != 0)) { // Move character to right one space
            val destCol = mCursorCol + width
            if (destCol < mColumns) {
                screen!!.blockCopy(
                    mCursorCol, mCursorRow, mColumns - destCol,
                    1, destCol, mCursorRow
                )
            }
        }

        if (width == 0) {
            // Combining character -- store along with character it modifies
            if (mJustWrapped) {
                screen!!.set(mColumns - mLastEmittedCharWidth, mCursorRow - 1, c, style)
            } else {
                screen!!.set(mCursorCol - mLastEmittedCharWidth, mCursorRow, c, style)
            }
        } else {
            screen!!.set(mCursorCol, mCursorRow, c, style)
            mJustWrapped = false
        }

        if (autoWrap) {
            mAboutToAutoWrap = (mCursorCol == mColumns - 1)

            //Force line-wrap flag to trigger even for lines being typed
            if (mAboutToAutoWrap) screen!!.setLineWrap(mCursorRow)
        }

        mCursorCol = min((mCursorCol + width).toDouble(), (mColumns - 1).toDouble()).toInt()
        if (width > 0) {
            mLastEmittedCharWidth = width
        }
    }

    private fun emit(b: Byte) {
        if (mUseAlternateCharSet && b < 128) {
            emit(mSpecialGraphicsCharMap[b.toInt()].code)
        } else {
            emit(b.toInt())
        }
    }

    /**
     * Send a UTF-16 char or surrogate pair to the screen.
     *
     * @param c A char[2] containing either a single UTF-16 char or a surrogate pair to be sent to the screen.
     */
    private fun emit(c: CharArray) {
        if (Character.isHighSurrogate(c[0])) {
            emit(Character.toCodePoint(c[0], c[1]))
        } else {
            emit(c[0].code)
        }
    }

    /**
     * Send an array of UTF-16 chars to the screen.
     *
     * @param c A char[] array whose contents are to be sent to the screen.
     */
    private fun emit(c: CharArray, offset: Int, length: Int, style: Int) {
        var i = offset
        while (i < length) {
            if (c[i].code == 0) {
                break
            }
            if (Character.isHighSurrogate(c[i])) {
                emit(Character.toCodePoint(c[i], c[i + 1]), style)
                ++i
            } else {
                emit(c[i].code, style)
            }
            ++i
        }
    }

    private fun setCursorRowCol(row: Int, col: Int) {
        mCursorRow = min(row.toDouble(), (mRows - 1).toDouble()).toInt()
        mCursorCol = min(col.toDouble(), (mColumns - 1).toDouble()).toInt()
        mAboutToAutoWrap = false
    }

    fun clearScrollCounter() {
        this.scrollCounter = 0
    }

    /**
     * Reset the terminal emulator to its initial state.
     */
    fun reset() {
        mCursorRow = 0
        mCursorCol = 0
        mArgIndex = 0
        mContinueSequence = false
        mEscapeState = ESC_NONE
        mSavedCursorRow = 0
        mSavedCursorCol = 0
        mSavedEffect = 0
        mSavedDecFlags_DECSC_DECRC = 0
        mDecFlags = 0
        if (DEFAULT_TO_AUTOWRAP_ENABLED) {
            mDecFlags = mDecFlags or K_WRAPAROUND_MODE_MASK
        }
        mDecFlags = mDecFlags or K_SHOW_CURSOR_MASK
        mSavedDecFlags = 0
        mInsertMode = false
        mTopMargin = 0
        mBottomMargin = mRows
        mAboutToAutoWrap = false
        this.foreColor = mDefaultForeColor
        this.backColor = mDefaultBackColor
        this.keypadApplicationMode = false
        mAlternateCharSet = false
        mCharSet[0] = CHAR_SET_ASCII
        mCharSet[1] = CHAR_SET_SPECIAL_GRAPHICS
        computeEffectiveCharSet()
        // mProcessedCharCount is preserved unchanged.
        setDefaultTabStops()
        blockClear(0, 0, mColumns, mRows)

        this.uTF8Mode = mDefaultUTF8Mode
        mUTF8EscapeUsed = false
        mUTF8ToFollow = 0
        mUTF8ByteBuffer.clear()
        mInputCharBuffer.clear()
    }

    fun setDefaultUTF8Mode(defaultToUTF8Mode: Boolean) {
        mDefaultUTF8Mode = defaultToUTF8Mode
        if (!mUTF8EscapeUsed) {
            this.uTF8Mode = defaultToUTF8Mode
        }
    }

    var uTF8Mode: Boolean
        get() = mUTF8Mode
        set(utf8Mode) {
            if (utf8Mode && !mUTF8Mode) {
                mUTF8ToFollow = 0
                mUTF8ByteBuffer.clear()
                mInputCharBuffer.clear()
            }
            mUTF8Mode = utf8Mode
            if (mUTF8ModeNotify != null) {
                mUTF8ModeNotify!!.onUpdate()
            }
        }

    fun setUTF8ModeUpdateCallback(utf8ModeNotify: UpdateCallback?) {
        mUTF8ModeNotify = utf8ModeNotify
    }

    fun setColorScheme(scheme: ColorScheme?) {
        mDefaultForeColor = TextStyle.ciForeground
        mDefaultBackColor = TextStyle.ciBackground
        mMainBuffer.setColorScheme(scheme)
        if (mAltBuffer != null) {
            mAltBuffer!!.setColorScheme(scheme)
        }
    }

    fun getSelectedText(x1: Int, y1: Int, x2: Int, y2: Int): String? {
        return screen!!.getSelectedText(x1, y1, x2, y2)
    }

    fun finish() {
        if (mAltBuffer != null) {
            mAltBuffer!!.finish()
            mAltBuffer = null
        }
    }

    companion object {
        /**
         * The number of parameter arguments. This name comes from the ANSI standard
         * for terminal escape codes.
         */
        private const val MAX_ESCAPE_PARAMETERS = 16

        /**
         * Don't know what the actual limit is, this seems OK for now.
         */
        private const val MAX_OSC_STRING_LENGTH = 512

        // Escape processing states:
        /**
         * Escape processing state: Not currently in an escape sequence.
         */
        private const val ESC_NONE = 0

        /**
         * Escape processing state: Have seen an ESC character
         */
        private const val ESC = 1

        /**
         * Escape processing state: Have seen ESC POUND
         */
        private const val ESC_POUND = 2

        /**
         * Escape processing state: Have seen ESC and a character-set-select char
         */
        private const val ESC_SELECT_LEFT_PAREN = 3

        /**
         * Escape processing state: Have seen ESC and a character-set-select char
         */
        private const val ESC_SELECT_RIGHT_PAREN = 4

        /**
         * Escape processing state: ESC [
         */
        private const val ESC_LEFT_SQUARE_BRACKET = 5

        /**
         * Escape processing state: ESC [ ?
         */
        private const val ESC_LEFT_SQUARE_BRACKET_QUESTION_MARK = 6

        /**
         * Escape processing state: ESC %
         */
        private const val ESC_PERCENT = 7

        /**
         * Escape processing state: ESC ] (AKA OSC - Operating System Controls)
         */
        private const val ESC_RIGHT_SQUARE_BRACKET = 8

        /**
         * Escape processing state: ESC ] (AKA OSC - Operating System Controls)
         */
        private const val ESC_RIGHT_SQUARE_BRACKET_ESC = 9

        // DecSet booleans
        /**
         * This mask indicates 132-column mode is set. (As opposed to 80-column
         * mode.)
         */
        private const val K_132_COLUMN_MODE_MASK = 1 shl 3

        /**
         * DECSCNM - set means reverse video (light background.)
         */
        private const val K_REVERSE_VIDEO_MASK = 1 shl 5

        /**
         * This mask indicates that origin mode is set. (Cursor addressing is
         * relative to the absolute screen size, rather than the currently set top
         * and bottom margins.)
         */
        private const val K_ORIGIN_MODE_MASK = 1 shl 6

        /**
         * This mask indicates that wraparound mode is set. (As opposed to
         * stop-at-right-column mode.)
         */
        private const val K_WRAPAROUND_MODE_MASK = 1 shl 7

        /**
         * This mask indicates that the cursor should be shown. DECTCEM
         */
        private const val K_SHOW_CURSOR_MASK = 1 shl 25

        /** This mask is the subset of DecSet bits that are saved / restored by
         * the DECSC / DECRC commands
         */
        private const val K_DECSC_DECRC_MASK = K_ORIGIN_MODE_MASK or K_WRAPAROUND_MODE_MASK

        private const val CHAR_SET_UK = 0
        private const val CHAR_SET_ASCII = 1
        private const val CHAR_SET_SPECIAL_GRAPHICS = 2
        private const val CHAR_SET_ALT_STANDARD = 3
        private const val CHAR_SET_ALT_SPECIAL_GRAPICS = 4

        /**
         * Special graphics character set
         */
        private val mSpecialGraphicsCharMap = CharArray(128)

        init {
            for (i in 0..127) {
                mSpecialGraphicsCharMap[i] = i.toChar()
            }
            mSpecialGraphicsCharMap['_'.code] = ' ' // Blank
            mSpecialGraphicsCharMap['b'.code] = 0x2409.toChar() // Tab
            mSpecialGraphicsCharMap['c'.code] = 0x240C.toChar() // Form feed
            mSpecialGraphicsCharMap['d'.code] = 0x240D.toChar() // Carriage return
            mSpecialGraphicsCharMap['e'.code] = 0x240A.toChar() // Line feed
            mSpecialGraphicsCharMap['h'.code] = 0x2424.toChar() // New line
            mSpecialGraphicsCharMap['i'.code] = 0x240B.toChar() // Vertical tab/"lantern"
            mSpecialGraphicsCharMap['}'.code] = 0x00A3.toChar() // Pound sterling symbol
            mSpecialGraphicsCharMap['f'.code] = 0x00B0.toChar() // Degree symbol
            mSpecialGraphicsCharMap['`'.code] = 0x2B25.toChar() // Diamond
            mSpecialGraphicsCharMap['~'.code] = 0x2022.toChar() // Bullet point
            mSpecialGraphicsCharMap['y'.code] = 0x2264.toChar() // Less-than-or-equals sign (<=)
            mSpecialGraphicsCharMap['|'.code] = 0x2260.toChar() // Not equals sign (!=)
            mSpecialGraphicsCharMap['z'.code] = 0x2265.toChar() // Greater-than-or-equals sign (>=)
            mSpecialGraphicsCharMap['g'.code] = 0x00B1.toChar() // Plus-or-minus sign (+/-)
            mSpecialGraphicsCharMap['{'.code] = 0x03C0.toChar() // Lowercase Greek letter pi
            mSpecialGraphicsCharMap['.'.code] = 0x25BC.toChar() // Down arrow
            mSpecialGraphicsCharMap[','.code] = 0x25C0.toChar() // Left arrow
            mSpecialGraphicsCharMap['+'.code] = 0x25B6.toChar() // Right arrow
            mSpecialGraphicsCharMap['-'.code] = 0x25B2.toChar() // Up arrow
            mSpecialGraphicsCharMap['h'.code] = '#' // Board of squares
            mSpecialGraphicsCharMap['a'.code] = 0x2592.toChar() // Checkerboard
            mSpecialGraphicsCharMap['0'.code] = 0x2588.toChar() // Solid block
            mSpecialGraphicsCharMap['q'.code] = 0x2500.toChar() // Horizontal line (box drawing)
            mSpecialGraphicsCharMap['x'.code] = 0x2502.toChar() // Vertical line (box drawing)
            mSpecialGraphicsCharMap['m'.code] =
                0x2514.toChar() // Lower left hand corner (box drawing)
            mSpecialGraphicsCharMap['j'.code] =
                0x2518.toChar() // Lower right hand corner (box drawing)
            mSpecialGraphicsCharMap['l'.code] =
                0x250C.toChar() // Upper left hand corner (box drawing)
            mSpecialGraphicsCharMap['k'.code] =
                0x2510.toChar() // Upper right hand corner (box drawing)
            mSpecialGraphicsCharMap['w'.code] =
                0x252C.toChar() // T pointing downwards (box drawing)
            mSpecialGraphicsCharMap['u'.code] =
                0x2524.toChar() // T pointing leftwards (box drawing)
            mSpecialGraphicsCharMap['t'.code] =
                0x251C.toChar() // T pointing rightwards (box drawing)
            mSpecialGraphicsCharMap['v'.code] = 0x2534.toChar() // T pointing upwards (box drawing)
            mSpecialGraphicsCharMap['n'.code] =
                0x253C.toChar() // Large plus/lines crossing (box drawing)
            mSpecialGraphicsCharMap['o'.code] = 0x23BA.toChar() // Horizontal scanline 1
            mSpecialGraphicsCharMap['p'.code] = 0x23BB.toChar() // Horizontal scanline 3
            mSpecialGraphicsCharMap['r'.code] = 0x23BC.toChar() // Horizontal scanline 7
            mSpecialGraphicsCharMap['s'.code] = 0x23BD.toChar() // Horizontal scanline 9
        }

        /**
         * UTF-8 support
         */
        private const val UNICODE_REPLACEMENT_CHAR = 0xfffd

        /** This is not accurate, but it makes the terminal more useful on
         * small screens.
         */
        private const val DEFAULT_TO_AUTOWRAP_ENABLED = true
    }
}