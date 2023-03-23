package au.com.shiftyjelly.pocketcasts.repositories.playback

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import au.com.shiftyjelly.pocketcasts.analytics.FirebaseAnalyticsTracker
import au.com.shiftyjelly.pocketcasts.models.db.helper.UserEpisodePodcastSubstitute
import au.com.shiftyjelly.pocketcasts.models.entity.Episode
import au.com.shiftyjelly.pocketcasts.models.entity.Podcast
import au.com.shiftyjelly.pocketcasts.models.to.FolderItem
import au.com.shiftyjelly.pocketcasts.models.to.SubscriptionStatus
import au.com.shiftyjelly.pocketcasts.models.type.PodcastsSortType
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.extensions.id
import au.com.shiftyjelly.pocketcasts.repositories.notification.NotificationDrawer
import au.com.shiftyjelly.pocketcasts.repositories.notification.NotificationHelper
import au.com.shiftyjelly.pocketcasts.repositories.playback.auto.AutoConverter
import au.com.shiftyjelly.pocketcasts.repositories.playback.auto.AutoConverter.convertFolderToMediaItem
import au.com.shiftyjelly.pocketcasts.repositories.playback.auto.AutoConverter.convertPodcastToMediaItem
import au.com.shiftyjelly.pocketcasts.repositories.podcast.EpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.FolderManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PlaylistManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.PodcastManager
import au.com.shiftyjelly.pocketcasts.repositories.podcast.UserEpisodeManager
import au.com.shiftyjelly.pocketcasts.repositories.subscription.SubscriptionManager
import au.com.shiftyjelly.pocketcasts.repositories.user.StatsManager
import au.com.shiftyjelly.pocketcasts.servers.ServerManager
import au.com.shiftyjelly.pocketcasts.utils.IS_RUNNING_UNDER_TEST
import au.com.shiftyjelly.pocketcasts.utils.SchedulerProvider
import au.com.shiftyjelly.pocketcasts.utils.SentryHelper
import au.com.shiftyjelly.pocketcasts.utils.Util
import au.com.shiftyjelly.pocketcasts.utils.extensions.getLaunchActivityPendingIntent
import au.com.shiftyjelly.pocketcasts.utils.log.LogBuffer
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jakewharton.rxrelay2.BehaviorRelay
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.awaitSingleOrNull
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

const val MEDIA_ID_ROOT = "__ROOT__"
const val PODCASTS_ROOT = "__PODCASTS__"
private const val DOWNLOADS_ROOT = "__DOWNLOADS__"
private const val FILES_ROOT = "__FILES__"
const val RECENT_ROOT = "__RECENT__"
const val SUGGESTED_ROOT = "__SUGGESTED__"
const val FOLDER_ROOT_PREFIX = "__FOLDER__"

private const val MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"

const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
const val EXTRA_CONTENT_STYLE_GROUP_TITLE_HINT = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"

/**
 * Value for {​@link ​#CONTENT_STYLE_PLAYABLE_HINT} and {​@link #CONTENT_STYLE_BROWSABLE_HINT} that
 * hints the corresponding items should be presented as lists.  */
const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1

/**
 * Value for {​@link ​#CONTENT_STYLE_PLAYABLE_HINT} and {​@link #CONTENT_STYLE_BROWSABLE_HINT} that
 * hints the corresponding items should be presented as grids.  */
const val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2

private const val EPISODE_LIMIT = 100

@UnstableApi
@AndroidEntryPoint
open class PlaybackService : MediaLibraryService(), CoroutineScope {
    inner class LocalBinder : Binder() {
        val service: PlaybackService
            get() = this@PlaybackService
    }

    companion object {
        private val BUFFER_TIME_MIN_MILLIS = TimeUnit.MINUTES.toMillis(15).toInt()
        private val BUFFER_TIME_MAX_MILLIS = BUFFER_TIME_MIN_MILLIS

        // Be careful increasing the size of the back buffer. It can easily lead to OOM errors.
        private val BACK_BUFFER_TIME_MILLIS = TimeUnit.MINUTES.toMillis(2).toInt()
    }

    @Inject lateinit var podcastManager: PodcastManager
    @Inject lateinit var episodeManager: EpisodeManager
    @Inject lateinit var folderManager: FolderManager
    @Inject lateinit var userEpisodeManager: UserEpisodeManager
    @Inject lateinit var playlistManager: PlaylistManager
    @Inject lateinit var playbackManager: PlaybackManager
    @Inject lateinit var notificationDrawer: NotificationDrawer
    @Inject lateinit var upNextQueue: UpNextQueue
    @Inject lateinit var settings: Settings
    @Inject lateinit var serverManager: ServerManager
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var subscriptionManager: SubscriptionManager
    @Inject lateinit var statsManager: StatsManager

    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var player: ExoPlayer
    private val librarySessionCallback = CustomMediaLibrarySessionCallback()

    private var mediaControllerCallback: MediaControllerCallback? = null
    lateinit var notificationManager: PlayerNotificationManager

    private val disposables = CompositeDisposable()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    override fun onBind(intent: Intent?): IBinder? {
        val binder = super.onBind(intent)
        return binder ?: LocalBinder() // We return our local binder for tests and use the media session service binder normally
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onCreate() {
        super.onCreate()

        LogBuffer.i(LogBuffer.TAG_PLAYBACK, "Playback service created")

        initializeSessionAndPlayer()

        notificationManager = PlayerNotificationManagerImpl(this)
    }

    private fun initializeSessionAndPlayer() {
        // TODO Use the CastPlayer when casting
        player = createExoPlayer()

        val mediaSessionBuilder = MediaLibrarySession.Builder(this, player, librarySessionCallback)
        if (!Util.isAutomotive(this)) { // We can't start activities on automotive
            mediaSessionBuilder.setSessionActivity(this.getLaunchActivityPendingIntent())
        }

        mediaLibrarySession = mediaSessionBuilder.build()
    }

    private fun createExoPlayer(): ExoPlayer {

        val renderersFactory = createRenderersFactory()

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            // TODO Confirm if we need these
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .setTrackSelector(DefaultTrackSelector(baseContext))
            .setLoadControl(createExoPlayerLoadControl())
            .setSeekForwardIncrementMs(settings.getSkipForwardInMs())
            .setSeekBackIncrementMs(settings.getSkipBackwardInMs())
            .build()
        renderersFactory.onAudioSessionId(exoPlayer.audioSessionId)

        // FIXME

        return exoPlayer
    }

    private fun createExoPlayerLoadControl(): DefaultLoadControl {
        // FIXME Need isStreaming
//            val minBufferMillis = if (isStreaming) BUFFER_TIME_MIN_MILLIS else DefaultLoadControl.DEFAULT_MIN_BUFFER_MS
//            val maxBufferMillis = if (isStreaming) BUFFER_TIME_MAX_MILLIS else DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
//            val backBufferMillis = if (isStreaming) BACK_BUFFER_TIME_MILLIS else DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS

        return DefaultLoadControl.Builder()
//                .setBufferDurationsMs(
//                    minBufferMillis,
//                    maxBufferMillis,
//                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
//                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
//                )
//                .setBackBuffer(
//                    backBufferMillis,
//                    DefaultLoadControl.DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME
//                )
            .build()
    }

    private fun createRenderersFactory(): ShiftyRenderersFactory {
        // FIXME get playback effects
//        val playbackEffects: PlaybackEffects? = this.playbackEffects
//        return if (playbackEffects == null) {
//            ShiftyRenderersFactory(context = baseContext, statsManager = statsManager, boostVolume = false)
//        } else {
//            ShiftyRenderersFactory(context = baseContext, statsManager = statsManager, boostVolume = playbackEffects.isVolumeBoosted)
//        }
        return ShiftyRenderersFactory(context = baseContext, statsManager = statsManager, boostVolume = false)
    }

    override fun onDestroy() {
        super.onDestroy()

        disposables.clear()

        LogBuffer.i(LogBuffer.TAG_PLAYBACK, "Playback service destroyed")
    }

    @Suppress("DEPRECATION")
    fun isForegroundService(): Boolean {
        val manager = baseContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (PlaybackService::class.java.name == service.service.className) {
                return service.foreground
            }
        }
        Timber.e("isServiceRunningInForeground found no matching service")
        return false
    }

    private inner class MediaControllerCallback(currentMetadataCompat: MediaMetadataCompat?) : MediaControllerCompat.Callback() {
        private val playbackStatusRelay = BehaviorRelay.create<PlaybackStateCompat>()
        private val mediaMetadataRelay = BehaviorRelay.create<MediaMetadataCompat>().apply {
            if (currentMetadataCompat != null) {
                accept(currentMetadataCompat)
            }
        }

        init {
            Observables.combineLatest(playbackStatusRelay, mediaMetadataRelay)
                .observeOn(SchedulerProvider.io)
                // only generate new notifications for a different playback state and episode. Also if we are playing but aren't a foreground service something isn't right
                .distinctUntilChanged { oldPair: Pair<PlaybackStateCompat, MediaMetadataCompat>, newPair: Pair<PlaybackStateCompat, MediaMetadataCompat> ->
                    val isForegroundService = isForegroundService()
                    (oldPair.first.state == newPair.first.state && oldPair.second.id == newPair.second.id) &&
                        (isForegroundService && (newPair.first.state == PlaybackStateCompat.STATE_PLAYING || newPair.first.state == PlaybackStateCompat.STATE_BUFFERING))
                }
                // build the notification including artwork in the background
                .map { (playbackState, metadata) -> playbackState to buildNotification(playbackState.state, metadata) }
                .observeOn(SchedulerProvider.mainThread)
                .subscribeBy(
                    onNext = { (state: PlaybackStateCompat, notification: Notification?) ->
                        onPlaybackStateChangedWithNotification(state, notification)
                    },
                    onError = { throwable ->
                        Timber.e(throwable)
                        LogBuffer.e(LogBuffer.TAG_PLAYBACK, throwable, "Playback service error")
                    }
                )
                .addTo(disposables)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            metadata ?: return
            mediaMetadataRelay.accept(metadata)
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>) {
            Timber.i("Queue changed ${queue.size}. $queue")
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            state ?: return
            playbackStatusRelay.accept(state)
        }

        /***
         // This is the most fragile and important code in the app, edit with care
         // Possible bugs to watch out for are:
         // - No notification shown during playback which means no foregrounds service, app could be killed or stutter
         // - Notification coming back after pausing
         // - Incorrect state shown in notification compared with player
         // - Notification not being able to be dismissed after pausing playback
         ***/
        private fun onPlaybackStateChangedWithNotification(playbackState: PlaybackStateCompat, notification: Notification?) {
            val isForegroundService = isForegroundService()
            val state = playbackState.state

            // If we have switched to casting we need to remove the notification
            if (isForegroundService && notification == null && playbackManager.isPlaybackRemote()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                LogBuffer.i(LogBuffer.TAG_PLAYBACK, "stopForeground as player is remote")
            }

            // If we are already showing a notification, update it no matter the state.
            if (notification != null && notificationHelper.isShowing(Settings.NotificationId.PLAYING.value)) {
                Timber.d("Updating playback notification")
                notificationManager.notify(Settings.NotificationId.PLAYING.value, notification)
                if (isForegroundService && (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_BUFFERING)) {
                    // Nothing else to do
                    return
                }
            }

            Timber.d("Playback Notification State Change $state")
            // Transition between foreground service running and not with a notification
            when (state) {
                PlaybackStateCompat.STATE_BUFFERING,
                PlaybackStateCompat.STATE_PLAYING,
                -> {
                    if (notification != null) {
                        try {
                            startForeground(Settings.NotificationId.PLAYING.value, notification)
                            notificationManager.enteredForeground(notification)
                            LogBuffer.i(LogBuffer.TAG_PLAYBACK, "startForeground state: $state")
                        } catch (e: Exception) {
                            LogBuffer.e(
                                LogBuffer.TAG_PLAYBACK,
                                "attempted startForeground for state: $state, but that threw an exception we caught: $e"
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                e is ForegroundServiceStartNotAllowedException
                            ) {
                                addBatteryWarnings()
                                SentryHelper.recordException(e)
                                FirebaseAnalyticsTracker.foregroundServiceStartNotAllowedException()
                            }
                        }
                    } else {
                        LogBuffer.i(
                            LogBuffer.TAG_PLAYBACK,
                            "can't startForeground as the notification is null"
                        )
                    }
                }
                PlaybackStateCompat.STATE_NONE,
                PlaybackStateCompat.STATE_STOPPED,
                PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.STATE_ERROR,
                -> {
                    val removeNotification =
                        state != PlaybackStateCompat.STATE_PAUSED || settings.hideNotificationOnPause()
                    // We have to be careful here to only call notify when moving from PLAY to PAUSE once
                    // or else the notification will come back after being swiped away
                    if (removeNotification || isForegroundService) {
                        val isTransientLoss =
                            playbackState.extras?.getBoolean(MediaSessionManager.EXTRA_TRANSIENT)
                                ?: false
                        if (isTransientLoss) {
                            // Don't kill the foreground service for transient pauses
                            return
                        }

                        if (notification != null && state == PlaybackStateCompat.STATE_PAUSED && isForegroundService) {
                            notificationManager.notify(
                                Settings.NotificationId.PLAYING.value,
                                notification
                            )
                            LogBuffer.i(
                                LogBuffer.TAG_PLAYBACK,
                                "stopForeground state: $state (update notification)"
                            )
                        } else {
                            LogBuffer.i(
                                LogBuffer.TAG_PLAYBACK,
                                "stopForeground state: $state removing notification: $removeNotification"
                            )
                        }

                        @Suppress("DEPRECATION")
                        stopForeground(removeNotification)
                    }

                    if (state == PlaybackStateCompat.STATE_ERROR) {
                        LogBuffer.e(
                            LogBuffer.TAG_PLAYBACK,
                            "Playback state error: ${
                            playbackStatusRelay.value?.errorCode
                                ?: -1
                            } ${
                            playbackStatusRelay.value?.errorMessage
                                ?: "Unknown error"
                            }"
                        )
                    }
                }
            }
        }

        private fun addBatteryWarnings() {
            val currentValue = settings.getTimesToShowBatteryWarning()
            settings.setTimesToShowBatteryWarning(2 + currentValue)
        }

        private fun buildNotification(state: Int, metadata: MediaMetadataCompat?): Notification? {
            if (Util.isAutomotive(this@PlaybackService)) {
                return null
            }

            if (playbackManager.isPlaybackRemote()) {
                return null
            }

//            val sessionToken = null // sessionToken
//            if (metadata == null || metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID).isEmpty()) return null
            return /*if (state != PlaybackStateCompat.STATE_NONE && sessionToken != null) notificationDrawer.buildPlayingNotification(sessionToken) else*/ null
        }
    }

    /****
     * testPlaybackStateChange
     * This method can be used for tests to pass in a playback state change to pass through.
     * Ideally we could mock mediacontroller and notificationcontroller but mocking final classes
     * is not supported on Android
     * @param metadata Metadata for playback
     * @param playbackStateCompat Playback state to pass through the service
     */
    fun testPlaybackStateChange(metadata: MediaMetadataCompat?, playbackStateCompat: PlaybackStateCompat) {
        assert(IS_RUNNING_UNDER_TEST) // This method should only be used for testing
        mediaControllerCallback?.onMetadataChanged(metadata)
        mediaControllerCallback?.onPlaybackStateChanged(playbackStateCompat)
    }

//    override fun onGetRoot(clientPackageName: String, clientUid: Int, bundle: Bundle?): BrowserRoot? {
//        val extras = Bundle()
//
//        Timber.d("onGetRoot() $clientPackageName ${bundle?.keySet()?.toList()}")
//        // tell Android Auto we support media search
//        extras.putBoolean(MEDIA_SEARCH_SUPPORTED, true)
//
//        // tell Android Auto we support grids and lists and that browsable things should be grids, the rest lists
//        extras.putBoolean(CONTENT_STYLE_SUPPORTED, true)
//        extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM_HINT_VALUE)
//        extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
//
//        // To ensure you are not allowing any arbitrary app to browse your app's contents, check the origin
//        if (!PackageValidator(this, LR.xml.allowed_media_browser_callers).isKnownCaller(clientPackageName, clientUid) && !BuildConfig.DEBUG) {
//            // If the request comes from an untrusted package, return null
//            Timber.e("Unknown caller trying to connect to media service $clientPackageName $clientUid")
//            return null
//        }
//
//        if (!clientPackageName.contains("au.com.shiftyjelly.pocketcasts")) {
//            LogBuffer.i(LogBuffer.TAG_PLAYBACK, "Client: $clientPackageName connected to media session") // Log things like Android Auto or Assistant connecting
//        }
//
//        return if (browserRootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) == true) { // Browser root hints is nullable even though it's not declared as such, come on Google
//            Timber.d("Browser root hint for recent items")
//            if (playbackManager.getCurrentEpisode() != null) {
//                BrowserRoot(RECENT_ROOT, extras)
//            } else {
//                null
//            }
//        } else if (browserRootHints?.getBoolean(BrowserRoot.EXTRA_SUGGESTED) == true) {
//            Timber.d("Browser root hint for suggested items")
//            BrowserRoot(SUGGESTED_ROOT, extras)
//        } else {
//            BrowserRoot(MEDIA_ID_ROOT, extras)
//        }
//    }

    private val NUM_SUGGESTED_ITEMS = 8
    private suspend fun loadSuggestedChildren(): ArrayList<MediaItem> {
        Timber.d("Loading sugggested children")
        val upNext = listOfNotNull(playbackManager.getCurrentEpisode()) + playbackManager.upNextQueue.queueEpisodes
        val mediaUpNext = upNext.take(NUM_SUGGESTED_ITEMS).mapNotNull { playable ->
            val filesPodcast = Podcast(uuid = UserEpisodePodcastSubstitute.substituteUuid, title = UserEpisodePodcastSubstitute.substituteTitle)
            val parentPodcast = (if (playable is Episode) podcastManager.findPodcastByUuid(playable.podcastUuid) else filesPodcast) ?: return@mapNotNull null
            AutoConverter.convertEpisodeToMediaItem(this, playable, parentPodcast)
        }

        if (mediaUpNext.size == NUM_SUGGESTED_ITEMS) {
            return ArrayList(mediaUpNext)
        }

        Timber.d("Up next length was ${mediaUpNext.size}. Trying top filter.")
        // If we don't have enough items in up next, try the top filter
        val topPlaylist = playlistManager.findAll().firstOrNull()
        if (topPlaylist == null) {
            Timber.d("Could not find top filter.")
            return ArrayList(mediaUpNext)
        }

        Timber.d("Loading suggestions from ${topPlaylist.title}")
        val playlistItems = loadEpisodeChildren(topPlaylist.uuid).take(NUM_SUGGESTED_ITEMS - mediaUpNext.size)
        Timber.d("Got ${playlistItems.size} from playlist.")

        val retList = mediaUpNext + playlistItems
        Timber.d("Returning ${retList.size} suggestions. $retList")
        return ArrayList(retList)
    }

    private fun loadRecentChildren(): ArrayList<MediaItem> {
        Timber.d("Loading recent children")
        val upNext = playbackManager.getCurrentEpisode() ?: return arrayListOf()
        val filesPodcast = Podcast(uuid = UserEpisodePodcastSubstitute.substituteUuid, title = UserEpisodePodcastSubstitute.substituteTitle)
        val parentPodcast = (if (upNext is Episode) podcastManager.findPodcastByUuid(upNext.podcastUuid) else filesPodcast) ?: return arrayListOf()

        Timber.d("Recent item ${upNext.title}")
        return arrayListOf(AutoConverter.convertEpisodeToMediaItem(this, upNext, parentPodcast))
    }

    open suspend fun loadRootChildren(): List<MediaItem> {
        val rootItems = ArrayList<MediaItem>()

        // podcasts
        val podcastsDescriptionMetadata = MediaMetadata.Builder()
            .setTitle("Podcasts")
            .setArtworkUri(AutoConverter.getPodcastsBitmapUri(this))
            .setIsBrowsable(true)
            .build()

        val podcastItem = MediaItem.Builder()
            .setMediaId(PODCASTS_ROOT)
            .setMediaMetadata(podcastsDescriptionMetadata)
            .build()

        rootItems.add(podcastItem)

        // playlists
        for (playlist in playlistManager.findAll().filterNot { it.manual }) {
            if (playlist.title.equals("video", ignoreCase = true)) continue

            val playlistItem = AutoConverter.convertPlaylistToMediaItem(this, playlist)
            rootItems.add(playlistItem)
        }

        // downloads
        val downloadsMetadata = MediaMetadata.Builder()
            .setTitle("Downloads")
            .setIsBrowsable(true)
            .setArtworkUri(AutoConverter.getDownloadsBitmapUri(this))
            .build()

        val downloadsItem = MediaItem.Builder()
            .setMediaId(DOWNLOADS_ROOT)
            .setMediaMetadata(downloadsMetadata)
            .build()
        rootItems.add(downloadsItem)

        // files
        val filesMetadata = MediaMetadata.Builder()
            .setTitle("Files")
            .setArtworkUri(AutoConverter.getFilesBitmapUri(this))
            .setIsBrowsable(true)
            .build()

        val filesItem = MediaItem.Builder()
            .setMediaId(FILES_ROOT)
            .setMediaMetadata(filesMetadata)
            .build()
        rootItems.add(filesItem)

        return rootItems
    }

    suspend fun loadPodcastsChildren(): List<MediaItem> {
        return if (subscriptionManager.getCachedStatus() is SubscriptionStatus.Plus) {
            folderManager.getHomeFolder().mapNotNull { item ->
                when (item) {
                    is FolderItem.Folder -> convertFolderToMediaItem(this, item.folder)
                    is FolderItem.Podcast -> convertPodcastToMediaItem(podcast = item.podcast, context = this)
                }
            }
        } else {
            podcastManager.findSubscribedSorted().mapNotNull { podcast ->
                convertPodcastToMediaItem(podcast = podcast, context = this)
            }
        }
    }

    suspend fun loadFolderPodcastsChildren(folderUuid: String): List<MediaItem> {
        return if (subscriptionManager.getCachedStatus() is SubscriptionStatus.Plus) {
            folderManager.findFolderPodcastsSorted(folderUuid).mapNotNull { podcast ->
                convertPodcastToMediaItem(podcast = podcast, context = this)
            }
        } else {
            emptyList()
        }
    }

    suspend fun loadEpisodeChildren(parentId: String): List<MediaItem> {
        // user tapped on a playlist or podcast, show the episodes
        val episodeItems = ArrayList<MediaItem>()

        val playlist = if (DOWNLOADS_ROOT == parentId) playlistManager.getSystemDownloadsFilter() else playlistManager.findByUuid(parentId)
        if (playlist != null) {
            val episodeList = if (DOWNLOADS_ROOT == parentId) episodeManager.observeDownloadedEpisodes().blockingFirst() else playlistManager.findEpisodes(playlist, episodeManager, playbackManager)
            val topEpisodes = episodeList.take(EPISODE_LIMIT)
            if (topEpisodes.isNotEmpty()) {
                for (episode in topEpisodes) {
                    podcastManager.findPodcastByUuid(episode.podcastUuid)?.let { parentPodcast ->
                        episodeItems.add(AutoConverter.convertEpisodeToMediaItem(this, episode, parentPodcast, sourceId = playlist.uuid))
                    }
                }
            }
        } else {
            val podcastFound = podcastManager.findPodcastByUuidSuspend(parentId) ?: podcastManager.findOrDownloadPodcastRx(parentId).toMaybe().onErrorComplete().awaitSingleOrNull()
            podcastFound?.let { podcast ->

                val showPlayed = settings.getAutoShowPlayed()
                val episodes = episodeManager
                    .findEpisodesByPodcastOrdered(podcast)
                    .filterNot { !showPlayed && (it.isFinished || it.isArchived) }
                    .take(EPISODE_LIMIT)
                    .toMutableList()
                if (!podcast.isSubscribed) {
                    episodes.sortBy { it.episodeType !is Episode.EpisodeType.Trailer } // Bring trailers to the top
                }
                episodes.forEach { episode ->
                    episodeItems.add(AutoConverter.convertEpisodeToMediaItem(this, episode, podcast, groupTrailers = !podcast.isSubscribed))
                }
            }
        }

        return episodeItems
    }

    protected suspend fun loadFilesChildren(): List<MediaItem> {
        return userEpisodeManager.findUserEpisodes().map {
            val podcast = Podcast(uuid = UserEpisodePodcastSubstitute.substituteUuid, title = UserEpisodePodcastSubstitute.substituteTitle, thumbnailUrl = it.artworkUrl)
            AutoConverter.convertEpisodeToMediaItem(this, it, podcast)
        }
    }

    protected suspend fun loadStarredChildren(): List<MediaItem> {
        return episodeManager.findStarredEpisodes().take(EPISODE_LIMIT).mapNotNull { episode ->
            podcastManager.findPodcastByUuid(episode.podcastUuid)?.let { podcast ->
                AutoConverter.convertEpisodeToMediaItem(context = this, episode = episode, parentPodcast = podcast)
            }
        }
    }

    protected suspend fun loadListeningHistoryChildren(): List<MediaItem> {
        return episodeManager.findPlaybackHistoryEpisodes().take(EPISODE_LIMIT).mapNotNull { episode ->
            podcastManager.findPodcastByUuid(episode.podcastUuid)?.let { podcast ->
                AutoConverter.convertEpisodeToMediaItem(context = this, episode = episode, parentPodcast = podcast)
            }
        }
    }

    /**
     * Search for local and remote podcasts.
     * Returning an empty list displays "No media available for browsing here"
     * Returning null displays "Something went wrong". There is no way to display our own error message.
     */
    private suspend fun podcastSearch(term: String): List<MediaItem>? {
        val termCleaned = term.trim()
        // search for local podcasts
        val localPodcasts = podcastManager.findSubscribedNoOrder()
            .filter { it.title.contains(termCleaned, ignoreCase = true) || it.author.contains(termCleaned, ignoreCase = true) }
            .sortedBy { PodcastsSortType.cleanStringForSort(it.title) }
        // search for podcasts on the server
        val serverPodcasts = try {
            // only search the server if the term is over one character long
            if (termCleaned.length <= 1) {
                emptyList()
            } else {
                serverManager.searchForPodcastsSuspend(searchTerm = term, resources = resources).searchResults
            }
        } catch (ex: Exception) {
            Timber.e(ex)
            // display the error message when the server call fails only if there is no local podcasts to display
            if (localPodcasts.isEmpty()) {
                return null
            }
            emptyList()
        }
        // merge the local and remote podcasts
        val podcasts = (localPodcasts + serverPodcasts).distinctBy { it.uuid }
        // convert podcasts to the media browser format
        return podcasts.mapNotNull { podcast -> convertPodcastToMediaItem(context = this, podcast = podcast) }
    }
    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            Timber.d("TEST123, onConnect: ${controller.packageName}")
            return super.onConnect(session, controller)
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            Timber.i("TEST123, onPostConnect: ${controller.packageName}")
            super.onPostConnect(session, controller)
        }

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ) {
            Timber.i("TEST123, onDisconnected: ${controller.packageName}")
            super.onDisconnected(session, controller)
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int {
            Timber.i("TEST123, onPlayerCommandRequest: $playerCommand")
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            future {
                Timber.i("TEST123, onAddMediaItems: $mediaItems")
                mediaItems
                    .mapNotNull {
                        val playableUuid = it.mediaId
                        val playable = episodeManager.findPlayableByUuid(playableUuid)

                        if (playable == null) {
                            LogBuffer.e(
                                LogBuffer.TAG_PLAYBACK,
                                "Could not find playable for $playableUuid in PlaybackService onAddMediaItems callback"
                            )
                            null
                        } else {

                            // FIXME Where to use the logic for creating a source instead of a media item
//                            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
//                                .setUserAgent("Pocket Casts")
//                                .setAllowCrossProtocolRedirects(true)
//                            val dataSourceFactory = DefaultDataSource.Factory(baseContext, httpDataSourceFactory)
//                            val extractorsFactory = DefaultExtractorsFactory().setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
//                            val source = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
//                                .createMediaSource(mediaItem)
//                            exoPlayer.setMediaSource(source)

                            val location = if (playable.isDownloaded) {
                                playable.downloadedFilePath
                            } else {
                                playable.downloadUrl
                            }

                            Timber.i("TEST123, adding location to media item: $location")

                            it.buildUpon().setUri(
                                Uri.parse(location)
                            ).build()
                        }
                    }.toMutableList()
            }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Timber.i("TEST123, onSetMediaItems: $mediaItems")
            return super.onSetMediaItems(
                mediaSession,
                controller,
                mediaItems,
                startIndex,
                startPositionMs
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            Timber.i("TEST123, onCustomCommand: $customCommand")
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            launch {
                val items: List<MediaItem> = when (parentId) {
                    RECENT_ROOT -> loadRecentChildren()
                    SUGGESTED_ROOT -> loadSuggestedChildren()
                    MEDIA_ID_ROOT -> loadRootChildren()
                    PODCASTS_ROOT -> loadPodcastsChildren()
                    FILES_ROOT -> loadFilesChildren()
                    else -> {
                        if (parentId.startsWith(FOLDER_ROOT_PREFIX)) {
                            loadFolderPodcastsChildren(folderUuid = parentId.substring(FOLDER_ROOT_PREFIX.length))
                        } else {
                            loadEpisodeChildren(parentId)
                        }
                    }
                }
                session.notifyChildrenChanged(browser, parentId, items.size, params)
            }

            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            var items: List<MediaItem>
            return future {
                items = when (parentId) {
                    RECENT_ROOT -> loadRecentChildren()
                    SUGGESTED_ROOT -> loadSuggestedChildren()
                    MEDIA_ID_ROOT -> loadRootChildren()
                    PODCASTS_ROOT -> loadPodcastsChildren()
                    FILES_ROOT -> loadFilesChildren()
                    else -> {
                        if (parentId.startsWith(FOLDER_ROOT_PREFIX)) {
                            loadFolderPodcastsChildren(folderUuid = parentId.substring(FOLDER_ROOT_PREFIX.length))
                        } else {
                            loadEpisodeChildren(parentId)
                        }
                    }
                }
                LibraryResult.ofItemList(items, params)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            launch {
                val results = podcastSearch(query)
                session.notifySearchResultChanged(browser, query, results?.size ?: 0, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
    }
}
