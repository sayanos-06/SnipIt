package com.example.snipit.ai

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object DucklingClient {

    private val client = OkHttpClient()

    data class DucklingResult(
        val dim: String,
        val body: String,
        val value: String
    )

    fun parse(
        text: String,
        onSuccess: (List<DucklingResult>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val json = JSONObject().apply {
            put("text", text)
            put("locale", "en_US")
            put("tz", "Asia/Kolkata")
        }

        val request = Request.Builder()
            .url("https://snipit-structured-api.onrender.com/parse")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val emptyResultList: List<DucklingResult> = emptyList()
                onSuccess(emptyResultList)
                Log.e("DucklingClient", "Error: ${e.message}")
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val resultList = mutableListOf<DucklingResult>()
                        val body = response.body?.string() ?: return

                        val array = JSONArray(body)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val dim = obj.getString("dim")
                            val bodyText = obj.getString("body")
                            val valueObj = obj.getJSONObject("value")
                            val value = valueObj.optString("value", "")
                            resultList.add(DucklingResult(dim, bodyText, value))
                        }

                        onSuccess(resultList)
                    } catch (e: Error) {
                        val emptyResultList: List<DucklingResult> = emptyList()
                        onSuccess(emptyResultList)
                        Log.e("DucklingClient", "Error: ${e.message}")
                    }
                }
            }
        })
    }
}