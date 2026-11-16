package com.translator.app.core.network

/** The provided API, as the data source sees it. [OkHttpTranslateApi] is the real one; tests use a fake. */
interface TranslateApi {
    suspend fun translate(text: String, sourceCode: String, targetCode: String): String

    /** Opens the connection ahead of time. Sends no text. */
    fun warmUp() {}
}

/** A non-2xx answer. [retryAfterSec] comes from the Retry-After header when the server sent one. */
class ApiHttpException(val code: Int, val retryAfterSec: Int? = null) : java.io.IOException("Unexpected response code: $code")
