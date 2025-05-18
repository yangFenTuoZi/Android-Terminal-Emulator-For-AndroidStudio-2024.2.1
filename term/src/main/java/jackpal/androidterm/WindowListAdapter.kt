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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import jackpal.androidterm.emulatorview.UpdateCallback
import jackpal.androidterm.util.SessionList

open class WindowListAdapter(sessions: SessionList?) : BaseAdapter(), UpdateCallback {
    private var mSessions: SessionList? = null

    init {
        setSessions(sessions)
    }

    fun setSessions(sessions: SessionList?) {
        mSessions = sessions
        if (sessions != null) {
            sessions.addCallback(this)
            sessions.addTitleChangedListener(this)
        } else {
            onUpdate()
        }
    }

    override fun getCount(): Int {
        return mSessions?.size ?: 0
    }

    override fun getItem(position: Int): Any? {
        return mSessions?.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    protected fun getSessionTitle(position: Int, defaultTitle: String): String {
        val session = mSessions?.get(position)
        return if (session is GenericTermSession) {
            session.getTitle(defaultTitle)
        } else {
            defaultTitle
        }
    }

    override fun getView(position: Int, convertView: View, parent: ViewGroup): View {
        val act = findActivityFromContext(parent.context)
        val child = act?.layoutInflater?.inflate(R.layout.window_list_item, parent, false)
        val close = child?.findViewById<View>(R.id.window_list_close)
        val label = child?.findViewById<TextView>(R.id.window_list_label)
        val defaultTitle = act?.getString(R.string.window_title, position + 1) ?: "Window ${position + 1}"
        label?.text = getSessionTitle(position, defaultTitle)
        val sessions = mSessions
        val closePosition = position
        close?.setOnClickListener {
            val session = sessions?.removeAt(closePosition)
            if (session != null) {
                session.finish()
                notifyDataSetChanged()
            }
        }
        return child!!
    }

    override fun onUpdate() {
        notifyDataSetChanged()
    }

    companion object {
        private fun findActivityFromContext(context: Context?): Activity? {
            return when (context) {
                null -> null
                is Activity -> context
                is ContextWrapper -> findActivityFromContext(context.baseContext)
                else -> null
            }
        }
    }
}

