package jackpal.androidterm.emulatorview

import kotlin.math.max

class GrowableIntArray(initalCapacity: Int) {
    fun append(i: Int) {
        if (mLength + 1 > mData.size) {
            val newLength = max(((mData.size * 3) shr 1).toDouble(), 16.0).toInt()
            val temp = IntArray(newLength)
            System.arraycopy(mData, 0, temp, 0, mLength)
            mData = temp
        }
        mData[mLength++] = i
    }

    fun length(): Int {
        return mLength
    }

    fun at(index: Int): Int {
        return mData[index]
    }

    var mData: IntArray
    var mLength: Int

    init {
        mData = IntArray(initalCapacity)
        mLength = 0
    }
}
