package com.finnvoorhees.visualeffects.effects

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import com.finnvoorhees.visualeffects.BackdropEffect
import com.finnvoorhees.visualeffects.BackdropView

@RequiresApi(33)
class MaskedVariableBlurBackdropEffect(
    private val maskBitmap: Bitmap,
    private val maxRadiusDp: Float,
    private val maxSamples: Int = 20,
    private val minSamplesAtMaxBlur: Int = 8,
) : BackdropEffect() {
    private val horizontalShader = RuntimeShader(VARIABLE_BLUR_SHADER)
    private val verticalShader = RuntimeShader(VARIABLE_BLUR_SHADER)
    private val maskMatrix = Matrix()

    override fun createHardwareRenderEffect(view: BackdropView): RenderEffect {
        val maskShader = BitmapShader(maskBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        maskMatrix.reset()
        maskMatrix.setScale(
            view.width.toFloat() / maskBitmap.width.toFloat(),
            view.height.toFloat() / maskBitmap.height.toFloat(),
        )
        maskShader.setLocalMatrix(maskMatrix)

        val radiusPx = (maxRadiusDp * view.resources.displayMetrics.density).coerceAtLeast(0.5f)
        configureShader(horizontalShader, maskShader, view, radiusPx, vertical = false)
        configureShader(verticalShader, maskShader, view, radiusPx, vertical = true)

        val horizontalEffect = RenderEffect.createRuntimeShaderEffect(horizontalShader, "source")
        return RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(verticalShader, "source"),
            horizontalEffect,
        )
    }

    private fun configureShader(
        shader: RuntimeShader,
        maskShader: Shader,
        view: BackdropView,
        radiusPx: Float,
        vertical: Boolean,
    ) {
        shader.setInputShader("mask", maskShader)
        shader.setFloatUniform("resolution", view.containerWidthPx, view.containerHeightPx)
        shader.setFloatUniform("backdropOrigin", view.captureLeftPx, view.captureTopPx)
        shader.setFloatUniform("backdropSize", view.captureWidthPx, view.captureHeightPx)
        shader.setFloatUniform("maxRadius", radiusPx)
        shader.setFloatUniform("maxSamples", maxSamples.coerceIn(1, 24).toFloat())
        shader.setFloatUniform(
            "minSamplesAtMaxBlur",
            minSamplesAtMaxBlur.coerceIn(1, maxSamples.coerceIn(1, 24)).toFloat(),
        )
        shader.setFloatUniform("vertical", if (vertical) 1f else 0f)
    }

    companion object {
        private const val VARIABLE_BLUR_SHADER = """
            uniform shader source;
            uniform shader mask;
            uniform float2 resolution;
            uniform float2 backdropOrigin;
            uniform float2 backdropSize;
            uniform float maxRadius;
            uniform float maxSamples;
            uniform float minSamplesAtMaxBlur;
            uniform float vertical;

            float gaussian(float distance, float sigma) {
                float exponent = -(distance * distance) / (2.0 * sigma * sigma);
                return exp(exponent);
            }

            half4 main(float2 fragCoord) {
                float2 localCoord = fragCoord - backdropOrigin;
                float2 uv = localCoord / backdropSize;
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    return source.eval(fragCoord);
                }

                float maskAlpha = clamp(mask.eval(localCoord).a, 0.0, 1.0);
                float pixelRadius = maskAlpha * maxRadius;
                if (pixelRadius < 1.0) {
                    return source.eval(fragCoord);
                }

                float effectiveSamples = mix(maxSamples, minSamplesAtMaxBlur, maskAlpha);
                float interval = max(1.0, pixelRadius / max(effectiveSamples, 1.0));
                float sigma = max(pixelRadius / 3.0, 0.001);
                float2 axis = vertical == 0.0 ? float2(1.0, 0.0) : float2(0.0, 1.0);

                float centerWeight = gaussian(0.0, sigma);
                half4 weightedColorSum = source.eval(fragCoord) * centerWeight;
                float totalWeight = centerWeight;

                for (int index = 1; index <= 24; index++) {
                    float distance = float(index) * interval;
                    if (distance > pixelRadius) {
                        break;
                    }

                    float2 offset = axis * distance;
                    float2 positivePos = fragCoord + offset;
                    float2 negativePos = fragCoord - offset;
                    float weight = gaussian(distance, sigma);

                    if (positivePos.x >= 0.0 && positivePos.x < resolution.x &&
                        positivePos.y >= 0.0 && positivePos.y < resolution.y) {
                        weightedColorSum += source.eval(positivePos) * weight;
                        totalWeight += weight;
                    }
                    if (negativePos.x >= 0.0 && negativePos.x < resolution.x &&
                        negativePos.y >= 0.0 && negativePos.y < resolution.y) {
                        weightedColorSum += source.eval(negativePos) * weight;
                        totalWeight += weight;
                    }
                }

                return weightedColorSum / totalWeight;
            }
        """
    }
}
