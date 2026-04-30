#!/system/bin/sh

# 脚本功能：恢复 adbd_config_prop、shell_prop 关联的 SELinux 上下文与系统属性
# 依赖：Magisk 环境（提供 resetprop）、SELinux 工具链（提供 restorecon）
# 修改说明：增加了安全属性重置、adbd 终止、SELinux 强制模式设置

# ========== 步骤1：权限与环境检查 ==========
if [ "$(id -u)" -ne 0 ]; then
    echo "错误：需 Root 权限运行此脚本！"
    exit 1
fi

# 检查 resetprop 是否存在（Magisk 内置）
if ! command -v resetprop >/dev/null 2>&1; then
    echo "错误：未检测到 resetprop（需安装 Magisk 或手动部署工具）！"
    exit 1
fi

# 检查 restorecon 是否存在（部分设备需手动安装 SELinux 工具包）
if ! command -v restorecon >/dev/null 2>&1; then
    echo "警告：未检测到 restorecon，跳过上下文强制恢复（仅重置属性）！"
    RESTORECON_AVAILABLE=0
else
    RESTORECON_AVAILABLE=1
fi

# ========== 步骤2：安全属性重置与 SELinux 强制模式设置（新增） ==========
# 目的：关闭可调试状态、启用安全启动、禁用 adb root、强制 SELinux 为 enforcing 模式
echo "正在重置关键安全属性..."
resetprop ro.debuggable 0      # 禁用应用调试（生产环境安全）
resetprop ro.secure 1          # 启用系统安全模式（关闭 adb 非 root 提权通道）
resetprop service.adb.root 0   # 禁止 adbd 以 root 权限运行
resetprop ro.boot.selinux enforcing  # 设置内核引导 SELinux 为强制模式

# 终止当前所有 adbd 进程，确保后续重启使用新属性
echo "正在终止现有 adbd 进程..."
pkill -9 adbd

# 强制设置 SELinux 为 enforcing 模式（实时生效）
echo "正在设置 SELinux 为 enforcing 模式..."
setenforce 1

# ========== 步骤3：恢复 SELinux 上下文（优先） ==========
if [ $RESTORECON_AVAILABLE -eq 1 ]; then
    # 恢复 adbd_config_prop 关联属性的 SELinux 上下文
    restorecon -v /dev/__properties__/u:object_r:adbd_config_prop:s0
    # 恢复 shell_prop 关联属性的 SELinux 上下文
    restorecon -v /dev/__properties__/u:object_r:shell_prop:s0
    echo "SELinux 上下文恢复完成（通过 restorecon）"
fi

# ========== 步骤5：验证恢复结果 ==========
echo "===== 验证属性值 ====="
getprop "$ADBD_PROP"
getprop "$SHELL_PROP"

echo "===== 验证 SELinux 上下文 ====="
ls -Z /dev/__properties__/ | grep adbd_config_prop
ls -Z /dev/__properties__/ | grep shell_prop
echo "Running..."
PID_MAX=$(cat /proc/sys/kernel/pid_max)
last_pid=$(sh -c 'echo $PPID')

wrapped=0
while true; do
    : &
    current_pid=$!
    if [ "$current_pid" -lt "$last_pid" ]; then
        wrapped=1
    fi

    if [ "$wrapped" -eq 1 ] && [ "$current_pid" -ge 1700 ]; then
        break
    fi
    last_pid=$current_pid
done

echo "Done. Restarting zygote..."
setprop ctl.restart zygote