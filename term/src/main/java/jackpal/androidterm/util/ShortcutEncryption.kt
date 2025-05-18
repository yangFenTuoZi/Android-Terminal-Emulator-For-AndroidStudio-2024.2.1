/*
 * Copyright (C) 2015 Steven Luo
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

package jackpal.androidterm.util

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementation of a simple authenticated encryption scheme suitable for
 * TEA shortcuts.
 */
object ShortcutEncryption {
    const val ENC_ALGORITHM = "AES"
    const val ENC_SYSTEM = "$ENC_ALGORITHM/CBC/PKCS5Padding"
    const val ENC_BLOCKSIZE = 16
    const val MAC_ALGORITHM = "HmacSHA256"
    const val KEYLEN = 128

    private const val SHORTCUT_KEYS_PREF = "shortcut_keys"
    private val COLON: Pattern = Pattern.compile(":")

    class Keys(val encKey: SecretKey, val macKey: SecretKey) {
        fun encode(): String = encodeToBase64(encKey.encoded) + ":" + encodeToBase64(macKey.encoded)
        companion object {
            fun decode(encodedKeys: String): Keys {
                val keys = COLON.split(encodedKeys)
                if (keys.size != 2) throw IllegalArgumentException("Invalid encoded keys!")
                val encKey = SecretKeySpec(decodeBase64(keys[0]), ENC_ALGORITHM)
                val macKey = SecretKeySpec(decodeBase64(keys[1]), MAC_ALGORITHM)
                return Keys(encKey, macKey)
            }
        }
    }

    fun getKeys(ctx: Context): Keys? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val keyEnc = prefs.getString(SHORTCUT_KEYS_PREF, null)
        if (keyEnc == null) return null
        return try {
            Keys.decode(keyEnc)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun saveKeys(ctx: Context, keys: Keys) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        prefs.edit(commit = true) {
            putString(SHORTCUT_KEYS_PREF, keys.encode())
        }
    }

    @Throws(GeneralSecurityException::class)
    fun generateKeys(): Keys {
        var gen = KeyGenerator.getInstance(ENC_ALGORITHM)
        gen.init(KEYLEN)
        val encKey = gen.generateKey()
        gen = KeyGenerator.getInstance(MAC_ALGORITHM)
        gen.init(KEYLEN)
        val macKey = gen.generateKey()
        return Keys(encKey, macKey)
    }

    @Throws(GeneralSecurityException::class)
    fun decrypt(encrypted: String, keys: Keys): String {
        val cipher = Cipher.getInstance(ENC_SYSTEM)
        val data = COLON.split(encrypted)
        if (data.size != 3) throw GeneralSecurityException("Invalid encrypted data!")
        val mac = data[0]
        val iv = data[1]
        val cipherText = data[2]
        val dataToAuth = "$iv:$cipherText"
        if (computeMac(dataToAuth, keys.macKey) != mac) throw GeneralSecurityException("Incorrect MAC!")
        val ivBytes = decodeBase64(iv)
        cipher.init(Cipher.DECRYPT_MODE, keys.encKey, IvParameterSpec(ivBytes))
        val bytes = cipher.doFinal(decodeBase64(cipherText))
        val decoder: CharsetDecoder = Charset.defaultCharset().newDecoder()
        decoder.onMalformedInput(CodingErrorAction.REPORT)
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
        val out = CharBuffer.allocate(bytes.size)
        val result: CoderResult = decoder.decode(ByteBuffer.wrap(bytes), out, true)
        if (result.isError) throw GeneralSecurityException("Corrupt decrypted data!")
        decoder.flush(out)
        return out.flip().toString()
    }

    @Throws(GeneralSecurityException::class)
    fun encrypt(data: String, keys: Keys): String {
        val cipher = Cipher.getInstance(ENC_SYSTEM)
        val rng = SecureRandom()
        val ivBytes = ByteArray(ENC_BLOCKSIZE)
        rng.nextBytes(ivBytes)
        val iv = encodeToBase64(ivBytes)
        cipher.init(Cipher.ENCRYPT_MODE, keys.encKey, IvParameterSpec(ivBytes))
        val bytes = data.toByteArray()
        val cipherText = encodeToBase64(cipher.doFinal(bytes))
        val dataToAuth = "$iv:$cipherText"
        val mac = computeMac(dataToAuth, keys.macKey)
        return "$mac:$dataToAuth"
    }

    @Throws(GeneralSecurityException::class)
    private fun computeMac(data: String, key: SecretKey): String {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(key)
        val macBytes = mac.doFinal(data.toByteArray())
        return encodeToBase64(macBytes)
    }

    private fun encodeToBase64(data: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(data)
    private fun decodeBase64(data: String): ByteArray = Base64.getDecoder().decode(data)
}

