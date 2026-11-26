package com.santiagocsm.xmblite

internal enum class Page {
    ROOT, APPEARANCE, EFFECTS, MOTION, INFO, MEDIA, SONGS, ALBUMS, ARTISTS, GROUP, PLAYER
}

internal class MenuSelection(private val categoryCount: Int) {
    private val categories = IntArray(categoryCount)
    private val rows = IntArray(Page.entries.size)
    var category = 0
        set(value) { field = value.coerceIn(categories.indices) }
    var page = Page.ROOT
    var groupKey: String? = null
        private set
    var groupParent = Page.ALBUMS
        private set
    private var playerParent = Page.ROOT

    fun rootRow(count: Int) = clampRow(categories[category], count)

    fun row(count: Int): Int {
        val position = if (page == Page.ROOT) categories[category] else rows[page.ordinal]
        return clampRow(position, count)
    }

    private fun clampRow(position: Int, count: Int) = position.coerceIn(0, (count - 1).coerceAtLeast(0))

    fun selectRow(index: Int) {
        if (page == Page.ROOT) categories[category] = index else rows[page.ordinal] = index
    }

    fun selectOn(page: Page, index: Int) {
        rows[page.ordinal] = index
    }

    fun enterGroup(key: String) {
        if (groupKey != key || groupParent != page) selectOn(Page.GROUP, 0)
        groupKey = key
        groupParent = page
    }

    fun enterPlayer() {
        playerParent = page
        selectOn(Page.PLAYER, 1)
    }

    fun parent(): Page? = when (page) {
        Page.ROOT -> null
        Page.EFFECTS, Page.MOTION -> Page.APPEARANCE
        Page.PLAYER -> playerParent
        Page.GROUP -> groupParent
        else -> Page.ROOT
    }

    fun snapshot() = Snapshot(category, page.ordinal, categories.copyOf(), rows.copyOf(),
        groupKey, groupParent.ordinal, playerParent.ordinal)

    fun restore(state: Snapshot) {
        category = state.category
        page = Page.entries.getOrNull(state.page) ?: Page.ROOT
        state.categories?.let { it.copyInto(categories, endIndex = minOf(it.size, categories.size)) }
        state.rows?.let { it.copyInto(rows, endIndex = minOf(it.size, rows.size)) }
        groupKey = state.groupKey
        groupParent = if (state.groupParent == Page.ARTISTS.ordinal) Page.ARTISTS else Page.ALBUMS
        playerParent = Page.entries.getOrNull(state.playerParent)
            ?.takeUnless { it == Page.PLAYER } ?: Page.ROOT
    }

    data class Snapshot(
        val category: Int, val page: Int, val categories: IntArray?, val rows: IntArray?,
        val groupKey: String?, val groupParent: Int, val playerParent: Int
    )
}
