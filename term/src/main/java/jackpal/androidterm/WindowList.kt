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

package jackpal.androidterm

import android.app.ActionBar
import android.app.ListActivity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.AttributeSet
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import androidx.appcompat.widget.AppCompatImageView
import jackpal.androidterm.util.SessionList

class WindowList : ListActivity() {
    private var sessions: SessionList? = null
    private var mWindowListAdapter: WindowListAdapter? = null
    private var mTermService: TermService? = null

    /**
     * View which isn't automatically in the pressed state if its parent is
     * pressed.  This allows the window's entry to be pressed without the close
     * button being triggered.
     * Idea and code shamelessly borrowed from the Android browser's tabs list.
     *
     * Used by layout xml.
     */
    class CloseButton : AppCompatImageView {
        constructor(context: Context) : super(context)
        constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
        constructor(context: Context, attrs: AttributeSet, style: Int) : super(context, attrs, style)

        override fun setPressed(pressed: Boolean) {
            if (pressed && (parent as View).isPressed) {
                return
            }
            super.setPressed(pressed)
        }
    }

    private val mTSConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TermService.TSBinder
            mTermService = binder.service
            populateList()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mTermService = null
        }
    }

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        val listView = listView
        val newWindow = layoutInflater.inflate(R.layout.window_list_new_window, listView, false)
        listView.addHeaderView(newWindow, null, true)

        setResult(RESULT_CANCELED)

        // Display up indicator on action bar home button
        val bar = actionBar
        bar?.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP)
    }

    override fun onResume() {
        super.onResume()

        val tsIntent = Intent(this, TermService::class.java)
        if (!bindService(tsIntent, mTSConnection, BIND_AUTO_CREATE)) {
            Log.w(TermDebug.LOG_TAG, "bind to service failed!")
        }
    }

    override fun onPause() {
        super.onPause()

        val adapter = mWindowListAdapter
        if (sessions != null) {
            adapter?.let {
                sessions?.removeCallback(it)
                sessions?.removeTitleChangedListener(it)
            }
        }
        adapter?.setSessions(null)
        unbindService(mTSConnection)
    }

    private fun populateList() {
        sessions = mTermService?.sessions
        var adapter = mWindowListAdapter
        if (adapter == null) {
            adapter = WindowListAdapter(sessions)
            listAdapter = adapter
            mWindowListAdapter = adapter
        } else {
            adapter.setSessions(sessions)
        }
        sessions?.addCallback(adapter)
        sessions?.addTitleChangedListener(adapter)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val data = Intent()
        data.putExtra(Term.EXTRA_WINDOW_ID, position - 1)
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

