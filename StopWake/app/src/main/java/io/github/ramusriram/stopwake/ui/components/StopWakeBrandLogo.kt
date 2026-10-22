package io.github.ramusriram.stopwake.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Signature vector brand emblem for StopWake:
 * A deep-navy-to-indigo radar badge featuring a crisp white GPS Destination Pin
 * radiating acoustic wake-up waves on both sides with an emerald geofence bell core.
 */
@Composable
fun StopWakeBrandLogo(
    size: Dp = 36.dp,
    cornerRadius: Dp = (size.value * 0.28f).dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0B132B),
                        Color(0xFF1E3A8A),
                        Color(0xFF1D4ED8)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(cornerRadius)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.5f
            val pinCy = h * 0.42f
            val pinRadius = w * 0.21f
            val pinTipY = h * 0.80f

            // 1. Subtle Ambient Emerald Radar Ring
            drawCircle(
                color = Color(0xFF34D399).copy(alpha = 0.18f),
                radius = w * 0.38f,
                center = Offset(cx, pinCy),
                style = Stroke(width = w * 0.025f)
            )

            // 2. Ground Arrival Pulse Ellipse under Pin Tip
            drawOval(
                color = Color(0xFF10B981).copy(alpha = 0.35f),
                topLeft = Offset(cx - w * 0.16f, pinTipY - h * 0.03f),
                size = Size(w * 0.32f, h * 0.08f)
            )

            // 3. Left & Right Acoustic Wake-Up Waves
            val waveArcSize = Size(w * 0.64f, h * 0.52f)
            val waveTopLeft = Offset(cx - waveArcSize.width / 2f, pinCy - waveArcSize.height / 2f)
            drawArc(
                color = Color(0xFF38BDF8),
                startAngle = 142f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = waveTopLeft,
                size = waveArcSize,
                style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF34D399),
                startAngle = 322f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = waveTopLeft,
                size = waveArcSize,
                style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
            )

            // 4. White GPS Destination Pin Silhouette
            val pinPath = Path().apply {
                moveTo(cx, pinTipY)
                cubicTo(
                    cx - pinRadius * 0.55f,
                    pinCy + pinRadius * 1.15f,
                    cx - pinRadius,
                    pinCy + pinRadius * 0.45f,
                    cx - pinRadius,
                    pinCy
                )
                cubicTo(
                    cx - pinRadius,
                    pinCy - pinRadius * 0.6f,
                    cx - pinRadius * 0.55f,
                    pinCy - pinRadius,
                    cx,
                    pinCy - pinRadius
                )
                cubicTo(
                    cx + pinRadius * 0.55f,
                    pinCy - pinRadius,
                    cx + pinRadius,
                    pinCy - pinRadius * 0.6f,
                    cx + pinRadius,
                    pinCy
                )
                cubicTo(
                    cx + pinRadius,
                    pinCy + pinRadius * 0.45f,
                    cx + pinRadius * 0.55f,
                    pinCy + pinRadius * 1.15f,
                    cx,
                    pinTipY
                )
                close()
            }
            drawPath(
                path = pinPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color(0xFFE0F2FE))
                )
            )

            // 5. Inner Emerald-Blue Geofence Core
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF10B981), Color(0xFF1D4ED8))
                ),
                radius = pinRadius * 0.62f,
                center = Offset(cx, pinCy)
            )

            // 6. Inner White Wake-Up Pulse Dot
            drawCircle(
                color = Color.White,
                radius = pinRadius * 0.25f,
                center = Offset(cx, pinCy)
            )
        }
    }
}
