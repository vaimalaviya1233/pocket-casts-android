package au.com.shiftyjelly.pocketcasts.shared

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import au.com.shiftyjelly.pocketcasts.analytics.AppLifecycleAnalytics
import au.com.shiftyjelly.pocketcasts.encryptedlogging.utils.WellSqlInitializer
import javax.inject.Inject

class AppLifecycleObserver @Inject constructor(
    private val appLifecycleAnalytics: AppLifecycleAnalytics,
    wellSqlInitializer: WellSqlInitializer,
) : DefaultLifecycleObserver {

    init {
        wellSqlInitializer.init()
    }

    fun setup() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        appLifecycleAnalytics.onApplicationInstalledOrUpgraded()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        appLifecycleAnalytics.onApplicationEnterForeground()
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        appLifecycleAnalytics.onApplicationEnterBackground()
    }
}
