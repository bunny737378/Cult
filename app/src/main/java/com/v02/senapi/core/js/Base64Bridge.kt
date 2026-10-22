package com.v02.senapi.core.js

import android.util.Base64
import java.math.BigInteger

interface Base64BridgeInterface {
    fun encode(data: String): String
    fun decode(data: String): String
    fun customEncode(hex: String, srcChars: String, targetChars: String): String
}

class Base64Bridge : Base64BridgeInterface {

    override fun encode(data: String): String {
        return Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    override fun decode(data: String): String {
        return try {
            val bytes = Base64.decode(data, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    override fun customEncode(hex: String, srcChars: String, targetChars: String): String {
        if (hex.isEmpty()) return ""
        return try {
            val srcBase = BigInteger.valueOf(srcChars.length.toLong())
            val targetBase = BigInteger.valueOf(targetChars.length.toLong())

            // Convert hex to big integer
            var value = BigInteger(hex, 16)
            if (value == BigInteger.ZERO) {
                return if (targetChars.isNotEmpty()) targetChars[0].toString() else ""
            }

            val result = StringBuilder()
            while (value > BigInteger.ZERO) {
                val remainder = value.mod(targetBase).toInt()
                result.append(targetChars[remainder])
                value = value.divide(targetBase)
            }

            result.reverse().toString()
        } catch (e: Exception) {
            ""
        }
    }
}
