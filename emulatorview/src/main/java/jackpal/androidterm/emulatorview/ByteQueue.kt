/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jackpal.androidterm.emulatorview

import kotlin.math.min

/**
 * A multi-thread-safe produce-consumer byte array.
 * Only allows one producer and one consumer.
 */
internal class ByteQueue(size: Int) {
    val bytesAvailable: Int
        get() {
            synchronized(this) {
                return mStoredBytes
            }
        }

    @Throws(InterruptedException::class)
    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        var offset = offset
        var length = length
        require(length + offset <= buffer.size) { "length + offset > buffer.length" }
        require(length >= 0) { "length < 0" }
        if (length == 0) {
            return 0
        }
        synchronized(this) {
            while (mStoredBytes == 0) {
                (this as Object).wait()
            }
            var totalRead = 0
            val bufferLength = mBuffer.size
            val wasFull = bufferLength == mStoredBytes
            while (length > 0 && mStoredBytes > 0) {
                val oneRun = min((bufferLength - mHead).toDouble(), mStoredBytes.toDouble()).toInt()
                val bytesToCopy = min(length.toDouble(), oneRun.toDouble()).toInt()
                System.arraycopy(mBuffer, mHead, buffer, offset, bytesToCopy)
                mHead += bytesToCopy
                if (mHead >= bufferLength) {
                    mHead = 0
                }
                mStoredBytes -= bytesToCopy
                length -= bytesToCopy
                offset += bytesToCopy
                totalRead += bytesToCopy
            }
            if (wasFull) {
                (this as Object).notify()
            }
            return totalRead
        }
    }

    /**
     * Attempt to write the specified portion of the provided buffer to
     * the queue.  Returns the number of bytes actually written to the queue;
     * it is the caller's responsibility to check whether all of the data
     * was written and repeat the call to write() if necessary.
     */
    @Throws(InterruptedException::class)
    fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        var offset = offset
        require(length + offset <= buffer.size) { "length + offset > buffer.length" }
        require(length >= 0) { "length < 0" }
        if (length == 0) {
            return 0
        }
        synchronized(this) {
            val bufferLength = mBuffer.size
            val wasEmpty = mStoredBytes == 0
            while (bufferLength == mStoredBytes) {
                (this as Object).wait()
            }
            var tail = mHead + mStoredBytes
            val oneRun: Int
            if (tail >= bufferLength) {
                tail = tail - bufferLength
                oneRun = mHead - tail
            } else {
                oneRun = bufferLength - tail
            }
            val bytesToCopy = min(oneRun.toDouble(), length.toDouble()).toInt()
            System.arraycopy(buffer, offset, mBuffer, tail, bytesToCopy)
            offset += bytesToCopy
            mStoredBytes += bytesToCopy
            if (wasEmpty) {
                (this as Object).notify()
            }
            return bytesToCopy
        }
    }

    private val mBuffer: ByteArray = ByteArray(size)
    private var mHead = 0
    private var mStoredBytes = 0
}