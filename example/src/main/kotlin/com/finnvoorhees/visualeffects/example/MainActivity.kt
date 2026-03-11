package com.finnvoorhees.visualeffects.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.finnvoorhees.visualeffects.effects.SystemMaterialBackdropEffect

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

private data class EffectDemoSpec(
    val label: String,
    val effect: BackdropEffect,
    val downsampleFactor: Float = 4f,
    val subtitle: String? = null,
)

@Composable
private fun VisualEffectsExample() {
    val featherMask = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            createLinearGradientMask(128, 128)
        } else {
            null
        }
    }

    val effects = remember(featherMask) {
        buildList {
            add(
                EffectDemoSpec(
                    label = "BlurBackdropEffect",
                    effect = BlurBackdropEffect(radiusDp = 18f),
                    downsampleFactor = 4f,
                ),
            )
            add(
                EffectDemoSpec(
                    label = "SaturationBackdropEffect",
                    effect = SaturationBackdropEffect(saturation = 0f),
                    downsampleFactor = 1f,
                ),
            )

            val maskedVariableBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && featherMask != null) {
                MaskedVariableBlurBackdropEffect(
                    maskBitmap = featherMask,
                    maxRadiusDp = 32f,
                )
            } else {
                BlurBackdropEffect(radiusDp = 32f)
            }
            add(
                EffectDemoSpec(
                    label = "MaskedVariableBlurBackdropEffect",
                    effect = maskedVariableBlur,
                    downsampleFactor = 1f,
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) null else "API 33+ only (blur fallback shown)",
                ),
            )

            SystemMaterialBackdropEffect.Style.entries.forEach { style ->
                add(
                    EffectDemoSpec(
                        label = "SystemMaterial.${style.name}",
                        effect = SystemMaterialBackdropEffect(style),
                        downsampleFactor = 5f,
                    ),
                )
            }
        }
    }

    val context = LocalContext.current
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
                    val playerView = LayoutInflater.from(viewContext)
                        .inflate(R.layout.player_texture_view, null) as PlayerView
                    playerView.apply {
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "VisualEffects Showcase",
                            color = Color(0xE6FFFFFF),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${effects.size} effects",
                            color = Color(0xCCFFFFFF),
                        )
                    }
                }

                items(effects.chunked(2)) { rowEffects ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowEffects.forEach { spec ->
                            EffectDemoCard(
                                spec = spec,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp),
                            )
                        }
                        if (rowEffects.size == 1) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun EffectDemoCard(
    spec: EffectDemoSpec,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Backdrop(
            modifier = Modifier.fillMaxSize(),
            effect = spec.effect,
            downsampleFactor = spec.downsampleFactor,
            cornerRadius = 18.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f)),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = spec.label,
                    color = Color(0xE6FFFFFF),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                spec.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = Color(0xCCFFFFFF),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
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
