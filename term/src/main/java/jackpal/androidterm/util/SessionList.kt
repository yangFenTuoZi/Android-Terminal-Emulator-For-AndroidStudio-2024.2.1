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

package jackpal.androidterm.util

import jackpal.androidterm.emulatorview.TermSession
import jackpal.androidterm.emulatorview.UpdateCallback

/**
 * An ArrayList of TermSessions which allows users to register callbacks in
 * order to be notified when the list is changed.
 */
class SessionList : ArrayList<TermSession> {
    private val callbacks = mutableListOf<UpdateCallback>()
    private val titleChangedListeners = mutableListOf<UpdateCallback>()
    private val mTitleChangedListener = UpdateCallback { notifyTitleChanged() }

    constructor() : super()
    constructor(capacity: Int) : super(capacity)

    fun addCallback(callback: UpdateCallback) {
        callbacks.add(callback)
        callback.onUpdate()
    }

    fun removeCallback(callback: UpdateCallback): Boolean {
        return callbacks.remove(callback)
    }

    private fun notifyChange() {
        for (callback in callbacks) {
            callback.onUpdate()
        }
    }

    fun addTitleChangedListener(listener: UpdateCallback) {
        titleChangedListeners.add(listener)
        listener.onUpdate()
    }

    fun removeTitleChangedListener(listener: UpdateCallback): Boolean {
        return titleChangedListeners.remove(listener)
    }

    private fun notifyTitleChanged() {
        for (listener in titleChangedListeners) {
            listener.onUpdate()
        }
    }

    override fun add(element: TermSession): Boolean {
        val result = super.add(element)
        element.setTitleChangedListener(mTitleChangedListener)
        notifyChange()
        return result
    }

    override fun add(index: Int, element: TermSession) {
        super.add(index, element)
        element.setTitleChangedListener(mTitleChangedListener)
        notifyChange()
    }

    override fun addAll(elements: Collection<TermSession>): Boolean {
        val result = super.addAll(elements)
        for (session in elements) {
            session.setTitleChangedListener(mTitleChangedListener)
        }
        notifyChange()
        return result
    }

    override fun addAll(index: Int, elements: Collection<TermSession>): Boolean {
        val result = super.addAll(index, elements)
        for (session in elements) {
            session.setTitleChangedListener(mTitleChangedListener)
        }
        notifyChange()
        return result
    }

    override fun clear() {
        for (session in this) {
            session.setTitleChangedListener(null)
        }
        super.clear()
        notifyChange()
    }

    override fun removeAt(index: Int): TermSession {
        val obj = super.removeAt(index)
        obj.setTitleChangedListener(null)
        notifyChange()
        return obj
    }

    override fun remove(element: TermSession): Boolean {
        val result = super.remove(element)
        if (result) {
            element.setTitleChangedListener(null)
            notifyChange()
        }
        return result
    }

    override fun set(index: Int, element: TermSession): TermSession {
        val old = super.set(index, element)
        element.setTitleChangedListener(mTitleChangedListener)
        old.setTitleChangedListener(null)
        notifyChange()
        return old
    }
}

