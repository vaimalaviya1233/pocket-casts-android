package au.com.shiftyjelly.pocketcasts.servers.zendesk

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

private const val AUTHORIZATION_HEADER = "Authorization"
private const val CONTENT_TYPE_HEADER = "Content-Type"
private const val CONTENT_TYPE_JSON = "application/json"

interface ZendeskServer {

    @Headers("$CONTENT_TYPE_HEADER: $CONTENT_TYPE_JSON")
    @POST("/api/v2/requests.json")
    suspend fun createRequest(
        @Header(AUTHORIZATION_HEADER) authToken: String,
        @Body request: ZDSupportRequestWrapper,
    ): ZDSupportResponseWrapper
}
