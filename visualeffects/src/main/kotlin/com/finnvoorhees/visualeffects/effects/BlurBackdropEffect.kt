package com.finnvoorhees.visualeffects.effects

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import com.finnvoorhees.visualeffects.BackdropEffect
import com.finnvoorhees.visualeffects.BackdropView

class BlurBackdropEffect(
    var radiusDp: Float = 28f,
) : BackdropEffect() {
    override fun createHardwareRenderEffect(view: BackdropView): Any? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val radiusPx = radiusDp * view.resources.displayMetrics.density
        return Api31Blur.createBlurEffect(
            radiusPx.coerceAtLeast(0.5f),
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private object Api31Blur {
        fun createBlurEffect(radiusPx: Float): RenderEffect {
            return RenderEffect.createBlurEffect(
                radiusPx,
                radiusPx,
                Shader.TileMode.CLAMP,
            )
        }
    }
}
