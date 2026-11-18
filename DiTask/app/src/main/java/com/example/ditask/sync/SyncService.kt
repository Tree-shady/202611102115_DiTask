package com.example.ditask.sync

import com.example.ditask.data.Session
import com.example.ditask.data.TaskRepository
import com.example.ditask.network.TaskApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncResult(
    val uploaded: Int,
    val pulled: Int,
    val applied: Int
)

/** 与 PC 一致：先上传本地变更，再拉取云端增量并按 updatedAt 合并。 */
class SyncService(
    private val repository: TaskRepository,
    private val taskApi: TaskApi,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val syncMutex = Mutex()

    suspend fun sync(session: Session): SyncResult = syncMutex.withLock {
        val userId = session.userId
        val syncStart = clock()

        val snapshot = repository.pending(userId)
        if (snapshot.isNotEmpty()) {
            taskApi.upload(session.token, snapshot)
            repository.acknowledgeUploaded(userId, snapshot)
        }

        val lastSyncTime = repository.getLastSyncTime(userId)
        val remoteTasks = taskApi.fetch(session.token, lastSyncTime)
        val applied = repository.mergeRemoteAndSaveCursor(userId, remoteTasks, syncStart)

        SyncResult(
            uploaded = snapshot.size,
            pulled = remoteTasks.size,
            applied = applied
        )
    }
}
