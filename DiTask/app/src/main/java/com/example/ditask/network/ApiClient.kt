package com.example.ditask.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

open class ApiException(
    override val message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class NetworkApiException(
    message: String = "网络连接失败或请求超时，请稍后重试",
    cause: Throwable? = null
) : ApiException(message, cause = cause)

class UnauthorizedApiException(
    message: String = "登录状态已失效，请重新登录"
) : ApiException(message, statusCode = 401)

/**
 * 基于 HttpURLConnection 的轻量请求客户端，所有请求均在 IO 线程执行。
 */
class ApiClient(baseUrl: String = DEFAULT_BASE_URL) {

    private val baseUrl = baseUrl.trimEnd('/')

    suspend fun get(path: String, token: String? = null): String = withContext(Dispatchers.IO) {
        request("GET", path, null, token)
    }

    suspend fun post(path: String, body: String, token: String? = null): String =
        withContext(Dispatchers.IO) {
            request("POST", path, body, token)
        }

    private fun request(method: String, path: String, body: String?, token: String?): String {
        var connection: HttpURLConnection? = null
        try {
            val requestPath = if (path.startsWith('/')) path else "/$path"
            connection = (URL("$baseUrl$requestPath").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                if (token != null) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                if (body != null) {
                    doOutput = true
                    setFixedLengthStreamingMode(body.toByteArray(Charsets.UTF_8).size)
                }
            }

            if (body != null) {
                val bodyBytes = body.toByteArray(Charsets.UTF_8)
                connection.outputStream.use { output ->
                    output.write(bodyBytes)
                    output.flush()
                }
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                val message = errorMessage(statusCode, response)
                if (statusCode == 401) {
                    throw UnauthorizedApiException(message)
                }
                throw ApiException(message, statusCode = statusCode)
            }
            return response
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw NetworkApiException(cause = e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun errorMessage(statusCode: Int, response: String): String {
        val serverMessage = try {
            JSONObject(response).optString("message")
        } catch (e: JSONException) {
            ""
        }
        if (serverMessage.isNotBlank()) {
            return serverMessage
        }
        return if (statusCode == 401) "登录状态已失效，请重新登录" else "服务器请求失败（$statusCode）"
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:3000"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
