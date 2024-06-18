package au.com.shiftyjelly.pocketcasts.repositories.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.hilt.work.HiltWorker
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import androidx.work.Worker
import androidx.work.WorkerParameters
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.playback.ExoPlayerCacheUtil.CACHE_SIZE_IN_MB
import com.automattic.android.tracks.crashlogging.CrashLogging
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@OptIn(UnstableApi::class)
@HiltWorker
class CacheWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val crashLogging: CrashLogging,
    private val settings: Settings,
) : Worker(context, params) {
    private var cacheWriter: CacheWriter? = null

    override fun doWork(): Result {
        try {
            val url = inputData.getString(URL_KEY)
            val episodeUuid = inputData.getString(EPISODE_UUID_KEY)
            val uri = Uri.parse(url)

            val dataSourceFactory = ExoPlayerCacheUtil.getDataSourceFactory()
            var dataSpec = DataSpec(uri)
            if (episodeUuid != null) {
                dataSpec = dataSpec.buildUpon().setKey(episodeUuid).build()
            }
            val simpleCache = ExoPlayerCacheUtil.getSimpleCache(context, CACHE_SIZE_IN_MB, crashLogging)
            val cacheDataSourceFactory = ExoPlayerCacheUtil.getCacheDataSourceFactory(
                dataSourceFactory,
                simpleCache,
            )
            cacheWriter = CacheWriter(
                cacheDataSourceFactory.createDataSource(),
                dataSpec,
                null,
            ) { requestLength: Long, bytesCached: Long, _: Long ->
                ExoPlayerCacheUtil.updateCachedKeysWithLengths(
                    episodeUuid!!,
                    requestLength,
                    simpleCache?.keys?.toList() ?: emptyList(),
                    settings,
                )
                Timber.d("Cache: requestLength " + requestLength.toMB() + " bytesCached " + bytesCached.toMB())
            }
            cacheWriter?.cache()
        } catch (exception: Exception) {
            Timber.e("Cache: ${exception.message}")
        }
        return Result.success()
    }

    override fun onStopped() {
        try {
            cacheWriter?.cancel()
            super.onStopped()
        } catch (exception: Exception) {
            Timber.e("Cache: ${exception.message}")
        }
    }

    private fun Long.toMB() = this / (1024 * 1024)

    companion object {
        const val URL_KEY = "url_key"
        const val EPISODE_UUID_KEY = "episode_uuid_key"
    }
}
