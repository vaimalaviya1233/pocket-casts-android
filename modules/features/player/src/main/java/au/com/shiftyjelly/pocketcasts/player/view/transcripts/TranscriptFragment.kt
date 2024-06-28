package au.com.shiftyjelly.pocketcasts.player.view.transcripts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.media3.common.util.UnstableApi
import au.com.shiftyjelly.pocketcasts.compose.AppTheme
import au.com.shiftyjelly.pocketcasts.compose.components.TextH30
import au.com.shiftyjelly.pocketcasts.compose.components.TextP40
import au.com.shiftyjelly.pocketcasts.player.view.transcripts.TranscriptViewModel.TranscriptError
import au.com.shiftyjelly.pocketcasts.player.view.transcripts.TranscriptViewModel.UiState
import au.com.shiftyjelly.pocketcasts.ui.theme.Theme
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import au.com.shiftyjelly.pocketcasts.localization.R as LR

@AndroidEntryPoint
class TranscriptFragment : BaseFragment() {
    companion object {
        fun newInstance() = TranscriptFragment()
    }

    private val viewModel by viewModels<TranscriptViewModel>({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setContent {
            val state = viewModel.uiState.collectAsState()
            AppTheme(Theme.ThemeType.DARK) {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                TranscriptTheme(
                    theme = theme,
                    podcast = state.value.podcast,
                ) {
                    Surface(modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())) {
                        when (state.value) {
                            is UiState.Empty -> Unit
                            is UiState.Success -> TranscriptContent(
                                state.value as UiState.Success,
                            )

                            is UiState.Error -> TranscriptError(state.value as UiState.Error)
                        }
                    }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    @Composable
    private fun TranscriptContent(
        state: UiState.Success,
        lazyListState: LazyListState = rememberLazyListState(),
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .background(LocalTranscriptTheme.current.background)
                .fillMaxSize(),
        ) {
            items(state.cues) { cues ->
                TextP40(
                    text = cues.startTimeUs.microseconds.format(),
                    color = LocalTranscriptTheme.current.text,
                )
                cues.cues.forEach {
                    TextP40(
                        text = it.text.toString(),
                        color = LocalTranscriptTheme.current.text,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    @Composable
    fun TranscriptError(
        state: UiState.Error,
    ) {
        val errorMessage = when (val error = state.error) {
            is TranscriptError.NotSupported ->
                stringResource(LR.string.error_transcript_format_not_supported, error.format)

            is TranscriptError.FailedToLoad ->
                stringResource(LR.string.error_transcript_failed_to_load)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalTranscriptTheme.current.background)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .background(
                        color = LocalTranscriptTheme.current.divider,
                        shape = RoundedCornerShape(size = 4.dp),
                    ),
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(16.dp),
                ) {
                    TextH30(
                        text = stringResource(LR.string.error),
                        color = LocalTranscriptTheme.current.title,
                    )
                    TextP40(
                        text = errorMessage,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                        color = LocalTranscriptTheme.current.text,
                    )
                }
            }
        }
    }
}

private fun Duration.format() = toComponents { hours, minutes, seconds, _ ->
    String.format(
        Locale.getDefault(),
        "%02d:%02d:%02d",
        hours,
        minutes,
        seconds,
    )
}
