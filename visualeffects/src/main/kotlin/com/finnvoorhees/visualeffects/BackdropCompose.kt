package com.finnvoorhees.visualeffects

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.finnvoorhees.visualeffects.effects.BlurBackdropEffect

private val LocalBackdropContainer = androidx.compose.runtime.compositionLocalOf<BackdropContainer?> { null }
private val LocalBackdropContainerBounds = androidx.compose.runtime.compositionLocalOf<WindowBounds?> { null }

private data class WindowBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

private fun windowBoundsOf(
    left: Float,
    top: Float,
    width: Int,
    height: Int,
): WindowBounds {
    return WindowBounds(
        left = left,
        top = top,
        width = width.toFloat(),
        height = height.toFloat(),
    )
}

@Composable
fun BackdropContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val parentComposition = rememberCompositionContext()
    val latestContent by rememberUpdatedState(content)
    val backdropContainerState = remember { mutableStateOf<BackdropContainer?>(null) }
    val backdropContainerBounds = remember { mutableStateOf<WindowBounds?>(null) }
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    val size = coordinates.size
                    backdropContainerBounds.value = windowBoundsOf(
                        left = position.x,
                        top = position.y,
                        width = size.width,
                        height = size.height,
                    )
                },
            factory = { context ->
                ComposeBackdropContainerView(context).apply {
                    setParentCompositionContext(parentComposition)
                    setComposeContent { latestContent() }
                    backdropContainerState.value = this
                }
            },
            update = { view ->
                view.setParentCompositionContext(parentComposition)
                backdropContainerState.value = view
            },
        )
        CompositionLocalProvider(
            LocalBackdropContainer provides backdropContainerState.value,
            LocalBackdropContainerBounds provides backdropContainerBounds.value,
        ) {
            Box(modifier = Modifier.fillMaxSize(), content = overlay)
        }
    }
}

@Composable
fun Backdrop(
    modifier: Modifier = Modifier,
    effect: BackdropEffect = BlurBackdropEffect(),
    downsampleFactor: Float = 6f,
    cornerRadius: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val parentComposition = rememberCompositionContext()
    val latestContent by rememberUpdatedState(content)
    val backdropContainer = LocalBackdropContainer.current
    val backdropContainerBounds = LocalBackdropContainerBounds.current
    var backdropBounds by remember { mutableStateOf<WindowBounds?>(null) }
    AndroidView(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInWindow()
            val size = coordinates.size
            backdropBounds = windowBoundsOf(
                left = position.x,
                top = position.y,
                width = size.width,
                height = size.height,
            )
        },
        factory = { context ->
            ComposeBackdropView(context).apply {
                setParentCompositionContext(parentComposition)
                targetContainer = backdropContainer
                this.effect = effect
                this.downsampleFactor = downsampleFactor
                this.cornerRadius = with(density) { cornerRadius.toPx() }
                composeCaptureRectPx = composeCaptureRect(backdropBounds, backdropContainerBounds)
                setComposeContent { latestContent() }
            }
        },
        update = { view ->
            view.setParentCompositionContext(parentComposition)
            view.targetContainer = backdropContainer
            view.effect = effect
            view.downsampleFactor = downsampleFactor
            view.cornerRadius = with(density) { cornerRadius.toPx() }
            view.composeCaptureRectPx = composeCaptureRect(backdropBounds, backdropContainerBounds)
        },
    )
}

private fun composeCaptureRect(
    backdropBounds: WindowBounds?,
    containerBounds: WindowBounds?,
): RectF? {
    if (backdropBounds == null || containerBounds == null) return null

    val left = backdropBounds.left - containerBounds.left
    val top = backdropBounds.top - containerBounds.top
    val width = backdropBounds.width
    val height = backdropBounds.height
    if (width <= 0f || height <= 0f) return null

    return RectF(left, top, left + width, top + height)
}

private class ComposeBackdropContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BackdropContainer(context, attrs, defStyleAttr) {
    private val composeContentView = BackdropComposeContentView(context).apply {
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    init {
        addView(composeContentView)
    }

    fun setParentCompositionContext(parent: CompositionContext?) {
        composeContentView.setParentCompositionContext(parent)
    }

    fun setComposeContent(content: @Composable BoxScope.() -> Unit) {
        composeContentView.content = {
            Box(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

private class ComposeBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BackdropView(context, attrs, defStyleAttr) {
    private val composeContentView = BackdropComposeContentView(context).apply {
        layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    init {
        addView(composeContentView)
    }

    fun setParentCompositionContext(parent: CompositionContext?) {
        composeContentView.setParentCompositionContext(parent)
    }

    fun setComposeContent(content: @Composable BoxScope.() -> Unit) {
        composeContentView.content = {
            Box(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

private class BackdropComposeContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {
    var content by mutableStateOf<@Composable () -> Unit>({})

    @Composable
    override fun Content() {
        content()
    }
}
