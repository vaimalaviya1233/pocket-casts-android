package au.com.shiftyjelly.pocketcasts.servers.wpcom

import au.com.shiftyjelly.pocketcasts.servers.bumpstats.AnonymousBumpStatsRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface WpComServer {

    @POST("/rest/v1.1/tracks/record")
    suspend fun bumpStatAnonymously(@Body request: AnonymousBumpStatsRequest): Response<String>
}
