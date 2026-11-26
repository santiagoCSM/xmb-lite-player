package com.santiagocsm.xmblite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MenuSelectionTest {
    @Test fun keepsSeparateRootSelectionsAndReturnsToAppearance() {
        val menu = MenuSelection(3)
        menu.selectRow(2)
        menu.category = 2
        menu.selectRow(1)
        menu.page = Page.MOTION
        assertEquals(Page.APPEARANCE, menu.parent())
        menu.page = Page.ROOT
        assertEquals(1, menu.row(2))
        menu.category = 0
        assertEquals(2, menu.row(4))
        assertNull(menu.parent())
    }

    @Test fun restoresPlayerGroupAndParentRowsAfterRecreation() {
        val menu = MenuSelection(3)
        menu.page = Page.ARTISTS
        menu.selectRow(3)
        menu.enterGroup("artist")
        menu.page = Page.GROUP
        menu.selectRow(4)
        menu.enterPlayer()
        menu.page = Page.PLAYER
        val restored = MenuSelection(3).apply { restore(menu.snapshot()) }

        assertEquals(Page.PLAYER, restored.page)
        assertEquals(1, restored.row(3))
        restored.page = restored.parent()!!
        assertEquals(Page.GROUP, restored.page)
        assertEquals("artist", restored.groupKey)
        assertEquals(0, restored.row(0))
        assertEquals(4, restored.row(8))
        restored.page = restored.parent()!!
        assertEquals(Page.ARTISTS, restored.page)
        assertEquals(3, restored.row(10))

        restored.enterGroup("another artist")
        restored.page = Page.GROUP
        assertEquals(0, restored.row(8))
    }

    @Test fun miniPlayerReturnsToSettingsAfterRecreation() {
        val menu = MenuSelection(3)
        menu.page = Page.APPEARANCE
        menu.selectRow(1)
        menu.enterPlayer()
        menu.page = Page.PLAYER

        val restored = MenuSelection(3).apply { restore(menu.snapshot()) }
        assertEquals(Page.APPEARANCE, restored.parent())
        restored.page = restored.parent()!!
        assertEquals(1, restored.row(2))
    }

    @Test fun clampsSelectionsAgainstSmallerListsAndKeepsSnapshotIndependent() {
        val menu = MenuSelection(3)
        menu.page = Page.SONGS
        menu.selectRow(10)
        val snapshot = menu.snapshot()
        menu.selectRow(0)
        val restored = MenuSelection(3).apply { restore(snapshot) }
        assertEquals(2, restored.row(3))
        assertEquals(10, restored.row(11))
    }

    @Test fun permissionsErrorsAndEmptyLibrariesHaveAnActionWhileLoadingHasNone() {
        assertEquals(1, LibraryState.PERMISSION.rows(0))
        assertEquals(0, LibraryState.LOADING.rows(3))
        assertEquals(1, LibraryState.ERROR.rows(0))
        assertEquals(1, LibraryState.READY.rows(0))
        assertEquals(3, LibraryState.READY.rows(3))
    }

    @Test fun playbackTimeHandlesUnknownAndLongTracks() {
        assertEquals("0:00", formatPlaybackTime(Long.MIN_VALUE))
        assertEquals("0:00", formatPlaybackTime(999))
        assertEquals("1:05", formatPlaybackTime(65_900))
        assertEquals("120:01", formatPlaybackTime(7_201_000))
    }
}
