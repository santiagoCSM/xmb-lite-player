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
    }

    private val vertices = FloatArray((SEGMENTS + 1) * 4)
    private val colors = IntArray((SEGMENTS + 1) * 2)
    private val waveCos = FloatArray(SEGMENTS + 1)
    private val waveSin = FloatArray(SEGMENTS + 1)
    private val detailCos = FloatArray(SEGMENTS + 1)
    private val detailSin = FloatArray(SEGMENTS + 1)
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
            val angle = 2 * Math.PI * u
            waveCos[column] = cos(angle).toFloat()
            waveSin[column] = sin(angle).toFloat()
            detailCos[column] = cos(2 * angle).toFloat()
            detailSin[column] = sin(2 * angle).toFloat()
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
        val phase = time * 0.72 + 2.2 + 0.42 * sin(time * 0.31)
        val detailPhase = phase * 2 - 0.65 + 0.52 * sin(time * 0.46)
        val phaseCos = cos(phase).toFloat()
        val phaseSin = sin(phase).toFloat()
        val secondaryCos = cos(detailPhase).toFloat()
        val secondarySin = sin(detailPhase).toFloat()
        val base = height * (0.55f + 0.015f * sin(time * 0.43).toFloat())
        val amplitude = height * (0.030f + 0.019f * sin(time * 0.51 + 0.7).toFloat())

        // A broad travelling crest with a mild asymmetric shoulder and a varying height.
        for (column in 0..SEGMENTS) {
            val crest = waveCos[column] * phaseCos + waveSin[column] * phaseSin
            val shoulder = detailCos[column] * secondaryCos + detailSin[column] * secondarySin
            vertices[column * 4 + 1] = base - amplitude * (crest + 0.15f * shoulder)
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
