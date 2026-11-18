package com.example.ditask.network

import java.time.Instant

/**
 * 本地 UTC 毫秒时间戳与服务端 ISO UTC 时间字符串的转换。
 */
object TimeCodec {

    fun toIso(epochMilli: Long): String = Instant.ofEpochMilli(epochMilli).toString()

    fun toEpochMilli(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
