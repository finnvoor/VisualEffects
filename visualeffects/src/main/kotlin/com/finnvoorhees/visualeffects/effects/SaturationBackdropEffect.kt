package com.finnvoorhees.visualeffects.effects

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import com.finnvoorhees.visualeffects.BackdropEffect
import com.finnvoorhees.visualeffects.BackdropView

class SaturationBackdropEffect(
    saturation: Float = 1.35f,
) : BackdropEffect() {
    private val colorFilter = ColorMatrixColorFilter(
        ColorMatrix().apply { setSaturation(saturation) },
    )

    override fun createHardwareRenderEffect(view: BackdropView): Any? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Api31Saturation.createColorFilterEffect(colorFilter)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private object Api31Saturation {
        fun createColorFilterEffect(colorFilter: ColorMatrixColorFilter): RenderEffect {
            return RenderEffect.createColorFilterEffect(colorFilter)
        }
    }
}
