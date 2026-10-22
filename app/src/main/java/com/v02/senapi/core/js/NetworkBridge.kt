package com.v02.senapi.core.js

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.v02.senapi.core.network.CultHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

interface NetworkBridgeInterface {
    fun get(url: String, headersJson: String?): String
    fun post(url: String, headersJson: String?, body: String?): String
}

class NetworkBridge : NetworkBridgeInterface {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, String>>() {}.type

    override fun get(url: String, headersJson: String?): String {
        return executeRequest(url, "GET", headersJson, null)
    }

    override fun post(url: String, headersJson: String?, body: String?): String {
        return executeRequest(url, "POST", headersJson, body)
    }

    private fun executeRequest(url: String, method: String, headersJson: String?, bodyContent: String?): String {
        val responseMap = HashMap<String, Any>()
        try {
            val reqBuilder = Request.Builder().url(url)

            if (!headersJson.isNullOrBlank() && headersJson != "{}") {
                try {
                    val headers: Map<String, String> = gson.fromJson(headersJson, mapType)
                    for ((k, v) in headers) {
                        reqBuilder.addHeader(k, v)
                    }
                } catch (e: Exception) {
                    // Ignore header parse exceptions
                }
            }

            if (method.equals("POST", ignoreCase = true)) {
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = (bodyContent ?: "").toRequestBody(mediaType)
                reqBuilder.post(body)
            } else {
                reqBuilder.get()
            }

            val response: Response = CultHttpClient.execute(reqBuilder.build())
            val resHeaders = HashMap<String, String>()
            for (name in response.headers.names()) {
                resHeaders[name.lowercase()] = response.header(name) ?: ""
            }

            responseMap["code"] = response.code
            responseMap["headers"] = resHeaders
            responseMap["body"] = response.body?.string() ?: ""

        } catch (e: IOException) {
            responseMap["code"] = 500
            responseMap["headers"] = emptyMap<String, String>()
            responseMap["body"] = "Error: ${e.message}"
        }

        return gson.toJson(responseMap)
    }
}
