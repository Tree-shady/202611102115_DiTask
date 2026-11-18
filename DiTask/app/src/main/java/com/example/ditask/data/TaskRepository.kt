package com.example.ditask.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地任务仓库：所有操作按账号隔离，写操作先落本地并标记待同步。
 */
class TaskRepository(
    context: Context,
    databaseName: String = TaskDbHelper.DB_NAME
) {

    private val helper = TaskDbHelper(context.applicationContext, databaseName)

    suspend fun list(userId: String, status: Int?): List<Task> = withContext(Dispatchers.IO) {
        val where = if (status == null) {
            "userId = ? AND isDeleted = 0"
        } else {
            "userId = ? AND isDeleted = 0 AND status = ?"
        }
        val args = if (status == null) arrayOf(userId) else arrayOf(userId, status.toString())
        helper.readableDatabase
            .query(TaskDbHelper.TABLE, null, where, args, null, null, ORDER_BY)
            .use { cursor -> cursor.readTasks() }
    }

    suspend fun save(userId: String, task: Task) = withContext(Dispatchers.IO) {
        require(task.userId == null || task.userId == userId) { "不能修改其他账号的任务" }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val local = db.findById(task.id)
            if (local != null && local.userId != userId) {
                throw IllegalStateException("任务已属于其他账号")
            }
            val updatedAt = if (local != null && task.updatedAt <= local.updatedAt) {
                local.updatedAt + 1
            } else {
                task.updatedAt
            }
            val values = task.copy(userId = userId, isPendingSync = true, updatedAt = updatedAt)
                .toContentValues()
            db.insertWithOnConflict(
                TaskDbHelper.TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun updateStatus(userId: String, id: String, status: Int) =
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val local = db.findById(id)
                if (local != null && local.userId == userId) {
                    val values = ContentValues().apply {
                        put("status", status)
                        put("isPendingSync", 1)
                        put("updatedAt", nextUpdatedAt(local.updatedAt))
                    }
                    db.update(
                        TaskDbHelper.TABLE, values,
                        "id = ? AND userId = ?", arrayOf(id, userId)
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

    suspend fun softDelete(userId: String, id: String) = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val local = db.findById(id)
            if (local != null && local.userId == userId) {
                val values = ContentValues().apply {
                    put("isDeleted", 1)
                    put("isPendingSync", 1)
                    put("updatedAt", nextUpdatedAt(local.updatedAt))
                }
                db.update(
                    TaskDbHelper.TABLE, values,
                    "id = ? AND userId = ?", arrayOf(id, userId)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun claimLegacyTasks(userId: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("userId", userId)
            put("isPendingSync", 1)
        }
        helper.writableDatabase.update(
            TaskDbHelper.TABLE, values, "userId IS NULL", null
        )
    }

    suspend fun pending(userId: String): List<Task> = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            TaskDbHelper.TABLE, null,
            "userId = ? AND isPendingSync = 1", arrayOf(userId),
            null, null, "updatedAt ASC"
        ).use { cursor -> cursor.readTasks() }
    }

    suspend fun pendingCount(userId: String): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${TaskDbHelper.TABLE} " +
                "WHERE userId = ? AND isPendingSync = 1",
            arrayOf(userId)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** 仅确认上传后仍未发生新修改的快照版本。 */
    suspend fun acknowledgeUploaded(userId: String, snapshot: List<Task>) =
        withContext(Dispatchers.IO) {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                snapshot.forEach { task ->
                    val values = ContentValues().apply { put("isPendingSync", 0) }
                    db.update(
                        TaskDbHelper.TABLE, values,
                        "id = ? AND userId = ? AND isPendingSync = 1 AND updatedAt = ?",
                        arrayOf(task.id, userId, task.updatedAt.toString())
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

    suspend fun getLastSyncTime(userId: String): Long? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            TaskDbHelper.SYNC_TABLE, arrayOf("lastSyncTime"),
            "userId = ?", arrayOf(userId), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    /** 远端合并与游标推进必须在同一事务中完成。 */
    suspend fun mergeRemoteAndSaveCursor(
        userId: String,
        remoteTasks: List<Task>,
        syncStart: Long
    ): Int = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        var applied = 0
        db.beginTransaction()
        try {
            remoteTasks.forEach { remote ->
                check(remote.userId == userId) { "服务端返回了其他账号的任务" }
                val local = db.findById(remote.id)
                if (local != null && local.userId != userId) {
                    throw IllegalStateException("本地存在跨账号任务 ID 冲突")
                }
                when {
                    local == null && !remote.isDeleted -> {
                        db.insertOrThrow(
                            TaskDbHelper.TABLE, null,
                            remote.copy(userId = userId, isPendingSync = false).toContentValues()
                        )
                        applied++
                    }
                    local != null && remote.updatedAt > local.updatedAt -> {
                        db.update(
                            TaskDbHelper.TABLE,
                            remote.copy(userId = userId, isPendingSync = false).toContentValues(),
                            "id = ? AND userId = ?", arrayOf(remote.id, userId)
                        )
                        applied++
                    }
                }
            }
            val state = ContentValues().apply {
                put("userId", userId)
                put("lastSyncTime", syncStart)
            }
            db.insertWithOnConflict(
                TaskDbHelper.SYNC_TABLE, null, state, SQLiteDatabase.CONFLICT_REPLACE
            )
            db.setTransactionSuccessful()
            applied
        } finally {
            db.endTransaction()
        }
    }

    private fun SQLiteDatabase.findById(id: String): Task? =
        query(TaskDbHelper.TABLE, null, "id = ?", arrayOf(id), null, null, null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.toTask() else null }

    private fun Cursor.readTasks(): List<Task> {
        val tasks = ArrayList<Task>(count)
        while (moveToNext()) tasks.add(toTask())
        return tasks
    }

    private fun nextUpdatedAt(previous: Long): Long =
        maxOf(System.currentTimeMillis(), previous + 1)

    private fun Task.toContentValues() = ContentValues().apply {
        put("id", id)
        put("userId", userId)
        put("title", title)
        put("content", content)
        put("status", status)
        put("priority", priority)
        if (dueTime == null) putNull("dueTime") else put("dueTime", dueTime)
        put("isDeleted", if (isDeleted) 1 else 0)
        put("isPendingSync", if (isPendingSync) 1 else 0)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun Cursor.toTask() = Task(
        id = getString(getColumnIndexOrThrow("id")),
        userId = if (isNull(getColumnIndexOrThrow("userId"))) null
        else getString(getColumnIndexOrThrow("userId")),
        title = getString(getColumnIndexOrThrow("title")),
        content = if (isNull(getColumnIndexOrThrow("content"))) null
        else getString(getColumnIndexOrThrow("content")),
        status = getInt(getColumnIndexOrThrow("status")),
        priority = getInt(getColumnIndexOrThrow("priority")),
        dueTime = if (isNull(getColumnIndexOrThrow("dueTime"))) null
        else getLong(getColumnIndexOrThrow("dueTime")),
        isDeleted = getInt(getColumnIndexOrThrow("isDeleted")) == 1,
        isPendingSync = getInt(getColumnIndexOrThrow("isPendingSync")) == 1,
        createdAt = getLong(getColumnIndexOrThrow("createdAt")),
        updatedAt = getLong(getColumnIndexOrThrow("updatedAt"))
    )

    companion object {
        private const val ORDER_BY =
            "CASE WHEN status = 2 THEN 1 ELSE 0 END ASC, priority DESC, " +
                "CASE WHEN dueTime IS NULL THEN 1 ELSE 0 END ASC, dueTime ASC, updatedAt DESC"
    }
}
