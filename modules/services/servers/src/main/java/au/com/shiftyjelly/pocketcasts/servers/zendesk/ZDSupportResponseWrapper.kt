package au.com.shiftyjelly.pocketcasts.servers.zendesk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ZDSupportResponseWrapper(
    @field:Json(name = "request") val response: ZDSupportResponse,
) {
    @JsonClass(generateAdapter = true)
    data class ZDSupportResponse(
        @field:Json(name = "id") val id: String,
    )
}
