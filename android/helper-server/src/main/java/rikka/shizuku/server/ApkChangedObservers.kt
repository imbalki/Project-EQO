// Origin: RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea,
//   path: server/src/main/java/rikka/shizuku/server/ApkChangedObservers.kt
package rikka.shizuku.server

import android.os.FileObserver
import android.util.Log
import java.io.File
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap

// inotify mask bits FileObserver does not expose (values from linux/inotify.h).
private const val IN_IGNORED = 0x00008000
private const val IN_ISDIR = 0x40000000

interface ApkChangedListener {
    fun onApkChanged()
}

private val observers = Collections.synchronizedMap(HashMap<String, ApkChangedObserver>())

object ApkChangedObservers {
    @JvmStatic
    fun start(
        apkPath: String,
        listener: ApkChangedListener,
    ) {
        // inotify watchs inode, if the there are still processes holds the file, DELTE_SELF will not be triggered
        // so we need to watch the parent folder

        val path = File(apkPath).parent!!
        val observer =
            observers.getOrPut(path) {
                ApkChangedObserver(path).apply {
                    startWatching()
                }
            }
        observer.addListener(listener)
    }

    @JvmStatic
    fun stop(listener: ApkChangedListener) {
        val pathToRemove = mutableListOf<String>()

        for ((path, observer) in observers) {
            observer.removeListener(listener)

            if (!observer.hasListeners()) {
                pathToRemove.add(path)
            }
        }

        for (path in pathToRemove) {
            observers.remove(path)?.stopWatching()
        }
    }
}

class ApkChangedObserver(
    private val path: String,
) : FileObserver(path, DELETE) {
    private val listeners = mutableSetOf<ApkChangedListener>()

    fun addListener(listener: ApkChangedListener): Boolean = listeners.add(listener)

    fun removeListener(listener: ApkChangedListener): Boolean = listeners.remove(listener)

    fun hasListeners(): Boolean = listeners.isNotEmpty()

    override fun onEvent(
        event: Int,
        path: String?,
    ) {
        Log.d("EqoHelperServer", "onEvent: ${eventToString(event)} $path")

        if ((event and IN_IGNORED) != 0 || path == null) {
            return
        }

        if (path == "base.apk") {
            stopWatching()
            ArrayList(listeners).forEach { it.onApkChanged() }
        }
    }

    override fun startWatching() {
        super.startWatching()
        Log.d("EqoHelperServer", "start watching $path")
    }

    override fun stopWatching() {
        super.stopWatching()
        Log.d("EqoHelperServer", "stop watching $path")
    }
}

private val eventNames =
    listOf(
        FileObserver.ACCESS to "ACCESS",
        FileObserver.MODIFY to "MODIFY",
        FileObserver.ATTRIB to "ATTRIB",
        FileObserver.CLOSE_WRITE to "CLOSE_WRITE",
        FileObserver.CLOSE_NOWRITE to "CLOSE_NOWRITE",
        FileObserver.OPEN to "OPEN",
        FileObserver.MOVED_FROM to "MOVED_FROM",
        FileObserver.MOVED_TO to "MOVED_TO",
        FileObserver.CREATE to "CREATE",
        FileObserver.DELETE to "DELETE",
        FileObserver.DELETE_SELF to "DELETE_SELF",
        FileObserver.MOVE_SELF to "MOVE_SELF",
        IN_IGNORED to "IN_IGNORED",
        IN_ISDIR to "IN_ISDIR",
    )

private fun eventToString(event: Int): String =
    eventNames
        .filter { (flag, _) -> event and flag == flag }
        .joinToString(" | ") { (_, name) -> name }
