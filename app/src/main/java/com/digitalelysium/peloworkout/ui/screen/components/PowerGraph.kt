package com.digitalelysium.peloworkout.ui.screen.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
internal fun PowerGraph(
    modifier: Modifier = Modifier,
    points: List<Double>,
    maxPower: Double,
    elapsed: Int
) {
    if (points.isEmpty() || maxPower <= 0.0) return

    // Theme-aware colours
    val onSurface = MaterialTheme.colorScheme.onSurface
    val axisColor = onSurface.copy(alpha = 0.60f)
    val gridColor = onSurface.copy(alpha = 0.13f)
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier) {
        // label paint (Compose -> Android bridge) using theme colour
        val labelPaint = androidx.compose.ui.graphics.Paint().asFrameworkPaint().apply {
            color = onSurface.toArgb()
            textSize = 10.dp.toPx()
            isAntiAlias = true
        }

        val w = size.width
        val h = size.height
        val leftPad = 40.dp.toPx()
        val bottomPad = 32.dp.toPx()
        val topPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val plotW = w - leftPad - rightPad
        val plotH = h - topPad - bottomPad

        val yMax = max(50.0, kotlin.math.ceil(maxPower / 50.0) * 50.0)

        // axes
        drawLine(axisColor, Offset(leftPad, h - bottomPad), Offset(w - rightPad, h - bottomPad))
        drawLine(axisColor, Offset(leftPad, h - bottomPad), Offset(leftPad, topPad))

        // Y ticks + labels every 50 W
        var yTick = 0.0
        while (yTick <= yMax + 0.1) {
            val y = (h - bottomPad) - (yTick.toFloat() / yMax.toFloat() * plotH)
            drawLine(axisColor, Offset(leftPad - 6f, y), Offset(leftPad, y))
            drawIntoCanvas { c ->
                c.nativeCanvas.drawText(
                    yTick.toInt().toString(),
                    8.dp.toPx(),
                    y + 3.dp.toPx(),
                    labelPaint
                )
            }
            yTick += 50.0
        }

        // X ticks every 5 minutes
        val n = points.size
        val stepX = if (n > 1) plotW / (n - 1) else plotW
        val fiveMin = 300
        if (elapsed >= fiveMin) {
            var s = fiveMin
            while (s < elapsed) {
                val x = leftPad + s * (plotW / elapsed.toFloat())
                drawLine(axisColor, Offset(x, h - bottomPad), Offset(x, h - bottomPad + 6f))
                drawIntoCanvas { c ->
                    c.nativeCanvas.drawText("${s / 60}m", x - 8.dp.toPx(), h - 6.dp.toPx(), labelPaint)
                }
                s += fiveMin
            }
        }

        // subtle horizontal gridlines at each 50 W
        yTick = 0.0
        while (yTick <= yMax + 0.1) {
            val y = (h - bottomPad) - (yTick.toFloat() / yMax.toFloat() * plotH)
            drawLine(gridColor, Offset(leftPad, y), Offset(w - rightPad, y))
            yTick += 50.0
        }

        // power polyline
        var last: Offset? = null
        points.forEachIndexed { i, v ->
            val x = leftPad + i * stepX
            val y = (h - bottomPad) - (v.toFloat() / yMax.toFloat() * plotH)
            val pt = Offset(x, y)
            last?.let { drawLine(lineColor, it, pt, strokeWidth = 3f) }
            last = pt
        }
    }
}
