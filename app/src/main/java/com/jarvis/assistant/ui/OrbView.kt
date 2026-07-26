package com.jarvis.assistant.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.JarvisBlue
import com.jarvis.assistant.ui.theme.JarvisBlueDeep
import com.jarvis.assistant.ui.theme.JarvisBlueLight
import com.jarvis.assistant.ui.theme.JarvisWhite

/**
 * Die pulsierende weiß/blaue "Kugel" – das visuelle Herzstück von Jarvis.
 * Reagiert auf den aktuellen [OrbState] mit unterschiedlicher Puls-Geschwindigkeit,
 * einer rotierenden Farbverlauf-Oberfläche und (beim Zuhören/Denken) einem
 * nach außen laufenden Ring.
 */
@Composable
fun OrbView(state: OrbState, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")

    val pulseDurationMillis = when (state) {
        OrbState.IDLE -> 3200
        OrbState.LISTENING -> 900
        OrbState.THINKING -> 1300
        OrbState.SPEAKING -> 550
        OrbState.ERROR -> 3200
    }

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDurationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    val rotationDurationMillis = if (state == OrbState.THINKING) 1800 else 7000
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationDurationMillis, easing = LinearEasing),
        ),
        label = "rotation",
    )

    val ringProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
        ),
        label = "ring",
    )

    val orbColors = if (state == OrbState.ERROR) {
        listOf(Color(0xFFB33A3A), Color(0xFFE87C7C), JarvisWhite, Color(0xFFE87C7C), Color(0xFFB33A3A))
    } else {
        listOf(JarvisBlueDeep, JarvisBlue, JarvisBlueLight, JarvisWhite, JarvisBlueLight, JarvisBlue, JarvisBlueDeep)
    }

    Canvas(modifier = modifier.aspectRatio(1f)) {
        val radius = size.minDimension / 2f
        val center = this.center

        for (i in 3 downTo 1) {
            val glowRadius = radius * (1f + i * 0.18f) * scale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(JarvisBlueLight.copy(alpha = 0.14f / i), Color.Transparent),
                    center = center,
                    radius = glowRadius,
                ),
                radius = glowRadius,
                center = center,
            )
        }

        if (state == OrbState.LISTENING || state == OrbState.THINKING) {
            drawCircle(
                color = JarvisBlue.copy(alpha = (1f - ringProgress) * 0.45f),
                radius = radius * (1f + ringProgress * 0.7f),
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        rotate(rotation, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(colors = orbColors, center = center),
                radius = radius * scale,
                center = center,
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(JarvisWhite.copy(alpha = 0.95f), JarvisBlueLight.copy(alpha = 0.5f), Color.Transparent),
                center = center - Offset(radius * 0.28f, radius * 0.28f),
                radius = radius * 0.95f,
            ),
            radius = radius * scale * 0.9f,
            center = center,
        )
    }
}
