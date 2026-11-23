package com.thanhvan.cotuongluyenky.engine.nativebridge

/**
 * Optional JNI bridge declaration corresponding to
 * `app/src/main/cpp/engine_bridge/pikafish_jni.cpp`.
 *
 * Loaded safely with UnsatisfiedLinkError fallback so builds without NDK
 * or native Pikafish sources remain fully functional.
 */
object PikafishNativeBridge {
    val isLibraryLoaded: Boolean = try {
        System.loadLibrary("engine_bridge")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    external fun nativeStartProcess(executablePath: String): Boolean
    external fun nativeSendCommand(command: String): Boolean
    external fun nativeReadLine(): String?
    external fun nativeStopProcess()
}
