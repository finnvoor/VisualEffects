package com.finnvoorhees.visualeffects.effects

import android.content.res.Configuration
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import com.finnvoorhees.visualeffects.BackdropEffect
import com.finnvoorhees.visualeffects.BackdropView
import kotlin.math.roundToInt

class SystemMaterialBackdropEffect(
    var style: Style = Style.systemMaterial,
) : BackdropEffect() {
    @Suppress("EnumEntryName")
    enum class Style {
        systemUltraThinMaterial,
        systemThinMaterial,
        systemMaterial,
        systemThickMaterial,
        systemChromeMaterial,
        systemUltraThinMaterialLight,
        systemThinMaterialLight,
        systemMaterialLight,
        systemThickMaterialLight,
        systemChromeMaterialLight,
        systemUltraThinMaterialDark,
        systemThinMaterialDark,
        systemMaterialDark,
        systemThickMaterialDark,
        systemChromeMaterialDark,
    }

    override fun createHardwareRenderEffect(view: BackdropView): Any? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Api31SystemMaterial.createEffect(
            recipe = recipeFor(style, isDarkMode(view)),
            density = view.resources.displayMetrics.density,
        )
    }

    private fun isDarkMode(view: BackdropView): Boolean {
        val uiMode = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun recipeFor(
        style: Style,
        prefersDark: Boolean,
    ): MaterialRecipe {
        val resolvedStyle = when (style) {
            Style.systemUltraThinMaterial -> if (prefersDark) Style.systemUltraThinMaterialDark else Style.systemUltraThinMaterialLight
            Style.systemThinMaterial -> if (prefersDark) Style.systemThinMaterialDark else Style.systemThinMaterialLight
            Style.systemMaterial -> if (prefersDark) Style.systemMaterialDark else Style.systemMaterialLight
            Style.systemThickMaterial -> if (prefersDark) Style.systemThickMaterialDark else Style.systemThickMaterialLight
            Style.systemChromeMaterial -> if (prefersDark) Style.systemChromeMaterialDark else Style.systemChromeMaterialLight
            else -> style
        }

        return when (resolvedStyle) {
            Style.systemUltraThinMaterialLight -> MaterialRecipe(
                blurRadiusDp = 25f,
                baseTint = rgb(191),
                baseTintAlpha = 0.44f,
                blendTint = rgb(13),
                blendTintAlpha = 1f,
                blendMode = BlendMode.COLOR_DODGE,
            )

            Style.systemThinMaterialLight -> MaterialRecipe(
                blurRadiusDp = 25f,
                baseTint = rgb(166),
                baseTintAlpha = 0.70f,
                blendTint = rgb(51),
                blendTintAlpha = 1f,
                blendMode = BlendMode.COLOR_DODGE,
            )

            Style.systemMaterialLight -> MaterialRecipe(
                blurRadiusDp = 25f,
                baseTint = rgb(179),
                baseTintAlpha = 0.82f,
                blendTint = rgb(56),
                blendTintAlpha = 1f,
                blendMode = BlendMode.COLOR_DODGE,
            )

            Style.systemThickMaterialLight -> MaterialRecipe(
                blurRadiusDp = 25f,
                baseTint = rgb(153),
                baseTintAlpha = 0.97f,
                blendTint = rgb(92),
                blendTintAlpha = 1f,
                blendMode = BlendMode.COLOR_DODGE,
            )

            Style.systemChromeMaterialLight -> MaterialRecipe(
                blurRadiusDp = 25f,
                blendTint = Color.WHITE,
                blendTintAlpha = 0.75f,
                blendMode = BlendMode.HARD_LIGHT,
            )

            Style.systemUltraThinMaterialDark -> MaterialRecipe(
                blurRadiusDp = 25f,
                baseTint = rgb(37),
                baseTintAlpha = 0.55f,
                blendTint = rgb(156),
                blendTintAlpha = 1f,
                blendMode = BlendMode.OVERLAY,
            )

            Style.systemThinMaterialDark -> MaterialRecipe(
                blurRadiusDp = 25f,
                baseTint = rgb(37),
                baseTintAlpha = 0.70f,
                blendTint = rgb(156),
                blendTintAlpha = 1f,
                blendMode = BlendMode.OVERLAY,
            )

            Style.systemMaterialDark -> MaterialRecipe(
                blurRadiusDp = 25f,
                baseTint = rgb(37),
                baseTintAlpha = 0.82f,
                blendTint = rgb(140),
                blendTintAlpha = 1f,
                blendMode = BlendMode.OVERLAY,
            )

            Style.systemThickMaterialDark -> MaterialRecipe(
                blurRadiusDp = 25f,
                baseTint = rgb(37),
                baseTintAlpha = 0.90f,
                blendTint = rgb(124),
                blendTintAlpha = 1f,
                blendMode = BlendMode.OVERLAY,
            )

            Style.systemChromeMaterialDark -> MaterialRecipe(
                blurRadiusDp = 25f,
                baseTint = rgb(28),
                baseTintAlpha = 0.90f,
                blendTint = rgb(124),
                blendTintAlpha = 1f,
                blendMode = BlendMode.OVERLAY,
            )

            Style.systemUltraThinMaterial,
            Style.systemThinMaterial,
            Style.systemMaterial,
            Style.systemThickMaterial,
            Style.systemChromeMaterial,
                -> error("Dynamic styles should be resolved before recipe lookup")
        }
    }

    @ColorInt
    private fun rgb(channel: Int): Int = Color.rgb(channel, channel, channel)

    private data class MaterialRecipe(
        val blurRadiusDp: Float,
        @param:ColorInt val baseTint: Int = Color.TRANSPARENT,
        val baseTintAlpha: Float = 0f,
        @param:ColorInt val blendTint: Int = Color.TRANSPARENT,
        val blendTintAlpha: Float = 0f,
        val blendMode: BlendMode? = null,
    )

    @RequiresApi(Build.VERSION_CODES.S)
    private object Api31SystemMaterial {
        fun createEffect(
            recipe: MaterialRecipe,
            density: Float,
        ): RenderEffect {
            val radiusPx = (recipe.blurRadiusDp * density).coerceAtLeast(0.5f)
            var effect: RenderEffect = RenderEffect.createBlurEffect(
                radiusPx,
                radiusPx,
                Shader.TileMode.CLAMP,
            )

            colorFilterFor(
                rgb = recipe.baseTint,
                alpha = recipe.baseTintAlpha,
                mode = BlendMode.SRC_OVER,
            )?.let { filter ->
                effect = RenderEffect.createColorFilterEffect(filter, effect)
            }

            val blendMode = recipe.blendMode
            if (blendMode != null) {
                colorFilterFor(
                    rgb = recipe.blendTint,
                    alpha = recipe.blendTintAlpha,
                    mode = blendMode,
                )?.let { filter ->
                    effect = RenderEffect.createColorFilterEffect(filter, effect)
                }
            }

            return effect
        }

        private fun colorFilterFor(
            @ColorInt rgb: Int,
            alpha: Float,
            mode: BlendMode,
        ): ColorFilter? {
            if (alpha <= 0f) return null
            return BlendModeColorFilter(
                withAlpha(rgb, alpha),
                mode,
            )
        }

        @ColorInt
        private fun withAlpha(
            @ColorInt color: Int,
            alpha: Float,
        ): Int {
            val alphaInt = (alpha * 255f).roundToInt().coerceIn(0, 255)
            return Color.argb(alphaInt, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
