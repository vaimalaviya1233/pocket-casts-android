package au.com.shiftyjelly.pocketcasts.servers.zendesk

import au.com.shiftyjelly.pocketcasts.preferences.BuildConfig
import au.com.shiftyjelly.pocketcasts.servers.di.ZendeskServerRetrofit
import okio.ByteString.Companion.encode
import retrofit2.Retrofit
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class ZendeskServerManager @Inject constructor(
    @ZendeskServerRetrofit retrofit: Retrofit
) {
    private val server: ZendeskServer = retrofit.create(ZendeskServer::class.java)

    suspend fun createRequest(requestWrapper: ZDSupportRequestWrapper): ZDSupportResponseWrapper =
        server.createRequest(authToken = "Basic ${authToken(requestWrapper.request.requester.email)}", requestWrapper)

    fun authToken(email: String): String =
        "$email/token:${BuildConfig.ZENDESK_API_KEY}".encode(StandardCharsets.UTF_8).base64Url()
}
