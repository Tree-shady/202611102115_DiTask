package com.example.ditask.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TaskDbHelper(
    context: Context,
    databaseName: String = DB_NAME
) : SQLiteOpenHelper(context, databaseName, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id TEXT PRIMARY KEY NOT NULL,
                userId TEXT,
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
        createIndexes(db)
        createSyncState(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN userId TEXT")
            db.execSQL("DROP INDEX IF EXISTS idx_task_pending")
            db.execSQL("DROP INDEX IF EXISTS idx_task_updated")
            createIndexes(db)
            createSyncState(db)
        }
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_task_user_pending " +
                "ON $TABLE(userId, isPendingSync)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_task_user_updated " +
                "ON $TABLE(userId, updatedAt)"
        )
    }

    private fun createSyncState(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $SYNC_TABLE (
                userId TEXT PRIMARY KEY NOT NULL,
                lastSyncTime INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    companion object {
        const val TABLE = "task"
        const val SYNC_TABLE = "sync_state"
        const val DB_VERSION = 2
        internal const val DB_NAME = "ditask.db"
    }
}
