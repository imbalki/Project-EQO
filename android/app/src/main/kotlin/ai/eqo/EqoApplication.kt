package ai.eqo

import ai.eqo.handle.prepareHandlePanel
import android.app.Application

/** Phase-One uses an explicit runtime, not the unsupported donor Hilt graph. */
class EqoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ai.eqo.accessibility.handle.EdgeHandleFeatures.refreshEntries = ai.eqo.explain.ExplainNotificationService::refresh
        ai.eqo.accessibility.handle.EdgeHandleFeatures.explain = { context, source ->
            ai.eqo.explain.ExplainEntryActivity
                .launch(context, source)
        }
        ai.eqo.accessibility.handle.EdgeHandleFeatures
            .install(ai.eqo.handle.createHandleRegistry(this), ::prepareHandlePanel)
    }
}
