package au.com.shiftyjelly.pocketcasts.servers.wpcom

import au.com.shiftyjelly.pocketcasts.servers.bumpstats.AnonymousBumpStatsRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

private const val AUTHORIZATION_HEADER = "Authorization"
private const val CONTENT_TYPE_HEADER = "Content-Type"
private const val CONTENT_TYPE_JSON = "application/json"
private const val UUID_HEADER = "log-uuid"

interface WpComServer {

    @POST("/rest/v1.1/tracks/record")
    suspend fun bumpStatAnonymously(@Body request: AnonymousBumpStatsRequest): Response<String>

    @Headers("$CONTENT_TYPE_HEADER: $CONTENT_TYPE_JSON")
    @POST("/rest/v1.1/encrypted-logging")
    suspend fun uploadEncryptedLogs(
        @Header(UUID_HEADER) logUuid: String,
        @Header(AUTHORIZATION_HEADER) clientSecret: String,
        @Body contents: ByteArray,
    ): Response<String>
}
