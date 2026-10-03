// Origin: RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea,
//   path: server/src/main/java/rikka/shizuku/server/ktx/Handler.kt
package rikka.shizuku.server.ktx

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

val mainHandler by lazy {
    Handler(Looper.getMainLooper())
}

private val workerThread by lazy(LazyThreadSafetyMode.NONE) {
    HandlerThread("Worker").apply { start() }
}

val workerHandler by lazy {
    Handler(workerThread.looper)
}
