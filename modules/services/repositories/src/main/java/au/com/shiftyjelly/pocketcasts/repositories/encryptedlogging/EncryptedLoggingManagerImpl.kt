package au.com.shiftyjelly.pocketcasts.repositories.encryptedlogging

import au.com.shiftyjelly.pocketcasts.servers.wpcom.WpComServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class EncryptedLoggingManagerImpl @Inject constructor(
    private val wpComServerManager: WpComServerManager,
) : EncryptedLoggingManager, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    override suspend fun uploadEncryptedLogs(
        logUuid: String,
        clientSecret: String,
        contents: ByteArray,
    ) {
        wpComServerManager.uploadEncryptedLogs(logUuid, clientSecret, contents)
    }
}
