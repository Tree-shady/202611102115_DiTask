package com.example.ditask.network

import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Mock 鉴权接口：模拟网络耗时与后端校验规则。
 * 正确账号：demo / 123456，其余组合返回 401 语义的错误。
 */
class MockAuthApi : AuthApi {

    override suspend fun login(username: String, password: String): LoginResult =
        authenticate(username, password)

    override suspend fun register(username: String, password: String): LoginResult =
        authenticate(username, password)

    private suspend fun authenticate(username: String, password: String): LoginResult {
        // 模拟网络耗时，便于验证登录中的加载态
        delay(800)
        return if (username == MOCK_USERNAME && password == MOCK_PASSWORD) {
            LoginResult.Success(
                token = "mock-token-${UUID.randomUUID()}",
                userId = MOCK_USER_ID
            )
        } else {
            LoginResult.Failure(message = "用户名或密码错误")
        }
    }

    companion object {
        const val MOCK_USERNAME = "demo"
        const val MOCK_PASSWORD = "123456"
        const val MOCK_USER_ID = "mock-user-demo"
    }
}
