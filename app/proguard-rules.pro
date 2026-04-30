# ProGuard rules for HyperRoot

# Keep Compose
-dontwarn androidx.compose.**

# Keep our Binder exploit class (uses reflection)
-keep class me.sukimon.miroot.util.MqsasExploit { *; }

# Keep Android hidden API reflection targets
-dontwarn android.os.ServiceManager
