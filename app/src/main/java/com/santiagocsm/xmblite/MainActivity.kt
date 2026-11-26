package com.santiagocsm.xmblite

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

@UnstableApi
internal class MainActivity : ComponentActivity(), MusicControls {
    private lateinit var menu: XmbMenuView
    private val appearance by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }

    private val libraryWorker = Executors.newSingleThreadExecutor()
    private var libraryQuery: CancellationSignal? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var connectionFailed = false
    private var started = false
    private var pendingPlay: PendingPlay? = null
    private var permissionRequestInFlight = false
    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRequestInFlight = false
        if (granted || shouldShowRequestPermissionRationale(musicPermission)) {
            appearance.edit().putBoolean("audio_permission_requested", !granted).apply()
        }
        loadLibrary()
    }
    private data class PendingPlay(val songs: List<Song>, val index: Int)
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { menu.goBack() }
    }
    private val playbackListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            menu.refreshPlayback()
        }
    }
    private val musicPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionRequestInFlight = savedInstanceState?.getBoolean("permissionInFlight") == true
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val background = WaveBackgroundView(this).apply {
            val savedEffect = appearance.getString("background", null)
            effect = WaveBackgroundView.Effect.entries.firstOrNull { it.id == savedEffect }
                ?: WaveBackgroundView.Effect.BLUE
            val savedMotion = appearance.getString("motion", null)
            motion = WaveBackgroundView.Motion.entries.firstOrNull { it.id == savedMotion }
                ?: WaveBackgroundView.Motion.FLUID
        }
        menu = XmbMenuView(this, background.effect, background.motion, this,
            onPageChanged = { page ->
                back.isEnabled = menu.hasParent()
                if (page == Page.PLAYER && !connectionFailed) connectPlayer()
                if (page != Page.PLAYER) pendingPlay = null
            },
            onEffect = { effect ->
                background.effect = effect
                appearance.edit().putString("background", effect.id).apply()
            },
            onMotion = { motion ->
                background.motion = motion
                appearance.edit().putString("motion", motion.id).apply()
            })
        savedInstanceState?.getBundle("menu")?.let(menu::restoreSelection)
        setContentView(FrameLayout(this).apply {
            addView(background)
            addView(menu)
        })
        menu.requestFocus()

        back.isEnabled = menu.hasParent()
        onBackPressedDispatcher.addCallback(this, back)
    }

    override fun onStart() {
        super.onStart()
        started = true
        if (menu.isPlayerPage()) connectPlayer()
        loadLibrary()
    }

    override fun onStop() {
        started = false
        val query = libraryQuery
        libraryQuery = null
        query?.cancel()
        pendingPlay = null
        disconnectPlayer()
        menu.refreshPlayback()
        super.onStop()
    }

    private fun disconnectPlayer() {
        val future = controllerFuture
        controllerFuture = null
        controller?.removeListener(playbackListener)
        controller = null
        future?.let(MediaController::releaseFuture)
    }

    override fun onDestroy() {
        libraryWorker.shutdownNow()
        super.onDestroy()
    }

    private fun connectPlayer() {
        if (!started || controllerFuture != null) return
        connectionFailed = false
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@MainActivity.controller === controller) {
                        disconnectPlayer()
                        connectionFailed = true
                        menu.refreshPlayback()
                    }
                }
            })
            .buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture === future) {
                try {
                    val player = future.get()
                    if (player.isConnected) {
                        controller = player
                        player.addListener(playbackListener)
                        pendingPlay?.let { playSelection(player, it) }
                    } else {
                        disconnectPlayer()
                        connectionFailed = true
                    }
                } catch (_: ExecutionException) {
                    disconnectPlayer()
                    connectionFailed = true
                } catch (_: java.util.concurrent.CancellationException) {
                    disconnectPlayer()
                    connectionFailed = true
                }
                menu.refreshPlayback()
            }
        }, mainExecutorCompat)
    }

    private val mainExecutorCompat = java.util.concurrent.Executor { runOnUiThread(it) }

    private fun loadLibrary() {
        if (!started) return
        val previousQuery = libraryQuery
        libraryQuery = null
        previousQuery?.cancel()
        if (checkSelfPermission(musicPermission) != PackageManager.PERMISSION_GRANTED) {
            menu.updateLibrary(LibraryState.PERMISSION)
            return
        }

        val query = CancellationSignal()
        libraryQuery = query
        menu.libraryLoading()
        libraryWorker.execute {
            val library = try {
                MusicLibrary.read(contentResolver, query)
            } catch (_: RuntimeException) {
                null
            }
            runOnUiThread {
                if (libraryQuery === query) {
                    libraryQuery = null
                    if (library == null) menu.updateLibrary(LibraryState.ERROR)
                    else menu.updateLibrary(LibraryState.READY, library)
                }
            }
        }
    }

    override fun requestLibrary() {
        if (permissionRequestInFlight) return
        if (checkSelfPermission(musicPermission) == PackageManager.PERMISSION_GRANTED) {
            loadLibrary()
        } else if (appearance.getBoolean("audio_permission_requested", false) &&
            !shouldShowRequestPermissionRationale(musicPermission)
        ) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)))
        } else {
            permissionRequestInFlight = true
            requestAudioPermission.launch(musicPermission)
        }
    }

    override fun playSongs(songs: List<Song>, index: Int) {
        val selection = PendingPlay(songs, index)
        val player = controller
        if (player == null) {
            pendingPlay = selection
            connectPlayer()
        } else {
            playSelection(player, selection)
        }
    }

    private fun playSelection(player: MediaController, selection: PendingPlay) {
        pendingPlay = null
        player.setMediaItems(selection.songs.map(Song::mediaItem), selection.index, 0L)
        player.prepare()
        player.play()
    }

    override fun transport(command: Transport) {
        val player = controller ?: run {
            if (connectionFailed && command == Transport.TOGGLE) {
                connectPlayer()
                menu.refreshPlayback()
            }
            return
        }
        if (player.mediaItemCount == 0) return
        when (command) {
            Transport.PREVIOUS -> if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
            }
            Transport.NEXT -> if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
            }
            Transport.TOGGLE -> when {
                player.playerError != null || player.playbackState == Player.STATE_IDLE -> {
                    player.prepare()
                    player.play()
                }
                player.playbackState == Player.STATE_ENDED -> {
                    player.seekTo(0L)
                    player.play()
                }
                player.playWhenReady -> player.pause()
                else -> player.play()
            }
        }
    }

    override fun seekTo(position: Long) {
        controller?.takeIf { it.isCurrentMediaItemSeekable }?.seekTo(position)
    }

    override fun playback(): Playback {
        val player = controller ?: return Playback(
            title = pendingPlay?.let { it.songs[it.index].title },
            connectionFailed = connectionFailed,
            message = if (connectionFailed) "Error de conexión · pulsa Reintentar" else "Conectando…"
        )
        val item = player.currentMediaItem
        return Playback(
            title = (item?.mediaMetadata?.title ?: player.mediaMetadata.title)?.toString(),
            artist = (item?.mediaMetadata?.artist ?: player.mediaMetadata.artist)?.toString(),
            position = player.currentPosition.coerceAtLeast(0L),
            duration = player.duration.coerceAtLeast(0L),
            playing = player.isPlaying,
            wantsToPlay = player.playWhenReady && player.playerError == null &&
                player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE,
            canSeek = player.isCurrentMediaItemSeekable,
            hasPrevious = player.hasPreviousMediaItem(),
            canToggle = player.mediaItemCount > 0,
            hasNext = player.hasNextMediaItem(),
            message = when {
                player.playerError != null -> "Archivo inaccesible o incompatible"
                player.playbackState == Player.STATE_ENDED -> "Fin de la cola"
                player.playbackState == Player.STATE_BUFFERING -> "Cargando…"
                player.mediaItemCount == 0 -> "Elige una canción de la biblioteca"
                else -> null
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("menu", menu.saveSelection())
        outState.putBoolean("permissionInFlight", permissionRequestInFlight)
    }
}
