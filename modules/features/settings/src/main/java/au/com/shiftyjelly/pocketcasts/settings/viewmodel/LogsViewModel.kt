package au.com.shiftyjelly.pocketcasts.settings.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.encryptedlogging.LogEncrypter
import au.com.shiftyjelly.pocketcasts.encryptedlogging.di.EncryptedLoggingModule
import au.com.shiftyjelly.pocketcasts.models.to.SubscriptionStatus
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.encryptedlogging.EncryptedLoggingManager
import au.com.shiftyjelly.pocketcasts.repositories.subscription.SubscriptionManager
import au.com.shiftyjelly.pocketcasts.repositories.support.Support
import au.com.shiftyjelly.pocketcasts.repositories.sync.SyncManager
import au.com.shiftyjelly.pocketcasts.servers.zendesk.ZDSupportRequest
import au.com.shiftyjelly.pocketcasts.servers.zendesk.ZDSupportRequestWrapper
import au.com.shiftyjelly.pocketcasts.servers.zendesk.ZendeskServerManager
import au.com.shiftyjelly.pocketcasts.utils.AppPlatform
import au.com.shiftyjelly.pocketcasts.utils.Util
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val support: Support,
    private val logEncrypter: LogEncrypter,
    private val encryptedLoggingManager: EncryptedLoggingManager,
    private val appSecrets: EncryptedLoggingModule.AppSecrets,
    private val settings: Settings,
    private val zendeskServerManager: ZendeskServerManager,
    private val syncManager: SyncManager,
    private val subscriptionManager: SubscriptionManager,
) : ViewModel() {

    data class State(
        val logs: String?,
    )

    private val _state = MutableStateFlow(State(null))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val logs = buildString {
                append(support.getUserDebug(false))
                val outputStream = ByteArrayOutputStream()
                LogBuffer.output(outputStream)
                append(outputStream.toString())
            }
            _state.update { it.copy(logs = logs) }
        }
    }

    fun shareLogs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            if (Util.getAppPlatform(context) == AppPlatform.Phone) {
                val intent = support.shareLogs(
                    subject = context.getString(LR.string.settings_logs),
                    intro = "",
                    emailSupport = false,
                    context = context
                )
                context.startActivity(intent)
            } else {
                try {
                    val file = support.saveDebugLogs()
                    encryptAndUploadLogFile(file)
                } catch (e: Exception) {
                    Timber.e(e.message)
                }
            }
        }
    }

    private suspend fun encryptAndUploadLogFile(file: File) {
        if (!isValidFile(file)) {
            Timber.e("File not valid")
            return
        }
        try {
            val uuid = settings.getUniqueDeviceId()
            val encryptedText = logEncrypter.encrypt(text = file.readText(), uuid = uuid)
            encryptedLoggingManager.uploadEncryptedLogs(uuid, appSecrets.appSecret, encryptedText.toByteArray())

            if (syncManager.isLoggedIn()) {
                val isPlus = subscriptionManager.getCachedStatus() is SubscriptionStatus.Plus
                zendeskServerManager.createRequest(
                    ZDSupportRequestWrapper(
                        request = ZDSupportRequest(
                            requester = ZDSupportRequest.ZDRequester(
                                email = requireNotNull(syncManager.getEmail())
                            ),
                            subject = "Android v${settings.getVersion()} ${if (isPlus) " - Plus Account" else ""}",
                            comment = ZDSupportRequest.ZDComment(
                                body = "Ignore it"
                            ),
                            customFields = listOf(
                                ZDSupportRequest.ZDCustomField(
                                    id = SupportCustomField.DebugLog.value,
                                    value = uuid
                                )
                            ),
                            tags = listOf(
                                "platform_automotive", "app_version_${settings.getVersion()}", "pocket_casts"
                            )
                        )
                    )
                )
            }
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e.message)
        }
    }

    private fun isValidFile(file: File): Boolean = file.exists() && file.canRead()

    enum class SupportCustomField(val value: Long) {
        DebugLog(360049192052L)
    }
}
