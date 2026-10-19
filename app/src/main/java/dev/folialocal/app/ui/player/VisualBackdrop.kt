package dev.folialocal.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import dev.folialocal.app.data.audio.AudioLevels
import dev.folialocal.app.data.model.Track
import dev.folialocal.app.data.settings.VisualStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private data class Dust(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val phase: Float,
)

private data class CloudPoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val size: Float,
)

/**
 * 全屏播放效果的背景层。每个样式都有独立的 Canvas 绘制，
 * 并共享音频响度/频段与时间动画。
 */
@Composable
fun VisualBackdrop(
    style: VisualStyle,
    track: Track?,
    accent: Color,
    levels: AudioLevels,
    ghostText: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    if (style == VisualStyle.COVER) return
    val textMeasurer = rememberTextMeasurer()
    val ghostLayouts = remember(ghostText, textMeasurer) {
        ghostText.filterNot { it.isWhitespace() }
            .take(10)
            .map { char ->
                textMeasurer.measure(
                    text = AnnotatedString(char.toString()),
                    style = TextStyle(
                        fontSize = 128.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB9C7D8),
                        drawStyle = Stroke(width = 1.6f),
                    ),
                )
            }
    }
    val dust = remember(style) {
        val random = Random(style.ordinal * 977L + 31L)
        List(if (style == VisualStyle.LUMI) 110 else 36) {
            Dust(
                x = random.nextFloat(),
                y = random.nextFloat(),
                size = 0.6f + random.nextFloat() * 2.4f,
                speed = 0.006f + random.nextFloat() * 0.018f,
                phase = random.nextFloat() * (PI.toFloat() * 2f),
            )
        }
    }
    val cloud = remember {
        val random = Random(5231L)
        List(90) {
            CloudPoint(
                x = (random.nextFloat() * 2f - 1f),
                y = (random.nextFloat() * 1.4f - 0.7f),
                z = random.nextFloat(),
                size = 0.6f + random.nextFloat() * 2.2f,
            )
        }
    }
    var time by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frame -> time = frame }
        }
    }
    val seconds = time / 1000f
    val energy = (levels.rms * 1.8f + levels.pulse * 0.8f).coerceIn(0f, 1f)

    Canvas(modifier = modifier.fillMaxSize()) {
        when (style) {
            VisualStyle.LUMI -> drawLumi(accent, seconds, energy, dust, ghostLayouts)
            VisualStyle.FUME -> drawFume(accent, seconds, energy, progress, ghostLayouts)
            VisualStyle.CAD -> drawCad(accent, seconds, energy)
            VisualStyle.PAT -> drawPat(accent, seconds, energy, dust)
            VisualStyle.CAPPELLA -> drawCappella(accent, seconds, energy)
            VisualStyle.TILT -> drawTilt(accent, seconds)
            VisualStyle.DIORAMA -> drawDiorama(accent, seconds, energy, cloud)
            VisualStyle.PENDOLO -> drawPendolo(accent, seconds, energy)
            VisualStyle.SPECTRUM -> drawSpectrum(accent, levels, seconds)
            VisualStyle.COVER -> Unit
        }
        drawScrim(style)
    }
}

private fun DrawScope.drawLumi(
    accent: Color,
    time: Float,
    energy: Float,
    dust: List<Dust>,
    ghosts: List<TextLayoutResult>,
) {
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF070C15), Color(0xFF0F1629), Color(0xFF080D16)),
        ),
    )
    val beamX = size.width * (0.58f + 0.035f * sin(time * 0.37f))
    val path = Path().apply {
        moveTo(beamX - size.width * 0.17f, -size.height * 0.05f)
        lineTo(beamX + size.width * 0.17f, -size.height * 0.05f)
        lineTo(beamX + size.width * 0.34f, size.height * 1.05f)
        lineTo(beamX - size.width * 0.12f, size.height * 1.05f)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.20f),
                accent.copy(alpha = 0.10f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height,
        ),
    )
    val secondBeamX = size.width * (0.42f - 0.025f * sin(time * 0.31f))
    val secondPath = Path().apply {
        moveTo(secondBeamX - size.width * 0.08f, 0f)
        lineTo(secondBeamX + size.width * 0.08f, 0f)
        lineTo(secondBeamX + size.width * 0.18f, size.height)
        lineTo(secondBeamX - size.width * 0.05f, size.height)
        close()
    }
    drawPath(
        path = secondPath,
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.8f,
        ),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.14f + energy * 0.08f), Color.Transparent),
            center = Offset(size.width * 0.52f, size.height * 0.42f),
            radius = size.minDimension * 0.5f,
        ),
        radius = size.minDimension * 0.5f,
        center = Offset(size.width * 0.52f, size.height * 0.42f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.12f + energy * 0.08f), Color.Transparent),
            center = Offset(size.width * 0.62f, size.height * 0.35f),
            radius = size.minDimension * 0.55f,
        ),
        radius = size.minDimension * 0.55f,
        center = Offset(size.width * 0.62f, size.height * 0.35f),
    )

    ghosts.forEachIndexed { index, layout ->
        val drift = (time * (0.012f + index * 0.002f) + index * 0.13f) % 1f
        val x = size.width * (0.08f + 0.74f * ((index * 0.618f) % 1f))
        val y = size.height * (0.08f + drift * 0.78f)
        val rotation = sin(time * 0.2f + index) * 12f
        rotate(rotation, Offset(x, y)) {
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x, y),
                alpha = 0.16f + energy * 0.06f,
            )
            if (index % 2 == 0) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x + 4f, y + 4f),
                    alpha = 0.05f,
                )
            }
        }
    }

    dust.forEachIndexed { index, particle ->
        val drift = (particle.y - time * particle.speed) % 1f
        val y = if (drift < 0f) drift + 1f else drift
        val x = particle.x + sin(time * 0.3f + particle.phase) * 0.008f
        val alpha = 0.20f + 0.30f * (0.5f + 0.5f * sin(time * 1.7f + particle.phase)) +
            energy * 0.25f
        drawCircle(
            color = Color(0xFFD9E4F5).copy(alpha = alpha.coerceIn(0f, 0.75f)),
            radius = particle.size * density * 1.2f,
            center = Offset(
                x * size.width,
                y * size.height,
            ),
        )
        if (index % 11 == 0) {
            drawCircle(
                color = accent.copy(alpha = 0.25f),
                radius = particle.size * density * 3.4f,
                center = Offset(x * size.width, y * size.height),
            )
        }
    }
}

private fun DrawScope.drawFume(
    accent: Color,
    time: Float,
    energy: Float,
    progress: Float,
    ghosts: List<TextLayoutResult>,
) {
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF0A0F19), Color(0xFF10172A), Color(0xFF080C15)),
        ),
    )
    val random = Random(90210L)
    repeat(190) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        drawCircle(
            color = Color(0xFF9DA7B8).copy(alpha = 0.015f + random.nextFloat() * 0.02f),
            radius = 0.6f * density,
            center = Offset(x, y),
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.10f + energy * 0.07f), Color.Transparent),
            center = Offset(size.width * 0.64f, size.height * 0.44f),
            radius = size.minDimension * 0.5f,
        ),
        radius = size.minDimension * 0.5f,
        center = Offset(size.width * 0.64f, size.height * 0.44f),
    )
    ghosts.take(4).forEachIndexed { index, layout ->
        val x = size.width * (0.08f + index * 0.19f)
        val y = size.height * (0.12f + index * 0.16f)
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(x, y),
            alpha = 0.035f,
        )
    }
    val printX = size.width * (0.10f + 0.78f * progress.coerceIn(0f, 1f))
    drawLine(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, accent.copy(alpha = 0.25f), Color.Transparent),
        ),
        start = Offset(printX, size.height * 0.30f),
        end = Offset(printX, size.height * 0.72f),
        strokeWidth = 1.4f * density,
    )
    drawRoundRect(
        color = accent.copy(alpha = 0.35f + 0.1f * sin(time * 3f)),
        topLeft = Offset(printX - 8f * density, size.height * 0.43f),
        size = Size(16f * density, 3f * density),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * density),
    )
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.34f)),
        ),
    )
}

private fun DrawScope.drawCad(accent: Color, time: Float, energy: Float) {
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color(0xFF111A2C), Color(0xFF080C16)),
            center = Offset(size.width * 0.5f, size.height * 0.4f),
            radius = size.maxDimension * 0.8f,
        ),
    )
    val center = Offset(size.width * 0.5f, size.height * 0.44f)
    val baseRadius = size.minDimension * 0.32f
    repeat(3) { ring ->
        drawCircle(
            color = accent.copy(alpha = 0.08f + ring * 0.02f),
            radius = baseRadius * (1f + ring * 0.32f),
            center = center,
            style = Stroke(width = 1f * density),
        )
    }
    val count = 14
    val points = List(count) { index ->
        val angle = (index.toFloat() / count) * PI.toFloat() * 2f + time * 0.12f
        val radius = baseRadius * (1.05f + 0.12f * sin(time * 0.6f + index))
        Offset(
            center.x + cos(angle) * radius,
            center.y + sin(angle) * radius * 0.72f,
        )
    }
    points.forEachIndexed { index, point ->
        val next = points[(index + 1) % points.size]
        drawLine(
            color = accent.copy(alpha = 0.10f + energy * 0.06f),
            start = point,
            end = next,
            strokeWidth = 1f * density,
        )
        if (index % 3 == 0) {
            drawLine(
                color = accent.copy(alpha = 0.06f),
                start = center,
                end = point,
                strokeWidth = 1f * density,
            )
        }
    }
    points.forEachIndexed { index, point ->
        val pulse = 1f + 0.7f * sin(time * 1.6f + index * 0.7f).coerceAtLeast(0f)
        val radius = (1.6f + energy * 3.4f) * density * pulse
        drawCircle(color = accent.copy(alpha = 0.55f), radius = radius, center = point)
        drawCircle(color = accent.copy(alpha = 0.10f), radius = radius * 3.2f, center = point)
    }
}

private fun DrawScope.drawPat(accent: Color, time: Float, energy: Float, dust: List<Dust>) {
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF0B1320), Color(0xFF182333), Color(0xFF090D15)),
        ),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.08f + energy * 0.04f), Color.Transparent),
            center = Offset(size.width * 0.42f, size.height * 0.3f),
            radius = size.minDimension * 0.7f,
        ),
        radius = size.minDimension * 0.7f,
        center = Offset(size.width * 0.42f, size.height * 0.3f),
    )
    repeat(6) { layer ->
        val y = size.height * (0.28f + layer * 0.09f)
        val shift = sin(time * 0.18f + layer * 0.9f) * size.width * 0.03f
        val height = size.height * (0.035f + layer * 0.004f)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFB9CBDA).copy(alpha = 0.045f + layer * 0.008f),
                    Color.Transparent,
                ),
            ),
            topLeft = Offset(-size.width * 0.2f + shift, y),
            size = Size(size.width * 1.4f, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(height * 0.5f),
        )
    }
    dust.forEach { particle ->
        val x = particle.x + sin(time * 0.2f + particle.phase) * 0.01f
        val y = (particle.y + time * particle.speed * 0.5f) % 1f
        drawCircle(
            color = Color.White.copy(alpha = 0.08f + energy * 0.10f),
            radius = particle.size * density * 0.8f,
            center = Offset(x * size.width, y * size.height),
        )
    }
}

private fun DrawScope.drawCappella(accent: Color, time: Float, energy: Float) {
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color(0xFF1A1424), Color(0xFF0C0F18)),
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = size.maxDimension * 0.8f,
        ),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.10f + energy * 0.06f), Color.Transparent),
            center = Offset(size.width * 0.55f, size.height * 0.4f),
            radius = size.minDimension * 0.6f,
        ),
        radius = size.minDimension * 0.6f,
        center = Offset(size.width * 0.55f, size.height * 0.4f),
    )
    val colors = listOf(
        Color(0xFFFFB3D1),
        Color(0xFFB7C9FF),
        Color(0xFFFFE0A8),
        Color(0xFFA8F0D1),
    )
    val positions = listOf(
        0.12f to 0.22f,
        0.86f to 0.28f,
        0.08f to 0.72f,
        0.90f to 0.68f,
        0.24f to 0.86f,
        0.76f to 0.16f,
    )
    positions.forEachIndexed { index, (x, y) ->
        val bob = sin(time * 1.2f + index * 1.3f) * size.height * 0.012f
        val radius = (20f + index % 3 * 4f + energy * 10f) * density
        val center = Offset(x * size.width, y * size.height + bob)
        val color = colors[index % colors.size]
        drawCircle(color = color.copy(alpha = 0.10f), radius = radius * 1.8f, center = center)
        drawCircle(color = color.copy(alpha = 0.18f), radius = radius, center = center)
        drawCircle(
            color = color.copy(alpha = 0.65f),
            radius = radius * 0.16f,
            center = Offset(center.x - radius * 0.28f, center.y - radius * 0.12f),
        )
        drawCircle(
            color = color.copy(alpha = 0.65f),
            radius = radius * 0.16f,
            center = Offset(center.x + radius * 0.28f, center.y - radius * 0.12f),
        )
        drawArc(
            color = color.copy(alpha = 0.55f),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.32f, center.y + radius * 0.05f),
            size = Size(radius * 0.64f, radius * 0.5f),
            style = Stroke(width = 1.8f * density),
        )
    }
}

private fun DrawScope.drawTilt(accent: Color, time: Float) {
    drawRect(Color(0xFF111729))
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
            center = Offset(size.width * 0.5f, size.height * 0.5f),
            radius = size.maxDimension * 0.7f,
        ),
    )
    val ringCenter = Offset(size.width * 0.86f, size.height * 0.5f)
    drawCircle(
        color = accent.copy(alpha = 0.14f),
        radius = size.minDimension * 0.11f,
        center = ringCenter,
        style = Stroke(width = 1.2f * density),
    )
    drawCircle(
        color = accent.copy(alpha = 0.10f),
        radius = size.minDimension * 0.045f,
        center = ringCenter,
        style = Stroke(width = 1f * density),
    )
    drawLine(
        color = accent.copy(alpha = 0.10f),
        start = Offset(size.width * 0.72f, ringCenter.y),
        end = Offset(size.width * 0.96f, ringCenter.y),
        strokeWidth = 1f * density,
    )
    drawLine(
        color = accent.copy(alpha = 0.06f + 0.02f * sin(time * 0.8f)),
        start = Offset(size.width * 0.5f, size.height * 0.3f),
        end = Offset(size.width * 0.5f, size.height * 0.7f),
        strokeWidth = 1f * density,
    )
}

private fun DrawScope.drawDiorama(
    accent: Color,
    time: Float,
    energy: Float,
    cloud: List<CloudPoint>,
) {
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color(0xFF101526), Color(0xFF080B14)),
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = size.maxDimension * 0.8f,
        ),
    )
    val center = Offset(size.width * 0.5f, size.height * 0.46f)
    cloud.forEachIndexed { index, point ->
        val z = (point.z + time * 0.045f) % 1f
        val scale = 0.30f + z * 0.70f
        val x = center.x + point.x * size.width * 0.52f * scale
        val y = center.y + point.y * size.height * 0.36f * scale
        val fog = sin(z * PI.toFloat()).coerceIn(0f, 1f)
        val alpha = (0.10f + fog * 0.55f) * (0.7f + energy * 0.6f)
        val radius = point.size * density * (0.6f + z * 1.6f)
        drawCircle(
            color = if (index % 5 == 0) accent.copy(alpha = alpha) else Color.White.copy(alpha = alpha * 0.8f),
            radius = radius,
            center = Offset(x, y),
        )
        if (index % 13 == 0) {
            drawCircle(
                color = accent.copy(alpha = alpha * 0.16f),
                radius = radius * 4.5f,
                center = Offset(x, y),
            )
        }
    }
    repeat(4) { index ->
        val z = ((index / 4f) + time * 0.03f) % 1f
        val scale = 0.3f + z * 0.7f
        val frameWidth = size.width * 0.26f * scale
        val frameHeight = size.height * 0.42f * scale
        drawRect(
            color = accent.copy(alpha = (1f - z) * 0.08f + 0.02f),
            topLeft = Offset(center.x - frameWidth / 2f, center.y - frameHeight / 2f),
            size = Size(frameWidth, frameHeight),
            style = Stroke(width = 1.2f * density),
        )
    }
}

private fun DrawScope.drawPendolo(accent: Color, time: Float, energy: Float) {
    drawRect(Color(0xFF020304))
    drawRect(
        Brush.horizontalGradient(
            listOf(Color(0xFF0A1215), Color(0xFF020304)),
        ),
    )
    val center = Offset(size.width * 0.02f, size.height * 0.5f)
    val radius = size.minDimension * 0.56f
    val lineColor = accent.copy(alpha = 0.18f)
    listOf(0.95f, 0.78f, 0.55f, 0.32f).forEach { factor ->
        drawCircle(
            color = lineColor,
            radius = radius * factor,
            center = center,
            style = Stroke(width = 1f * density),
        )
    }
    val ticks = 60
    for (index in 0 until ticks) {
        val angle = index * (360f / ticks) + time * 1.2f
        val long = index % 5 == 0
        val inner = radius * if (long) 0.86f else 0.91f
        val outer = radius * 0.96f
        val radians = angle * PI.toFloat() / 180f
        drawLine(
            color = accent.copy(alpha = if (long) 0.26f else 0.12f),
            start = Offset(
                center.x + cos(radians) * inner,
                center.y + sin(radians) * inner,
            ),
            end = Offset(
                center.x + cos(radians) * outer,
                center.y + sin(radians) * outer,
            ),
            strokeWidth = if (long) 1.4f * density else 0.8f * density,
        )
    }
    // 齿轮
    listOf(
        Triple(0.20f, 0.30f, 38f),
        Triple(0.31f, 0.22f, 24f),
        Triple(0.26f, 0.68f, 30f),
        Triple(0.40f, 0.50f, 22f),
    ).forEachIndexed { index, (x, y, teeth) ->
        val gearCenter = Offset(size.width * x, size.height * y)
        val gearRadius = min(size.width, size.height) * (0.035f + index * 0.006f)
        val rotation = time * (12f + index * 3f) * (1f + energy)
        drawCircle(
            color = accent.copy(alpha = 0.16f),
            radius = gearRadius,
            center = gearCenter,
            style = Stroke(width = 1f * density),
        )
        repeat(teeth.toInt()) { tooth ->
            val angle = (tooth * 360f / teeth + rotation) * PI.toFloat() / 180f
            drawLine(
                color = accent.copy(alpha = 0.15f),
                start = Offset(
                    gearCenter.x + cos(angle) * gearRadius * 0.9f,
                    gearCenter.y + sin(angle) * gearRadius * 0.9f,
                ),
                end = Offset(
                    gearCenter.x + cos(angle) * gearRadius * 1.16f,
                    gearCenter.y + sin(angle) * gearRadius * 1.16f,
                ),
                strokeWidth = 1f * density,
            )
        }
    }
    // 摆轮与摆锤
    val pivot = Offset(size.width * 0.20f, size.height * 0.24f)
    val swing = sin(time * (1.5f + energy * 1.2f)) * 0.34f
    val pendulumEnd = Offset(
        pivot.x + sin(swing) * size.minDimension * 0.26f,
        pivot.y + cos(swing) * size.minDimension * 0.26f,
    )
    drawLine(
        color = accent.copy(alpha = 0.24f),
        start = pivot,
        end = pendulumEnd,
        strokeWidth = 1.2f * density,
    )
    drawCircle(color = accent.copy(alpha = 0.20f), radius = 8f * density, center = pendulumEnd)
    drawCircle(
        color = accent.copy(alpha = 0.10f),
        radius = 18f * density,
        center = pendulumEnd,
        style = Stroke(width = 1f * density),
    )
    // 指针
    val handAngle = time * 0.25f - PI.toFloat() / 2f
    drawLine(
        color = accent.copy(alpha = 0.28f),
        start = center,
        end = Offset(
            center.x + cos(handAngle) * radius * 0.42f,
            center.y + sin(handAngle) * radius * 0.42f,
        ),
        strokeWidth = 1.4f * density,
    )
    drawLine(
        color = accent.copy(alpha = 0.18f),
        start = Offset(0f, center.y),
        end = Offset(size.width, center.y),
        strokeWidth = 1f * density,
    )
}

private fun DrawScope.drawSpectrum(accent: Color, levels: AudioLevels, time: Float) {
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF080A12), Color(0xFF0E1220), Color(0xFF05060B)),
        ),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.10f + levels.pulse * 0.13f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.55f),
            radius = size.minDimension * 0.7f,
        ),
        radius = size.minDimension * 0.7f,
        center = Offset(size.width * 0.5f, size.height * 0.55f),
    )
    val bars = 36
    val gap = size.width * 0.008f
    val barWidth = (size.width - gap * (bars + 1)) / bars
    val baseY = size.height * 0.83f
    for (index in 0 until bars) {
        val band = levels.bands[(index * levels.bands.size / bars).coerceIn(0, levels.bands.lastIndex)]
        val height = size.height * (0.02f + band * 0.34f) * (0.8f + 0.2f * sin(time * 2f + index))
        val x = gap + index * (barWidth + gap)
        val brush = Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.15f)),
            startY = baseY - height,
            endY = baseY,
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(x, baseY - height),
            size = Size(barWidth, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.45f),
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.08f),
            topLeft = Offset(x, baseY + 2f * density),
            size = Size(barWidth, height * 0.35f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.45f),
        )
    }
}

private fun DrawScope.drawScrim(style: VisualStyle) {
    val topAlpha = when (style) {
        VisualStyle.TILT -> 0.12f
        VisualStyle.FUME -> 0.18f
        VisualStyle.LUMI -> 0.16f
        else -> 0.28f
    }
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = topAlpha),
            0.35f to Color.Transparent,
            0.72f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.62f),
        ),
    )
}
