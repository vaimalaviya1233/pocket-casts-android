package au.com.shiftyjelly.pocketcasts.servers.wpcom

import au.com.shiftyjelly.pocketcasts.models.entity.AnonymousBumpStat
import au.com.shiftyjelly.pocketcasts.servers.bumpstats.AnonymousBumpStatsRequest
import au.com.shiftyjelly.pocketcasts.servers.di.WpComServerRetrofit
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import javax.inject.Inject

class WpComServerManager @Inject constructor(
    @WpComServerRetrofit retrofit: Retrofit
) {
    private val server: WpComServer = retrofit.create(WpComServer::class.java)

    suspend fun bumpStatAnonymously(bumpStats: List<AnonymousBumpStat>): Response<String> {
        val request = AnonymousBumpStatsRequest(bumpStats)
        return server.bumpStatAnonymously(request)
    }

    suspend fun uploadEncryptedLogs(
        logUuid: String,
        clientSecret: String,
        contents: ByteArray,
    ): Response<String> = server.uploadEncryptedLogs(logUuid, clientSecret, contents.toRequestBody())
}
