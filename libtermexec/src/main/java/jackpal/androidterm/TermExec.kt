package jackpal.androidterm

import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.util.Hashtable

/**
 * Utility methods for creating and managing a subprocess. This class differs from
 * {@link java.lang.ProcessBuilder} in that a pty is used to communicate with the subprocess.
 * <p>
 * Pseudo-terminals are a powerful Unix feature, that allows programs to interact with other programs
 * they start in slightly more human-like way. For example, a pty owner can send ^C (aka SIGINT)
 * to attached shell, even if said shell runs under a different user ID.
 */
class TermExec {
    companion object {
        // Warning: bump the library revision, when an incompatible change happens
        init {
            System.loadLibrary("term_exec")
        }
        const val SERVICE_ACTION_V1 = "jackpal.androidterm.action.START_TERM.v1"
        @JvmStatic
        external fun waitFor(processId: Int): Int
        @JvmStatic
        external fun sendSignal(processId: Int, signal: Int)
        @JvmStatic
        @Throws(IOException::class)
        fun createSubprocess(
            masterFd: ParcelFileDescriptor,
            cmd: String,
            args: Array<String>,
            envVars: Array<String>
        ): Int {
            val integerFd = FdHelperHoneycomb.getFd(masterFd)
            return createSubprocessInternal(cmd, args, envVars, integerFd)
        }
        @JvmStatic
        private external fun createSubprocessInternal(
            cmd: String,
            args: Array<String>,
            envVars: Array<String>,
            masterFd: Int
        ): Int
    }

    private val command: MutableList<String>
    private val environment: MutableMap<String, String>

    constructor(vararg command: String) : this(command.toList())
    constructor(command: List<String>) {
        this.command = command.toMutableList()
        this.environment = Hashtable(System.getenv())
    }

    fun command(): List<String> = command
    fun environment(): Map<String, String> = environment

    fun command(vararg command: String): TermExec = command(command.toList())
    fun command(command: List<String>): TermExec {
        this.command.clear()
        this.command.addAll(command)
        return this
    }

    /**
     * Start the process and attach it to the pty, corresponding to given file descriptor.
     * You have to obtain this file descriptor yourself by calling
     * {@link android.os.ParcelFileDescriptor#open} on special terminal multiplexer
     * device (located at /dev/ptmx).
     * <p>
     * Callers are responsible for closing the descriptor.
     *
     * @return the PID of the started process.
     */
    @Throws(IOException::class)
    fun start(ptmxFd: ParcelFileDescriptor): Int {
        if (Looper.getMainLooper() == Looper.myLooper())
            throw IllegalStateException("This method must not be called from the main thread!")
        if (command.isEmpty())
            throw IllegalStateException("Empty command!")
        val cmd = command.removeAt(0)
        val cmdArray = command.toTypedArray()
        val envArray = Array(environment.size) { "" }
        var i = 0
        for ((key, value) in environment) {
            envArray[i++] = "$key=$value"
        }
        return createSubprocess(ptmxFd, cmd, cmdArray, envArray)
    }
}

// prevents runtime errors on old API versions with ruthless verifier
object FdHelperHoneycomb {
    fun getFd(descriptor: ParcelFileDescriptor): Int = descriptor.fd
}

