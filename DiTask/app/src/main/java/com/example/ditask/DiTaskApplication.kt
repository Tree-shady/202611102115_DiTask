package com.example.ditask

import android.app.Application
import com.example.ditask.data.ServerSettings

class DiTaskApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 先加载用户自定义的服务器地址，后续网络请求才能读取到
        ServerSettings.init(this)
    }
}
