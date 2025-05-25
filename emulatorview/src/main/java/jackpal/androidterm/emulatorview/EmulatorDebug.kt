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

/**
 * Debug settings.
 */
object EmulatorDebug {
    /**
     * Set to true to add debugging code and logging.
     */
    const val DEBUG: Boolean = false

    /**
     * Set to true to log IME calls.
     */
    const val LOG_IME: Boolean = DEBUG and false

    /**
     * Set to true to log each character received from the remote process to the
     * android log, which makes it easier to debug some kinds of problems with
     * emulating escape sequences and control codes.
     */
    const val LOG_CHARACTERS_FLAG: Boolean = DEBUG and false

    /**
     * Set to true to log unknown escape sequences.
     */
    const val LOG_UNKNOWN_ESCAPE_SEQUENCES: Boolean = DEBUG and false

    /**
     * The tag we use when logging, so that our messages can be distinguished
     * from other messages in the log. Public because it's used by several
     * classes.
     */
    const val LOG_TAG: String = "EmulatorView"

    @JvmStatic
    fun bytesToString(data: ByteArray, base: Int, length: Int): String {
        val buf = StringBuilder()
        for (i in 0..<length) {
            val b = data[base + i]
            if (b < 32 || b > 126) {
                buf.append(String.format("\\x%02x", b))
            } else {
                buf.append(Char(b.toUShort()))
            }
        }
        return buf.toString()
    }
}