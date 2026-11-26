package com.santiagocsm.xmblite

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.min

/** Primera pantalla XMB. Solo navegación; reproducción multimedia en el siguiente hito. */
class MainActivity : Activity() {
    private lateinit var menu: MenuView
    private lateinit var waveBackground: WaveBackgroundView
    private val appearance by lazy { getSharedPreferences("appearance", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        menu = MenuView()
        waveBackground = WaveBackgroundView(this).apply {
            val savedEffect = appearance.getString("background", null)
            effect = WaveBackgroundView.Effect.entries.firstOrNull { it.id == savedEffect }
                ?: WaveBackgroundView.Effect.BLUE
        }
        setContentView(FrameLayout(this).apply {
            addView(waveBackground)
            addView(menu)
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { menu.changeCategory(-1); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { menu.changeCategory(1); true }
        KeyEvent.KEYCODE_DPAD_UP -> { menu.changeItem(-1); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { menu.changeItem(1); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
            menu.activateAppearance() || super.onKeyDown(keyCode, event)
        else -> super.onKeyDown(keyCode, event)
    }

    private fun showAppearance() {
        val effects = WaveBackgroundView.Effect.entries
        AlertDialog.Builder(this)
            .setTitle("Fondo animado")
            .setSingleChoiceItems(effects.map { it.label }.toTypedArray(), waveBackground.effect.ordinal) {
                dialog, index ->
                val effect = effects[index]
                waveBackground.effect = effect
                appearance.edit().putString("background", effect.id).apply()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private inner class MenuView : View(this) {
        private val categories = arrayOf("MÚSICA", "VIDEO", "AJUSTES")
        private val items = arrayOf(
            arrayOf("Biblioteca", "Álbumes", "Artistas"),
            arrayOf("Videos locales", "Carpetas"),
            arrayOf("Apariencia", "Información")
        )
        private val selection = intArrayOf(0, 0, 0)
        private var category = 0
        private var animatedCategory = 0f
        private var animatedItem = 0f
        private var downX = 0f
        private var downY = 0f
        private val scale = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val white = Color.rgb(239, 248, 250)
        private val secondary = Color.rgb(159, 186, 200)
        private val accent = Color.rgb(110, 225, 215)
        private val appearanceSelected get() = category == 2 && selection[category] == 0

        init {
            isFocusable = true
            isClickable = true
            contentDescription = "Menú XMB. Desliza horizontalmente para categorías y verticalmente para opciones."
        }

        fun changeCategory(delta: Int) {
            category = (category + delta).coerceIn(0, categories.lastIndex)
            invalidate()
        }

        fun changeItem(delta: Int) {
            selection[category] = (selection[category] + delta)
                .coerceIn(0, items[category].lastIndex)
            invalidate()
        }

        fun activateAppearance(): Boolean {
            if (!appearanceSelected) return false
            showAppearance()
            return true
        }

        private fun text(canvas: Canvas, value: String, x: Float, y: Float,
                         size: Float, color: Int, bold: Boolean = false) {
            paint.reset()
            paint.isAntiAlias = true
            paint.color = color
            paint.textSize = size * scale
            paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD
                else android.graphics.Typeface.DEFAULT
            canvas.drawText(value, x, y, paint)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            animatedCategory += (category - animatedCategory) * 0.21f
            animatedItem += (selection[category] - animatedItem) * 0.24f
            val centerX = w * .26f
            val topY = h * .35f
            val spacing = min(w * .38f, 150f * scale)

            text(canvas, "XMB / LITE", 24f * scale, 43f * scale, 12f, accent, true)
            text(canvas, "REPRODUCTOR LOCAL · PROTOTIPO 0.1",
                24f * scale, 64f * scale, 8f, secondary)

            categories.forEachIndexed { index, title ->
                val x = centerX + (index - animatedCategory) * spacing
                if (x < -70f * scale || x > w + 70f * scale) return@forEachIndexed
                val selected = index == category
                paint.reset()
                paint.isAntiAlias = true
                paint.color = accent
                paint.alpha = if (selected) 56 else 18
                canvas.drawCircle(x, topY, (if (selected) 29 else 23) * scale, paint)
                text(canvas, when (index) {
                    0 -> "♫"
                    1 -> "▶"
                    else -> "⚙"
                }, x - 12f * scale, topY + 11f * scale,
                    29f, if (selected) white else secondary)
                text(canvas, title, x - 26f * scale, topY + 54f * scale,
                    if (selected) 12f else 10f, if (selected) white else secondary, selected)
            }

            val startY = topY + 116f * scale
            items[category].forEachIndexed { index, label ->
                val y = startY + (index - animatedItem) * 44f * scale
                if (y < topY + 70f * scale || y > h - 48f * scale) return@forEachIndexed
                val active = index == selection[category]
                if (active) {
                    paint.reset()
                    paint.color = accent
                    paint.alpha = 25
                    canvas.drawRoundRect(22f * scale, y - 25f * scale,
                        min(w - 18f * scale, 260f * scale), y + 13f * scale,
                        8f * scale, 8f * scale, paint)
                }
                text(canvas, label, 34f * scale, y, if (active) 18f else 15f,
                    if (active) white else secondary, active)
            }

            text(canvas, "← → CATEGORÍA        ↑ ↓ OPCIÓN",
                22f * scale, h - 24f * scale, 9f, secondary)

            if (abs(category - animatedCategory) > .005f ||
                abs(selection[category] - animatedItem) > .005f) postInvalidateOnAnimation()
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
                    val threshold = 38f * scale
                    if (abs(dx) > threshold && abs(dx) > abs(dy)) {
                        changeCategory(if (dx < 0) 1 else -1)
                    } else if (abs(dy) > threshold) {
                        changeItem(if (dy < 0) 1 else -1)
                    } else if (appearanceSelected && hitAppearance(event.x, event.y)) {
                        activateAppearance()
                    } else if (event.y < height * .5f) {
                        if (event.x < width * .17f) changeCategory(-1)
                        if (event.x > width * .65f) changeCategory(1)
                    } else if (event.y < height * .9f) {
                        changeItem(if (event.y > height * .66f) 1 else -1)
                    }
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }

        private fun hitAppearance(x: Float, y: Float): Boolean {
            val top = height * .35f
            val baseline = top + (116f - animatedItem * 44f) * scale
            return baseline >= top + 70f * scale && baseline <= height - 48f * scale &&
                x >= 22f * scale && x <= min(width - 18f * scale, 260f * scale) &&
                y >= baseline - 25f * scale && y <= baseline + 13f * scale
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
