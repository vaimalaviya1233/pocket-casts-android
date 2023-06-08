package au.com.shiftyjelly.pocketcasts.encryptedlogging

import com.automattic.android.tracks.crashlogging.PerformanceMonitoringConfig
import javax.inject.Inject

class PCPerformanceMonitoringConfig @Inject constructor() {
    operator fun invoke() = PerformanceMonitoringConfig.Disabled
}
