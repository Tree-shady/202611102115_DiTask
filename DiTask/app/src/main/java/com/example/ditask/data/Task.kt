package com.example.ditask.data

/**
 * 任务实体，字段与云端 Task 表保持一致，额外增加本地待同步标记。
 * 时间统一使用 UTC 毫秒时间戳。
 */
data class Task(
    val id: String,
    val userId: String? = null,
    val title: String,
    val content: String?,
    val status: Int,
    val priority: Int,
    val dueTime: Long?,
    val isDeleted: Boolean = false,
    val isPendingSync: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val STATUS_TODO = 0
        const val STATUS_DOING = 1
        const val STATUS_DONE = 2

        const val PRIORITY_NORMAL = 0
        const val PRIORITY_IMPORTANT = 1
        const val PRIORITY_URGENT = 2
    }
}
