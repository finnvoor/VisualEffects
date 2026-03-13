package com.finnvoorhees.visualeffects

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.content.withStyledAttributes
import androidx.core.view.doOnLayout
import com.finnvoorhees.visualeffects.effects.BlurBackdropEffect
import kotlin.math.roundToInt

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
    private var isPreDrawListenerRegistered = false
    private val preDrawInvalidateListener = ViewTreeObserver.OnPreDrawListener {
        if (isShown) {
            invalidate()
        }
        true
    }

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

    internal var composeCaptureRectPx: RectF? = null
        set(value) {
            if (rectEquals(field, value)) return
            field = value?.let(::RectF)
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
        reinitializeRenderPipeline()
        updatePreDrawInvalidationRegistration()
    }

    override fun onDetachedFromWindow() {
        unregisterPreDrawInvalidation()
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
        if (isVisible) {
            reinitializeRenderPipeline()
        }
        updatePreDrawInvalidationRegistration()
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

        val captureRect = composeCaptureRectPx
        val left: Float
        val top: Float
        if (captureRect != null) {
            left = captureRect.left
            top = captureRect.top
        } else {
            captureContainer.getLocationOnScreen(containerLocation)
            getLocationOnScreen(viewLocation)
            left = (viewLocation[0] - containerLocation[0]).toFloat()
            top = (viewLocation[1] - containerLocation[1]).toFloat()
        }

        captureLeftPx = left
        captureTopPx = top
        captureWidthPx = width.toFloat()
        captureHeightPx = height.toFloat()
        containerWidthPx = captureContainer.width.toFloat()
        containerHeightPx = captureContainer.height.toFloat()

        val roundedLeft = left.roundToInt()
        val roundedTop = top.roundToInt()
        if (shouldUpdateRenderEffect(roundedLeft, roundedTop, width, height)) {
            updateHardwareBlur()
            lastAppliedLeft = roundedLeft
            lastAppliedTop = roundedTop
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
        blurNode.setTranslationX(-roundedLeft.toFloat())
        blurNode.setTranslationY(-roundedTop.toFloat())

        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRenderNode(blurNode)
        canvas.restore()
    }

    private fun updateHardwareBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        Api31RenderEffectCompat.setRenderEffect(blurNode, effect.createHardwareRenderEffect(this))
    }

    private fun shouldUpdateRenderEffect(
        left: Int,
        top: Int,
        captureWidth: Int,
        captureHeight: Int,
    ): Boolean {
        return lastAppliedLeft != left ||
            lastAppliedTop != top ||
            lastAppliedWidth != captureWidth ||
            lastAppliedHeight != captureHeight ||
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

    private fun reinitializeRenderPipeline() {
        captureContainer = targetContainer ?: findBackdropContainer()
        resetRenderState()
        blurNode.discardDisplayList()
        updateHardwareBlur()
        invalidate()
    }

    private fun updatePreDrawInvalidationRegistration() {
        val shouldRegister = isAttachedToWindow && isShown
        if (shouldRegister) {
            registerPreDrawInvalidation()
        } else {
            unregisterPreDrawInvalidation()
        }
    }

    private fun registerPreDrawInvalidation() {
        if (isPreDrawListenerRegistered) return
        val observer = viewTreeObserver
        if (!observer.isAlive) return
        observer.addOnPreDrawListener(preDrawInvalidateListener)
        isPreDrawListenerRegistered = true
    }

    private fun unregisterPreDrawInvalidation() {
        if (!isPreDrawListenerRegistered) return
        val observer = viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnPreDrawListener(preDrawInvalidateListener)
        }
        isPreDrawListenerRegistered = false
    }

    private fun dpToPx(value: Float): Float = value * resources.displayMetrics.density

    private fun pxToDp(value: Float): Float = value / resources.displayMetrics.density

    private fun rectEquals(
        first: RectF?,
        second: RectF?,
    ): Boolean {
        if (first == null || second == null) return first == second
        return first.left == second.left &&
            first.top == second.top &&
            first.right == second.right &&
            first.bottom == second.bottom
    }

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

    @RequiresApi(Build.VERSION_CODES.S)
    private object Api31RenderEffectCompat {
        fun setRenderEffect(
            node: RenderNode,
            renderEffect: Any?,
        ) {
            node.setRenderEffect(renderEffect as? RenderEffect)
        }
    }
}
