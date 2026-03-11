package com.finnvoorhees.visualeffects

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.RenderNode
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import androidx.core.view.doOnLayout
import com.finnvoorhees.visualeffects.effects.BlurBackdropEffect

open class BackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val blurNode = RenderNode("BackdropView blur node")
    private val containerLocation = IntArray(2)
    private val viewLocation = IntArray(2)

    private var captureContainer: BackdropContainer? = null
    private var lastRecordedSnapshotGeneration = Long.MIN_VALUE
    private var lastAppliedLeft = Int.MIN_VALUE
    private var lastAppliedTop = Int.MIN_VALUE
    private var lastAppliedWidth = -1
    private var lastAppliedHeight = -1
    private var lastAppliedContainerWidth = -1
    private var lastAppliedContainerHeight = -1

    internal var captureLeftPx: Float = 0f
        private set
    internal var captureTopPx: Float = 0f
        private set
    internal var captureWidthPx: Float = 0f
        private set
    internal var captureHeightPx: Float = 0f
        private set
    internal var containerWidthPx: Float = 0f
        private set
    internal var containerHeightPx: Float = 0f
        private set

    var targetContainer: BackdropContainer? = null
        set(value) {
            if (field === value) return
            field = value
            resetRenderState()
            invalidate()
        }

    var downsampleFactor: Float = 6f
        set(value) {
            val next = value.coerceIn(1f, 12f)
            if (field == next) return
            field = next
            updateHardwareBlur()
            invalidate()
        }

    var cornerRadius: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            clipToOutline = value > 0f
            invalidateOutline()
            invalidate()
        }

    var effect: BackdropEffect = BlurBackdropEffect(radiusDp = 28f)
        set(value) {
            field = value
            updateHardwareBlur()
            invalidate()
        }

    private val blurEffect: BlurBackdropEffect?
        get() = effect as? BlurBackdropEffect

    var blurRadius: Float
        get() = blurEffect?.radiusDp ?: 0f
        set(value) {
            val blurEffect = blurEffect
            if (blurEffect != null) {
                if (blurEffect.radiusDp == value) return
                blurEffect.radiusDp = value
                updateHardwareBlur()
                invalidate()
            } else {
                effect = BlurBackdropEffect(radiusDp = value)
            }
        }

    init {
        setWillNotDraw(false)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (cornerRadius > 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                } else {
                    outline.setRect(0, 0, view.width, view.height)
                }
            }
        }

        context.withStyledAttributes(attrs, R.styleable.BackdropView) {
            blurRadius = pxToDp(getDimension(R.styleable.BackdropView_backdropBlurRadius, dpToPx(28f)))
            downsampleFactor = getFloat(R.styleable.BackdropView_backdropDownsampleFactor, downsampleFactor)
            cornerRadius = getDimension(R.styleable.BackdropView_backdropCornerRadius, cornerRadius)
        }

        doOnLayout { invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        captureContainer = targetContainer ?: findBackdropContainer()
        updateHardwareBlur()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        captureContainer = null
        resetRenderState()
        blurNode.discardDisplayList()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetRenderState()
        updateHardwareBlur()
        invalidate()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) invalidate()
    }

    override fun draw(canvas: Canvas) {
        drawBackdrop(canvas)
        super.draw(canvas)
    }

    private fun drawBackdrop(canvas: Canvas) {
        if (!canvas.isHardwareAccelerated) return
        val captureContainer = captureContainer ?: (targetContainer ?: findBackdropContainer()).also { captureContainer = it } ?: return
        val snapshotNode = captureContainer.snapshotNode ?: return
        if (width <= 0 || height <= 0) return

        captureContainer.getLocationOnScreen(containerLocation)
        getLocationOnScreen(viewLocation)
        val left = viewLocation[0] - containerLocation[0]
        val top = viewLocation[1] - containerLocation[1]
        captureLeftPx = left.toFloat()
        captureTopPx = top.toFloat()
        captureWidthPx = width.toFloat()
        captureHeightPx = height.toFloat()
        containerWidthPx = captureContainer.width.toFloat()
        containerHeightPx = captureContainer.height.toFloat()

        if (shouldUpdateRenderEffect(left, top)) {
            updateHardwareBlur()
            lastAppliedLeft = left
            lastAppliedTop = top
            lastAppliedWidth = width
            lastAppliedHeight = height
            lastAppliedContainerWidth = captureContainer.width
            lastAppliedContainerHeight = captureContainer.height
        }

        if (shouldRecordBackdrop(captureContainer.snapshotGeneration)) {
            blurNode.setPosition(0, 0, captureContainer.width, captureContainer.height)
            val recordingCanvas = blurNode.beginRecording()
            recordingCanvas.drawRenderNode(snapshotNode)
            blurNode.endRecording()
            lastRecordedSnapshotGeneration = captureContainer.snapshotGeneration
        }
        blurNode.setTranslationX(-left.toFloat())
        blurNode.setTranslationY(-top.toFloat())

        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRenderNode(blurNode)
        canvas.restore()
    }

    private fun updateHardwareBlur() {
        blurNode.setRenderEffect(effect.createHardwareRenderEffect(this))
    }

    private fun shouldUpdateRenderEffect(
        left: Int,
        top: Int,
    ): Boolean {
        return lastAppliedLeft != left ||
            lastAppliedTop != top ||
            lastAppliedWidth != width ||
            lastAppliedHeight != height ||
            lastAppliedContainerWidth != captureContainer?.width ||
            lastAppliedContainerHeight != captureContainer?.height
    }

    private fun shouldRecordBackdrop(snapshotGeneration: Long): Boolean {
        return lastRecordedSnapshotGeneration != snapshotGeneration ||
            lastAppliedLeft == Int.MIN_VALUE
    }

    private fun resetRenderState() {
        lastRecordedSnapshotGeneration = Long.MIN_VALUE
        lastAppliedLeft = Int.MIN_VALUE
        lastAppliedTop = Int.MIN_VALUE
        lastAppliedWidth = -1
        lastAppliedHeight = -1
        lastAppliedContainerWidth = -1
        lastAppliedContainerHeight = -1
    }

    private fun dpToPx(value: Float): Float = value * resources.displayMetrics.density

    private fun pxToDp(value: Float): Float = value / resources.displayMetrics.density

    internal fun samplesFrom(container: BackdropContainer): Boolean {
        return resolveTargetContainer() === container
    }

    private fun resolveTargetContainer(): BackdropContainer? {
        return captureContainer ?: targetContainer ?: findBackdropContainer()
    }

    private fun findBackdropContainer(): BackdropContainer? {
        var current: View? = parent as? View
        while (current != null) {
            if (current is BackdropContainer) return current
            current = current.parent as? View
        }
        return null
    }
}
