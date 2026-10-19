package dev.folialocal.app

import android.app.Application
import android.content.Context
import dev.folialocal.app.data.audio.AudioEffectsRepository
import dev.folialocal.app.data.library.CoverRepository
import dev.folialocal.app.data.library.LibraryRepository
import dev.folialocal.app.data.library.TrackOverrideRepository
import dev.folialocal.app.data.lyrics.LyricsRepository
import dev.folialocal.app.data.lyrics.OnlineLyricsService
import dev.folialocal.app.data.playback.PlaybackConnection
import dev.folialocal.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val trackOverrideRepository = TrackOverrideRepository(context.applicationContext)
    val libraryRepository = LibraryRepository(context.applicationContext, trackOverrideRepository)
    val coverRepository = CoverRepository(context.applicationContext)
    val lyricsRepository = LyricsRepository(context.applicationContext)
    val onlineLyricsService = OnlineLyricsService(context.applicationContext, libraryRepository)
    val settingsRepository = SettingsRepository(context.applicationContext)
    val audioEffectsRepository = AudioEffectsRepository(context.applicationContext)
    val playbackConnection = PlaybackConnection(context.applicationContext, mainScope)
}

class FoliaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
