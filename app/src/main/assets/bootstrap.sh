#!/system/bin/sh
#####################################################################
#   HyperRoot - Bootstrap Script
#####################################################################
#
# This script is the entry point called by daemon_runner.
# It sets up the environment and hands off to master.sh via busybox.
#
# Usage: daemon_runner bootstrap.sh <files_dir>
#   <files_dir>: App's filesDir containing all assets
#####################################################################

FILES_DIR="$1"
if [ -z "$FILES_DIR" ]; then
  echo "Usage: bootstrap.sh <files_dir>"
  exit 1
fi

WORK_DIR="/data/local/tmp"

# Copy all assets to working directory
cp -f "$FILES_DIR/busybox"    "$WORK_DIR/busybox"
cp -f "$FILES_DIR/resetprop"  "$WORK_DIR/resetprop"
cp -f "$FILES_DIR/magisk.apk" "$WORK_DIR/magisk.apk"
cp -f "$FILES_DIR/master.sh"  "$WORK_DIR/master.sh"

# Set permissions
chmod 755 "$WORK_DIR/busybox"
chmod 755 "$WORK_DIR/resetprop"
chmod 755 "$WORK_DIR/master.sh"

# Execute master.sh with busybox
cd "$WORK_DIR"
export PATH="$WORK_DIR:$PATH"
export ASH_STANDALONE=1
exec ./busybox sh master.sh
