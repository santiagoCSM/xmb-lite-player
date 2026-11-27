package com.santiagocsm.xmblite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

internal class XmbMenuView(
    context: Context,
    private var effect: WaveBackgroundView.Effect,
    private var motion: WaveBackgroundView.Motion,
    private val music: MusicControls,
    private val onPageChanged: (Page) -> Unit,
    private val onEffect: (WaveBackgroundView.Effect) -> Unit,
    private val onMotion: (WaveBackgroundView.Motion) -> Unit
) : View(context) {
    private enum class Category(val title: String, val icon: XmbIcon, vararg val items: String) {
        MUSIC("Música", XmbIcon.MUSIC, "Canciones", "Álbumes", "Artistas", "En reproducción"),
        VIDEO("Video", XmbIcon.VIDEO, "Videos locales", "Carpetas"),
        SETTINGS("Ajustes", XmbIcon.SETTINGS, "Apariencia", "Información");

        fun rowIcon(index: Int): XmbIcon = when (this) {
            MUSIC -> when (index) {
                0 -> XmbIcon.SONGS
                1 -> XmbIcon.ALBUMS
                2 -> XmbIcon.ARTISTS
                else -> XmbIcon.NOW_PLAYING
            }
            VIDEO -> if (index == 0) XmbIcon.LOCAL_VIDEOS else XmbIcon.FOLDERS
            SETTINGS -> if (index == 0) XmbIcon.APPEARANCE else XmbIcon.INFORMATION
        }
    }

    private val appearanceItems = arrayOf("Fondo animado", "Rendimiento")
    private val effectItems = WaveBackgroundView.Effect.entries.map { it.label }.toTypedArray()
    private val motionItems = WaveBackgroundView.Motion.entries.map { it.label }.toTypedArray()
    private val appearanceRows = appearanceItems.asList()
    private val effectRows = effectItems.asList()
    private val motionRows = motionItems.asList()
    private val permissionRows = listOf("Permitir acceso a música")
    private val retryRows = listOf("Volver a cargar")
    private val playerRows = listOf("Anterior", "Reproducir", "Siguiente")
    private var libraryState = LibraryState.PERMISSION
    private var library: MusicLibrary? = null
    private var playback = Playback()
    private var positionLabel = "0:00"
    private var durationLabel = "0:00"
    private val progressTick = Runnable { refreshPlayback() }
    private val selection = MenuSelection(Category.entries.size)
    private val category get() = selection.category
    private val page get() = selection.page
    private var effectIndex = effect.ordinal
    private var motionIndex = motion.ordinal

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val icons = XmbIcons(context)
    private data class FittedText(val width: Float, val size: Float, val bold: Boolean, val text: CharSequence)
    private val fittedLabels = LruCache<String, FittedText>(64)
    private val white = Color.rgb(239, 248, 250)
    private val muted = Color.rgb(184, 211, 224)
    private val accent = Color.rgb(110, 225, 215)
    private var downX = 0f
    private var downY = 0f
    private var trackingTouch = false
    private var lastCategory = 0f
    private var lastRow = 0f
    private var animationStart = 0L
    private var revealRows = false
    private var cardAnimationStart = 0L
    private var cardAnimationFrom = 0f

    private var cutoutTop = 0
    private var cutoutLeft = 0
    private var cutoutRight = 0

    private val anchorX get() = width * 0.25f
    private val categoryY get() = (height * .30f).coerceAtLeast(
        if (hasDockedPlayer) dockTop + cardHeight + 28f * density else 0f
    )
    private val categoryStep get() = min(width * 0.22f, 104f * density)
    private val titleY get() = (height * .28f).coerceAtLeast(
        if (hasDockedPlayer) dockTop + cardHeight + 28f * density else headerY + 32f * density
    )
    private val libraryMessageY get() = maxOf(height * .37f, titleY + 24f * density)
    private val rowY get() = when (page) {
        Page.ROOT -> categoryY + min(118f * density, height * .20f)
            .coerceAtLeast(48f * density + rowStep * .43f)
        else -> maxOf(height * .45f, libraryMessageY + rowStep * .43f + 8f * density)
    }
    private val rowStep get() = min(62f * density, height * 0.135f)
    private val rowLeft get() = (anchorX - 38f * density).coerceAtLeast(10f * density)
    private val rowRight get() = min(width - 12f * density, anchorX + 280f * density)
    private val listTop get() = if (page == Page.ROOT) categoryY + 48f * density
        else titleY + 10f * density
    private val listBottom get() = height - 18f * density
    private val titleX get() = cutoutLeft + 20f * density
    private val headerY get() = cutoutTop + 24f * density

    private val cardCenterX get() = (cutoutLeft + width - cutoutRight) * .5f
    private val cardWidth get() = (width - cutoutLeft - cutoutRight - 28f * density)
        .coerceIn(0f, 368f * density)
    private val cardHeight get() = min(108f * density, height * .31f)
    private val cardLeft get() = cardCenterX - cardWidth * .5f
    private val cardRight get() = cardCenterX + cardWidth * .5f
    private val dockTop get() = cutoutTop + 36f * density
    private val railLeft get() = cardLeft + 54f * density
    private val railRight get() = cardRight - 54f * density
    private val controlStep get() = min(52f * density, cardWidth * .18f)

    private val hasCurrentTrack get() = playback.canToggle && playback.title != null
    private val hasDockedPlayer get() = !isPlayerPage() && hasCurrentTrack

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 28) {
            val cutout = insets.displayCutout
            cutoutTop = cutout?.safeInsetTop ?: 0
            cutoutLeft = cutout?.safeInsetLeft ?: 0
            cutoutRight = cutout?.safeInsetRight ?: 0
            invalidate()
        }
        return super.onApplyWindowInsets(insets)
    }

    init {
        isFocusableInTouchMode = true
        isClickable = true
        describeSelection()
    }

    private fun rootItems() = Category.entries[category].items

    private fun groups(forPage: Page) =
        (if (forPage == Page.ARTISTS) library?.artists else library?.albums).orEmpty()

    private var currentGroup: MusicLibrary.Group? = null

    private fun songs() = if (page == Page.GROUP) currentGroup?.songs.orEmpty() else library?.songs.orEmpty()

    private fun isLibraryPage() = when (page) {
        Page.SONGS, Page.ARTISTS, Page.ALBUMS, Page.GROUP -> true
        else -> false
    }

    private fun rowCount(): Int = if (isLibraryPage()) {
        libraryState.rows(when (page) {
            Page.SONGS, Page.GROUP -> songs().size
            else -> groups(page).size
        })
    } else when (page) {
        Page.ROOT -> rootItems().size
        Page.APPEARANCE -> appearanceRows.size
        Page.EFFECTS -> effectRows.size
        Page.MOTION -> motionRows.size
        Page.PLAYER -> playerRows.size
        else -> 0
    }

    private fun rowLabel(index: Int): String = if (isLibraryPage()) {
        when (libraryState) {
            LibraryState.PERMISSION -> permissionRows[index]
            LibraryState.LOADING -> ""
            LibraryState.ERROR -> retryRows[index]
            LibraryState.READY -> when (page) {
                Page.SONGS, Page.GROUP -> songs().getOrNull(index)?.title ?: retryRows[0]
                else -> groups(page).getOrNull(index)?.label ?: retryRows[0]
            }
        }
    } else when (page) {
        Page.ROOT -> rootItems()[index]
        Page.APPEARANCE -> appearanceRows[index]
        Page.EFFECTS -> effectRows[index]
        Page.MOTION -> motionRows[index]
        Page.PLAYER -> when {
            index != 1 -> playerRows[index]
            playback.wantsToPlay -> "Pausar"
            playback.connectionFailed -> "Reintentar"
            else -> playerRows[index]
        }
        else -> ""
    }

    private fun row() = selection.row(rowCount())

    fun libraryLoading() {
        if (libraryState != LibraryState.READY) updateLibrary(LibraryState.LOADING)
    }

    fun updateLibrary(state: LibraryState, result: MusicLibrary? = null) {
        libraryState = state
        library = result
        currentGroup = selection.groupKey?.let { key -> groups(selection.groupParent).find { it.key == key } }
        if (isLibraryPage()) {
            describeSelection()
            invalidate()
        }
    }

    fun refreshPlayback() {
        removeCallbacks(progressTick)
        if (!isAttachedToWindow || !isShown || windowVisibility != VISIBLE) return
        val next = music.playback()
        if (next != playback) {
            if (next.position / 1000 != playback.position / 1000) positionLabel = formatPlaybackTime(next.position)
            if (next.duration / 1000 != playback.duration / 1000) durationLabel = formatPlaybackTime(next.duration)
            val controlChanged = next.wantsToPlay != playback.wantsToPlay ||
                next.message != playback.message || next.title != playback.title
            playback = next
            if (controlChanged) describeSelection()
            invalidate()
        }
        if (playback.playing) postDelayed(progressTick, 500)
    }

    fun pausePlaybackUpdates() {
        removeCallbacks(progressTick)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) refreshPlayback() else removeCallbacks(progressTick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(progressTick)
        super.onDetachedFromWindow()
    }

    private fun title() = when (page) {
        Page.ROOT -> ""
        Page.APPEARANCE -> "Apariencia"
        Page.EFFECTS -> "Fondo animado"
        Page.MOTION -> "Rendimiento"
        Page.INFO -> "Información"
        Page.MEDIA -> rootItems().getOrElse(selection.rootRow(rootItems().size)) { "Video" }
        Page.SONGS -> "Canciones"
        Page.ARTISTS -> "Artistas"
        Page.ALBUMS -> "Álbumes"
        Page.GROUP -> currentGroup?.label ?: "Canciones"
        Page.PLAYER -> "En reproducción"
    }

    private fun describeSelection() {
        val selected = if (rowCount() == 0) null else rowLabel(row())
        val selectionLabel = when (page) {
            Page.ROOT -> "${Category.entries[category].title}, $selected"
            Page.INFO -> "Información. XMB Lite Player. Versión 0.2."
            Page.MEDIA -> "${title()}. Reproducción local pendiente."
            else -> selected?.let { "${title()}, $it" } ?: libraryMessage() ?: title()
        }
        contentDescription = if (!isPlayerPage() && hasCurrentTrack) {
            val status = if (playback.wantsToPlay) "En reproducción" else "En pausa"
            "$selectionLabel. $status, ${playback.title}"
        } else selectionLabel
    }

    fun saveSelection(): Bundle {
        val state = selection.snapshot()
        return Bundle().apply {
            putInt("category", state.category)
            putInt("page", state.page)
            putIntArray("categories", state.categories)
            putIntArray("rows", state.rows)
            putString("groupKey", state.groupKey)
            putInt("groupParent", state.groupParent)
            putInt("playerParent", state.playerParent)
        }
    }

    fun restoreSelection(state: Bundle) {
        selection.restore(MenuSelection.Snapshot(state.getInt("category"), state.getInt("page"),
            state.getIntArray("categories"), state.getIntArray("rows"), state.getString("groupKey"),
            state.getInt("groupParent"), state.getInt("playerParent")))
        lastCategory = category.toFloat()
        lastRow = row().toFloat()
        cardAnimationFrom = if (isPlayerPage()) 1f else 0f
        describeSelection()
    }

    fun isPlayerPage() = page == Page.PLAYER

    fun hasParent() = selection.parent() != null

    private fun progress(now: Long): Float {
        val t = ((now - animationStart) / 170f).coerceIn(0f, 1f)
        val remaining = 1f - t
        return 1f - remaining * remaining * remaining
    }

    private fun categoryPosition(progress: Float) = lastCategory + (category - lastCategory) * progress
    private fun rowPosition(progress: Float) = lastRow + (row() - lastRow) * progress

    private fun selectCategory(target: Int) {
        val next = target.coerceIn(Category.entries.indices)
        if (next == category) return
        val now = SystemClock.uptimeMillis()
        lastCategory = categoryPosition(progress(now))
        selection.category = next
        lastRow = row().toFloat()
        revealRows = true
        animationStart = now
        describeSelection()
        invalidate()
    }

    private fun selectRow(target: Int) {
        val count = rowCount()
        if (count == 0) return
        val next = target.coerceIn(0, count - 1)
        if (next == row()) return
        val now = SystemClock.uptimeMillis()
        val progress = progress(now)
        lastCategory = categoryPosition(progress)
        lastRow = rowPosition(progress)
        selection.selectRow(next)
        revealRows = false
        animationStart = now
        describeSelection()
        invalidate()
    }

    private fun showPage(next: Page) {
        val now = SystemClock.uptimeMillis()
        if (isPlayerPage() != (next == Page.PLAYER)) {
            cardAnimationFrom = cardExpansion(now)
            cardAnimationStart = now
        }
        selection.page = next
        onPageChanged(next)
        refreshPlayback()
        lastCategory = category.toFloat()
        lastRow = row().toFloat()
        revealRows = true
        animationStart = SystemClock.uptimeMillis()
        describeSelection()
        invalidate()
    }

    private fun confirm(): Boolean {
        if (isLibraryPage()) return confirmLibrary()
        when (page) {
            Page.ROOT -> {
                val next = when (Category.entries[category]) {
                    Category.SETTINGS -> if (row() == 0) Page.APPEARANCE else Page.INFO
                    Category.VIDEO -> Page.MEDIA
                    Category.MUSIC -> when (row()) {
                        0 -> Page.SONGS
                        1 -> Page.ALBUMS
                        2 -> Page.ARTISTS
                        else -> {
                            selection.enterPlayer()
                            Page.PLAYER
                        }
                    }
                }
                showPage(next)
            }
            Page.APPEARANCE -> {
                if (row() == 0) {
                    selection.selectOn(Page.EFFECTS, effectIndex)
                    showPage(Page.EFFECTS)
                } else {
                    selection.selectOn(Page.MOTION, motionIndex)
                    showPage(Page.MOTION)
                }
            }
            Page.EFFECTS -> {
                effectIndex = row()
                onEffect(WaveBackgroundView.Effect.entries[effectIndex])
                showPage(Page.APPEARANCE)
            }
            Page.MOTION -> {
                motionIndex = row()
                onMotion(WaveBackgroundView.Motion.entries[motionIndex])
                showPage(Page.APPEARANCE)
            }
            Page.PLAYER -> {
                val index = row()
                if (!transportEnabled(index)) return false
                music.transport(Transport.entries[index])
            }
            else -> return false
        }
        return true
    }

    private fun confirmLibrary(): Boolean {
        when (libraryState) {
            LibraryState.PERMISSION, LibraryState.ERROR -> music.requestLibrary()
            LibraryState.LOADING -> return false
            LibraryState.READY -> when (page) {
                Page.ARTISTS, Page.ALBUMS -> {
                    val group = groups(page).getOrNull(row())
                    if (group == null) {
                        music.requestLibrary()
                    } else {
                        selection.enterGroup(group.key)
                        currentGroup = group
                        showPage(Page.GROUP)
                    }
                }
                else -> {
                    val tracks = songs()
                    if (tracks.isEmpty()) {
                        music.requestLibrary()
                    } else {
                        music.playSongs(tracks, row())
                        selection.enterPlayer()
                        showPage(Page.PLAYER)
                    }
                }
            }
        }
        return true
    }

    fun goBack(): Boolean {
        val parent = selection.parent() ?: return false
        showPage(parent)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            if (page == Page.ROOT) selectCategory(category - 1)
            else if (page == Page.PLAYER) seekRelative(-5000)
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (page == Page.ROOT) selectCategory(category + 1)
            else if (page == Page.PLAYER) seekRelative(5000)
            true
        }
        KeyEvent.KEYCODE_DPAD_UP -> { selectRow(row() - 1); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { selectRow(row() + 1); true }
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_BUTTON_A -> {
            if (event.repeatCount == 0) performClick()
            true
        }
        KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> {
            if (event.repeatCount == 0) goBack()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }

    private fun seekRelative(delta: Long) {
        if (playback.canSeek && playback.duration > 0) {
            val position = music.playback().position
            music.seekTo((position + delta).coerceIn(0, playback.duration))
        }
    }

    override fun performClick(): Boolean {
        val activated = confirm()
        return super.performClick() || activated
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val progress = progress(now)
        val expansion = cardExpansion(now)
        if (page == Page.ROOT) {
            drawCategories(canvas, categoryPosition(progress))
        } else {
            if (isPlayerPage()) {
                text(canvas, title(), cardCenterX, titleY, 25f, white, true, Paint.Align.CENTER,
                    maxWidth = cardWidth)
            } else {
                text(canvas, title(), anchorX, titleY, 25f, white, true,
                    maxWidth = width - anchorX - 12f * density)
            }
        }

        when (page) {
            Page.INFO -> {
                text(canvas, "XMB Lite Player", anchorX, rowY, 20f, white, true)
                text(canvas, "Reproductor local · Versión 0.2", anchorX, rowY + 28f * density, 14f, muted)
            }
            Page.MEDIA -> {
                text(canvas, "Reproducción local pendiente", anchorX, rowY, 17f, muted)
            }
            Page.PLAYER -> Unit
            else -> {
                libraryMessage()?.let {
                    text(canvas, it, anchorX, libraryMessageY, 14f, muted,
                        maxWidth = rowRight - anchorX)
                }
                drawRows(canvas, rowPosition(progress), if (revealRows) progress else 1f)
            }
        }
        val showCard = isPlayerPage() || hasCurrentTrack
        if (showCard) {
            drawPlaybackCard(canvas, expansion)
        }
        val cardMoving = showCard && expansion != (if (isPlayerPage()) 1f else 0f)
        if (progress < 1f || cardMoving) postInvalidateOnAnimation()
    }

    private fun libraryMessage(): String? {
        if (!isLibraryPage()) return null
        return when (libraryState) {
            LibraryState.PERMISSION -> "Acceso necesario para ver tu música"
            LibraryState.LOADING -> "Cargando biblioteca…"
            LibraryState.ERROR -> "No se pudo leer la biblioteca"
            LibraryState.READY -> when (page) {
                Page.GROUP -> if (currentGroup == null) "La colección ya no está disponible" else null
                else -> if (library?.songs.isNullOrEmpty()) "No se encontró música local" else null
            }
        }
    }

    private fun transportEnabled(index: Int) = when (index) {
        0 -> playback.hasPrevious
        2 -> playback.hasNext
        else -> playback.canToggle || playback.connectionFailed
    }

    private fun cardExpansion(now: Long): Float {
        val target = if (isPlayerPage()) 1f else 0f
        val t = ((now - cardAnimationStart) / 300f).coerceIn(0f, 1f)
        val remaining = 1f - t
        return mix(target, cardAnimationFrom, remaining * remaining * remaining)
    }

    private fun mix(start: Float, end: Float, fraction: Float) =
        start + (end - start) * fraction

    private fun cardTop(expansion: Float) = mix(dockTop, titleYForPlayer + 14f * density, expansion)

    private val titleYForPlayer get() = maxOf(height * .28f, headerY + 32f * density)

    private fun drawPlaybackCard(canvas: Canvas, expansion: Float) {
        if (cardWidth <= 0f || cardHeight <= 0f) return
        val top = cardTop(expansion)
        val bottom = top + cardHeight
        val radius = 14f * density
        paint.color = Color.WHITE
        paint.alpha = 22
        canvas.drawRoundRect(cardLeft, top, cardRight, bottom, radius, radius, paint)
        paint.alpha = 44
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        val inset = density * .5f
        canvas.drawRoundRect(cardLeft + inset, top + inset, cardRight - inset, bottom - inset,
            radius, radius, paint)
        paint.style = Paint.Style.FILL

        val maxWidth = cardWidth - 28f * density
        playback.title?.let {
            text(canvas, it, cardCenterX, top + cardHeight * .19f,
                16f, white, true, Paint.Align.CENTER, maxWidth, centerVertically = true)
        }
        val note = playback.message ?: playback.artist
        note?.let {
            text(canvas, it, cardCenterX, top + cardHeight * .36f,
                12f, muted, align = Paint.Align.CENTER, maxWidth = maxWidth, centerVertically = true)
        }

        val barY = top + cardHeight * .54f
        if (railRight > railLeft) {
            val fraction = if (playback.duration > 0) {
                (playback.position.toDouble() / playback.duration).toFloat().coerceIn(0f, 1f)
            } else 0f
            val halfStroke = density
            paint.color = Color.WHITE
            paint.alpha = 68
            canvas.drawRect(railLeft, barY - halfStroke, railRight, barY + halfStroke, paint)
            paint.color = accent
            canvas.drawRect(railLeft, barY - halfStroke,
                mix(railLeft, railRight, fraction), barY + halfStroke, paint)
            text(canvas, positionLabel, railLeft - 8f * density, barY, 11f, muted,
                align = Paint.Align.RIGHT, maxWidth = 36f * density, centerVertically = true)
            text(canvas, durationLabel, railRight + 8f * density, barY, 11f, muted,
                maxWidth = 36f * density, centerVertically = true)
        }

        val controlY = top + cardHeight * .81f
        val iconSize = min(22f * density, cardHeight * .24f)
        val canSeek = playback.canSeek && playback.duration > 0
        for (slot in 0..4) {
            val x = cardCenterX + (slot - 2) * controlStep
            val enabled = if (slot == 0 || slot == 4) canSeek else transportEnabled(slot - 1)
            if (isPlayerPage() && slot == row() + 1) {
                paint.color = Color.WHITE
                paint.alpha = 32
                canvas.drawCircle(x, controlY, iconSize * .73f, paint)
            }
            val icon = when (slot) {
                0 -> XmbIcon.REWIND
                1 -> XmbIcon.PREVIOUS
                2 -> rowIconForToggle()
                3 -> XmbIcon.NEXT
                else -> XmbIcon.FAST_FORWARD
            }
            icons.draw(canvas, icon, x, controlY, iconSize, if (enabled) 255 else 114)
        }
    }

    private fun rowIconForToggle() = when {
        playback.connectionFailed -> XmbIcon.RETRY
        playback.wantsToPlay -> XmbIcon.PAUSE
        else -> XmbIcon.PLAY
    }

    private fun drawCategories(canvas: Canvas, position: Float) {
        for (entry in Category.entries) {
            val x = anchorX + (entry.ordinal - position) * categoryStep
            val focus = 1f - abs(entry.ordinal - position).coerceAtMost(1f)
            icons.draw(canvas, entry.icon, x, categoryY, (36f + 12f * focus) * density,
                (204 + 51 * focus).toInt())
            val selected = entry.ordinal == category
            text(canvas, entry.title, x, categoryY + 43f * density, 13f,
                if (selected) white else muted, selected, Paint.Align.CENTER)
        }
    }

    private fun rowIcon(index: Int): XmbIcon {
        if (isLibraryPage()) {
            return when (libraryState) {
                LibraryState.PERMISSION -> XmbIcon.ACCESS
                LibraryState.ERROR -> XmbIcon.RETRY
                else -> when (page) {
                    Page.ALBUMS -> if (groups(page).isEmpty()) XmbIcon.RETRY else XmbIcon.ALBUMS
                    Page.ARTISTS -> if (groups(page).isEmpty()) XmbIcon.RETRY else XmbIcon.ARTISTS
                    else -> if (songs().isEmpty()) XmbIcon.RETRY else XmbIcon.SONGS
                }
            }
        }
        return when (page) {
            Page.ROOT -> Category.entries[category].rowIcon(index)
            Page.APPEARANCE -> if (index == 0) XmbIcon.BACKGROUND else XmbIcon.PERFORMANCE
            Page.EFFECTS -> XmbIcon.BACKGROUND
            Page.MOTION -> XmbIcon.PERFORMANCE
            else -> error("There are no rows on $page")
        }
    }

    private fun drawRows(canvas: Canvas, position: Float, reveal: Float) {
        val offset = (1f - reveal) * 12f * density
        canvas.save()
        canvas.clipRect(0f, listTop, width.toFloat(), listBottom)
        val origin = rowY + offset
        val first = floor(position + (listTop - origin) / rowStep).toInt().coerceAtLeast(0)
        val last = ceil(position + (listBottom - origin) / rowStep).toInt().coerceAtMost(rowCount() - 1)
        val selectedRow = row()
        for (index in first..last) {
            val y = origin + (index - position) * rowStep
            val selected = index == selectedRow
            val focus = 1f - abs(index - position).coerceAtMost(1f)
            if (focus > 0f) {
                val halfHeight = rowStep * .43f
                val radius = min(15f * density, halfHeight)
                paint.color = Color.WHITE
                paint.alpha = (22 * focus).toInt()
                canvas.drawRoundRect(rowLeft, y - halfHeight, rowRight, y + halfHeight, radius, radius, paint)

                val border = density
                paint.alpha = (44 * focus).toInt()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = border
                val inset = border * .5f
                canvas.drawRoundRect(rowLeft + inset, y - halfHeight + inset, rowRight - inset,
                    y + halfHeight - inset, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }
            val applied = (page == Page.EFFECTS && index == effectIndex) ||
                (page == Page.MOTION && index == motionIndex)
            val iconAlpha = (202 + 53 * focus).toInt()
            val textX = rowLeft + 52f * density
            val iconSize = min((23f + 5f * focus) * density, rowStep * .66f)
            icons.draw(canvas, rowIcon(index), rowLeft + 25f * density, y, iconSize, iconAlpha)
            text(canvas, rowLabel(index), textX, y,
                if (selected) 20f else 17f, if (selected) white else muted, selected,
                maxWidth = rowRight - textX - (if (applied) 42f else 16f) * density,
                centerVertically = true)
            if (applied) {
                icons.draw(canvas, XmbIcon.APPLIED, rowRight - 24f * density, y, 18f * density, 235)
            }
        }
        canvas.restore()
    }

    private fun text(
        canvas: Canvas, label: String, x: Float, y: Float, size: Float, color: Int,
        bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT,
        maxWidth: Float = Float.POSITIVE_INFINITY, centerVertically: Boolean = false
    ) {
        paint.color = color
        paint.textSize = size * density
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textAlign = align
        if (maxWidth <= 0f) return
        val visible = if (maxWidth.isFinite()) fittedLabel(label, maxWidth, size, bold) else label
        val baseline = if (centerVertically) y - (paint.ascent() + paint.descent()) * .5f else y
        canvas.drawText(visible, 0, visible.length, x, baseline, paint)
    }

    private fun fittedLabel(label: String, width: Float, size: Float, bold: Boolean): CharSequence {
        val cached = fittedLabels.get(label)
        if (cached != null && cached.width == width && cached.size == size && cached.bold == bold) {
            return cached.text
        }
        val fitted = TextUtils.ellipsize(label, paint, width, TextUtils.TruncateAt.END)
        fittedLabels.put(label, FittedText(width, size, bold, fitted))
        return fitted
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                trackingTouch = true
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!trackingTouch) return true
                trackingTouch = false
                val dx = event.x - downX
                val dy = event.y - downY
                when {
                    abs(dx) > touchSlop && abs(dx) > abs(dy) -> {
                        // Root: swipe left advances categories, swipe right goes to the previous one.
                        // Subpages: swipe left returns to the parent; swipe right is left unused.
                        if (page == Page.ROOT) {
                            selectCategory(category + if (dx < 0) 1 else -1)
                        } else if (dx < 0) {
                            goBack()
                        }
                    }
                    abs(dy) > touchSlop -> selectRow(row() + if (dy < 0) 1 else -1)
                    else -> tap(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                trackingTouch = false
                return true
            }
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val progress = progress(now)
        if (isPlayerPage() || hasCurrentTrack) {
            val top = cardTop(cardExpansion(now))
            if (x in cardLeft..cardRight && y in top..(top + cardHeight)) {
                tapPlaybackCard(x, (y - top) / cardHeight)
                return
            }
        }
        if (isPlayerPage()) return
        if (page == Page.ROOT && abs(y - (categoryY + 8f * density)) <= 37f * density) {
            val index = ((x - anchorX) / categoryStep + categoryPosition(progress)).roundToInt()
            if (index in Category.entries.indices) selectCategory(index)
            return
        }
        if (x !in rowLeft..rowRight || y !in listTop..listBottom) return
        val offset = if (revealRows) (1f - progress) * 12f * density else 0f
        val index = ((y - rowY - offset) / rowStep + rowPosition(progress)).roundToInt()
        if (index !in 0 until rowCount()) return
        if (index == row()) performClick() else selectRow(index)
    }

    private fun tapPlaybackCard(x: Float, relativeY: Float) {
        if (relativeY >= .66f && controlStep > 0f) {
            val slot = ((x - cardCenterX) / controlStep + 2f).roundToInt()
            when (slot) {
                0 -> seekRelative(-5000)
                4 -> seekRelative(5000)
                in 1..3 -> if (transportEnabled(slot - 1)) {
                    if (isPlayerPage()) selectRow(slot - 1)
                    music.transport(Transport.entries[slot - 1])
                }
            }
            return
        }
        if (relativeY in 0.44f..0.64f && x in railLeft..railRight && railRight > railLeft &&
            playback.canSeek && playback.duration > 0
        ) {
            music.seekTo((playback.duration * ((x - railLeft) / (railRight - railLeft))).toLong())
            return
        }
        if (!isPlayerPage()) {
            selection.enterPlayer()
            showPage(Page.PLAYER)
        }
    }
}
