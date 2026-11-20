package com.example.ditask.data

import android.content.Context
import com.example.ditask.network.ApiClient
import java.net.URI
import java.net.URISyntaxException

/**
 * 服务器地址设置：默认取构建注入值，用户可在应用内修改并持久化。
 */
object ServerSettings {

    private const val PREF_NAME = "server_settings"
    private const val KEY_BASE_URL = "baseUrl"

    /** 当前生效的服务器地址，网络请求时实时读取 */
    @Volatile
    var baseUrl: String = ApiClient.DEFAULT_BASE_URL
        private set

    /** 应用启动时调用：加载用户上次填写的地址 */
    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)
        baseUrl = if (saved.isNullOrBlank()) ApiClient.DEFAULT_BASE_URL else normalize(saved)
    }

    fun save(context: Context, url: String) {
        baseUrl = normalize(url)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, baseUrl)
            .apply()
    }

    /** 去掉首尾空白与末尾斜杠，避免拼接出重复的 / */
    fun normalize(url: String): String = url.trim().trimEnd('/')

    /** 仅接受带 http/https 协议和主机名的绝对地址 */
    fun isValid(url: String): Boolean = try {
        val uri = URI(url)
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    } catch (e: URISyntaxException) {
        false
    }
}
