package com.example.ditask.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ditask.data.Task
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskSyncDtoTest {

    @Test
    fun timeCodec_preservesMillisecondsAndAcceptsFractionalSeconds() {
        val millis = 1_700_000_000_123L
        assertEquals(millis, TimeCodec.toEpochMilli(TimeCodec.toIso(millis)))
        assertEquals(
            1_700_000_000_123L,
            TimeCodec.toEpochMilli("2023-11-14T22:13:20.1234567Z")
        )
    }

    @Test
    fun taskJson_roundTripSupportsChineseAndNullFields() {
        val source = Task(
            id = "task-1",
            userId = "user-1",
            title = "中文任务",
            content = null,
            status = Task.STATUS_DOING,
            priority = Task.PRIORITY_URGENT,
            dueTime = null,
            isDeleted = false,
            isPendingSync = true,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_123L
        )

        val json = TaskSyncDto.toJson(source)
        assertFalse(json.has("isPendingSync"))
        assertEquals(JSONObject.NULL, json.get("content"))
        assertEquals(JSONObject.NULL, json.get("dueTime"))

        val restored = TaskSyncDto.fromJson(json)
        assertEquals(source.title, restored.title)
        assertEquals(source.userId, restored.userId)
        assertEquals(source.status, restored.status)
        assertEquals(source.priority, restored.priority)
        assertEquals(source.createdAt, restored.createdAt)
        assertEquals(source.updatedAt, restored.updatedAt)
        assertNull(restored.content)
        assertNull(restored.dueTime)
        assertFalse(restored.isPendingSync)
    }
}
