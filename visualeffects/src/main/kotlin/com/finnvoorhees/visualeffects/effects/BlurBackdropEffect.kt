package com.finnvoorhees.visualeffects.effects

import android.graphics.RenderEffect
import android.graphics.Shader
import com.finnvoorhees.visualeffects.BackdropEffect
import com.finnvoorhees.visualeffects.BackdropView

class BlurBackdropEffect(
    var radiusDp: Float = 28f,
) : BackdropEffect() {
    override fun createHardwareRenderEffect(view: BackdropView): RenderEffect? {
        val radiusPx = radiusDp * view.resources.displayMetrics.density
        return RenderEffect.createBlurEffect(
            radiusPx.coerceAtLeast(0.5f),
            radiusPx.coerceAtLeast(0.5f),
            Shader.TileMode.CLAMP,
        )
    }
}
