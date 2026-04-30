package me.sukimon.miroot.viewmodel

import android.app.Application
import android.os.Parcel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.sukimon.miroot.util.AssetExtractor
import me.sukimon.miroot.util.MqsasExploit
import me.sukimon.miroot.util.SystemChecks
import java.io.File

data class InjectionState(
    val checks: List<SystemChecks.CheckResult> = emptyList(),
    val allChecksPassed: Boolean = false,
    val isInjecting: Boolean = false,
    val isRestarting: Boolean = false,
    val injectionComplete: Boolean = false,
    val logs: List<String> = emptyList(),
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(InjectionState())
    val state: StateFlow<InjectionState> = _state.asStateFlow()

    init {
        runChecks()
    }

    fun runChecks() {
        viewModelScope.launch(Dispatchers.IO) {
            val results = SystemChecks.runAllChecks()
            _state.value = _state.value.copy(
                checks = results,
                allChecksPassed = results.all { it.passed }
            )
        }
    }

    private fun addLog(message: String) {
        _state.value = _state.value.copy(
            logs = _state.value.logs + message
        )
    }

    /**
     * Start the injection process using the MQSAS exploit.
     */
    fun startInjection() {
        if (_state.value.isInjecting) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(
                isInjecting = true,
                error = null,
                logs = emptyList()
            )

            try {
                // Phase 0: Extract assets
                addLog("阶段 0: 释放文件...")
                val context = getApplication<Application>()
                val result = AssetExtractor.extractAll(context)
                if (result.isFailure) {
                    throw result.exceptionOrNull()!!
                }
                val filesDir = result.getOrThrow()
                AssetExtractor.chmodAll(filesDir)
                addLog("文件释放完成: ${filesDir.absolutePath}")

                // Phase 1: Verify exploit
                addLog("阶段 1: 验证漏洞可用性...")
                val testResult = MqsasExploit.testExploit()
                if (!testResult) {
                    throw Exception("漏洞验证失败: IMQSNative 服务不可用或已修复")
                }
                addLog("漏洞验证通过")

                // Phase 2: Launch daemon
                addLog("阶段 2: 启动注入进程 (double-fork)...")
                val daemonRunner = File(filesDir, "daemon_runner").absolutePath
                val bootstrap = File(filesDir, "bootstrap.sh").absolutePath

                val launchResult = MqsasExploit.launchDaemon(
                    daemonRunnerPath = daemonRunner,
                    bootstrapPath = bootstrap,
                    filesDir = filesDir.absolutePath
                )

                if (launchResult.isFailure) {
                    throw launchResult.exceptionOrNull()!!
                }
                launchResult.getOrNull()?.recycle()

                addLog("注入进程已启动")
                addLog("阶段 3: Magisk 注入中 (后台执行)...")
                addLog("zygote 将重启，界面可能会短暂消失")
                addLog("注入完成后 Magisk 将自动生效")

                _state.value = _state.value.copy(
                    isInjecting = false,
                    injectionComplete = true
                )

            } catch (e: Exception) {
                addLog("错误: ${e.message}")
                _state.value = _state.value.copy(
                    isInjecting = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Restart Magisk using su (requires existing root from Magisk).
     * This re-runs master.sh via su instead of the exploit.
     */
    fun restartMagisk() {
        if (_state.value.isRestarting) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(
                isRestarting = true,
                error = null,
                logs = emptyList()
            )

            try {
                addLog("重启 Magisk: 释放文件...")
                val context = getApplication<Application>()
                val result = AssetExtractor.extractAll(context)
                if (result.isFailure) {
                    throw result.exceptionOrNull()!!
                }
                val filesDir = result.getOrThrow()
                AssetExtractor.chmodAll(filesDir)

                addLog("重启 Magisk: 通过 su 执行...")

                // Use su to run bootstrap.sh directly (already have root)
                val process = withContext(Dispatchers.IO) {
                    Runtime.getRuntime().exec(
                        arrayOf(
                            "su", "-c",
                            "${filesDir.absolutePath}/busybox sh ${filesDir.absolutePath}/bootstrap.sh ${filesDir.absolutePath}"
                        )
                    )
                }

                addLog("Magisk 重启指令已发送")
                addLog("zygote 将重启，界面可能会短暂消失")
                addLog("完成后 Magisk 将重新生效")

                _state.value = _state.value.copy(
                    isRestarting = false,
                    injectionComplete = true
                )
            } catch (e: Exception) {
                addLog("错误: ${e.message}")
                _state.value = _state.value.copy(
                    isRestarting = false,
                    error = e.message
                )
            }
        }
    }
}
