package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AuraCobalt
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraIris
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VoiceOrb(
    isListening: Boolean,
    isThinking: Boolean,
    rmsLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "orb_transition")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 2400 else 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_phase"
    )

    val breathe by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathe"
    )

    val smoothRms by animateFloatAsState(
        targetValue = if (isListening) rmsLevel.coerceIn(0.08f, 1f) else 0f,
        animationSpec = tween(durationMillis = 110),
        label = "orb_rms"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val desc = when {
        isListening -> "Stop listening"
        isThinking -> "Aura is thinking"
        else -> "Tap to speak with Aura"
    }

    Box(
        modifier = modifier
            .size(96.dp)
            .clip(CircleShape)
            .semantics { contentDescription = desc }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.30f

            // Outer ambient halo
            val haloRadius = baseRadius * (1.42f + smoothRms * 0.48f) * breathe
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AuraCobalt.copy(alpha = if (isListening) 0.42f else 0.22f),
                        AuraIris.copy(alpha = if (isListening) 0.24f else 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = center
            )

            // Organic harmonic Siri-like wave ribbons when listening or idle
            val waveCount = if (isListening) 3 else 2
            for (layer in 0 until waveCount) {
                val path = Path()
                val steps = 72
                val layerAmp = if (isListening) {
                    (4.5f + smoothRms * 10f) * (1f - layer * 0.22f)
                } else {
                    2.0f
                }
                val freq = 3 + layer
                val layerPhase = phase * (if (layer % 2 == 0) 1f else -1.3f) + layer * 1.1f
                val rBase = baseRadius * (1.12f + layer * 0.08f)

                for (i in 0..steps) {
                    val theta = (i.toFloat() / steps) * (2f * PI).toFloat()
                    val offset = sin(theta * freq + layerPhase) * layerAmp
                    val r = rBase + offset
                    val x = center.x + cos(theta) * r
                    val y = center.y + sin(theta) * r
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()

                val ringColor = when (layer) {
                    0 -> AuraCyan.copy(alpha = if (isListening) 0.72f else 0.32f)
                    1 -> AuraIris.copy(alpha = if (isListening) 0.62f else 0.26f)
                    else -> AuraCobalt.copy(alpha = 0.50f)
                }
                drawPath(
                    path = path,
                    color = ringColor,
                    style = Stroke(width = if (isListening) 2.2.dp.toPx() else 1.4.dp.toPx())
                )
            }

            // Thinking orbital sweep arc
            if (isThinking) {
                val sweepRadius = baseRadius * 1.32f
                val startDeg = Math.toDegrees(phase.toDouble()).toFloat()
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color.Transparent, AuraCyan, AuraCobalt, AuraIris, Color.Transparent),
                        center = center
                    ),
                    startAngle = startDeg,
                    sweepAngle = 210f,
                    useCenter = false,
                    topLeft = Offset(center.x - sweepRadius, center.y - sweepRadius),
                    size = Size(sweepRadius * 2f, sweepRadius * 2f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Core luminous orb
            val coreRadius = baseRadius * (if (isListening) 0.96f + smoothRms * 0.14f else 0.94f * breathe)
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(AuraCyan, AuraCobalt, AuraIris),
                    start = Offset(center.x - coreRadius, center.y - coreRadius),
                    end = Offset(center.x + coreRadius, center.y + coreRadius)
                ),
                radius = coreRadius,
                center = center
            )
        }

        Icon(
            imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}
