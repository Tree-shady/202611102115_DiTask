package com.example.ditask.network

import com.example.ditask.BuildConfig
import com.example.ditask.data.ServerSettings
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
class ApiClient(baseUrl: String? = null) {

    /** 显式传入时使用固定地址（便于测试），否则每次请求读取用户设置的服务器地址 */
    private val fixedBaseUrl = baseUrl?.trimEnd('/')

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
            val baseUrl = fixedBaseUrl ?: ServerSettings.baseUrl
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
        // 服务端地址由构建脚本注入（见 app/build.gradle.kts 的 SERVER_BASE_URL），
        // 默认值为模拟器访问宿主机的占位地址，本地可通过 local.properties 覆盖。
        val DEFAULT_BASE_URL: String = BuildConfig.SERVER_BASE_URL
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
