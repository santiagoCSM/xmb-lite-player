package com.santiagocsm.xmblite

internal enum class LibraryState {
    PERMISSION, LOADING, READY, ERROR;

    fun rows(itemCount: Int): Int = when (this) {
        PERMISSION, ERROR -> 1
        LOADING -> 0
        READY -> itemCount.coerceAtLeast(1)
    }
}


internal data class Playback(
    val title: String? = null,
    val artist: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
    val playing: Boolean = false,
    val wantsToPlay: Boolean = false,
    val canSeek: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val canToggle: Boolean = false,
    val connectionFailed: Boolean = false,
    val message: String? = null
)

internal enum class Transport { PREVIOUS, TOGGLE, NEXT }

internal interface MusicControls {
    fun requestLibrary()
    fun playSongs(songs: List<Song>, index: Int)
    fun transport(command: Transport)
    fun seekTo(position: Long)
    fun playback(): Playback
}


internal fun formatPlaybackTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
