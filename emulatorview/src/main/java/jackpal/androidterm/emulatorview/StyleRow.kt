package jackpal.androidterm.emulatorview

/**
 * Utility class for dealing with text style lines.
 *
 * We pack color and formatting information for a particular character into an
 * int -- see the TextStyle class for details.  The simplest way of storing
 * that information for a screen row would be to use an array of int -- but
 * given that we only use the lower three bytes of the int to store information,
 * that effectively wastes one byte per character -- nearly 8 KB per 100 lines
 * with an 80-column transcript.
 *
 * Instead, we use an array of bytes and store the bytes of each int
 * consecutively in big-endian order.
 */
class StyleRow internal constructor(private var mStyle: Int, private val mColumns: Int) {
    /** Initially null, will be allocated when needed.  */
    private var mData: ByteArray? = null

    fun set(column: Int, style: Int) {
        if (style == mStyle && mData == null) {
            return
        }
        ensureData()
        setStyle(column, style)
    }

    fun get(column: Int): Int {
        if (mData == null) {
            return mStyle
        }
        return getStyle(column)
    }

    val isSolidStyle: Boolean
        get() = mData == null

    fun getSolidStyle(): Int {
        require(mData == null) { "Not a solid style" }
        return mStyle
    }

    fun copy(start: Int, dst: StyleRow, offset: Int, len: Int) {
        // fast case
        if (mData == null && dst.mData == null && start == 0 && offset == 0 && len == mColumns) {
            dst.mStyle = mStyle
            return
        }
        // There are other potentially fast cases, but let's just treat them
        // all the same for simplicity.
        ensureData()
        dst.ensureData()
        System.arraycopy(mData, 3 * start, dst.mData, 3 * offset, 3 * len)
    }

    fun ensureData() {
        if (mData == null) {
            allocate()
        }
    }

    private fun allocate() {
        mData = ByteArray(3 * mColumns)
        for (i in 0..<mColumns) {
            setStyle(i, mStyle)
        }
    }

    private fun getStyle(column: Int): Int {
        val index = 3 * column
        val line = mData
        return (line!![index].toInt() and 0xff or ((line[index + 1].toInt() and 0xff) shl 8
                ) or ((line[index + 2].toInt() and 0xff) shl 16))
    }

    private fun setStyle(column: Int, value: Int) {
        val index = 3 * column
        val line = mData
        line!![index] = (value and 0xff).toByte()
        line[index + 1] = ((value shr 8) and 0xff).toByte()
        line[index + 2] = ((value shr 16) and 0xff).toByte()
    }
}