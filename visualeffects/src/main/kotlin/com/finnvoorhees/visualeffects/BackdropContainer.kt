package com.finnvoorhees.visualeffects

import android.content.Context
import android.graphics.Canvas
import android.graphics.RecordingCanvas
import android.graphics.RenderNode
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

open class BackdropContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    internal val snapshotNode: RenderNode? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderNode("BackdropContainer snapshot") else null
    internal var snapshotGeneration: Long = 0L
        private set

    override fun dispatchDraw(canvas: Canvas) {
        val snapshotNode = snapshotNode
        if (snapshotNode != null && canvas.isHardwareAccelerated) {
            snapshotNode.setPosition(0, 0, width, height)
            val recordingCanvas = snapshotNode.beginRecording()
            drawBackgroundForSnapshot(recordingCanvas)
            drawChildren(recordingCanvas, includeBackdropViews = false)
            snapshotNode.endRecording()
            snapshotGeneration += 1
            canvas.drawRenderNode(snapshotNode)
            drawChildren(canvas, includeBackdropViews = true)
            return
        }
        super.dispatchDraw(canvas)
    }

    private fun drawBackgroundForSnapshot(canvas: RecordingCanvas) {
        background?.let {
            it.bounds = canvas.clipBounds
            it.draw(canvas)
        }
    }

    private fun drawChildren(
        canvas: Canvas,
        includeBackdropViews: Boolean,
    ) {
        val drawingTime = drawingTime
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (!shouldDrawChild(child, includeBackdropViews)) continue
            drawChild(canvas, child, drawingTime)
        }
    }

    private fun shouldDrawChild(child: View, includeBackdropViews: Boolean): Boolean {
        if (child.visibility != VISIBLE) return false
        return (child is BackdropView) == includeBackdropViews
    }
}
