package com.example.ditask.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.ditask.data.Session
import com.example.ditask.data.Task
import com.example.ditask.data.TaskRepository
import com.example.ditask.network.NetworkApiException
import com.example.ditask.network.TaskApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SyncServiceTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "sync-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        repository = TaskRepository(context, databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun sync_uploadsOnlyCurrentAccountAndMergesRemote() = runBlocking {
        repository.save(USER_A, task("local-a"))
        repository.save(USER_B, task("local-b", userId = USER_B))
        val api = FakeTaskApi().apply {
            onFetch = { listOf(task("remote-a", updatedAt = 3_000L)) }
        }

        val result = SyncService(repository, api) { 10_000L }.sync(session(USER_A))

        assertEquals(1, result.uploaded)
        assertEquals(1, result.pulled)
        assertEquals(listOf("local-a"), api.uploads.single().map { it.id })
        assertEquals(setOf("local-a", "remote-a"), repository.list(USER_A, null).map { it.id }.toSet())
        assertEquals(listOf("local-b"), repository.list(USER_B, null).map { it.id })
        assertEquals(0, repository.pendingCount(USER_A))
        assertEquals(1, repository.pendingCount(USER_B))
        assertEquals(10_000L, repository.getLastSyncTime(USER_A))
        assertNull(repository.getLastSyncTime(USER_B))
    }

    @Test
    fun uploadAcknowledgement_doesNotClearEditMadeDuringUpload() = runBlocking {
        val original = task("local-a", updatedAt = 1_000L)
        repository.save(USER_A, original)
        val api = FakeTaskApi().apply {
            onUpload = {
                repository.save(
                    USER_A,
                    original.copy(title = "上传期间的新修改", updatedAt = 2_000L)
                )
            }
        }

        SyncService(repository, api) { 10_000L }.sync(session(USER_A))

        val local = repository.list(USER_A, null).single()
        assertEquals("上传期间的新修改", local.title)
        assertEquals(2_000L, local.updatedAt)
        assertTrue(local.isPendingSync)
        assertEquals(1, repository.pendingCount(USER_A))
    }

    @Test
    fun pullFailure_keepsCursorAndAcknowledgedUploadIsNotRepeated() = runBlocking {
        repository.save(USER_A, task("local-a"))
        val api = FakeTaskApi().apply {
            onFetch = { throw NetworkApiException() }
        }
        val service = SyncService(repository, api) { 10_000L }

        var failed = false
        try {
            service.sync(session(USER_A))
        } catch (_: NetworkApiException) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(0, repository.pendingCount(USER_A))
        assertNull(repository.getLastSyncTime(USER_A))

        api.onFetch = { emptyList() }
        service.sync(session(USER_A))
        assertEquals(1, api.uploads.size)
        assertEquals(10_000L, repository.getLastSyncTime(USER_A))
    }

    @Test
    fun newerRemoteWins_andSoftDeleteParticipatesInNextUpload() = runBlocking {
        repository.save(USER_A, task("local-a", title = "旧标题", updatedAt = 1_000L))
        val api = FakeTaskApi().apply {
            onFetch = {
                listOf(task("local-a", title = "云端新标题", updatedAt = 2_000L))
            }
        }
        val service = SyncService(repository, api) { 10_000L }
        service.sync(session(USER_A))

        assertEquals("云端新标题", repository.list(USER_A, null).single().title)

        repository.softDelete(USER_A, "local-a")
        api.onFetch = { emptyList() }
        service.sync(session(USER_A))

        assertEquals(2, api.uploads.size)
        assertTrue(api.uploads.last().single().isDeleted)
        assertTrue(repository.list(USER_A, null).isEmpty())
    }

    @Test
    fun remoteFromAnotherAccount_rollsBackMergeAndCursor() = runBlocking {
        val api = FakeTaskApi().apply {
            onFetch = { listOf(task("foreign", userId = USER_B)) }
        }

        var failed = false
        try {
            SyncService(repository, api) { 10_000L }.sync(session(USER_A))
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
        assertTrue(repository.list(USER_A, null).isEmpty())
        assertTrue(repository.list(USER_B, null).isEmpty())
        assertNull(repository.getLastSyncTime(USER_A))
    }

    private fun session(userId: String) = Session(
        username = userId,
        token = "token-$userId",
        userId = userId
    )

    private fun task(
        id: String,
        userId: String = USER_A,
        title: String = "任务-$id",
        updatedAt: Long = 1_000L
    ) = Task(
        id = id,
        userId = userId,
        title = title,
        content = "备注",
        status = Task.STATUS_TODO,
        priority = Task.PRIORITY_NORMAL,
        dueTime = null,
        isDeleted = false,
        isPendingSync = true,
        createdAt = updatedAt - 100L,
        updatedAt = updatedAt
    )

    private class FakeTaskApi : TaskApi {
        val uploads = mutableListOf<List<Task>>()
        var onUpload: suspend (List<Task>) -> Unit = {}
        var onFetch: suspend (Long?) -> List<Task> = { emptyList() }

        override suspend fun upload(token: String, tasks: List<Task>): Int {
            uploads += tasks
            onUpload(tasks)
            return tasks.size
        }

        override suspend fun fetch(token: String, lastSyncTime: Long?): List<Task> =
            onFetch(lastSyncTime)
    }

    companion object {
        private const val USER_A = "user-a"
        private const val USER_B = "user-b"
    }
}
