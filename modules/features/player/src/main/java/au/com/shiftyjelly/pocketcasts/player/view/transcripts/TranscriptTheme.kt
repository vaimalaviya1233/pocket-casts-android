package au.com.shiftyjelly.pocketcasts.player.view.transcripts

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import au.com.shiftyjelly.pocketcasts.compose.theme
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme

val LocalTranscriptTheme = staticCompositionLocalOf<TranscriptTheme> { error("No default chapters theme") }

@Composable
fun TranscriptTheme(
    theme: Theme,
    podcast: Podcast?,
    content: @Composable () -> Unit,
) {
    val transcriptTheme = TranscriptTheme(
        background = Color(theme.playerBackgroundColor(podcast)),
        divider = MaterialTheme.theme.colors.playerContrast06,
        text = MaterialTheme.theme.colors.playerContrast02,
        title = MaterialTheme.theme.colors.playerContrast01,

    )
    TranscriptTheme(transcriptTheme, content)
}

@Composable
private fun TranscriptTheme(
    transcriptTheme: TranscriptTheme,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTranscriptTheme provides transcriptTheme) {
        content()
    }
}

data class TranscriptTheme(
    val background: Color,
    val divider: Color,
    val text: Color,
    val title: Color,
)
