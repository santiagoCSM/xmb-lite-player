package com.santiagocsm.xmblite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal class WaveBackgroundView(context: Context) : View(context) {
    enum class Effect(val id: String, val label: String) {
        BLUE("blue", "Ondas azules"),
        VIOLET("violet", "Onda violeta")
    }

    enum class Motion(val id: String, val label: String) {
        FLUID("fluid", "Fluido"),
        SAVER("saver", "Ahorro · aprox. 30 FPS"),
        STILL("still", "Estático")
    }

    var effect = Effect.BLUE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var motion = Motion.FLUID
        set(value) {
            if (field == value) return
            field = value
            updateAnimation()
            invalidate()
        }

    private val backdrop = Paint()
    private val rear = Ribbon(
        period = 5.3, phaseOffset = Math.PI, cyclesAcrossWidth = 0.40,
        baseline = 0.667f, amplitude = 0.044f, depth = 0.88f, highlight = 0x3A7DD6FF
    )
    private val front = Ribbon(
        period = 2.76, phaseOffset = 0.31, cyclesAcrossWidth = 0.71,
        baseline = 0.652f, amplitude = 0.024f, depth = 1f, highlight = 0x5A9CEFFF
    )
    private val violet = VioletWave()
    private var visible = false
    private var activeSince = 0L
    private var elapsed = 0L
    private val choreographer = Choreographer.getInstance()
    private val frame = Choreographer.FrameCallback {
        invalidate()
        scheduleFrame()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        visible = isVisible
        updateAnimation()
    }

    private fun updateAnimation() {
        choreographer.removeFrameCallback(frame)
        if (visible && motion != Motion.STILL) {
            if (activeSince == 0L) activeSince = SystemClock.uptimeMillis()
            invalidate()
            scheduleFrame()
        } else if (activeSince != 0L) {
            elapsed += SystemClock.uptimeMillis() - activeSince
            activeSince = 0L
        }
    }

    private fun scheduleFrame() {
        // Choreographer aligns the delayed callback with the next refresh.
        val delay = if (motion == Motion.SAVER) 28L else 0L
        choreographer.postFrameCallbackDelayed(frame, delay)
    }

    override fun onDetachedFromWindow() {
        if (visible) {
            visible = false
            updateAnimation()
        }
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        backdrop.shader = LinearGradient(
            w * 0.18f, h * 0.05f, w * 0.83f, h * 0.95f,
            intArrayOf(0xff060b48.toInt(), 0xff0a337e.toInt(), 0xff139fc1.toInt(), 0xff1bb8cf.toInt()),
            floatArrayOf(0f, 0.34f, 0.85f, 1f),
            Shader.TileMode.CLAMP
        )
        rear.resize(w.toFloat(), h.toFloat())
        front.resize(w.toFloat(), h.toFloat())
        violet.resize(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        val active = if (activeSince == 0L) 0L else SystemClock.uptimeMillis() - activeSince
        val time = (elapsed + active) / 1000.0
        when (effect) {
            Effect.BLUE -> {
                canvas.drawPaint(backdrop)
                val lift = 0.022f * sin(2 * Math.PI * time / 9.2).toFloat()
                rear.draw(canvas, time, lift)
                front.draw(canvas, time, lift)
            }
            Effect.VIOLET -> violet.draw(canvas, time)
        }
    }

    private class Ribbon(
        period: Double,
        private val phaseOffset: Double,
        cyclesAcrossWidth: Double,
        private val baseline: Float,
        private val amplitude: Float,
        depth: Float,
        private val highlight: Int
    ) {
        private companion object {
            const val SEGMENTS = 32
            const val ROWS = 4
        }

        private val rate = 2 * Math.PI / period
        private val waveSin = FloatArray(SEGMENTS + 1) { column ->
            sin(2 * Math.PI * cyclesAcrossWidth * column / SEGMENTS).toFloat()
        }
        private val waveCos = FloatArray(SEGMENTS + 1) { column ->
            cos(2 * Math.PI * cyclesAcrossWidth * column / SEGMENTS).toFloat()
        }
        private val edge = Path()
        private val veil = Path()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val meshPaint = Paint()
        private val veilTop = Color.argb((44 * depth).toInt(), 113, 193, 255)
        private val veilBottom = Color.argb((52 * depth).toInt(), 48, 224, 244)
        private val vertices = FloatArray((SEGMENTS + 1) * ROWS * 2)
        private val colors = IntArray((SEGMENTS + 1) * ROWS)
        private val triangles = ShortArray(SEGMENTS * (ROWS - 1) * 6).apply {
            var i = 0
            for (column in 0 until SEGMENTS) {
                for (row in 0 until ROWS - 1) {
                    val a = column * ROWS + row
                    val b = a + ROWS
                    this[i++] = a.toShort()
                    this[i++] = (a + 1).toShort()
                    this[i++] = b.toShort()
                    this[i++] = b.toShort()
                    this[i++] = (a + 1).toShort()
                    this[i++] = (b + 1).toShort()
                }
            }
        }
        private var width = 0f
        private var height = 0f
        private var glowWidth = 0f

        fun resize(w: Float, h: Float) {
            width = w
            height = h
            glowWidth = min(w, h) * 0.014f
            for (column in 0..SEGMENTS) {
                val x = w * column / SEGMENTS
                val offset = column * ROWS
                for (row in 0 until ROWS) vertices[(offset + row) * 2] = x
                colors[offset] = Color.TRANSPARENT
                colors[offset + 1] = highlight
                colors[offset + 2] = veilTop
                colors[offset + 3] = veilBottom
            }
            fill.shader = LinearGradient(
                0f, h * 0.52f, w * 0.3f, h,
                veilTop, veilBottom,
                Shader.TileMode.CLAMP
            )
        }

        fun draw(canvas: Canvas, seconds: Double, lift: Float) {
            val phase = phaseOffset + seconds * rate
            val phaseCos = cos(phase).toFloat()
            val phaseSin = sin(phase).toFloat()
            val center = height * (baseline + lift)
            val waveHeight = height * amplitude
            for (column in 0..SEGMENTS) {
                // sin(kx + phase): the sampled crests progress from right to left.
                val y = center + waveHeight * (waveSin[column] * phaseCos + waveCos[column] * phaseSin)
                val offset = column * ROWS * 2
                vertices[offset + 1] = y - glowWidth * 0.4f
                vertices[offset + 3] = y
                vertices[offset + 5] = y + glowWidth
                vertices[offset + 7] = height
            }
            if (Build.VERSION.SDK_INT >= 29 || !canvas.isHardwareAccelerated) {
                canvas.drawVertices(Canvas.VertexMode.TRIANGLES, vertices.size, vertices, 0,
                    null, 0, colors, 0, triangles, 0, triangles.size, meshPaint)
            } else {
                drawPaths(canvas)
            }
        }

        private fun drawPaths(canvas: Canvas) {
            edge.rewind()
            edge.moveTo(0f, vertices[3])
            for (column in 1..SEGMENTS) {
                val offset = column * ROWS * 2
                edge.lineTo(vertices[offset + 2], vertices[offset + 3])
            }
            veil.set(edge)
            veil.lineTo(width, height)
            veil.lineTo(0f, height)
            veil.close()
            canvas.drawPath(veil, fill)

            stroke.color = highlight
            stroke.strokeWidth = glowWidth * 0.55f
            canvas.drawPath(edge, stroke)
        }
    }
}
