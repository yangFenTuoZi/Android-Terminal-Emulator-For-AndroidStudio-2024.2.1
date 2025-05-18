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

package jackpal.androidterm

import android.content.Intent

/*
 * New procedure for launching a command in ATE.
 * Build the path and arguments into a Uri and set that into Intent.data.
 * intent.data(new Uri.Builder().setScheme("file").setPath(path).setFragment(arguments))
 *
 * The old procedure of using Intent.Extra is still available but is discouraged.
 */
class RunScript : RemoteInterface() {
    companion object {
        private const val ACTION_RUN_SCRIPT = "jackpal.androidterm.RUN_SCRIPT"
        private const val EXTRA_WINDOW_HANDLE = "jackpal.androidterm.window_handle"
        private const val EXTRA_INITIAL_COMMAND = "jackpal.androidterm.iInitialCommand"
    }

    override fun handleIntent() {
        val service = termService
        if (service == null) {
            finish()
            return
        }

        val myIntent = intent
        val action = myIntent.action
        if (action == ACTION_RUN_SCRIPT) {
            // 有权限的调用者请求我们运行脚本
            var handle = myIntent.getStringExtra(EXTRA_WINDOW_HANDLE)
            var command: String? = null
            // 先从 Intent.data 取 path，如果没有则回退到 EXTRA_INITIAL_COMMAND
            val uri = myIntent.data
            if (uri != null) {
                val s = uri.scheme
                if (s != null && s.equals("file", ignoreCase = true)) {
                    command = uri.path
                    if (command == null) command = ""
                    if (command.isNotEmpty()) command = quoteForBash(command)
                    // 拼接参数
                    val frag = uri.fragment
                    if (frag != null) command += " $frag"
                }
            }
            // 如果 Intent.data 没有用，则回退到旧方法
            if (command == null) command = myIntent.getStringExtra(EXTRA_INITIAL_COMMAND)
            handle = if (handle != null) {
                appendToWindow(handle, command)
            } else {
                openNewWindow(command)
            }
            val result = Intent()
            result.putExtra(EXTRA_WINDOW_HANDLE, handle)
            setResult(RESULT_OK, result)
            finish()
        } else {
            super.handleIntent()
        }
    }
}

