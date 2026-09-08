-keepclasseswithmembernames class dev.zeroinput.engine.rime.NativeRimeBridge {
    native <methods>;
}

-keep class dev.zeroinput.engine.rime.NativeRimeUpdate { *; }
-keep class dev.zeroinput.engine.rime.NativeCandidatePage { *; }

-keep class dev.zeroinput.ime.ZeroInputService { *; }
-keep class dev.zeroinput.ime.auth.SecureClipboardUnlockActivity { *; }
