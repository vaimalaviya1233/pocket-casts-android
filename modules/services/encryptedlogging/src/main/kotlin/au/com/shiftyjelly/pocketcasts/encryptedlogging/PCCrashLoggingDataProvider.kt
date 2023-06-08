package au.com.shiftyjelly.pocketcasts.encryptedlogging

import au.com.shiftyjelly.pocketcasts.encryptedlogging.utils.LocaleProvider
import au.com.shiftyjelly.pocketcasts.encryptedlogging.utils.LogFileProviderWrapper
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.utils.di.ForApplicationScope
import com.automattic.android.tracks.crashlogging.CrashLoggingDataProvider
import com.automattic.android.tracks.crashlogging.CrashLoggingUser
import com.automattic.android.tracks.crashlogging.EventLevel
import com.automattic.android.tracks.crashlogging.ExtraKnownKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.model.AccountModel
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.fluxc.store.AccountStore.OnAccountChanged
import java.util.Locale
import javax.inject.Inject

class PCCrashLoggingDataProvider @Inject constructor(
//    private val sharedPreferences: SharedPreferences,
    private val accountStore: AccountStore,
    private val localeProvider: LocaleProvider,
    private val encryptedLogging: EncryptedLogging,
    private val logFileProvider: LogFileProviderWrapper,
    @ForApplicationScope private val coroutineScope: CoroutineScope,
    pcPerformanceMonitoringConfig: PCPerformanceMonitoringConfig,
    dispatcher: Dispatcher,
    settings: Settings,
) : CrashLoggingDataProvider {
    init {
        dispatcher.register(this)
    }

    override val buildType: String = BuildConfig.BUILD_TYPE
    override val enableCrashLoggingLogs: Boolean = BuildConfig.DEBUG
    override val locale: Locale?
        get() = localeProvider.provideLocale()
    override val releaseName: String = BuildConfig.VERSION_NAME
    override val sentryDSN: String = settings.getSentryDsn()

    override val applicationContextProvider = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun crashLoggingEnabled(): Boolean {
         /*if (BuildConfig.DEBUG) {
             return false
         }

         val hasUserAllowedReporting = sharedPreferences.getBoolean(
             context.getString(R.string.pref_key_send_crash),
             true
         )
        return hasUserAllowedReporting*/
        return true
    }

    override fun extraKnownKeys(): List<ExtraKnownKey> {
        return listOf(EXTRA_UUID)
    }

    /**
     * If Sentry is unable to upload the event in its first attempt, it'll call the `setBeforeSend` callback
     * before trying to send it again. This can be easily reproduced by turning off network connectivity
     * and re-launching the app over and over again which will hit this callback each time.
     *
     * The problem with that is it'll keep queuing more and more logs to be uploaded to MC and more
     * importantly, it'll set the `uuid` of the Sentry event to the log file at the time of the successful
     * Sentry request. Since we are interested in the logs for when the crash happened, this would not be
     * correct for us.
     *
     * We can simply fix this issue by checking if the [EXTRA_UUID] field is already set.
     */
    override fun provideExtrasForEvent(
        currentExtras: Map<ExtraKnownKey, String>,
        eventLevel: EventLevel
    ): Map<ExtraKnownKey, String> {
        return currentExtras + if (currentExtras[EXTRA_UUID] == null) {
            appendEncryptedLogsUuid(eventLevel)
        } else {
            emptyMap()
        }
    }

    private fun appendEncryptedLogsUuid(eventLevel: EventLevel): Map<ExtraKnownKey, String> {
        val encryptedLogsUuid = mutableMapOf<ExtraKnownKey, String>()
        logFileProvider.getLogFiles().lastOrNull()?.let { logFile ->
            if (logFile.exists()) {
                encryptedLogging.encryptAndUploadLogFile(
                    logFile = logFile,
                    shouldStartUploadImmediately = eventLevel != EventLevel.FATAL
                )?.let { uuid ->
                    encryptedLogsUuid.put(EXTRA_UUID, uuid)
                }
            }
        }
        return encryptedLogsUuid
    }

    override fun shouldDropWrappingException(module: String, type: String, value: String): Boolean {
        return module == EVENT_BUS_MODULE &&
            type == EVENT_BUS_EXCEPTION &&
            value == EVENT_BUS_INVOKING_SUBSCRIBER_FAILED_ERROR
    }

    override val user = MutableStateFlow(accountStore.account?.toCrashLoggingUser())

    override val performanceMonitoringConfig = pcPerformanceMonitoringConfig()

    @Suppress("unused", "unused_parameter")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onAccountChanged(event: OnAccountChanged) {
        coroutineScope.launch {
            user.emit(accountStore.account.toCrashLoggingUser())
        }
    }

    private fun AccountModel.toCrashLoggingUser(): CrashLoggingUser? {
        if (userId == 0L) return null

        return CrashLoggingUser(
            userID = userId.toString(),
            email = email,
            username = userName
        )
    }

    companion object {
        const val EXTRA_UUID = "uuid"
        const val EVENT_BUS_MODULE = "org.greenrobot.eventbus"
        const val EVENT_BUS_EXCEPTION = "EventBusException"
        const val EVENT_BUS_INVOKING_SUBSCRIBER_FAILED_ERROR = "Invoking subscriber failed"
    }
}
