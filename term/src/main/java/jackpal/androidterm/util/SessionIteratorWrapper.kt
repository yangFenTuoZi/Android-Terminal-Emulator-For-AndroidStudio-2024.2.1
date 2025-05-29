package jackpal.androidterm.util

import android.widget.Toast
import jackpal.androidterm.GenericTermSession
import jackpal.androidterm.R
import jackpal.androidterm.emulatorview.EmulatorView
import jackpal.androidterm.emulatorview.TermSession
import jackpal.androidterm.emulatorview.UpdateCallback
import java.util.LinkedList

class SessionIteratorWrapper(
    val sessionList: SessionList,
    val emulatorView: EmulatorView
) : ListIterator<TermSession> {

    private var currentIndex: Int = 0
    private var mToast: Toast? = null
    private val callbacks = LinkedList<UpdateCallback>()

    val index get() = currentIndex

    fun addCallback(callback: UpdateCallback) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: UpdateCallback) {
        callbacks.remove(callback)
    }

    private fun notifyChange() {
        for (callback in callbacks) {
            callback.onUpdate()
        }
    }

    fun pauseCurrentView() {
        emulatorView.onPause()
    }

    fun resumeCurrentView() {
        emulatorView.onResume()
        emulatorView.requestFocus()
    }

    private fun showTitle() {
        if (sessionList.isEmpty()) return
        val session = emulatorView.termSession ?: return
        var title = emulatorView.context.getString(R.string.window_title, currentIndex + 1)
        if (session is GenericTermSession) {
            title = session.getTitle(title)
        }
        mToast?.cancel()
        mToast = Toast.makeText(emulatorView.context, title, Toast.LENGTH_SHORT).apply { show() }
    }

    fun showPrevious() {
        setDisplayedChild(previousIndex())
    }

    fun showNext() {
        setDisplayedChild(nextIndex())
    }

    fun setDisplayedChild(position: Int) {
        pauseCurrentView()
        emulatorView.attachSession(sessionList[position])
        currentIndex = position
        showTitle()
        resumeCurrentView()
        notifyChange()
    }

    override fun hasNext(): Boolean = sessionList.isNotEmpty()

    override fun next(): TermSession {
        if (sessionList.isEmpty()) throw NoSuchElementException("List is empty")
        val index = nextIndex()
        val element = sessionList[index]
        currentIndex = index
        return element
    }

    override fun nextIndex(): Int = if (sessionList.isEmpty()) 0 else (currentIndex + 1) % sessionList.size

    override fun hasPrevious(): Boolean = sessionList.isNotEmpty()

    override fun previous(): TermSession {
        if (sessionList.isEmpty()) throw NoSuchElementException("List is empty")
        val index = previousIndex()
        val element = sessionList[index]
        currentIndex = index
        return element
    }

    override fun previousIndex(): Int = if (sessionList.isEmpty()) 0 else (currentIndex - 1 + sessionList.size) % sessionList.size
}