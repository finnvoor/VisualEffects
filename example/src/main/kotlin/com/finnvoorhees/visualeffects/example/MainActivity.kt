package com.finnvoorhees.visualeffects.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.finnvoorhees.visualeffects.Backdrop
import com.finnvoorhees.visualeffects.BackdropContainer
import com.finnvoorhees.visualeffects.BackdropEffect
import com.finnvoorhees.visualeffects.effects.BlurBackdropEffect
import com.finnvoorhees.visualeffects.effects.MaskedVariableBlurBackdropEffect
import com.finnvoorhees.visualeffects.effects.SaturationBackdropEffect
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VisualEffectsExample()
        }
    }

}

private const val SAMPLE_VIDEO_URL =
    "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

@Composable
private fun VisualEffectsExample() {
    val context = LocalContext.current
    val featherMask = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            createLinearGradientMask(128, 128)
        } else {
            null
        }
    }
    val blurEffect = remember {
        BlurBackdropEffect(radiusDp = 18f)
    }
    val saturationEffect = remember {
        SaturationBackdropEffect(saturation = 0f)
    }
    val featherEffect = remember(featherMask) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && featherMask != null) {
            MaskedVariableBlurBackdropEffect(
                maskBitmap = featherMask,
                maxRadiusDp = 32f,
            )
        } else {
            blurEffect
        }
    }

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(SAMPLE_VIDEO_URL))
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
            volume = 0f
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    BackdropContainer(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08111B)),
        content = {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { AnimatedBackdropSceneView(it) },
            )

            AndroidView(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 250.dp, height = 445.dp),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setShutterBackgroundColor(Color(0xFF05080C).toArgb())
                        setKeepContentOnPlayerReset(true)
                        this.player = player
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
            )
        },
        overlay = {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tileSize = 96.dp
            val blurState = rememberDraggableTileState(28.dp, 112.dp)
            val saturationState = rememberDraggableTileState(152.dp, 184.dp)
            val featherState = rememberDraggableTileState(
                x = maxWidth - tileSize - 28.dp,
                y = maxHeight - tileSize - 96.dp,
            )

            DraggableBackdropTile(
                label = stringResource(R.string.effect_blur_label),
                effect = blurEffect,
                state = blurState,
                boundsWidth = maxWidth,
                boundsHeight = maxHeight,
                size = tileSize,
                downsampleFactor = 4f,
            )

            DraggableBackdropTile(
                label = stringResource(R.string.effect_saturation_label),
                effect = saturationEffect,
                state = saturationState,
                boundsWidth = maxWidth,
                boundsHeight = maxHeight,
                size = tileSize,
                downsampleFactor = 1f,
            )

            DraggableBackdropTile(
                label = stringResource(R.string.effect_feather_blur_label),
                effect = featherEffect,
                state = featherState,
                boundsWidth = maxWidth,
                boundsHeight = maxHeight,
                size = tileSize,
                downsampleFactor = 1f,
            )
            }
        },
    )
}

@Composable
private fun DraggableBackdropTile(
    label: String,
    effect: BackdropEffect,
    state: DraggableTileState,
    boundsWidth: Dp,
    boundsHeight: Dp,
    size: Dp,
    downsampleFactor: Float,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    val maxX = remember(boundsWidth, size, density) {
        ((boundsWidth.value - size.value).coerceAtLeast(0f) * density)
    }
    val maxY = remember(boundsHeight, size, density) {
        ((boundsHeight.value - size.value).coerceAtLeast(0f) * density)
    }

    Box(
        modifier = Modifier
            .size(size)
            .pointerInput(maxX, maxY) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    state.offsetX = (state.offsetX + dragAmount.x).coerceIn(0f, maxX)
                    state.offsetY = (state.offsetY + dragAmount.y).coerceIn(0f, maxY)
                }
            }
            .offset { IntOffset(state.offsetX.roundToInt(), state.offsetY.roundToInt()) },
    ) {
        Backdrop(
            modifier = Modifier.fillMaxSize(),
            effect = effect,
            downsampleFactor = downsampleFactor,
            cornerRadius = 22.dp,
        ) {
            Text(
                text = label,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 12.dp),
                color = Color(0xE6FFFFFF),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private class DraggableTileState(
    initialX: Float,
    initialY: Float,
) {
    var offsetX by mutableStateOf(initialX)
    var offsetY by mutableStateOf(initialY)
}

@Composable
private fun rememberDraggableTileState(
    x: Dp,
    y: Dp,
): DraggableTileState {
    val density = LocalContext.current.resources.displayMetrics.density
    return remember(x, y, density) {
        DraggableTileState(
            initialX = x.value * density,
            initialY = y.value * density,
        )
    }
}

private fun createLinearGradientMask(
    width: Int,
    height: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return bitmap
}
