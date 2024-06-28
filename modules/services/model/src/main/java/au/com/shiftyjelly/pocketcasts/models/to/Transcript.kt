package au.com.shiftyjelly.pocketcasts.models.to

data class Transcript(
    val url: String,
    val type: String? = null,
    val language: String? = null,
)
