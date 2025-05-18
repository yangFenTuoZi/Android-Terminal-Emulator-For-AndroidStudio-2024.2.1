/*
 * Copyright (C) 2015 Steven Luo
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

package jackpal.androidterm

import android.content.Intent
import android.util.Log
import jackpal.androidterm.util.ShortcutEncryption
import java.security.GeneralSecurityException

class RunShortcut : RemoteInterface() {
    companion object {
        const val ACTION_RUN_SHORTCUT = "jackpal.androidterm.RUN_SHORTCUT"
        const val EXTRA_WINDOW_HANDLE = "jackpal.androidterm.window_handle"
        const val EXTRA_SHORTCUT_COMMAND = "jackpal.androidterm.iShortcutCommand"
    }

    override fun handleIntent() {
        val service = termService
        if (service == null) {
            finish()
            return
        }

        val myIntent = intent
        val action = myIntent.action
        if (action == ACTION_RUN_SHORTCUT) {
            val encCommand = myIntent.getStringExtra(EXTRA_SHORTCUT_COMMAND)
            if (encCommand == null) {
                Log.e(TermDebug.LOG_TAG, "No command provided in shortcut!")
                finish()
                return
            }

            // Decrypt and verify the command
            val keys = ShortcutEncryption.getKeys(this)
            if (keys == null) {
                // No keys -- no valid shortcuts can exist
                Log.e(TermDebug.LOG_TAG, "No shortcut encryption keys found!")
                finish()
                return
            }
            val command: String = try {
                ShortcutEncryption.decrypt(encCommand, keys)
            } catch (e: GeneralSecurityException) {
                Log.e(TermDebug.LOG_TAG, "Invalid shortcut: $e")
                finish()
                return
            }

            var handle = myIntent.getStringExtra(EXTRA_WINDOW_HANDLE)
            handle = if (handle != null) {
                // Target the request at an existing window if open
                appendToWindow(handle, command)
            } else {
                // Open a new window
                openNewWindow(command)
            }
            val result = Intent()
            result.putExtra(EXTRA_WINDOW_HANDLE, handle)
            setResult(RESULT_OK, result)
        }
        finish()
    }
}

