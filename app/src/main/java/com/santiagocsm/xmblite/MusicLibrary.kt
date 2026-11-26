package com.santiagocsm.xmblite

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.text.Collator

internal data class Song(val uri: String, val title: String, val artist: String, val album: String) {
    fun mediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(uri)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build()
        )
        .build()
}

internal data class IndexedSong(val song: Song, val artistId: Long, val albumId: Long, val track: Int)

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
            val tracks = mutableListOf<IndexedSong>()
            val cursor = checkNotNull(resolver.query(collection, projection,
                "${Media.IS_MUSIC} != 0", null, null, cancellation))
            cursor.use {
                while (it.moveToNext()) {
                    cancellation.throwIfCanceled()
                    val song = Song(ContentUris.withAppendedId(collection, it.getLong(0)).toString(),
                        label(it.getString(1), "Sin título"),
                        label(it.getString(2), "Artista desconocido"),
                        label(it.getString(3), "Álbum desconocido"))
                    tracks.add(IndexedSong(song, it.getLong(4), it.getLong(5), it.getInt(6)))
                }
            }
            return organize(tracks, checkCanceled = cancellation::throwIfCanceled)
        }

        fun organize(tracks: List<IndexedSong>, collator: Collator = Collator.getInstance(),
            checkCanceled: () -> Unit = {}
        ): MusicLibrary {
            val songs = ArrayList<Song>(tracks.size)
            val artists = mutableMapOf<String, MutableList<Song>>()
            val albums = mutableMapOf<String, MutableList<IndexedSong>>()
            for (track in tracks) {
                checkCanceled()
                val song = track.song
                songs.add(song)
                artists.getOrPut("${track.artistId}:${song.artist}", ::mutableListOf).add(song)
                albums.getOrPut("${track.albumId}:${song.album}", ::mutableListOf).add(track)
            }

            val byTitle = Comparator<Song> { a, b ->
                val title = collator.compare(a.title, b.title)
                if (title != 0) title else a.uri.compareTo(b.uri)
            }
            val byLabel = Comparator<Group> { a, b ->
                val label = collator.compare(a.label, b.label)
                if (label != 0) label else a.key.compareTo(b.key)
            }
            // MediaStore encodes the disc number in the thousands. A zero track within a
            // disc means the order is unknown, so sort it after numbered tracks on that disc.
            val byTrack = compareBy<IndexedSong> { (it.track / 1000).coerceAtLeast(1) }
                .thenBy { (it.track % 1000).takeIf { number -> number > 0 } ?: Int.MAX_VALUE }
                .thenComparator { a, b -> byTitle.compare(a.song, b.song) }

            songs.sortWith(byTitle)
            return MusicLibrary(
                songs,
                artists.map { (key, group) ->
                    checkCanceled()
                    Group(key, group.first().artist, group.sortedWith(byTitle))
                }.sortedWith(byLabel),
                albums.map { (key, group) ->
                    checkCanceled()
                    val first = group.first().song
                    val artist = first.artist.takeIf { name -> group.all { it.song.artist == name } }
                        ?: "Varios artistas"
                    Group(key, "${first.album} · $artist", group.sortedWith(byTrack).map { it.song })
                }.sortedWith(byLabel)
            )
        }

        private fun label(raw: String?, fallback: String): String =
            raw?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING } ?: fallback
    }
}
