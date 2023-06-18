package au.com.shiftyjelly.pocketcasts.servers.zendesk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ZDSupportRequestWrapper(
    @field:Json(name = "request") val request: ZDSupportRequest
)

@JsonClass(generateAdapter = true)
data class ZDSupportRequest(
    @field:Json(name = "requester") val requester: ZDRequester,
    @field:Json(name = "subject") val subject: String,
    @field:Json(name = "comment") val comment: ZDComment,
    @field:Json(name = "custom_fields") val customFields: List<ZDCustomField>,
    @field:Json(name = "tags") val tags: List<String>
) {
    @JsonClass(generateAdapter = true)
    data class ZDRequester(
        @field:Json(name = "email") val email: String
    )

    @JsonClass(generateAdapter = true)
    data class ZDComment(
        @field:Json(name = "body") val body: String
    )

    @JsonClass(generateAdapter = true)
    data class ZDCustomField(
        @field:Json(name = "id") val id: Long,
        @field:Json(name = "value") val value: String
    )
}
