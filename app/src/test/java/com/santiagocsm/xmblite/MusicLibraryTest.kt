package com.santiagocsm.xmblite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Collator
import java.util.Locale

class MusicLibraryTest {
    private fun track(id: Int, title: String, artist: String, album: String, albumId: Long,
        number: Int = 0, artistId: Long = 1
    ) = IndexedSong(Song("content://media/audio/$id", title, artist, album), artistId, albumId, number)

    private fun library(vararg tracks: IndexedSong) =
        MusicLibrary.organize(tracks.toList(), Collator.getInstance(Locale.forLanguageTag("es-MX")))

    @Test fun sortsSongsAndAlbumsByLabelWithStableTies() {
        val z = track(1, "Zeta", "A", "Zeta", 1)
        val b = track(3, "Álamo", "A", "Álbum", 2)
        val a = track(2, "Álamo", "A", "Álbum", 2)
        val library = library(z, b, a)
        assertEquals(listOf(a.song, b.song, z.song), library.songs)
        assertEquals(listOf("Álbum · A", "Zeta · A"), library.albums.map { it.label })
    }

    @Test fun albumTracksFollowDiscAndTrackOrderAndShowCompilations() {
        val unknown = track(1, "A", "Ana", "Directo", 1)
        val secondDisc = track(2, "B", "Luis", "Directo", 1, 2001, 2)
        val secondTrack = track(3, "C", "Ana", "Directo", 1, 2)
        val firstTrack = track(4, "D", "Ana", "Directo", 1, 1001)
        val album = library(unknown, secondDisc, secondTrack, firstTrack).albums.single()
        assertEquals("Directo · Varios artistas", album.label)
        assertEquals(listOf(firstTrack.song, secondTrack.song, unknown.song, secondDisc.song), album.songs)
    }

    @Test fun groupsByArtistIdAndSortsTracksInEachGroup() {
        val a = track(1, "Z", "Ana", "Uno", 1)
        val b = track(2, "A", "Ana", "Dos", 2)
        val c = track(3, "B", "Ana", "Tres", 3, artistId = 2)
        val artists = library(c, a, b).artists
        assertEquals(listOf("1:Ana", "2:Ana"), artists.map { it.key })
        assertEquals(listOf(b.song, a.song), artists[0].songs)
        assertEquals(listOf(c.song), artists[1].songs)
    }

    @Test fun emptyLibraryHasNoGroupsOrSongs() {
        val empty = library()
        assertTrue(empty.songs.isEmpty())
        assertTrue(empty.artists.isEmpty())
        assertTrue(empty.albums.isEmpty())
    }
}
