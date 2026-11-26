package com.santiagocsm.xmblite

internal enum class LibraryState { PERMISSION, LOADING, READY, ERROR }

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
    val message: String? = null
)

internal enum class Transport { PREVIOUS, TOGGLE, NEXT }

internal interface MusicControls {
    fun requestLibrary()
    fun playSongs(songs: List<Song>, index: Int): Boolean
    fun transport(command: Transport)
    fun seekTo(position: Long)
    fun playback(): Playback
}
