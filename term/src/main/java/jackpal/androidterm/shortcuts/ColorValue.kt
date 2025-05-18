// ...existing code...
package jackpal.androidterm.shortcuts

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.view.Gravity
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import jackpal.androidterm.R

class ColorValue(
    private val context: Context,
    private val imgview: ImageView,
    private val result: Array<String>
) : CompoundButton.OnCheckedChangeListener {
    private var value: EditText? = null
    private val color = intArrayOf(0xFF, 0, 0, 0)
    private var started = false
    private lateinit var builder: AlertDialog.Builder
    private var barLock = false
    private val locks = booleanArrayOf(false, false, false, false)
    private val MP = LinearLayout.LayoutParams.MATCH_PARENT
    private val WC = LinearLayout.LayoutParams.WRAP_CONTENT
    private var imgtext: String = result[0]

    init {
        colorValue()
    }

    fun colorValue() {
        val arraySizes = 4
        builder = AlertDialog.Builder(context)
        val lv = LinearLayout(context)
        lv.orientation = LinearLayout.VERTICAL
        val lab = arrayOf(
            context.getString(R.string.colorvalue_letter_alpha) + " ",
            context.getString(R.string.colorvalue_letter_red) + " ",
            context.getString(R.string.colorvalue_letter_green) + " ",
            context.getString(R.string.colorvalue_letter_blue) + " "
        )
        val clr = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt())
        val n = imgview.tag as Int
        for (i in 0 until arraySizes) color[i] = (n shr (24 - i * 8)) and 0xFF
        val lt = TextView(context)
        lt.text = context.getString(R.string.colorvalue_label_lock_button_column)
        lt.setPadding(lt.paddingLeft, lt.paddingTop, 5, lt.paddingBottom)
        lt.gravity = Gravity.END
        value = EditText(context)
        value!!.setText(imgtext)
        value!!.setSingleLine(false)
        value!!.gravity = Gravity.CENTER
        value!!.setTextColor(imgview.tag as Int)
        value!!.setBackgroundColor((0xFF shl 24) or 0x007799)
        val vh = LinearLayout(context)
        vh.orientation = LinearLayout.HORIZONTAL
        vh.gravity = Gravity.CENTER_HORIZONTAL
        vh.addView(value)
        value!!.hint = context.getString(R.string.colorvalue_icon_text_entry_hint)
        lv.addView(vh)
        lv.addView(lt)
        val sb = Array(arraySizes + 1) { SeekBar(context) }
        val lk = Array(arraySizes) { CheckBox(context) }
        val hexWindow = Array(arraySizes) { TextView(context) }
        for (i in 0 until arraySizes) {
            val lh = LinearLayout(context)
            lh.gravity = Gravity.CENTER_VERTICAL
            val tv = TextView(context)
            tv.typeface = Typeface.MONOSPACE
            tv.text = lab[i]
            tv.setTextColor(clr[i])
            sb[i] = SeekBar(context)
            sb[i].max = 0xFF
            sb[i].progress = color[i]
            sb[i].secondaryProgress = color[i]
            sb[i].tag = i
            sb[i].setBackgroundColor((0xFF shl 24) or (color[i] shl (24 - i * 8)))
            sb[i].layoutParams = LinearLayout.LayoutParams(WC, WC, 1f)
            sb[i].setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    doProgressChanged(seekBar, progress, fromUser)
                }
                private fun doProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser && started) {
                        val me = seekBar.tag as Int
                        val k = (color[0] shl 24) or (color[1] shl 16) or (color[2] shl 8) or color[3]
                        value!!.setTextColor(k)
                        val (start, end) = if (barLock && locks[me]) 0 to arraySizes - 1 else me to me
                        for (i in start..end) {
                            if (i == me || (barLock && locks[i])) {
                                color[i] = progress
                                toHexWindow(hexWindow[i], color[i])
                                sb[i].setBackgroundColor((0xFF shl 24) or (progress shl (24 - i * 8)))
                                sb[i].progress = progress
                            }
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    doProgressChanged(seekBar, seekBar.progress, true)
                }
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    doProgressChanged(seekBar, seekBar.progress, true)
                }
            })
            lk[i] = CheckBox(context)
            lk[i].layoutParams = LinearLayout.LayoutParams(WC, WC, 0f)
            lk[i].setOnCheckedChangeListener(this)
            lk[i].tag = i
            lh.addView(tv)
            lh.addView(sb[i])
            lh.addView(lk[i])
            lv.addView(lh, MP, WC)
        }
        run {
            val lh = LinearLayout(context)
            lh.gravity = Gravity.CENTER
            for (i in 0 until arraySizes) {
                hexWindow[i] = TextView(context)
                toHexWindow(hexWindow[i], color[i])
                lh.addView(hexWindow[i])
            }
            lv.addView(lh)
        }
        val sv = ScrollView(context)
        sv.addView(lv)
        builder.setView(sv)
        val ocl = DialogInterface.OnClickListener { dialog, which ->
            buttonHit(which, (color[0] shl 24) or (color[1] shl 16) or (color[2] shl 8) or color[3])
        }
        val title = context.getString(R.string.addshortcut_make_text_icon)
        builder.setTitle(title)
        builder.setPositiveButton(android.R.string.ok, ocl)
        builder.setNegativeButton(android.R.string.cancel, ocl)
        builder.show()
        started = true
    }

    fun toHexWindow(tv: TextView, k: Int) {
        val HEX = "0123456789ABCDEF"
        var s = ""
        var n = 8
        var k2 = k and ((1L shl 8) - 1L).toInt()
        n -= 4
        while (n >= 0) {
            s += HEX[(k2 shr n) and 0xF]
            n -= 4
        }
        tv.text = s
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        val view = buttonView.tag as Int
        locks[view] = isChecked
        barLock = locks.any { it }
    }

    private fun buttonHit(hit: Int, color: Int) {
        when (hit) {
            AlertDialog.BUTTON_NEGATIVE -> return
            AlertDialog.BUTTON_POSITIVE -> {
                imgtext = value?.text?.toString() ?: ""
                result[1] = imgtext
                imgview.tag = color
                if (imgtext.isNotEmpty()) {
                    imgview.setImageBitmap(
                        TextIcon.getTextIcon(
                            imgtext,
                            color,
                            96,
                            96
                        )
                    )
                }
                return
            }
        }
    }
}
// ...existing code...

