package com.santiagocsm.xmblite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
    private val onEffect: (WaveBackgroundView.Effect) -> Unit,
    private val onMotion: (WaveBackgroundView.Motion) -> Unit
) : View(context) {
    private enum class Category(val title: String, val symbol: String, vararg val items: String) {
        MUSIC("Música", "♫", "Canciones", "Álbumes", "Artistas", "En reproducción"),
        VIDEO("Video", "▶", "Videos locales", "Carpetas"),
        SETTINGS("Ajustes", "⚙", "Apariencia", "Información")
    }

    private enum class Page {
        ROOT, APPEARANCE, EFFECTS, MOTION, INFO, MEDIA, SONGS, ALBUMS, ARTISTS, GROUP, PLAYER
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
    private var groupKey: String? = null
    private var groupParent = Page.ALBUMS
    private var playerParent = Page.ROOT
    private var playback = Playback()
    private var positionLabel = "0:00"
    private var durationLabel = "0:00"
    private val progressTick = Runnable { refreshPlayback() }
    private val categorySelections = IntArray(Category.entries.size)
    private val pageSelections = IntArray(Page.entries.size)
    private var category = 0
    private var page = Page.ROOT
    private var effectIndex = effect.ordinal
    private var motionIndex = motion.ordinal

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val white = Color.rgb(239, 248, 250)
    private val muted = Color.rgb(184, 211, 224)
    private val accent = Color.rgb(110, 225, 215)
    private val highlight = 0x40256375
    private var downX = 0f
    private var downY = 0f
    private var lastCategory = 0f
    private var lastRow = 0f
    private var animationStart = 0L
    private var revealRows = false

    private val anchorX get() = width * 0.25f
    private val categoryY get() = height * 0.30f
    private val categoryStep get() = min(width * 0.22f, 104f * density)
    private val rowY get() = height * if (page == Page.PLAYER) 0.69f else 0.53f
    private val rowStep get() = min(54f * density, height * 0.135f)
    private val rowLeft get() = anchorX - 24f * density
    private val rowRight get() = min(width - 12f * density, rowLeft + 300f * density)
    private val listTop get() = when (page) {
        Page.ROOT -> categoryY + 35f * density
        Page.PLAYER -> height * 0.52f
        else -> height * 0.30f
    }
    private val progressY get() = height * 0.445f
    private val listBottom get() = height - 18f * density
    private val titleX get() = 20f * density
    private val headerY get() = min(33f * density, height * 0.12f)

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
        when (libraryState) {
            LibraryState.PERMISSION -> permissionRows.size
            LibraryState.LOADING -> 0
            LibraryState.ERROR -> retryRows.size
            LibraryState.READY -> when (page) {
                Page.SONGS, Page.GROUP -> songs().size.coerceAtLeast(1)
                else -> groups(page).size.coerceAtLeast(1)
            }
        }
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
            playback.message?.startsWith("Error de conexión") == true -> "Reintentar"
            else -> playerRows[index]
        }
        else -> ""
    }

    private fun row(): Int {
        val selected = if (page == Page.ROOT) categorySelections[category] else pageSelections[page.ordinal]
        return selected.coerceIn(0, (rowCount() - 1).coerceAtLeast(0))
    }

    fun libraryLoading() {
        if (libraryState != LibraryState.READY) updateLibrary(LibraryState.LOADING)
    }

    fun updateLibrary(state: LibraryState, result: MusicLibrary? = null) {
        libraryState = state
        library = result
        currentGroup = groupKey?.let { key -> groups(groupParent).find { it.key == key } }
        if (isLibraryPage()) {
            describeSelection()
            invalidate()
        }
    }

    fun refreshPlayback() {
        removeCallbacks(progressTick)
        if (!isAttachedToWindow || !isShown || windowVisibility != VISIBLE || page != Page.PLAYER) return
        val next = music.playback()
        if (next != playback) {
            if (next.position / 1000 != playback.position / 1000) positionLabel = timeLabel(next.position)
            if (next.duration / 1000 != playback.duration / 1000) durationLabel = timeLabel(next.duration)
            val controlChanged = next.wantsToPlay != playback.wantsToPlay ||
                next.message != playback.message || next.title != playback.title
            playback = next
            if (controlChanged) describeSelection()
            invalidate()
        }
        if (playback.playing) postDelayed(progressTick, 500)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) refreshPlayback() else removeCallbacks(progressTick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(progressTick)
        super.onDetachedFromWindow()
    }

    private fun timeLabel(millis: Long): String {
        val seconds = millis / 1000
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    private fun title() = when (page) {
        Page.ROOT -> "XMB / LITE"
        Page.APPEARANCE -> "Apariencia"
        Page.EFFECTS -> "Fondo animado"
        Page.MOTION -> "Rendimiento"
        Page.INFO -> "Información"
        Page.MEDIA -> rootItems().getOrElse(categorySelections[category]) { "Video" }
        Page.SONGS -> "Canciones"
        Page.ARTISTS -> "Artistas"
        Page.ALBUMS -> "Álbumes"
        Page.GROUP -> currentGroup?.label ?: "Canciones"
        Page.PLAYER -> "En reproducción"
    }

    private fun describeSelection() {
        val selected = if (rowCount() == 0) null else rowLabel(row())
        contentDescription = when (page) {
            Page.ROOT -> "${Category.entries[category].title}, $selected"
            Page.INFO -> "Información. XMB Lite Player. Prototipo 0.1."
            Page.MEDIA -> "${title()}. Reproducción local pendiente."
            else -> "${title()}, $selected"
        }
    }

    fun saveSelection() = Bundle().apply {
        putInt("category", category)
        putInt("page", page.ordinal)
        putIntArray("categories", categorySelections)
        putIntArray("rows", pageSelections)
        putString("groupKey", groupKey)
        putInt("groupParent", groupParent.ordinal)
        putInt("playerParent", playerParent.ordinal)
    }

    fun restoreSelection(state: Bundle) {
        category = state.getInt("category").coerceIn(Category.entries.indices)
        page = Page.entries.getOrElse(state.getInt("page")) { Page.ROOT }
        state.getIntArray("categories")?.let { it.copyInto(categorySelections, endIndex = min(it.size, categorySelections.size)) }
        state.getIntArray("rows")?.let { it.copyInto(pageSelections, endIndex = min(it.size, pageSelections.size)) }
        groupKey = state.getString("groupKey")
        groupParent = if (state.getInt("groupParent") == Page.ARTISTS.ordinal) Page.ARTISTS else Page.ALBUMS
        playerParent = Page.entries.getOrNull(state.getInt("playerParent"))
            ?.takeIf { it == Page.SONGS || it == Page.GROUP } ?: Page.ROOT
        lastCategory = category.toFloat()
        lastRow = row().toFloat()
        describeSelection()
    }

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
        category = next
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
        if (page == Page.ROOT) categorySelections[category] = next else pageSelections[page.ordinal] = next
        revealRows = false
        animationStart = now
        describeSelection()
        invalidate()
    }

    private fun showPage(next: Page) {
        page = next
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
                            playerParent = Page.ROOT
                            pageSelections[Page.PLAYER.ordinal] = 1
                            Page.PLAYER
                        }
                    }
                }
                showPage(next)
            }
            Page.APPEARANCE -> {
                if (row() == 0) {
                    pageSelections[Page.EFFECTS.ordinal] = effectIndex
                    showPage(Page.EFFECTS)
                } else {
                    pageSelections[Page.MOTION.ordinal] = motionIndex
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
            Page.PLAYER -> music.transport(Transport.entries[row()])
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
                        if (groupKey != group.key || groupParent != page) pageSelections[Page.GROUP.ordinal] = 0
                        currentGroup = group
                        groupKey = group.key
                        groupParent = page
                        showPage(Page.GROUP)
                    }
                }
                else -> {
                    val tracks = songs()
                    if (tracks.isEmpty()) {
                        music.requestLibrary()
                    } else if (music.playSongs(tracks, row())) {
                        playerParent = page
                        pageSelections[Page.PLAYER.ordinal] = 1
                        showPage(Page.PLAYER)
                    }
                }
            }
        }
        return true
    }

    fun goBack(): Boolean {
        val parent = when (page) {
            Page.ROOT -> return false
            Page.EFFECTS, Page.MOTION -> Page.APPEARANCE
            Page.PLAYER -> playerParent
            Page.GROUP -> groupParent
            else -> Page.ROOT
        }
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
        val progress = progress(SystemClock.uptimeMillis())
        if (page == Page.ROOT) {
            text(canvas, "XMB / LITE", titleX, headerY, 13f, accent, true)
            drawCategories(canvas, categoryPosition(progress))
        } else {
            text(canvas, "‹ Atrás", titleX, headerY, 13f, accent, true)
            text(canvas, title(), anchorX, height * .28f, 22f, white, true, maxWidth = width - anchorX - 12f * density)
        }

        when (page) {
            Page.INFO -> {
                text(canvas, "XMB Lite Player", anchorX, rowY, 17f, white, true)
                text(canvas, "Reproductor local · Prototipo 0.1", anchorX, rowY + 25f * density, 12f, muted)
            }
            Page.MEDIA -> {
                text(canvas, "Reproducción local pendiente", anchorX, rowY, 15f, muted)
            }
            Page.PLAYER -> {
                drawPlayer(canvas)
                drawRows(canvas, rowPosition(progress), if (revealRows) progress else 1f)
            }
            else -> {
                libraryMessage()?.let {
                    text(canvas, it, anchorX, height * 0.37f, 12f, muted,
                        maxWidth = rowRight - anchorX)
                }
                drawRows(canvas, rowPosition(progress), if (revealRows) progress else 1f)
            }
        }
        if (progress < 1f) postInvalidateOnAnimation()
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

    private fun drawPlayer(canvas: Canvas) {
        val width = (rowRight - anchorX).coerceAtLeast(0f)
        val note = playback.message ?: playback.artist
        playback.title?.let {
            text(canvas, it, anchorX, height * 0.344f, 16f, white, true, maxWidth = width)
        }
        note?.let {
            text(canvas, it, anchorX, height * 0.394f, 12f, muted, maxWidth = width)
        }
        val fraction = if (playback.duration > 0) {
            (playback.position.toDouble() / playback.duration).toFloat().coerceIn(0f, 1f)
        } else 0f
        val stroke = 2f * density
        paint.color = muted
        canvas.drawRect(anchorX, progressY, rowRight, progressY + stroke, paint)
        paint.color = accent
        canvas.drawRect(anchorX, progressY, anchorX + width * fraction, progressY + stroke, paint)
        text(canvas, positionLabel, anchorX, height * 0.505f, 11f, muted)
        text(canvas, durationLabel, rowRight, height * 0.505f, 11f, muted, align = Paint.Align.RIGHT)
    }

    private fun drawCategories(canvas: Canvas, position: Float) {
        for (entry in Category.entries) {
            val x = anchorX + (entry.ordinal - position) * categoryStep
            val distance = abs(entry.ordinal - position).coerceAtMost(1f)
            val selected = entry.ordinal == category
            paint.color = if (selected) 0x50418D9E else 0x30256375
            canvas.drawCircle(x, categoryY, (23f - 5f * distance) * density, paint)
            text(canvas, entry.symbol, x, categoryY + 8f * density,
                23f - 4f * distance, if (selected) white else muted, align = Paint.Align.CENTER)
            text(canvas, entry.title, x, categoryY + 38f * density, 11f,
                if (selected) white else muted, selected, Paint.Align.CENTER)
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
            if (selected) {
                paint.color = highlight
                canvas.drawRoundRect(rowLeft, y - rowStep * .40f, rowRight, y + rowStep * .40f,
                    10f * density, 10f * density, paint)
                paint.color = accent
                canvas.drawCircle(anchorX - 10f * density, y, 3f * density, paint)
            }
            val applied = (page == Page.EFFECTS && index == effectIndex) ||
                (page == Page.MOTION && index == motionIndex)
            val enabled = page != Page.PLAYER || when (index) {
                0 -> playback.hasPrevious
                2 -> playback.hasNext
                else -> playback.title != null || playback.message?.startsWith("Error de conexión") == true
            }
            text(canvas, rowLabel(index), anchorX, y + 5f * density, if (selected) 17f else 14f,
                if (selected && enabled) white else muted, selected,
                maxWidth = rowRight - anchorX - (if (applied) 30f else 8f) * density)
            if (applied) {
                text(canvas, "✓", rowRight - 12f * density, y + 5f * density,
                    15f, accent, true, Paint.Align.RIGHT)
            }
        }
        canvas.restore()
    }

    private fun text(
        canvas: Canvas, label: String, x: Float, y: Float, size: Float, color: Int,
        bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT, maxWidth: Float = Float.POSITIVE_INFINITY
    ) {
        paint.color = color
        paint.textSize = size * density
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textAlign = align
        if (maxWidth <= 0f) return
        val visible = if (maxWidth.isFinite()) TextUtils.ellipsize(label, paint, maxWidth, TextUtils.TruncateAt.END) else label
        canvas.drawText(visible, 0, visible.length, x, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                when {
                    abs(dx) > touchSlop && abs(dx) > abs(dy) -> {
                        if (page == Page.ROOT) selectCategory(category + if (dx < 0) 1 else -1)
                    }
                    abs(dy) > touchSlop -> selectRow(row() + if (dy < 0) 1 else -1)
                    else -> tap(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        val progress = progress(SystemClock.uptimeMillis())
        if (page != Page.ROOT && x < 100f * density && y < headerY + 16f * density) {
            goBack()
            return
        }
        if (page == Page.ROOT && abs(y - (categoryY + 8f * density)) <= 37f * density) {
            val index = ((x - anchorX) / categoryStep + categoryPosition(progress)).roundToInt()
            if (index in Category.entries.indices) selectCategory(index)
            return
        }
        if (page == Page.PLAYER && playback.canSeek && playback.duration > 0 &&
            x in anchorX..rowRight && abs(y - progressY) <= min(16f * density, height * 0.06f)
        ) {
            music.seekTo((playback.duration * ((x - anchorX) / (rowRight - anchorX))).toLong())
            return
        }
        if (x !in rowLeft..rowRight || y !in listTop..listBottom) return
        val offset = if (revealRows) (1f - progress) * 12f * density else 0f
        val index = ((y - rowY - offset) / rowStep + rowPosition(progress)).roundToInt()
        if (index !in 0 until rowCount()) return
        if (page == Page.PLAYER) {
            selectRow(index)
            performClick()
        } else if (index == row()) performClick() else selectRow(index)
    }
}
