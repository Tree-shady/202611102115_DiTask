package com.example.ditask.data

import android.content.ContentValues
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TaskDbMigrationTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private var helper: TaskDbHelper? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        helper?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun upgradeFromV1_preservesTasksAndCreatesSyncState() {
        val db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        db.execSQL(
            """
            CREATE TABLE task (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                content TEXT,
                status INTEGER NOT NULL DEFAULT 0,
                priority INTEGER NOT NULL DEFAULT 0,
                dueTime INTEGER,
                isDeleted INTEGER NOT NULL DEFAULT 0,
                isPendingSync INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_task_pending ON task(isPendingSync)")
        db.execSQL("CREATE INDEX idx_task_updated ON task(updatedAt)")
        val values = ContentValues().apply {
            put("id", "legacy-1")
            put("title", "旧任务")
            put("content", "保留内容")
            put("status", 1)
            put("priority", 2)
            put("dueTime", 1_700_000_100_000L)
            put("isDeleted", 0)
            put("isPendingSync", 1)
            put("createdAt", 1_700_000_000_000L)
            put("updatedAt", 1_700_000_000_123L)
        }
        db.insertOrThrow("task", null, values)
        db.version = 1
        db.close()

        helper = TaskDbHelper(context, databaseName)
        val upgraded = helper!!.readableDatabase
        upgraded.query("task", null, "id = ?", arrayOf("legacy-1"), null, null, null)
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧任务", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("status")))
                assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("priority")))
                assertEquals(
                    1_700_000_000_123L,
                    cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt"))
                )
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("userId")))
            }
        upgraded.rawQuery("SELECT COUNT(*) FROM sync_state", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }
}
