package com.finnvoorhees.visualeffects

import android.graphics.RenderEffect

abstract class BackdropEffect {
    open fun createHardwareRenderEffect(view: BackdropView): RenderEffect? = null
}
