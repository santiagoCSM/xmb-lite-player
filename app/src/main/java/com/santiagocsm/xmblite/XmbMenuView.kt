package com.santiagocsm.xmblite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal class XmbMenuView(
    context: Context,
    private var effect: WaveBackgroundView.Effect,
    private var motion: WaveBackgroundView.Motion,
    private val onEffect: (WaveBackgroundView.Effect) -> Unit,
    private val onMotion: (WaveBackgroundView.Motion) -> Unit
) : View(context) {
    private enum class Category(val title: String, val symbol: String, vararg val items: String) {
        MUSIC("Música", "♫", "Biblioteca", "Álbumes", "Artistas"),
        VIDEO("Video", "▶", "Videos locales", "Carpetas"),
        SETTINGS("Ajustes", "⚙", "Apariencia", "Información")
    }

    private enum class Page {
        ROOT, APPEARANCE, EFFECTS, MOTION, INFO, MEDIA
    }

    private val appearanceItems = arrayOf("Fondo animado", "Rendimiento")
    private val effectItems = WaveBackgroundView.Effect.entries.map { it.label }.toTypedArray()
    private val motionItems = WaveBackgroundView.Motion.entries.map { it.label }.toTypedArray()
    private val emptyItems = emptyArray<String>()
    private val categorySelections = IntArray(Category.entries.size)
    private val pageSelections = IntArray(Page.entries.size)
    private var category = 0
    private var page = Page.ROOT
    private var effectIndex = effect.ordinal
    private var motionIndex = motion.ordinal

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    private val rowY get() = height * 0.53f
    private val rowStep get() = min(54f * density, height * 0.135f)
    private val rowLeft get() = anchorX - 24f * density
    private val rowRight get() = min(width - 12f * density, rowLeft + 300f * density)
    private val listTop get() = if (page == Page.ROOT) categoryY + 35f * density else height * 0.30f
    private val listBottom get() = height - 18f * density
    private val titleX get() = 20f * density
    private val headerY get() = min(33f * density, height * 0.12f)

    init {
        isFocusableInTouchMode = true
        isClickable = true
        describeSelection()
    }

    private fun rootItems() = Category.entries[category].items

    private fun items(): Array<out String> = when (page) {
        Page.ROOT -> rootItems()
        Page.APPEARANCE -> appearanceItems
        Page.EFFECTS -> effectItems
        Page.MOTION -> motionItems
        Page.INFO, Page.MEDIA -> emptyItems
    }

    private fun row() = if (page == Page.ROOT) categorySelections[category] else pageSelections[page.ordinal]

    private fun title() = when (page) {
        Page.ROOT -> "XMB / LITE"
        Page.APPEARANCE -> "Apariencia"
        Page.EFFECTS -> "Fondo animado"
        Page.MOTION -> "Rendimiento"
        Page.INFO -> "Información"
        Page.MEDIA -> rootItems()[categorySelections[category]]
    }

    private fun describeSelection() {
        val selected = items().getOrNull(row())
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
    }

    fun restoreSelection(state: Bundle) {
        category = state.getInt("category").coerceIn(Category.entries.indices)
        page = Page.entries.getOrElse(state.getInt("page")) { Page.ROOT }
        state.getIntArray("categories")?.let { it.copyInto(categorySelections, endIndex = min(it.size, categorySelections.size)) }
        state.getIntArray("rows")?.let { it.copyInto(pageSelections, endIndex = min(it.size, pageSelections.size)) }
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
        if (items().isEmpty()) return
        val next = target.coerceIn(items().indices)
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
        lastCategory = category.toFloat()
        lastRow = row().toFloat()
        revealRows = true
        animationStart = SystemClock.uptimeMillis()
        describeSelection()
        invalidate()
    }

    private fun confirm(): Boolean {
        when (page) {
            Page.ROOT -> {
                val next = if (Category.entries[category] == Category.SETTINGS) {
                    if (row() == 0) Page.APPEARANCE else Page.INFO
                } else Page.MEDIA
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
            Page.INFO, Page.MEDIA -> return false
        }
        return true
    }

    fun goBack(): Boolean {
        val parent = when (page) {
            Page.ROOT -> return false
            Page.EFFECTS, Page.MOTION -> Page.APPEARANCE
            else -> Page.ROOT
        }
        showPage(parent)
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            if (page == Page.ROOT) selectCategory(category - 1)
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (page == Page.ROOT) selectCategory(category + 1)
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
            text(canvas, title(), anchorX, height * .28f, 22f, white, true)
        }

        when (page) {
            Page.INFO -> {
                text(canvas, "XMB Lite Player", anchorX, rowY, 17f, white, true)
                text(canvas, "Reproductor local · Prototipo 0.1", anchorX, rowY + 25f * density, 12f, muted)
            }
            Page.MEDIA -> {
                text(canvas, "Reproducción local pendiente", anchorX, rowY, 15f, muted)
            }
            else -> drawRows(canvas, rowPosition(progress), if (revealRows) progress else 1f)
        }
        if (progress < 1f) postInvalidateOnAnimation()
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
        items().forEachIndexed { index, label ->
            val y = rowY + (index - position) * rowStep + offset
            val selected = index == row()
            if (selected) {
                paint.color = highlight
                canvas.drawRoundRect(rowLeft, y - rowStep * .40f, rowRight, y + rowStep * .40f,
                    10f * density, 10f * density, paint)
                paint.color = accent
                canvas.drawCircle(anchorX - 10f * density, y, 3f * density, paint)
            }
            val applied = (page == Page.EFFECTS && index == effectIndex) ||
                (page == Page.MOTION && index == motionIndex)
            text(canvas, label, anchorX, y + 5f * density, if (selected) 17f else 14f,
                if (selected) white else muted, selected)
            if (applied) {
                text(canvas, "✓", rowRight - 12f * density, y + 5f * density,
                    15f, accent, true, Paint.Align.RIGHT)
            }
        }
        canvas.restore()
    }

    private fun text(
        canvas: Canvas, label: String, x: Float, y: Float, size: Float, color: Int,
        bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT
    ) {
        paint.color = color
        paint.textSize = size * density
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.textAlign = align
        canvas.drawText(label, x, y, paint)
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
        if (x !in rowLeft..rowRight || y !in listTop..listBottom) return
        val offset = if (revealRows) (1f - progress) * 12f * density else 0f
        val index = ((y - rowY - offset) / rowStep + rowPosition(progress)).roundToInt()
        if (index !in items().indices) return
        if (index == row()) performClick() else selectRow(index)
    }
}
