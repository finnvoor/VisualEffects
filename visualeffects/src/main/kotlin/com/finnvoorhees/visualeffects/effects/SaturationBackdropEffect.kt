package com.finnvoorhees.visualeffects.effects

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import com.finnvoorhees.visualeffects.BackdropEffect
import com.finnvoorhees.visualeffects.BackdropView

class SaturationBackdropEffect(
    saturation: Float = 1.35f,
) : BackdropEffect() {
    private val colorFilter = ColorMatrixColorFilter(
        ColorMatrix().apply { setSaturation(saturation) },
    )

    override fun createHardwareRenderEffect(view: BackdropView): RenderEffect? {
        return RenderEffect.createColorFilterEffect(colorFilter)
    }
}
