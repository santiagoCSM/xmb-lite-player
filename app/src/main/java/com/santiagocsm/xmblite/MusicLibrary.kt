package com.santiagocsm.xmblite

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.text.Collator

internal data class Song(val uri: Uri, val title: String, val artist: String, val album: String) {
    fun mediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(uri.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build()
        )
        .build()
}

internal class MusicLibrary(
    val songs: List<Song>,
    val artists: List<Group>,
    val albums: List<Group>
) {
    class Group(val key: String, val label: String, val songs: List<Song>)

    companion object {
        fun read(resolver: ContentResolver, cancellation: CancellationSignal): MusicLibrary {
            val collection = if (Build.VERSION.SDK_INT >= 29) {
                Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(Media._ID, Media.TITLE, Media.ARTIST, Media.ALBUM,
                Media.ARTIST_ID, Media.ALBUM_ID, Media.TRACK)
            val songs = mutableListOf<Song>()
            val artists = mutableMapOf<String, MutableList<Song>>()
            val albums = mutableMapOf<String, MutableList<Pair<Int, Song>>>()
            val cursor = checkNotNull(resolver.query(collection, projection,
                "${Media.IS_MUSIC} != 0", null, null, cancellation))
            cursor.use {
                while (it.moveToNext()) {
                    cancellation.throwIfCanceled()
                    val artist = label(it.getString(2), "Artista desconocido")
                    val album = label(it.getString(3), "Álbum desconocido")
                    val song = Song(ContentUris.withAppendedId(collection, it.getLong(0)),
                        label(it.getString(1), "Sin título"), artist, album)
                    songs.add(song)
                    artists.getOrPut("${it.getLong(4)}:$artist", ::mutableListOf).add(song)
                    albums.getOrPut("${it.getLong(5)}:$album", ::mutableListOf).add(it.getInt(6) to song)
                }
            }
            val collator = Collator.getInstance()
            val byTitle = Comparator<Song> { a, b ->
                val title = collator.compare(a.title, b.title)
                if (title != 0) title else a.uri.compareTo(b.uri)
            }
            val byLabel = Comparator<Group> { a, b ->
                val label = collator.compare(a.label, b.label)
                if (label != 0) label else a.key.compareTo(b.key)
            }
            val byTrack = compareBy<Pair<Int, Song>> { if (it.first > 0) it.first else Int.MAX_VALUE }
                .thenComparator { a, b -> byTitle.compare(a.second, b.second) }
            return MusicLibrary(
                songs.sortedWith(byTitle),
                artists.map { (key, tracks) ->
                    Group(key, tracks.first().artist, tracks.sortedWith(byTitle))
                }.sortedWith(byLabel),
                albums.map { (key, tracks) ->
                    val first = tracks.first().second
                    val artist = first.artist.takeIf { tracks.all { it.second.artist == first.artist } }
                        ?: "Varios artistas"
                    Group(key, "${first.album} · $artist", tracks.sortedWith(byTrack).map { it.second })
                }.sortedWith(byLabel)
            )
        }

        private fun label(raw: String?, fallback: String): String =
            raw?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING } ?: fallback
    }
}
