package com.example.ditask.network

import com.example.ditask.data.Task
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 任务网络字段转换，本地待同步标记不参与上传。
 */
object TaskSyncDto {

    fun toJson(task: Task): JSONObject = JSONObject()
        .put("id", task.id)
        .put("userId", task.userId ?: JSONObject.NULL)
        .put("title", task.title)
        .put("content", task.content ?: JSONObject.NULL)
        .put("status", task.status)
        .put("priority", task.priority)
        .put(
            "dueTime",
            if (task.dueTime == null) JSONObject.NULL else TimeCodec.toIso(task.dueTime)
        )
        .put("isDeleted", task.isDeleted)
        .put("createdAt", TimeCodec.toIso(task.createdAt))
        .put("updatedAt", TimeCodec.toIso(task.updatedAt))

    fun fromJson(json: JSONObject): Task {
        if (!json.has("content") || !json.has("dueTime")) {
            throw JSONException("任务缺少 content 或 dueTime 字段")
        }
        val content = if (json.isNull("content")) null else json.getString("content")
        val dueTime = if (json.isNull("dueTime")) {
            null
        } else {
            TimeCodec.toEpochMilli(json.getString("dueTime"))
        }
        return Task(
            id = json.getString("id"),
            userId = json.getString("userId"),
            title = json.getString("title"),
            content = content,
            status = json.getInt("status"),
            priority = json.getInt("priority"),
            dueTime = dueTime,
            isDeleted = json.getBoolean("isDeleted"),
            isPendingSync = false,
            createdAt = TimeCodec.toEpochMilli(json.getString("createdAt")),
            updatedAt = TimeCodec.toEpochMilli(json.getString("updatedAt"))
        )
    }

    fun toJsonArray(tasks: List<Task>): JSONArray = JSONArray().apply {
        tasks.forEach { put(toJson(it)) }
    }

    fun fromJsonArray(json: JSONArray): List<Task> = List(json.length()) { index ->
        fromJson(json.getJSONObject(index))
    }
}
