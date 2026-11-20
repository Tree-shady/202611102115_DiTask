package com.example.ditask

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.ditask.data.ServerSettings
import com.example.ditask.data.Session
import com.example.ditask.data.SessionStore
import com.example.ditask.data.Task
import com.example.ditask.data.TaskRepository
import com.example.ditask.databinding.ActivityMainBinding
import com.example.ditask.network.ApiException
import com.example.ditask.network.MockAuthApi
import com.example.ditask.network.NetworkApiException
import com.example.ditask.network.RealTaskApi
import com.example.ditask.network.UnauthorizedApiException
import com.example.ditask.sync.SyncService
import com.example.ditask.ui.ServerSettingsDialog
import com.example.ditask.ui.TaskAdapter
import com.example.ditask.ui.TaskEditorDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: TaskRepository
    private lateinit var syncService: SyncService
    private lateinit var adapter: TaskAdapter
    private lateinit var session: Session

    /** 当前筛选状态，null 表示全部 */
    private var statusFilter: Int? = null
    private var firstLoad = true
    private var syncJob: Job? = null
    private var autoSyncJob: Job? = null
    private var syncAgain = false

    /** Android 16+ 访问局域网服务必须先获得授权 */
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestSync()
        } else {
            binding.syncStatus.setText(R.string.local_network_required)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentSession = SessionStore.session(this)
        if (currentSession == null) {
            openLogin()
            return
        }
        session = currentSession
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 头部延伸到状态栏下方，列表底部避开导航栏
        val headerTop = binding.header.paddingTop
        val headerBottom = binding.header.paddingBottom
        val listTop = binding.list.paddingTop
        val listBottom = binding.list.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.setPadding(bars.left, headerTop + bars.top, bars.right, headerBottom)
            binding.list.setPadding(bars.left, listTop, bars.right, listBottom + bars.bottom)
            insets
        }

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !night
        }

        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_sync -> {
                    requestSync(manual = true)
                    true
                }
                R.id.action_server -> {
                    openServerSettings()
                    true
                }
                R.id.action_logout -> {
                    syncJob?.cancel()
                    SessionStore.clear(this)
                    openLogin()
                    true
                }
                else -> false
            }
        }

        repository = TaskRepository(this)
        syncService = SyncService(repository, RealTaskApi())
        adapter = TaskAdapter(
            onClick = { task -> openEditor(task) },
            onStatusClick = { task -> cycleStatus(task) },
            onLongClick = { task -> confirmDelete(task) }
        )
        binding.list.adapter = adapter

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            statusFilter = when (checkedIds.firstOrNull()) {
                R.id.chipTodo -> Task.STATUS_TODO
                R.id.chipDoing -> Task.STATUS_DOING
                R.id.chipDone -> Task.STATUS_DONE
                else -> null
            }
            reload()
        }

        binding.fabAdd.setOnClickListener { openEditor(null) }
    }

    override fun onStart() {
        super.onStart()
        if (!::binding.isInitialized) return
        reload()
        if (isOfflineTestAccount()) {
            showOfflineTestStatus()
            return
        }
        if (!hasLocalNetworkPermission()) {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            requestSync()
        }
        autoSyncJob?.cancel()
        autoSyncJob = lifecycleScope.launch {
            while (isActive) {
                delay(AUTO_SYNC_INTERVAL_MS)
                requestSync()
            }
        }
    }

    override fun onStop() {
        autoSyncJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        autoSyncJob?.cancel()
        syncJob?.cancel()
        super.onDestroy()
    }

    private fun reload() {
        if (!::binding.isInitialized) return
        lifecycleScope.launch {
            val tasks = repository.list(session.userId, statusFilter)
            adapter.submitList(tasks) {
                binding.emptyView.isVisible = tasks.isEmpty()
                // 首次载入时播放列表进入动画
                if (firstLoad && tasks.isNotEmpty()) {
                    firstLoad = false
                    binding.list.scheduleLayoutAnimation()
                }
            }
            binding.toolbar.subtitle = getString(R.string.task_count, tasks.size)
        }
    }

    private fun openEditor(task: Task?) {
        TaskEditorDialog.show(this, task) { entity ->
            lifecycleScope.launch {
                repository.save(session.userId, entity)
                reload()
                requestSync()
            }
        }
    }

    private fun cycleStatus(task: Task) {
        val next = when (task.status) {
            Task.STATUS_TODO -> Task.STATUS_DOING
            Task.STATUS_DOING -> Task.STATUS_DONE
            else -> Task.STATUS_TODO
        }
        lifecycleScope.launch {
            repository.updateStatus(session.userId, task.id, next)
            reload()
            requestSync()
        }
    }

    private fun confirmDelete(task: Task) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, task.title))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repository.softDelete(session.userId, task.id)
                    reload()
                    requestSync()
                }
            }
            .show()
    }

    private fun openServerSettings() {
        ServerSettingsDialog.show(this, ServerSettings.baseUrl) { url ->
            if (url == ServerSettings.baseUrl) return@show
            ServerSettings.save(this, url)
            // 旧令牌在新服务器上无效，清除会话并回到登录页
            syncJob?.cancel()
            SessionStore.clear(this)
            Toast.makeText(this, R.string.server_changed, Toast.LENGTH_LONG).show()
            openLogin()
        }
    }

    private fun isOfflineTestAccount(): Boolean = session.userId == MockAuthApi.MOCK_USER_ID

    private fun showOfflineTestStatus(manual: Boolean = false) {
        if (!::binding.isInitialized) return
        val message = getString(R.string.offline_test_mode)
        binding.syncStatus.text = message
        if (manual) Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun hasLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT < 36 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED

    private fun requestSync(manual: Boolean = false) {
        if (isOfflineTestAccount()) {
            showOfflineTestStatus(manual)
            return
        }
        if (syncJob?.isActive == true) {
            if (!manual) syncAgain = true
            return
        }
        syncJob = lifecycleScope.launch {
            var manualRound = manual
            do {
                syncAgain = false
                setSyncing(true)
                var shouldExit = false
                try {
                    syncService.sync(session)
                    reload()
                    showSyncSuccess()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: UnauthorizedApiException) {
                    shouldExit = true
                    handleUnauthorized()
                } catch (error: NetworkApiException) {
                    showSyncFailure(error, manualRound, offline = true)
                } catch (error: ApiException) {
                    showSyncFailure(error, manualRound, offline = false)
                } catch (error: Exception) {
                    showSyncFailure(error, manualRound, offline = false)
                } finally {
                    setSyncing(false)
                }
                manualRound = false
                if (shouldExit) return@launch
            } while (syncAgain && isActive)
        }
    }

    private fun setSyncing(syncing: Boolean) {
        if (!::binding.isInitialized) return
        binding.toolbar.menu.findItem(R.id.action_sync)?.isEnabled = !syncing
        if (syncing) binding.syncStatus.setText(R.string.sync_in_progress)
    }

    private suspend fun showSyncSuccess() {
        val pending = repository.pendingCount(session.userId)
        binding.syncStatus.text = if (pending == 0) {
            getString(R.string.sync_success)
        } else {
            getString(R.string.sync_pending, pending)
        }
    }

    private fun showSyncFailure(error: Exception, manual: Boolean, offline: Boolean) {
        if (!::binding.isInitialized) return
        val message = if (offline) {
            getString(R.string.sync_offline)
        } else {
            getString(
                R.string.sync_failed,
                error.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.sync_unknown_error)
            )
        }
        binding.syncStatus.text = message
        if (manual) Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun handleUnauthorized() {
        val current = SessionStore.session(this)
        if (current?.userId == session.userId && current.token == session.token) {
            SessionStore.clear(this)
            openLogin()
        }
    }

    private fun openLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    companion object {
        private const val AUTO_SYNC_INTERVAL_MS = 5 * 60 * 1000L
    }
}
