package jackpal.androidterm.shortcuts

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import jackpal.androidterm.R
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.Locale
import kotlin.Boolean
import kotlin.Comparator
import kotlin.Int
import kotlin.String
import kotlin.also
import kotlin.isInitialized
import kotlin.let

class FSNavigator : AppCompatActivity() {
    private val ACTION_THEME_SWAP = 0x00000100
    private val BUTTON_SIZE = 150
    private val context: Context = this
    private val textLg = 24f
    private var theme = android.R.style.Theme
    private lateinit var SP: SharedPreferences
    private lateinit var cd: File
    private lateinit var extSdCardFile: File
    private lateinit var extSdCard: String
    private lateinit var cachedFileView: HashMap<Int, LinearLayout>
    private lateinit var cachedDirectoryView: HashMap<Int, LinearLayout>
    private lateinit var cachedDividerView: HashMap<Int, TextView>
    private var countFileView = 0
    private var countDirectoryView = 0
    private var countDividerView = 0
    private lateinit var contentView: LinearLayout
    private lateinit var titleView: LinearLayout
    private lateinit var pathEntryView: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(getString(R.string.fsnavigator_title))
        SP = PreferenceManager.getDefaultSharedPreferences(context)
        theme = SP.getInt("theme", theme)
        setTheme(theme)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        val intent = intent
        extSdCardFile = Environment.getExternalStorageDirectory()
        extSdCard = getCanonicalPath(extSdCardFile)
        val uri = intent.data
        val path = uri?.path
        if (path == null) chdir(extSdCard)
        if (intent.hasExtra("title")) setTitle(intent.getStringExtra("title"))

        titleView = directoryEntry("..")
        pathEntryView = fileEntry(null)
        contentView = makeContentView()
        cachedDirectoryView = HashMap()
        cachedFileView = HashMap()
        cachedDividerView = HashMap()
    }

    override fun onPause() {
        super.onPause()
        doPause()
    }

    private fun doPause() {
        SP.edit(commit = true) { putString("lastDirectory", getCanonicalPath(cd)) }
    }

    override fun onResume() {
        super.onResume()
        doResume()
    }

    private fun doResume() {
        makeView()
    }

    private fun swapTheme() {
        theme = when (theme) {
            android.R.style.Theme -> android.R.style.Theme_Light
            android.R.style.Theme_Light -> android.R.style.Theme
            else -> return
        }
        SP.edit(commit = true) { putInt("theme", theme) }
        startActivityForResult(intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT), -1)
        finish()
    }

    private fun ifAvailable(goTo: String): String {
        return if (goTo.startsWith(extSdCard)) {
            val s = Environment.getExternalStorageState()
            if (s == Environment.MEDIA_MOUNTED || s == Environment.MEDIA_MOUNTED_READ_ONLY) {
                goTo
            } else {
                toast(getString(R.string.fsnavigator_no_external_storage), 1)
                extSdCard
            }
        } else goTo
    }

    private fun chdir(file: File): File {
        val path = ifAvailable(getCanonicalPath(file))
        System.setProperty("user.dir", path)
        cd = File(path)
        return cd
    }

    private fun chdir(path: String): File = chdir(File(path))

    private fun entryDividerH(): TextView {
        val tv = if (countDividerView < cachedDividerView.size) {
            cachedDividerView[countDividerView]!!
        } else {
            TextView(context).also {
                it.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1, 1f
                )
                cachedDividerView[countDividerView] = it
            }
        }
        ++countDividerView
        return tv
    }

    private val fileListener = View.OnClickListener { view ->
        val path = view.tag as? String
        if (path != null) {
            setResult(RESULT_OK, intent.setData(Uri.fromFile(File(cd, path))))
            finish()
        }
    }

    private fun fileView(entryWindow: Boolean): LinearLayout {
        val ll = LinearLayout(context)
        ll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        ll.orientation = LinearLayout.HORIZONTAL
        ll.gravity = Gravity.FILL
        val tv: TextView
        if (entryWindow) {
            tv = EditText(context)
            tv.hint = getString(R.string.fsnavigator_optional_enter_path)
            tv.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                2f
            )
            tv.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    val path = tv.text.toString()
                    val file = File(getCanonicalPath(path))
                    chdir(file.parentFile ?: file)
                    if (file.isFile) {
                        setResult(RESULT_OK, intent.setData(Uri.fromFile(file)))
                        finish()
                    } else {
                        chdir(file)
                        makeView()
                    }
                    true
                } else false
            }
            ll.addView(tv)
        } else {
            tv = TextView(context)
            tv.isClickable = true
            tv.isLongClickable = true
            tv.setOnClickListener(fileListener)
            tv.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            val hv = HorizontalScrollView(context)
            hv.isFillViewport = true
            hv.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                BUTTON_SIZE,
                7f
            )
            hv.addView(tv)
            ll.addView(hv)
        }
        tv.isFocusable = true
        tv.setSingleLine()
        tv.textSize = textLg
        tv.setTypeface(Typeface.SERIF, Typeface.BOLD)
        tv.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        tv.setPadding(10, 5, 10, 5)
        tv.id = R.id.textview
        return ll
    }

    private fun fileEntry(entry: String?): LinearLayout {
        val ll = if (entry == null) fileView(true)
        else if (countFileView < cachedFileView.size) cachedFileView[countFileView]!!
        else fileView(false).also { cachedFileView[countFileView] = it }
        ++countFileView
        val tv = ll.findViewById<TextView>(R.id.textview)
        tv.text = entry ?: ""
        tv.tag = entry ?: ""
        return ll
    }

    private fun imageViewFolder(up: Boolean): ImageView {
        val b1 = ImageView(context)
        b1.isClickable = true
        b1.isFocusable = true
        b1.id = R.id.imageview
        b1.layoutParams = LinearLayout.LayoutParams(120, 120, 1f)
        b1.setImageResource(if (up) R.drawable.ic_folderup else R.drawable.ic_folder)
        b1.setOnClickListener(directoryListener)
        b1.scaleType = ImageView.ScaleType.CENTER_INSIDE
        return b1
    }

    private val directoryListener = View.OnClickListener { view ->
        val path = view.tag as? String
        if (path != null) {
            val file = File(path)
            if (file.isFile) {
                setResult(RESULT_OK, intent.setData(Uri.fromFile(file)))
                finish()
            } else chdir(file)
            makeView()
        }
    }

    private fun directoryView(up: Boolean): LinearLayout {
        val b1 = imageViewFolder(up)
        val tv = TextView(context)
        tv.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        tv.isClickable = true
        tv.isLongClickable = true
        tv.isFocusable = true
        tv.setOnClickListener(directoryListener)
        tv.maxLines = 1
        tv.textSize = textLg
        tv.setPadding(10, 5, 10, 5)
        tv.id = R.id.textview
        tv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            BUTTON_SIZE,
            1f
        )
        val hv = HorizontalScrollView(context)
        hv.addView(tv)
        hv.isFillViewport = true
        hv.isFocusable = true
        hv.setOnClickListener(directoryListener)
        hv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            BUTTON_SIZE,
            7f
        )
        val ll = LinearLayout(context)
        ll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            BUTTON_SIZE,
            2f
        )
        ll.orientation = LinearLayout.HORIZONTAL
        ll.gravity = Gravity.FILL
        ll.setOnClickListener(directoryListener)
        ll.addView(b1)
        ll.addView(hv)
        return ll
    }

    private fun directoryEntry(name: String): LinearLayout {
        val up = name == ".."
        val ll = if (up) directoryView(up)
        else if (countDirectoryView < cachedDirectoryView.size) cachedDirectoryView[countDirectoryView]!!
        else directoryView(up).also { cachedDirectoryView[countDirectoryView] = it }
        ++countDirectoryView
        val tv = ll.findViewById<TextView>(R.id.textview)
        tv.tag = name
        tv.text = if (up) "[${cd.path}]" else name
        ll.findViewById<ImageView>(R.id.imageview).tag = name
        return ll
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            true
        } else super.onKeyUp(keyCode, event)
    }

    private fun makeContentView(): LinearLayout {
        val ll = LinearLayout(context)
        ll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        ll.id = R.id.mainview
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.FILL
        val sv = ScrollView(context)
        sv.id = R.id.scrollview
        sv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        sv.addView(ll)
        val bg = LinearLayout(context)
        bg.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        bg.orientation = LinearLayout.VERTICAL
        bg.gravity = Gravity.FILL
        bg.tag = ll
        bg.addView(
            titleView,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        bg.addView(sv)
        bg.addView(
            pathEntryView,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return bg
    }

    private fun makeView() {
        countDirectoryView = 0
        countFileView = 0
        countDividerView = 0
        val sv = contentView.findViewById<ScrollView>(R.id.scrollview)
        val ll = sv.findViewById<LinearLayout>(R.id.mainview)
        ll.removeAllViews()
        if (!::cd.isInitialized) chdir("/")
        var path = getCanonicalPath(cd)
        if (path == "") chdir(path.also { path = "/" })
        if (path == "/") {
            titleView.visibility = View.GONE
        } else {
            titleView.visibility = View.VISIBLE
            titleView.requestLayout()
            titleView.findViewById<TextView>(R.id.textview).text = "[${cd.path}]"
        }
        val zd = cd.list { file, name -> File(file, name).isDirectory }
        zd?.let {
            Arrays.sort(it, 0, it.size, stringSortComparator)
            for (i in it.indices) {
                if (it[i] == ".") continue
                ll.addView(directoryEntry(it[i]))
                ll.addView(entryDividerH())
            }
        }
        val zf = cd.list { file, name -> !File(file, name).isDirectory }
        zf?.let {
            Arrays.sort(it, 0, it.size, stringSortComparator)
            for (i in it.indices) {
                ll.addView(fileEntry(it[i]))
                ll.addView(entryDividerH())
            }
        }
        pathEntryView.findViewById<TextView>(R.id.textview).text = ""
        sv.scrollTo(0, 0)
        setContentView(contentView)
    }

    private val stringSortComparator = Comparator<String> { a, b ->
        a.lowercase(Locale.getDefault()).compareTo(b.lowercase(Locale.getDefault()))
    }

    fun getCanonicalPath(path: String): String = getCanonicalPath(File(path))
    fun getCanonicalPath(file: File): String = try {
        file.canonicalPath
    } catch (e: IOException) {
        file.path
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menu.add(0, ACTION_THEME_SWAP, 0, getString(R.string.fsnavigator_change_theme))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        super.onOptionsItemSelected(item)
        return doOptionsItem(item.itemId)
    }

    private fun doOptionsItem(itemId: Int): Boolean {
        return when (itemId) {
            ACTION_THEME_SWAP -> {
                swapTheme()
                true
            }

            else -> false
        }
    }

    private fun toast(message: String, duration: Int) {
        runOnUiThread {
            Toast.makeText(
                context,
                message,
                if (duration == 0) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
        }
    }
}

