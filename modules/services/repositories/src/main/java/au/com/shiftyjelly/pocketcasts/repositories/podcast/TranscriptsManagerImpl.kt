package au.com.shiftyjelly.pocketcasts.repositories.podcast

import au.com.shiftyjelly.pocketcasts.models.db.dao.TranscriptDao
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import javax.inject.Inject

class TranscriptsManagerImpl @Inject constructor(
    private val transcriptDao: TranscriptDao,
) : TranscriptsManager {
    private val supportedFormats = listOf(TranscriptFormat.SRT, TranscriptFormat.VTT)

    override suspend fun updateTranscripts(
        transcripts: List<Transcript>,
    ) {
        findBestTranscript(transcripts)?.let { bestTranscript ->
            transcriptDao.insert(bestTranscript)
        }
    }

    override fun observerTranscriptForEpisode(episodeUuid: String) =
        transcriptDao.observerTranscriptForEpisode(episodeUuid)

    private fun findBestTranscript(availableTranscripts: List<Transcript>): Transcript? {
        for (format in supportedFormats) {
            val transcript = availableTranscripts.firstOrNull { it.type == format.mimeType }
            if (transcript != null) {
                return transcript
            }
        }
        return availableTranscripts.firstOrNull()
    }
}

enum class TranscriptFormat(val mimeType: String) {
    SRT("application/srt"),
    VTT("text/vtt"),
}
