package com.example.ditask.data

import android.content.Context

/**
 * 本地会话存储：用户名 + 登录令牌 + 用户 ID。
 */
data class Session(
    val username: String,
    val token: String,
    val userId: String
)

object SessionStore {

    private const val PREF_NAME = "session"
    private const val KEY_USERNAME = "username"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "userId"

    fun save(context: Context, username: String, token: String, userId: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun session(context: Context): Session? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val username = prefs.getString(KEY_USERNAME, null)
        val token = prefs.getString(KEY_TOKEN, null)
        val userId = prefs.getString(KEY_USER_ID, null)
        if (username.isNullOrEmpty() || token.isNullOrEmpty() || userId.isNullOrEmpty()) {
            return null
        }
        return Session(username, token, userId)
    }

    // 旧 Mock 会话没有 userId，不能作为真实登录态使用。
    fun isLoggedIn(context: Context): Boolean = session(context) != null

    fun clear(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_USERNAME)
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .apply()
    }
}
