package com.v02.senapi.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CultElectricViolet
import com.example.ui.theme.CultLuminousCyan
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Morphing Loading Indicator.
 * Smoothly morphs between organic geometric polygons:
 * Circle -> 4-Leaf Clover -> Rounded Square -> Radiant Star
 * with continuous rotation and luminous gradient pulse.
 */
@Composable
fun ExpressiveMorphLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 3.5.dp,
    primaryColor: Color = CultLuminousCyan,
    secondaryColor: Color = CultElectricViolet
) {
    val infiniteTransition = rememberInfiniteTransition(label = "morphLoading")

    // Rotation angle
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Morph progress: 0f..4f cycling through 4 distinct geometric profiles
    // 0: Circle, 1: 4-leaf Clover, 2: Rounded Square, 3: Star, 4: Circle
    val morphPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphPhase"
    )

    // Luminous scale breathing
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
                scaleX = pulseScale
                scaleY = pulseScale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = this.center
            val baseRadius = (this.size.minDimension / 2f) - (strokeWidth.toPx() * 1.5f)

            // Calculate current morph interpolation
            val currentStage = morphPhase.toInt() % 4
            val stageFraction = morphPhase - morphPhase.toInt()

            val path = Path()
            val steps = 120
            var firstPoint = true

            for (i in 0..steps) {
                val theta = (i.toFloat() / steps) * 2f * PI.toFloat()

                // Profile 0: Pure Circle
                val rCircle = baseRadius

                // Profile 1: 4-Leaf Clover
                val rClover = baseRadius * (0.75f + 0.25f * cos(4f * theta))

                // Profile 2: Rounded Square (Superellipse approximation)
                val cos4 = cos(4f * theta)
                val rSquare = baseRadius * (0.86f + 0.14f * cos4)

                // Profile 3: Radiant Star
                val rStar = baseRadius * (0.65f + 0.35f * sin(4f * theta + PI.toFloat() / 4f))

                // Smoothly blend across stages
                val blendedRadius = when (currentStage) {
                    0 -> lerp(rCircle, rClover, stageFraction)
                    1 -> lerp(rClover, rSquare, stageFraction)
                    2 -> lerp(rSquare, rStar, stageFraction)
                    else -> lerp(rStar, rCircle, stageFraction)
                }

                val x = center.x + blendedRadius * cos(theta)
                val y = center.y + blendedRadius * sin(theta)

                if (firstPoint) {
                    path.moveTo(x, y)
                    firstPoint = false
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            val brush = Brush.sweepGradient(
                colors = listOf(
                    primaryColor,
                    secondaryColor,
                    primaryColor.copy(alpha = 0.2f),
                    primaryColor
                ),
                center = center
            )

            drawPath(
                path = path,
                brush = brush,
                style = Stroke(
                    width = strokeWidth.toPx()
                )
            )
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}
