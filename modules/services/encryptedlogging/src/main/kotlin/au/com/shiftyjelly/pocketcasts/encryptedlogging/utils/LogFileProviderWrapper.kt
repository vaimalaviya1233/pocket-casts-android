package au.com.shiftyjelly.pocketcasts.encryptedlogging.utils

import android.content.Context
import javax.inject.Inject

class LogFileProviderWrapper @Inject constructor(context: Context) {
    private val logFileProvider = LogFileProvider.fromContext(context)

    fun getLogFiles() = logFileProvider.getLogFiles()
}
