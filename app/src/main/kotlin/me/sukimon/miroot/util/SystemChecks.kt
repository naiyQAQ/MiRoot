package me.sukimon.miroot.util

import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * System prerequisite checks for the HyperRoot exploit.
 */
object SystemChecks {

    data class CheckResult(
        val passed: Boolean,
        val description: String,
        val detail: String = ""
    )

    /**
     * Check 1: Security patch level <= 2026-01-01
     * (Must be before 2026-02-01 to be exploitable)
     */
    fun checkSecurityPatch(): CheckResult {
        val patchStr = Build.VERSION.SECURITY_PATCH // e.g. "2025-12-01"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val patchDate = sdf.parse(patchStr)
            val cutoffDate = sdf.parse("2026-02-01")
            if (patchDate != null && cutoffDate != null && patchDate.before(cutoffDate)) {
                CheckResult(true, "安全补丁: $patchStr", "补丁日期在漏洞修复之前")
            } else {
                CheckResult(false, "安全补丁: $patchStr", "补丁日期过新，漏洞可能已修复")
            }
        } catch (e: Exception) {
            CheckResult(false, "安全补丁: 未知", "无法解析补丁日期: $patchStr")
        }
    }

    /**
     * Check 2: Android version >= 15 (API 35)
     */
    fun checkAndroidVersion(): CheckResult {
        val sdk = Build.VERSION.SDK_INT
        return if (sdk >= 35) {
            CheckResult(true, "Android ${Build.VERSION.RELEASE} (API $sdk)", "满足 Android 15+ 要求")
        } else {
            CheckResult(false, "Android ${Build.VERSION.RELEASE} (API $sdk)", "需要 Android 15 (API 35) 或更高")
        }
    }

    /**
     * Check 3: SELinux is Permissive
     */
    fun checkSELinux(): CheckResult {
        return try {
            val enforce = File("/sys/fs/selinux/enforce").readText().trim()
            if (enforce == "0") {
                CheckResult(true, "SELinux: Permissive", "SELinux 已设为宽松模式")
            } else {
                CheckResult(
                    false,
                    "SELinux: Enforcing",
                    "请通过 fastboot 设置 SELinux 为宽松模式:\n" +
                            "fastboot oem set-gpu-preemption 0 androidboot.selinux=permissive\n" +
                            "完成后重新打开 App。"
                )
            }
        } catch (e: Exception) {
            CheckResult(false, "SELinux: 无法读取", "无法读取 /sys/fs/selinux/enforce: ${e.message}")
        }
    }

    /**
     * Check 4: Device is Xiaomi
     */
    fun checkDeviceManufacturer(): CheckResult {
        val manufacturer = Build.MANUFACTURER
        return if (manufacturer.equals("Xiaomi", ignoreCase = true)) {
            CheckResult(true, "设备: $manufacturer", "小米设备确认")
        } else {
            CheckResult(false, "设备: $manufacturer", "需要小米(Xiaomi)设备")
        }
    }

    /**
     * Run all checks and return results.
     */
    fun runAllChecks(): List<CheckResult> {
        return listOf(
            checkSecurityPatch(),
            checkAndroidVersion(),
            checkSELinux(),
            checkDeviceManufacturer()
        )
    }

    /**
     * Returns true if all checks pass.
     */
    fun allChecksPassed(): Boolean {
        return runAllChecks().all { it.passed }
    }
}
