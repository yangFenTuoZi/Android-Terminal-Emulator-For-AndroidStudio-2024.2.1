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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import jackpal.androidterm.emulatorview.UpdateCallback
import jackpal.androidterm.util.SessionList

open class WindowListAdapter(val mSessions: SessionList?) : BaseAdapter(), UpdateCallback {

    var mDialog: AlertDialog? = null
    init {
        if (mSessions != null) {
            mSessions.addCallback(this)
            mSessions.addTitleChangedListener(this)
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

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val child = LayoutInflater.from(parent.context).inflate(R.layout.window_list_item, parent, false)
        val sessions = mSessions
        val closePosition = position
        child?.findViewById<View>(R.id.window_list_close)?.setOnClickListener {
            mDialog?.dismiss()
            val session = sessions?.removeAt(closePosition)
            if (session != null) {
                session.finish()
                notifyDataSetChanged()
            }
        }
        child?.findViewById<TextView>(R.id.window_list_label)?.apply {
            text = getSessionTitle(position, parent.context.getString(R.string.window_title, position + 1))
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

