package com.finnvoorhees.visualeffects.example

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class AnimatedBackdropSceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF
        strokeWidth = 1f.dp
        style = Paint.Style.STROKE
    }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tileRect = RectF()

    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedFraction
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) {
            animator.start()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        drawGrid(canvas)
        drawMovingTiles(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(0xFF071019.toInt(), 0xFF122233.toInt(), 0xFF111A2B.toInt()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val stripeSpacing = 160f.dp
        val stripeOffset = progress * stripeSpacing
        val stripeWidth = 72f.dp
        var x = -stripeWidth * 2 + stripeOffset
        while (x < width + stripeWidth * 2) {
            stripePaint.shader = LinearGradient(
                x,
                0f,
                x + stripeWidth,
                0f,
                intArrayOf(0x001CE5FF, 0xAA1CE5FF.toInt(), 0x001CE5FF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.save()
            canvas.rotate(-24f, x, 0f)
            canvas.drawRect(x, -height.toFloat(), x + stripeWidth, height * 2f, stripePaint)
            canvas.restore()
            x += stripeSpacing
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val spacing = 28f.dp
        var x = 0f
        while (x <= width.toFloat()) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += spacing
        }
        var y = 0f
        while (y <= height.toFloat()) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += spacing
        }
    }

    private fun drawMovingTiles(canvas: Canvas) {
        val tileSize = 54f.dp
        val firstLaneSpacing = tileSize + 18f.dp
        val secondLaneSpacing = tileSize + 20f.dp
        val laneTop = height * 0.42f
        val laneHeight = 120f.dp
        val offset = progress * firstLaneSpacing
        var index = 0
        var x = -tileSize * 2 + offset
        while (x < width + tileSize * 2) {
            val top = laneTop + if (index % 2 == 0) 0f else 46f.dp
            tileRect.set(x, top, x + tileSize, top + tileSize)
            tilePaint.color = if (index % 3 == 0) 0xFFFF8A3D.toInt() else 0xFF8CE0FF.toInt()
            canvas.drawRoundRect(tileRect, 14f.dp, 14f.dp, tilePaint)
            index++
            x += firstLaneSpacing
        }

        val accentOffset = progress * secondLaneSpacing
        x = width.toFloat() - accentOffset
        index = 0
        while (x > -tileSize * 2) {
            val top = laneTop + laneHeight + if (index % 2 == 0) 24f.dp else 70f.dp
            tileRect.set(x, top, x + tileSize, top + tileSize)
            tilePaint.color = if (index % 2 == 0) 0xFFB18CFF.toInt() else 0xFFFFFFFF.toInt()
            canvas.drawRoundRect(tileRect, 14f.dp, 14f.dp, tilePaint)
            index++
            x -= secondLaneSpacing
        }
    }

    private val Float.dp: Float
        get() = this * resources.displayMetrics.density
}
