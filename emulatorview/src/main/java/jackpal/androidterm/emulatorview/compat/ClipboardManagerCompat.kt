package jackpal.androidterm.emulatorview.compat

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context

class ClipboardManagerCompat(context: Context) {
    private val clip: ClipboardManager = context.applicationContext
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var text: CharSequence?
        get() = clip.primaryClip?.getItemAt(0)?.text
        set(text) {
            clip.setPrimaryClip(ClipData.newPlainText("", text))
        }

    fun hasText(): Boolean {
        return (clip.hasPrimaryClip() && clip.primaryClipDescription!!
            .hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))
    }
}