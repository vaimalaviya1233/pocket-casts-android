package au.com.shiftyjelly.pocketcasts.repositories.encryptedlogging

interface EncryptedLoggingManager {
    suspend fun uploadEncryptedLogs(
        logUuid: String,
        clientSecret: String,
        contents: ByteArray,
    )
}
