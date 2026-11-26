package com.santiagocsm.xmblite

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import kotlin.math.cos
import kotlin.math.sin

internal class VioletWave {
    private companion object {
        const val SEGMENTS = 40
        const val BACKDROP = 0xff5320c0.toInt()
        const val CYCLES_ACROSS_WIDTH = 0.72
        const val PERIOD_SECONDS = 12.5
        const val AMPLITUDE = 0.023f
    }

    private val vertices = FloatArray((SEGMENTS + 1) * 4)
    private val colors = IntArray((SEGMENTS + 1) * 2)
    private val waveCos = FloatArray(SEGMENTS + 1)
    private val waveSin = FloatArray(SEGMENTS + 1)
    private val meshPaint = Paint()
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fallbackPath = Path()
    private var width = 0f
    private var height = 0f

    fun resize(w: Float, h: Float) {
        width = w
        height = h
        for (column in 0..SEGMENTS) {
            val u = column.toFloat() / SEGMENTS
            val angle = 2 * Math.PI * CYCLES_ACROSS_WIDTH * u
            waveCos[column] = cos(angle).toFloat()
            waveSin[column] = sin(angle).toFloat()
            val offset = column * 4
            vertices[offset] = w * u
            vertices[offset + 2] = w * u
            vertices[offset + 3] = h
            val light = u * u * (3f - 2f * u)
            colors[column * 2] = Color.rgb((160 + 91 * light).toInt(), (52 + 60 * light).toInt(), 255)
            colors[column * 2 + 1] = Color.rgb((148 + 75 * light).toInt(), (53 + 38 * light).toInt(), 253)
        }
        fallbackPaint.shader = LinearGradient(0f, h * 0.54f, w, h * 0.39f,
            colors[1], colors[SEGMENTS * 2], Shader.TileMode.CLAMP)
    }

    fun draw(canvas: Canvas, time: Double) {
        val phase = 2 * Math.PI * time / PERIOD_SECONDS + 2.2
        val phaseCos = cos(phase).toFloat()
        val phaseSin = sin(phase).toFloat()
        val base = height * 0.55f
        val amplitude = height * AMPLITUDE

        for (column in 0..SEGMENTS) {
            // Expand sin(kx - phase) using the precomputed spatial samples.
            val wave = waveSin[column] * phaseCos - waveCos[column] * phaseSin
            vertices[column * 4 + 1] = base + amplitude * wave
        }

        canvas.drawColor(BACKDROP)
        if (Build.VERSION.SDK_INT >= 29 || !canvas.isHardwareAccelerated) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLE_STRIP, vertices.size, vertices, 0,
                null, 0, colors, 0, null, 0, 0, meshPaint)
        } else {
            fallbackPath.rewind()
            fallbackPath.moveTo(0f, vertices[1])
            for (column in 1..SEGMENTS) {
                fallbackPath.lineTo(vertices[column * 4], vertices[column * 4 + 1])
            }
            fallbackPath.lineTo(width, height)
            fallbackPath.lineTo(0f, height)
            fallbackPath.close()
            canvas.drawPath(fallbackPath, fallbackPaint)
        }
    }
}
