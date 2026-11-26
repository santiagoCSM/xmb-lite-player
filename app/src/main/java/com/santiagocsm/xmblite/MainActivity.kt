package com.santiagocsm.xmblite

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import android.provider.Settings
import android.widget.Toast
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

@UnstableApi

internal class MainActivity : Activity(), MusicControls {
    private lateinit var menu: XmbMenuView
    private val appearance by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }

    private val libraryWorker = Executors.newSingleThreadExecutor()
    private var libraryQuery: CancellationSignal? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var connectionFailed = false
    private val playbackListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            menu.refreshPlayback()
        }
    }
    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (this@MainActivity.controller === controller) {
                this@MainActivity.controller = null
                connectionFailed = true
                menu.refreshPlayback()
            }
        }
    }

    private val musicPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                if (!menu.goBack() && !moveTaskToBack(false)) finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectPlayer()
        loadLibrary()
    }

    override fun onStop() {
        libraryQuery?.cancel()
        libraryQuery = null
        controller?.removeListener(playbackListener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        menu.refreshPlayback()
        super.onStop()
    }

    override fun onDestroy() {
        libraryWorker.shutdownNow()
        super.onDestroy()
    }

    private fun connectPlayer() {
        controllerFuture?.let(MediaController::releaseFuture)
        connectionFailed = false
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture === future) {
                try {
                    controller = future.get().also { it.addListener(playbackListener) }
                } catch (_: ExecutionException) {
                    connectionFailed = true
                }
                menu.refreshPlayback()
            }
        }, mainExecutorCompat)
    }

    private val mainExecutorCompat = java.util.concurrent.Executor { runOnUiThread(it) }

    private fun loadLibrary() {
        libraryQuery?.cancel()
        libraryQuery = null
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
        if (checkSelfPermission(musicPermission) == PackageManager.PERMISSION_GRANTED) {
            loadLibrary()
        } else if (appearance.getBoolean("audio_permission_requested", false) &&
            !shouldShowRequestPermissionRationale(musicPermission)
        ) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)))
        } else {
            requestPermissions(arrayOf(musicPermission), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty()) {
            // A granted permission may later be reset by Android, so allow a new request.
            appearance.edit().putBoolean("audio_permission_requested",
                grantResults[0] != PackageManager.PERMISSION_GRANTED).apply()
            loadLibrary()
        }
    }

    override fun playSongs(songs: List<Song>, index: Int): Boolean {
        val player = controller ?: run {
            if (connectionFailed) connectPlayer()
            Toast.makeText(this, "Conectando con el reproductor. Intenta de nuevo.",
                Toast.LENGTH_SHORT).show()
            return false
        }
        player.setMediaItems(songs.map(Song::mediaItem), index, 0L)
        player.prepare()
        player.play()
        return true
    }

    override fun transport(command: Transport) {
        val player = controller ?: run {
            if (connectionFailed) connectPlayer()
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
        val player = controller ?: return Playback(message = if (connectionFailed)
            "Error de conexión · pulsa Reintentar" else "Conectando…")
        return Playback(
            title = player.mediaMetadata.title?.toString(),
            artist = player.mediaMetadata.artist?.toString(),
            position = player.currentPosition.coerceAtLeast(0L),
            duration = player.duration.coerceAtLeast(0L),
            playing = player.isPlaying,
            wantsToPlay = player.playWhenReady && player.playerError == null &&
                player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE,
            canSeek = player.isCurrentMediaItemSeekable,
            hasPrevious = player.hasPreviousMediaItem(),
            hasNext = player.hasNextMediaItem(),
            message = when {
                player.playerError != null -> "Archivo inaccesible o incompatible"
                player.playbackState == Player.STATE_BUFFERING -> "Cargando…"
                player.mediaItemCount == 0 -> "Elige una canción de la biblioteca"
                else -> null
            }
        )
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!menu.goBack()) super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("menu", menu.saveSelection())
    }
}
