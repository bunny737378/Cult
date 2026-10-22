package com.v02.senapi.core.js

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

interface CryptoBridgeInterface {
    fun md5Hex(data: String): String
    fun sha256Hex(data: String): String
    fun hmacMd5Base64(data: String, key: String): String
    fun hmacSha256Hex(data: String, key: String): String
    fun hkdfSha256(ikm: String, salt: String, info: String, length: Int): String
    fun aesCbcDecrypt(ciphertext: String, key: String, iv: String): String
    fun aesCbcEncrypt(plaintext: String, key: String, iv: String): String
    fun aesGcmDecrypt(ciphertext: String, key: String, iv: String): String
    fun generateRandomHex(length: Int): String
}

class CryptoBridge : CryptoBridgeInterface {

    override fun md5Hex(data: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(data.toByteArray(Charsets.UTF_8))
        return bytesToHex(bytes)
    }

    override fun sha256Hex(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(data.toByteArray(Charsets.UTF_8))
        return bytesToHex(bytes)
    }

    override fun hmacMd5Base64(data: String, key: String): String {
        val mac = Mac.getInstance("HmacMD5")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacMD5"))
        val signed = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signed, Base64.NO_WRAP)
    }

    override fun hmacSha256Hex(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val signed = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return bytesToHex(signed)
    }

    override fun hkdfSha256(ikm: String, salt: String, info: String, length: Int): String {
        try {
            val ikmBytes = if (isHex(ikm)) hexToBytes(ikm) else ikm.toByteArray(Charsets.UTF_8)
            val saltBytes = if (salt.isEmpty()) ByteArray(32) else if (isHex(salt)) hexToBytes(salt) else salt.toByteArray(Charsets.UTF_8)
            val infoBytes = if (isHex(info)) hexToBytes(info) else info.toByteArray(Charsets.UTF_8)

            // Extract
            val macExtract = Mac.getInstance("HmacSHA256")
            macExtract.init(SecretKeySpec(saltBytes, "HmacSHA256"))
            val prk = macExtract.doFinal(ikmBytes)

            // Expand
            val macExpand = Mac.getInstance("HmacSHA256")
            macExpand.init(SecretKeySpec(prk, "HmacSHA256"))

            val output = ByteArray(length)
            var t = ByteArray(0)
            var bytesGenerated = 0
            var i: Byte = 1

            while (bytesGenerated < length) {
                macExpand.reset()
                macExpand.update(t)
                macExpand.update(infoBytes)
                macExpand.update(i)
                t = macExpand.doFinal()

                val toCopy = Math.min(t.size, length - bytesGenerated)
                System.arraycopy(t, 0, output, bytesGenerated, toCopy)
                bytesGenerated += toCopy
                i++
            }

            return bytesToHex(output)
        } catch (e: Exception) {
            return ""
        }
    }

    override fun aesCbcDecrypt(ciphertext: String, key: String, iv: String): String {
        try {
            val cipherBytes = if (isHex(ciphertext)) hexToBytes(ciphertext) else Base64.decode(ciphertext, Base64.DEFAULT)
            val keyBytes = if (isHex(key) && (key.length == 32 || key.length == 64)) hexToBytes(key) else key.toByteArray(Charsets.UTF_8)
            val ivBytes = if (isHex(iv) && iv.length == 32) hexToBytes(iv) else iv.toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(padKey(keyBytes, 16), "AES")
            val ivSpec = IvParameterSpec(padKey(ivBytes, 16))
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decrypted = cipher.doFinal(cipherBytes)
            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            return ""
        }
    }

    override fun aesCbcEncrypt(plaintext: String, key: String, iv: String): String {
        try {
            val keyBytes = if (isHex(key) && (key.length == 32 || key.length == 64)) hexToBytes(key) else key.toByteArray(Charsets.UTF_8)
            val ivBytes = if (isHex(iv) && iv.length == 32) hexToBytes(iv) else iv.toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(padKey(keyBytes, 16), "AES")
            val ivSpec = IvParameterSpec(padKey(ivBytes, 16))
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            return bytesToHex(encrypted)
        } catch (e: Exception) {
            return ""
        }
    }

    override fun aesGcmDecrypt(ciphertext: String, key: String, iv: String): String {
        try {
            val cipherBytes = if (isHex(ciphertext)) hexToBytes(ciphertext) else Base64.decode(ciphertext, Base64.DEFAULT)
            val keyBytes = if (isHex(key) && (key.length == 32 || key.length == 64)) hexToBytes(key) else key.toByteArray(Charsets.UTF_8)
            val ivBytes = if (isHex(iv) && (iv.length == 24 || iv.length == 32)) hexToBytes(iv) else iv.toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(padKey(keyBytes, 16), "AES")
            val gcmSpec = GCMParameterSpec(128, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val decrypted = cipher.doFinal(cipherBytes)
            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            return ""
        }
    }

    override fun generateRandomHex(length: Int): String {
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return bytesToHex(bytes)
    }

    private fun padKey(bytes: ByteArray, size: Int): ByteArray {
        if (bytes.size == size || bytes.size == 24 || bytes.size == 32) return bytes
        val padded = ByteArray(size)
        System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.size, size))
        return padded
    }

    private fun isHex(s: String): Boolean {
        if (s.isEmpty() || s.length % 2 != 0) return false
        return s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
