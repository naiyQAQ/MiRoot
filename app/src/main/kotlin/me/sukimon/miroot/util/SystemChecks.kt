package me.sukimon.miroot.util

import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * System prerequisite checks for the HyperRoot exploit.
 */
object SystemChecks {

    enum class Status { PASS, FAIL, UNKNOWN }

    data class CheckResult(
        val status: Status,
        val description: String,
        val detail: String = ""
    ) {
        val passed get() = status == Status.PASS
        val unknown get() = status == Status.UNKNOWN
    }

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
                CheckResult(Status.PASS, "安全补丁: $patchStr", "补丁日期在漏洞修复之前")
            } else {
                CheckResult(Status.FAIL, "安全补丁: $patchStr", "补丁日期过新，漏洞可能已修复")
            }
        } catch (e: Exception) {
            CheckResult(Status.FAIL, "安全补丁: 未知", "无法解析补丁日期: $patchStr")
        }
    }

    /**
     * Check 2: Android version >= 15 (API 35)
     */
    fun checkAndroidVersion(): CheckResult {
        val sdk = Build.VERSION.SDK_INT
        return if (sdk >= 35) {
            CheckResult(Status.PASS, "Android ${Build.VERSION.RELEASE} (API $sdk)", "满足 Android 15+ 要求")
        } else {
            CheckResult(Status.FAIL, "Android ${Build.VERSION.RELEASE} (API $sdk)", "需要 Android 15 (API 35) 或更高")
        }
    }

    /**
     * Check 3: SELinux is Permissive
     *
     * /sys/fs/selinux/enforce is not readable by normal apps.
     * Use android.os.SELinux.isSELinuxEnforced() via reflection instead,
     * with getenforce command and prop fallbacks.
     */
    fun checkSELinux(): CheckResult {
        val mode = getSELinuxMode()
        return when (mode) {
            "Permissive" -> CheckResult(Status.PASS, "SELinux: Permissive", "SELinux 已设为宽松模式")
            "Enforcing" -> CheckResult(
                Status.FAIL,
                "SELinux: Enforcing",
                "请通过 fastboot 设置 SELinux 为宽松模式:\n" +
                        "fastboot oem set-gpu-preemption 0 androidboot.selinux=permissive\n" +
                        "完成后重新打开 App。"
            )
            else -> CheckResult(
                Status.UNKNOWN,
                "SELinux: 无法自动检测",
                "请确认你已通过 fastboot 设置 SELinux 为 Permissive。\n" +
                        "如果已设置，可以继续操作。"
            )
        }
    }

    private fun getSELinuxMode(): String? {
        // Method 1: android.os.SELinux.isSELinuxEnforced() — works for normal apps
        try {
            val seLinuxClass = Class.forName("android.os.SELinux")
            val isSELinuxEnforced = seLinuxClass.getMethod("isSELinuxEnforced")
            val enforced = isSELinuxEnforced.invoke(null) as Boolean
            return if (enforced) "Enforcing" else "Permissive"
        } catch (_: Exception) {}

        // Method 2: getenforce command
        try {
            val process = Runtime.getRuntime().exec("getenforce")
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output.equals("Permissive", ignoreCase = true)) return "Permissive"
            if (output.equals("Enforcing", ignoreCase = true)) return "Enforcing"
        } catch (_: Exception) {}

        // Method 3: system property
        try {
            val getProp = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.selinux"))
            val output = getProp.inputStream.bufferedReader().readText().trim()
            getProp.waitFor()
            if (output.equals("permissive", ignoreCase = true)) return "Permissive"
            if (output.equals("enforcing", ignoreCase = true)) return "Enforcing"
        } catch (_: Exception) {}

        // Method 4: read file (may work on some ROMs)
        try {
            val enforce = java.io.File("/sys/fs/selinux/enforce").readText().trim()
            return if (enforce == "0") "Permissive" else "Enforcing"
        } catch (_: Exception) {}

        return null
    }

    /**
     * Check 4: Device is Xiaomi
     */
    fun checkDeviceManufacturer(): CheckResult {
        val manufacturer = Build.MANUFACTURER
        return if (manufacturer.equals("Xiaomi", ignoreCase = true)) {
            CheckResult(Status.PASS, "设备: $manufacturer", "小米设备确认")
        } else {
            CheckResult(Status.FAIL, "设备: $manufacturer", "需要小米(Xiaomi)设备")
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
     * Returns true if no check explicitly failed.
     * UNKNOWN checks do NOT block — user takes responsibility.
     */
    fun allChecksPassed(): Boolean {
        return runAllChecks().none { it.status == Status.FAIL }
    }
}
