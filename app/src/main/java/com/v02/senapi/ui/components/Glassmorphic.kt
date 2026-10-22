package com.v02.senapi.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CultGlassBorder
import com.example.ui.theme.CultGlassRim

fun Modifier.cultFrostedGlass(
    cornerRadius: Dp = 20.dp,
    containerColor: Color = Color(0xCC0D121F),
    strokeColor: Color = CultGlassBorder,
    blurRadius: Dp = 24.dp
): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)

    val baseModifier = this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    containerColor.copy(alpha = 0.85f),
                    containerColor.copy(alpha = 0.65f)
                )
            ),
            shape = shape
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    CultGlassRim,
                    strokeColor
                )
            ),
            shape = shape
        )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        baseModifier.graphicsLayer {
            val radiusPx = blurRadius.toPx()
            if (radiusPx > 0) {
                renderEffect = RenderEffect.createBlurEffect(
                    radiusPx,
                    radiusPx,
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
        }
    } else {
        baseModifier
    }
}
