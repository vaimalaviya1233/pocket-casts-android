package au.com.shiftyjelly.pocketcasts.settings.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.shiftyjelly.pocketcasts.encryptedlogging.LogEncrypter
import au.com.shiftyjelly.pocketcasts.encryptedlogging.di.EncryptedLoggingModule
import au.com.shiftyjelly.pocketcasts.repositories.encryptedlogging.EncryptedLoggingManager
import au.com.shiftyjelly.pocketcasts.repositories.support.Support
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
import java.util.UUID
import javax.inject.Inject
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val support: Support,
    private val logEncrypter: LogEncrypter,
    private val encryptedLoggingManager: EncryptedLoggingManager,
    private val appSecrets: EncryptedLoggingModule.AppSecrets,
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
            val intent = support.shareLogs(
                subject = context.getString(LR.string.settings_logs),
                intro = "",
                emailSupport = false,
                context = context
            )
            context.startActivity(intent)
        }
    }

    fun encryptAndUploadLogFile(
        file: File,
    ) {
        if (!isValidFile(file)) {
            Timber.e("File not valid")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uuid = UUID.randomUUID().toString()
                val encryptedText = logEncrypter.encrypt(text = file.readText(), uuid = uuid)
                val result = encryptedLoggingManager.uploadEncryptedLogs(uuid, appSecrets.appSecret, encryptedText.toByteArray())
                Timber.d(result.toString())
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e.message)
            }
        }
    }

    private fun isValidFile(file: File): Boolean = file.exists() && file.canRead()
}
