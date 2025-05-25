package jackpal.androidterm

import android.os.ParcelFileDescriptor
import android.text.TextUtils
import jackpal.androidterm.util.TermSettings

class BoundSession(
    ptmxFd: ParcelFileDescriptor,
    settings: TermSettings,
    private val issuerTitle: String
) : GenericTermSession(ptmxFd, settings, true) {
    private var fullyInitialized = false

    init {
        termIn = ParcelFileDescriptor.AutoCloseInputStream(ptmxFd)
        termOut = ParcelFileDescriptor.AutoCloseOutputStream(ptmxFd)
    }

    override var title: String?
        get() {
            val extraTitle = super.title
            return if (TextUtils.isEmpty(extraTitle)) {
                issuerTitle
            } else {
                "$issuerTitle — $extraTitle"
            }
        }
        set(value) {
            super.title = value
        }

    override fun initializeEmulator(columns: Int, rows: Int) {
        super.initializeEmulator(columns, rows)
        fullyInitialized = true
    }

    override fun isFailFast(): Boolean {
        return !fullyInitialized
    }
}

