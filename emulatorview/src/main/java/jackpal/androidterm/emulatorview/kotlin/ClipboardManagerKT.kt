package jackpal.androidterm.emulatorview.kotlin

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context

object ClipboardManagerKT {

    val ClipboardManager.hasTextX: Boolean
        get() = hasPrimaryClip() && primaryClipDescription!!.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)

    var ClipboardManager.textX: CharSequence?
        get() = primaryClip?.getItemAt(0)?.text
        set(value) {
            setPrimaryClip(ClipData.newPlainText("", value))
        }

    val Context.clipboardManager: ClipboardManager
        get() = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}