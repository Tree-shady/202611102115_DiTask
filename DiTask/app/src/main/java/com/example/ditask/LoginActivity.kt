package com.example.ditask

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.ditask.data.ServerSettings
import com.example.ditask.data.SessionStore
import com.example.ditask.data.TaskRepository
import com.example.ditask.databinding.ActivityLoginBinding
import com.example.ditask.network.ApiClient
import com.example.ditask.network.AuthApi
import com.example.ditask.network.LoginResult
import com.example.ditask.network.MockAuthApi
import com.example.ditask.network.RealAuthApi
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 登录 / 注册页：真实鉴权成功后保留并认领本机旧任务。 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val mockAuthApi: AuthApi = MockAuthApi()
    private val repository by lazy { TaskRepository(this) }
    private var isRegisterMode = false

    /** Android 16+ 访问局域网服务必须先获得授权 */
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        setLoading(false)
        if (granted) {
            authenticate()
        } else {
            Snackbar.make(
                binding.root,
                R.string.local_network_required,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (SessionStore.isLoggedIn(this)) {
            openMain()
            return
        }
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 品牌头部延伸到状态栏下方
        val headerLeft = binding.loginHeader.paddingLeft
        val headerTop = binding.loginHeader.paddingTop
        val headerRight = binding.loginHeader.paddingRight
        val headerBottom = binding.loginHeader.paddingBottom
        val contentBottom = binding.loginContent.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.loginHeader.setPadding(
                headerLeft + bars.left,
                headerTop + bars.top,
                headerRight + bars.right,
                headerBottom
            )
            binding.loginContent.setPadding(bars.left, 0, bars.right, contentBottom + bars.bottom)
            insets
        }

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = !night
        }

        binding.btnLogin.setOnClickListener { authenticate() }
        binding.inputServer.setText(ServerSettings.baseUrl)
        binding.btnToggleAuthMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            renderMode()
        }
        binding.btnOfflineTest.setOnClickListener {
            isRegisterMode = false
            renderMode()
            binding.inputUsername.setText(MockAuthApi.MOCK_USERNAME)
            binding.inputPassword.setText(MockAuthApi.MOCK_PASSWORD)
            authenticate()
        }
        binding.inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                authenticate()
                true
            } else {
                false
            }
        }
        renderMode()
    }

    private fun renderMode() {
        binding.loginCardTitle.setText(
            if (isRegisterMode) R.string.register_card_title else R.string.login_card_title
        )
        binding.loginCardSubtitle.setText(
            if (isRegisterMode) R.string.register_card_subtitle else R.string.login_card_subtitle
        )
        binding.btnToggleAuthMode.setText(
            if (isRegisterMode) R.string.login_mode_toggle else R.string.register_mode_toggle
        )
        binding.inputServerLayout.error = null
        binding.inputUsernameLayout.error = null
        binding.inputPasswordLayout.error = null
        setLoading(false)
    }

    private fun authenticate() {
        if (!binding.btnLogin.isEnabled) return

        val serverUrl = ServerSettings.normalize(binding.inputServer.text?.toString().orEmpty())
        val username = binding.inputUsername.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()

        binding.inputServerLayout.error = null
        binding.inputUsernameLayout.error = null
        binding.inputPasswordLayout.error = null

        if (serverUrl.isEmpty()) {
            binding.inputServerLayout.error = getString(R.string.server_required)
            return
        }
        if (!ServerSettings.isValid(serverUrl)) {
            binding.inputServerLayout.error = getString(R.string.server_invalid)
            return
        }
        // 先保存地址，注册失败或网络异常时下次打开仍保留用户输入
        ServerSettings.save(this, serverUrl)

        if (username.isEmpty()) {
            binding.inputUsernameLayout.error = getString(R.string.username_required)
            return
        }
        if (password.isEmpty()) {
            binding.inputPasswordLayout.error = getString(R.string.password_required)
            return
        }
        if (isRegisterMode && password.length < 6) {
            binding.inputPasswordLayout.error = getString(R.string.password_too_short)
            return
        }

        val useOfflineTestAccount = !isRegisterMode && username == MockAuthApi.MOCK_USERNAME
        if (!useOfflineTestAccount && !hasLocalNetworkPermission()) {
            setLoading(true)
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val authApi: AuthApi = if (useOfflineTestAccount) {
                    mockAuthApi
                } else {
                    // 每次按最新服务器地址创建，用户修改地址后重试立即生效
                    RealAuthApi(ApiClient())
                }
                val result = if (isRegisterMode) {
                    authApi.register(username, password)
                } else {
                    authApi.login(username, password)
                }
                when (result) {
                    is LoginResult.Success -> {
                        repository.claimLegacyTasks(result.userId)
                        // 每次登录都作废同步游标，保证换设备后首次同步全量拉取
                        repository.resetSyncCursor(result.userId)
                        SessionStore.save(
                            this@LoginActivity,
                            username,
                            result.token,
                            result.userId
                        )
                        openMain()
                    }
                    is LoginResult.Failure -> {
                        setLoading(false)
                        Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                setLoading(false)
                Snackbar.make(
                    binding.root,
                    error.message ?: getString(R.string.sync_unknown_error),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun hasLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT < 36 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_LOCAL_NETWORK
            ) == PackageManager.PERMISSION_GRANTED

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnToggleAuthMode.isEnabled = !loading
        binding.btnOfflineTest.isEnabled = !loading
        val text = when {
            loading && isRegisterMode -> R.string.register_loading
            loading -> R.string.login_loading
            isRegisterMode -> R.string.register
            else -> R.string.login
        }
        binding.btnLogin.setText(text)
    }

    private fun openMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
