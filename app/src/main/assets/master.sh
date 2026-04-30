#!/system/bin/sh
#####################################################################
#   HyperRoot - Master Script
#####################################################################
#
# Merged from live_setup.sh + hide_magisk_root.sh
# Optimized: only ONE zygote restart instead of two.
#
# This script:
#   1. Installs Magisk APK
#   2. Extracts Magisk binaries from APK
#   3. Stops zygote
#   4. Sets up Magisk tmpfs overlay
#   5. Loads SELinux policy patches
#   6. Starts Magisk daemon
#   7. Hides root traces (resetprop)
#   8. Restarts zygote (single restart)
#   9. Completes Magisk boot sequence
#  10. Cleanup: restore SELinux contexts
#
# Usage: Called by bootstrap.sh after double-fork detach
#####################################################################

LOG="/data/local/tmp/master.log"
log() {
  echo "[$(date '+%H:%M:%S')] $1" >> "$LOG"
  echo "[$(date '+%H:%M:%S')] $1"
}

mount_tmpfs() {
  # If a file named 'magisk' is in current directory, mount will fail
  mv magisk magisk.tmp 2>/dev/null
  mount -t tmpfs -o 'mode=0755' magisk "$1"
  mv magisk.tmp magisk 2>/dev/null
}

mount_sbin() {
  mount_tmpfs /sbin
  chcon u:object_r:rootfs:s0 /sbin
}

# ===== Phase 0: Environment setup =====
log "===== HyperRoot Master Script starting ====="
log "PID=$$, PPID=$PPID, UID=$(id -u)"

cd /data/local/tmp
chmod 755 busybox

# Re-exec with busybox ash if not already
if [ -z "$FIRST_STAGE" ]; then
  export FIRST_STAGE=1
  export ASH_STANDALONE=1
  exec ./busybox sh "$0" "$@"
fi

# ===== Phase 1: Install Magisk APK =====
log "Phase 1: Installing Magisk APK..."
pm install -r -g "$(pwd)/magisk.apk" >> "$LOG" 2>&1
log "Magisk APK installed"

# ===== Phase 2: Extract binaries from APK =====
log "Phase 2: Extracting Magisk binaries..."
unzip -oj magisk.apk 'assets/util_functions.sh' 'assets/stub.apk' >> "$LOG" 2>&1
. ./util_functions.sh

api_level_arch_detect

unzip -oj magisk.apk "lib/$ABI/*" -x "lib/$ABI/libbusybox.so" >> "$LOG" 2>&1
for file in lib*.so; do
  chmod 755 "$file"
  mv "$file" "${file:3:${#file}-6}"
done

if $IS64BIT && [ -e "/system/bin/linker" ]; then
  unzip -oj magisk.apk "lib/$ABI32/libmagisk.so" >> "$LOG" 2>&1
  mv libmagisk.so magisk32
  chmod 755 magisk32
fi
log "Binaries extracted (ABI=$ABI)"

# ===== Phase 3: Stop zygote =====
log "Phase 3: Stopping zygote and existing Magisk..."
magisk --stop 2>/dev/null
stop
if [ -d /debug_ramdisk ]; then
  umount -l /debug_ramdisk 2>/dev/null
fi

# Make sure boot completed props are not set to 1
setprop sys.boot_completed 0

# Mount /cache if not already mounted
if ! grep -q ' /cache ' /proc/mounts; then
  mount -t tmpfs -o 'mode=0755' tmpfs /cache
fi
log "Zygote stopped"

# ===== Phase 4: Setup Magisk tmpfs overlay =====
log "Phase 4: Setting up Magisk overlay..."
MAGISKTMP=/sbin

if mount | grep -q rootfs; then
  # Legacy rootfs
  mount -o rw,remount /
  rm -rf /root
  mkdir /root /sbin 2>/dev/null
  chmod 750 /root /sbin
  ln /sbin/* /root
  mount -o ro,remount /
  mount_sbin
  ln -s /root/* /sbin
elif [ -e /sbin ]; then
  # Legacy SAR
  mount_sbin
  mkdir -p /dev/sysroot
  block=$(mount | grep ' / ' | awk '{ print $1 }')
  [ "$block" = "/dev/root" ] && block=/dev/block/vda1
  mount -o ro "$block" /dev/sysroot
  for file in /dev/sysroot/sbin/*; do
    [ ! -e "$file" ] && break
    if [ -L "$file" ]; then
      cp -af "$file" /sbin
    else
      sfile=/sbin/$(basename "$file")
      touch "$sfile"
      mount -o bind "$file" "$sfile"
    fi
  done
  umount -l /dev/sysroot
  rm -rf /dev/sysroot
else
  # Android Q+ without sbin (most HyperOS devices)
  MAGISKTMP=/debug_ramdisk
  mount_tmpfs /debug_ramdisk
fi
log "Magisk tmpfs at $MAGISKTMP"

# Copy Magisk binaries to overlay
mkdir -p $MAGISKBIN 2>/dev/null
unzip -oj magisk.apk 'assets/*.sh' -d $MAGISKBIN >> "$LOG" 2>&1
mkdir /data/adb/modules 2>/dev/null
mkdir /data/adb/post-fs-data.d 2>/dev/null
mkdir /data/adb/service.d 2>/dev/null

for file in magisk magisk32 magiskpolicy stub.apk; do
  [ ! -f "./$file" ] && continue
  chmod 755 "./$file"
  cp -af "./$file" "$MAGISKTMP/$file"
  cp -af "./$file" "$MAGISKBIN/$file"
done
cp -af ./magiskboot "$MAGISKBIN/magiskboot" 2>/dev/null
cp -af ./magiskinit "$MAGISKBIN/magiskinit" 2>/dev/null
cp -af ./busybox "$MAGISKBIN/busybox"

ln -s ./magisk "$MAGISKTMP/su"
ln -s ./magisk "$MAGISKTMP/resetprop"
ln -s ./magiskpolicy "$MAGISKTMP/supolicy"

mkdir -p "$MAGISKTMP/.magisk/device"
mkdir -p "$MAGISKTMP/.magisk/worker"
mount_tmpfs "$MAGISKTMP/.magisk/worker"
mount --make-private "$MAGISKTMP/.magisk/worker"
touch "$MAGISKTMP/.magisk/config"

export MAGISKTMP
MAKEDEV=1 $MAGISKTMP/magisk --preinit-device >> "$LOG" 2>&1
log "Magisk overlay ready"

# ===== Phase 5: SELinux policy patching =====
log "Phase 5: Patching SELinux policy..."
RULESCMD=""
rule="$MAGISKTMP/.magisk/preinit/sepolicy.rule"
[ -f "$rule" ] && RULESCMD="--apply $rule"

if [ -d /sys/fs/selinux ]; then
  if [ -f /vendor/etc/selinux/precompiled_sepolicy ]; then
    ./magiskpolicy --load /vendor/etc/selinux/precompiled_sepolicy --live --magisk $RULESCMD >> "$LOG" 2>&1
  elif [ -f /sepolicy ]; then
    ./magiskpolicy --load /sepolicy --live --magisk $RULESCMD >> "$LOG" 2>&1
  else
    ./magiskpolicy --live --magisk $RULESCMD >> "$LOG" 2>&1
  fi
fi
log "SELinux policy patched"

# ===== Phase 6: Start Magisk daemon (post-fs-data) =====
log "Phase 6: Starting Magisk post-fs-data..."
$MAGISKTMP/magisk --post-fs-data >> "$LOG" 2>&1

# ===== Phase 7: Hide root traces BEFORE restarting zygote =====
log "Phase 7: Hiding root traces..."

# Use the bundled resetprop (from Magisk overlay)
$MAGISKTMP/resetprop ro.debuggable 0
$MAGISKTMP/resetprop ro.secure 1
$MAGISKTMP/resetprop service.adb.root 0
$MAGISKTMP/resetprop ro.boot.selinux enforcing

# Kill adbd to pick up new props
pkill -9 adbd 2>/dev/null
log "Root traces hidden"

# ===== Phase 8: Restart zygote (SINGLE restart) =====
log "Phase 8: Restarting zygote..."
start

# ===== Phase 9: Complete Magisk boot sequence =====
log "Phase 9: Completing Magisk boot..."
$MAGISKTMP/magisk --service >> "$LOG" 2>&1

# Wait for zygote to be ready
sleep 3
$MAGISKTMP/magisk --boot-complete >> "$LOG" 2>&1
log "Magisk boot complete"

# ===== Phase 10: SELinux context restoration =====
log "Phase 10: Restoring SELinux contexts..."

if command -v restorecon >/dev/null 2>&1; then
  restorecon -v /dev/__properties__/u:object_r:adbd_config_prop:s0 >> "$LOG" 2>&1
  restorecon -v /dev/__properties__/u:object_r:shell_prop:s0 >> "$LOG" 2>&1
  log "SELinux contexts restored"
else
  log "restorecon not available, skipping context restore"
fi

log "===== HyperRoot injection complete! ====="
