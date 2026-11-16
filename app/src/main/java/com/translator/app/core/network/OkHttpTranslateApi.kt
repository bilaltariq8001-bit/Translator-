package com.translator.app.core.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The API implementation provided by the manager, unchanged in behaviour. Differences: it implements [TranslateApi],
 * and a non-2xx answer throws [ApiHttpException] (an IOException) so the status code survives for error mapping.
 * Cancelling the coroutine cancels the HTTP call. Nothing here logs the text.
 */
class OkHttpTranslateApi(private val client: OkHttpClient) : TranslateApi {

    override fun warmUp() {
        // A bodyless HEAD to the service's host: it sets up DNS, TCP and TLS, which the next real request then reuses.
        client.newCall(Request.Builder().url("https://translate.googleapis.com/").head().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    override suspend fun translate(text: String, sourceCode: String, targetCode: String): String =
        suspendCancellableCoroutine { continuation ->
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=$sourceCode&tl=$targetCode&dt=t&ie=UTF-8&oe=UTF-8&q=$encodedText"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val body = it.body?.string()

                            if (!it.isSuccessful) {
                                throw ApiHttpException(it.code, it.header("Retry-After")?.toIntOrNull())
                            }

                            if (body.isNullOrEmpty()) {
                                throw IOException("Empty response body")
                            }

                            // A web page instead of data (a captcha or block page, sent with a success code) means the service is
                            // refusing this connection. It is reported as "too many requests", not as a broken answer.
                            if (body.trimStart().startsWith("<")) throw ApiHttpException(429, null)

                            val translations = JSONArray(body).getJSONArray(0)
                            val translatedSentence = StringBuilder()
                            for (i in 0 until translations.length()) {
                                val chunk = translations.getJSONArray(i)
                                translatedSentence.append(chunk.optString(0, ""))
                            }
                            // An empty answer to a non-empty request is a failure, never an empty "translation" on screen.
                            if (translatedSentence.isBlank() && text.isNotBlank()) throw org.json.JSONException("Empty translation")

                            if (continuation.isActive) {
                                continuation.resume(translatedSentence.toString())
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
}
