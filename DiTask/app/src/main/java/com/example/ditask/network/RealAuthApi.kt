package com.example.ditask.network

import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

/**
 * 真实鉴权接口，异常统一转换为页面可直接展示的中文失败结果。
 */
class RealAuthApi(
    private val apiClient: ApiClient = ApiClient()
) : AuthApi {

    override suspend fun login(username: String, password: String): LoginResult =
        authenticate("/api/v1/auth/login", username, password)

    override suspend fun register(username: String, password: String): LoginResult =
        authenticate("/api/v1/auth/register", username, password)

    private suspend fun authenticate(path: String, username: String, password: String): LoginResult {
        return try {
            val request = JSONObject()
                .put("username", username)
                .put("password", password)
            val response = JSONObject(apiClient.post(path, request.toString()))
            val token = response.getString("token")
            val userId = extractUserId(token)
            if (token.isBlank() || userId == null) {
                LoginResult.Failure("服务器返回的登录凭证无效")
            } else {
                LoginResult.Success(token = token, userId = userId)
            }
        } catch (e: ApiException) {
            LoginResult.Failure(e.message)
        } catch (e: JSONException) {
            LoginResult.Failure("服务器返回数据异常，请稍后重试")
        }
    }

    private fun extractUserId(token: String): String? {
        return try {
            val parts = token.split('.')
            if (parts.size != 3) {
                return null
            }
            val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
            val payload = JSONObject(String(payloadBytes, Charsets.UTF_8))
            payload.getString("userId").takeIf { it.isNotBlank() }
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: JSONException) {
            null
        }
    }
}
