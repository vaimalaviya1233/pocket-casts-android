package au.com.shiftyjelly.pocketcasts.encryptedlogging.utils

import android.content.Context
import com.automattic.android.tracks.NetworkUtils
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@Reusable
class NetworkUtilsWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns true if a network connection is available.
     */
    fun isNetworkAvailable() = NetworkUtils.isNetworkAvailable(context)
}
