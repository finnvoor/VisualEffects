package com.finnvoorhees.visualeffects

abstract class BackdropEffect {
    open fun createHardwareRenderEffect(view: BackdropView): Any? = null
}
