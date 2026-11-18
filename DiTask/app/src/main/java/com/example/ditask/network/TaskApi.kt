package com.example.ditask.network

import com.example.ditask.data.Task
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.time.DateTimeException

interface TaskApi {
    suspend fun upload(token: String, tasks: List<Task>): Int

    suspend fun fetch(token: String, lastSyncTime: Long?): List<Task>
}

class RealTaskApi(
    private val apiClient: ApiClient = ApiClient()
) : TaskApi {

    override suspend fun upload(token: String, tasks: List<Task>): Int {
        try {
            val body = TaskSyncDto.toJsonArray(tasks).toString()
            val response = JSONObject(apiClient.post("/api/v1/tasks/sync", body, token))
            val synced = response.getInt("synced")
            if (synced != tasks.size) {
                throw ApiException("服务器确认的任务同步数量不一致")
            }
            return synced
        } catch (e: ApiException) {
            throw e
        } catch (e: JSONException) {
            throw ApiException("服务器返回的任务同步数据异常", cause = e)
        } catch (e: DateTimeException) {
            throw ApiException("任务时间格式错误", cause = e)
        }
    }

    override suspend fun fetch(token: String, lastSyncTime: Long?): List<Task> {
        try {
            val query = lastSyncTime?.let {
                val iso = TimeCodec.toIso(it)
                "?lastSyncTime=${URLEncoder.encode(iso, Charsets.UTF_8.name())}"
            }.orEmpty()
            val response = apiClient.get("/api/v1/tasks$query", token)
            return TaskSyncDto.fromJsonArray(JSONArray(response))
        } catch (e: ApiException) {
            throw e
        } catch (e: JSONException) {
            throw ApiException("服务器返回的任务列表格式异常", cause = e)
        } catch (e: DateTimeException) {
            throw ApiException("服务器返回的任务时间格式异常", cause = e)
        }
    }
}
