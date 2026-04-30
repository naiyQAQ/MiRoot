/system/bin/service call miui.mqsas.IMQSNative 21 i32 1 s16 "/data/local/tmp/resetprop" i32 1 s16 "ro.debuggable 1" s16 "/dev/null" i32 60

/system/bin/service call miui.mqsas.IMQSNative 21 i32 1 s16 "/data/local/tmp/resetprop" i32 1 s16 "ro.secure 0" s16 "/dev/null" i32 60

/system/bin/service call miui.mqsas.IMQSNative 21 i32 1 s16 "/data/local/tmp/resetprop" i32 1 s16 "service.adb.root 1" s16 "/dev/null" i32 60

/system/bin/service call miui.mqsas.IMQSNative 21 i32 1 s16 "/system/bin/sh" i32 1 s16 "/data/local/tmp/restartadbd.sh" s16 "/dev/null" i32 60
