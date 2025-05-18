//From the desk of Frank P. Westlake; public domain.
package jackpal.androidterm.shortcuts

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.View.OnFocusChangeListener
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import jackpal.androidterm.R
import jackpal.androidterm.RemoteInterface
import jackpal.androidterm.RunShortcut
import jackpal.androidterm.TermDebug
import jackpal.androidterm.util.ShortcutEncryption
import java.io.File
import java.security.GeneralSecurityException

class AddShortcut : AppCompatActivity() {
    private val OP_MAKE_SHORTCUT = 1
    private val context: Context = this
    private var SP: SharedPreferences? = null
    private var ix = 0
    private val PATH = ix++
    private val ARGS = ix++
    private val NAME = ix++
    private val et = arrayOfNulls<EditText>(5)
    private var path: String? = null
    private var name = ""
    private val iconText = arrayOf<String>("")

    /**/////////////////////////////////////////////////////////////////// */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SP = PreferenceManager.getDefaultSharedPreferences(context)
        val action = intent.action
        if (action != null && action == "android.intent.action.CREATE_SHORTCUT") makeShortcut()
        else finish()
    }

    /**/////////////////////////////////////////////////////////////////// */
    fun makeShortcut() {
        if (path == null) path = ""
        val alert =
            AlertDialog.Builder(context)
        val lv = LinearLayout(context)
        lv.orientation = LinearLayout.VERTICAL
        var i = 0
        val n = et.size
        while (i < n) {
            et[i] = EditText(context)
            et[i]!!.setSingleLine(true)
            i++
        }
        if (!path!!.isEmpty()) et[0]!!.setText(path)
        et[PATH]!!.setHint(getString(R.string.addshortcut_command_hint)) //"command");
        et[NAME]!!.setText(name)
        et[ARGS]!!.setHint(getString(R.string.addshortcut_example_hint)) //"--example=\"a\"");
        et[ARGS]!!.onFocusChangeListener = OnFocusChangeListener { view: View?, focus: Boolean ->
            if (!focus) {
                var s: String? = null
                if (et[NAME]?.getText().toString().isEmpty()
                    && !(et[ARGS]?.getText().toString().also { s = it }).isEmpty()
                ) et[NAME]?.setText(s?.split("\\s".toRegex())?.dropLastWhile { it.isEmpty() }
                    ?.toTypedArray()[0])
            }
        }

        val btn_path = Button(context)
        btn_path.text = getString(R.string.addshortcut_button_find_command) //"Find command");
        btn_path.setOnClickListener(
            View.OnClickListener { p1: View? ->
                val lastPath = SP!!.getString("lastPath", null)
                val get = if (lastPath == null)
                    Environment.getExternalStorageDirectory()
                else
                    File(lastPath).getParentFile()
                val pickerIntent = Intent()
                if (SP!!.getBoolean("useInternalScriptFinder", false)) {
                    pickerIntent.setClass(applicationContext, FSNavigator::class.java)
                        .setData(Uri.fromFile(get))
                        .putExtra(
                            "title",
                            getString(R.string.addshortcut_navigator_title)
                        ) //"SELECT SHORTCUT TARGET")
                } else {
                    pickerIntent
                        .putExtra("CONTENT_TYPE", "*/*")
                        .setAction(Intent.ACTION_PICK)
                }
                startActivityForResult(pickerIntent, OP_MAKE_SHORTCUT)
            }
        )
        lv.addView(
            layoutTextViewH(
                getString(R.string.addshortcut_command_window_instructions),  //"Command window requires full path, no arguments. For other commands use Arguments window (ex: cd /sdcard)."
                null,
                false
            )
        )
        lv.addView(layoutViewViewH(btn_path, et[PATH]))
        lv.addView(layoutTextViewH(getString(R.string.addshortcut_arguments_label), et[ARGS]))
        lv.addView(layoutTextViewH(getString(R.string.addshortcut_shortcut_label), et[NAME]))

        val img = ImageView(context)
        img.setImageResource(R.drawable.ic_launcher)
        img.maxHeight = 100
        img.tag = -0x1
        img.maxWidth = 100
        img.setAdjustViewBounds(true)
        img.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
        val btn_color = Button(context)
        btn_color.text = getString(R.string.addshortcut_button_text_icon) //"Text icon");
        btn_color.setOnClickListener{
            p1: View? -> ColorValue(context, img, iconText)
        }

        lv.addView(
            layoutTextViewH(
                getString(R.string.addshortcut_text_icon_instructions),  //"Optionally create a text icon:"
                null,
                false
            )
        )
        lv.addView(layoutViewViewH(btn_color, img))
        val sv = ScrollView(context)
        sv.isFillViewport = true
        sv.addView(lv)

        alert.setView(sv)
        alert.setTitle(getString(R.string.addshortcut_title)) //"Term Shortcut");
        alert.setPositiveButton(
            android.R.string.yes,
            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                buildShortcut(
                    path,
                    et[ARGS]!!.getText().toString(),
                    et[NAME]!!.getText().toString(),
                    iconText[1],
                    (img.tag as Int?)!!
                )
            }
        )
        alert.setNegativeButton(
            android.R.string.cancel,
            DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int -> finish() }
        )
        alert.show()
    }

    /**/////////////////////////////////////////////////////////////////// */
    fun layoutTextViewH(text: String?, vw: View?): LinearLayout? {
        return (layoutTextViewH(text, vw, true))
    }

    fun layoutTextViewH(text: String?, vw: View?, attributes: Boolean): LinearLayout {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        val tv = TextView(context)
        tv.text = text
        if (attributes) tv.setTypeface(Typeface.DEFAULT_BOLD)
        if (attributes) tv.setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
        tv.setPadding(10, tv.paddingTop, 10, tv.paddingBottom)
        val lh = LinearLayout(context)
        lh.orientation = LinearLayout.HORIZONTAL
        lh.addView(tv, lp)
        if (vw != null) lh.addView(vw, lp)
        return (lh)
    }

    /**/////////////////////////////////////////////////////////////////// */
    fun layoutViewViewH(vw1: View?, vw2: View?): LinearLayout {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        val lh = LinearLayout(context)
        lh.orientation = LinearLayout.HORIZONTAL
        lh.addView(vw1, lp)
        if (vw2 != null) lh.addView(vw2, lp)
        return (lh)
    }

    /**/////////////////////////////////////////////////////////////////// */
    fun buildShortcut(
        path: String?,
        arguments: String?,
        shortcutName: String?,
        shortcutText: String?,
        shortcutColor: Int
    ) {
        var keys = ShortcutEncryption.getKeys(context)
        if (keys == null) {
            try {
                keys = ShortcutEncryption.generateKeys()
            } catch (e: GeneralSecurityException) {
                Log.e(TermDebug.LOG_TAG, "Generating shortcut encryption keys failed: $e")
                throw RuntimeException(e)
            }
            ShortcutEncryption.saveKeys(context, keys)
        }
        val cmd = StringBuilder()
        if (path != null && !path.isEmpty()) cmd.append(RemoteInterface.quoteForBash(path))
        if (arguments != null && !arguments.isEmpty()) cmd.append(" ").append(arguments)
        val cmdStr = cmd.toString()
        val cmdEnc: String?
        try {
            cmdEnc = ShortcutEncryption.encrypt(cmdStr, keys)
        } catch (e: GeneralSecurityException) {
            Log.e(TermDebug.LOG_TAG, "Shortcut encryption failed: $e")
            throw RuntimeException(e)
        }
        val target = Intent().setClass(context, RunShortcut::class.java)
        target.setAction(RunShortcut.ACTION_RUN_SHORTCUT)
        target.putExtra(RunShortcut.EXTRA_SHORTCUT_COMMAND, cmdEnc)
        target.putExtra(RunShortcut.EXTRA_WINDOW_HANDLE, shortcutName)
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val wrapper = Intent()
        wrapper.setAction("com.android.launcher.action.INSTALL_SHORTCUT")
        wrapper.putExtra(Intent.EXTRA_SHORTCUT_INTENT, target)
        if (shortcutName != null && !shortcutName.isEmpty()) {
            wrapper.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName)
        }
        if (shortcutText != null && !shortcutText.isEmpty()) {
            wrapper.putExtra(
                Intent.EXTRA_SHORTCUT_ICON,
                TextIcon.getTextIcon(
                    shortcutText,
                    shortcutColor,
                    96,
                    96
                )
            )
        } else {
            wrapper.putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_launcher)
            )
        }
        setResult(RESULT_OK, wrapper)
        finish()
    }

    /**/////////////////////////////////////////////////////////////////// */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        var uri: Uri? = null
        path = null
        if (requestCode == OP_MAKE_SHORTCUT) {
            if (data != null && (data.data.also { uri = it }) != null && (uri?.path
                    .also { path = it }) != null
            ) {
                SP!!.edit { putString("lastPath", path) }
                et[PATH]!!.setText(path)
                name = path!!.replace(".*/".toRegex(), "")
                if (et[NAME]!!.getText().toString().isEmpty()) et[NAME]!!.setText(name)
                if (iconText[0].isEmpty()) iconText[0] = name
            } else finish()
        }
    } /**/////////////////////////////////////////////////////////////////// */
}