package com.example.ditask.network

/**
 * 鉴权结果。失败时 message 可直接展示给页面用户。
 */
sealed interface LoginResult {
    data class Success(val token: String, val userId: String) : LoginResult
    data class Failure(val message: String) : LoginResult
}

/**
 * 鉴权接口抽象，页面无需关心真实接口或 Mock 实现。
 */
interface AuthApi {
    suspend fun login(username: String, password: String): LoginResult

    suspend fun register(username: String, password: String): LoginResult
}
