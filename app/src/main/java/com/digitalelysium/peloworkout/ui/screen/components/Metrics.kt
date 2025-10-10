package com.digitalelysium.peloworkout.ui.screen.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digitalelysium.peloworkout.ui.util.*
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance

lateinit var resistanceLabel: String

@Composable
internal fun NineTileGrid(
    cadence: Double?, power: Double?, resistancePct: Double?, resistanceRaw: Double?,
    distance: Double, speed: Double?, totalKJ: Double,
    peakPower: Double, peakSpeed: Double, calories: Double
) {
    resistanceLabel = when {
        resistancePct != null -> "${fmt(resistancePct, 0)} %"
        resistanceRaw != null -> fmt(resistanceRaw, 0)
        else -> "--"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Cadence", "${fmt(cadence,0)} rpm", MetricType.Cadence, cadence, Modifier.weight(1f))
            MetricCard("Output",  "${fmt(power,0)} W",  MetricType.Output,power,   Modifier.weight(1f))
            MetricCard("Resistance", resistanceLabel, MetricType.Resistance,
                resistanceRaw, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Peak Power", "${fmt(peakPower,0)} W", MetricType.Output,peakPower, Modifier.weight(1f))
            MetricCard("Speed",    "${fmt(speed,1)} kph", MetricType.Speed,speed,  Modifier.weight(1f))
            MetricCard("Peak Speed", "${fmt(peakSpeed,1)} kph", MetricType.Speed,peakSpeed,Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Distance", "${fmt(distance,2)} km", MetricType.Plain, 0.0, Modifier.weight(1f))
            MetricCard("Total Output", "${fmt(totalKJ,1)} kJ", MetricType.Plain,0.0, Modifier.weight(1f))
            MetricCard("Calories",   "${fmt(calories,0)} kcal",MetricType.Plain,0.0, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MetricCard(
    title: String,
    valueText: String,
    metricType: MetricType = MetricType.Plain,
    numericValue: Double? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val targetBg = metricBackground(metricType, numericValue) ?: MaterialTheme.colorScheme.surface
    val bg by animateColorAsState(targetValue = targetBg, animationSpec = tween(220), label = "metricBg")

    // Choose on-colour by contrast (pick the better of black/white)
    val on = remember(bg) { bestOnColor(bg) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = bg,
            contentColor = on
        )
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title)
            Spacer(Modifier.height(6.dp))
            Text(valueText, style = MaterialTheme.typography.titleMedium)
        }
    }
}

enum class MetricType { Resistance, Output, Cadence, Speed, Plain }

fun metricBackground(metric: MetricType, value: Double?): Color? = when (metric) {
    MetricType.Resistance -> value?.let { heatColor(it, 100.0) }
    MetricType.Output     -> value?.let { heatColor(min(it, 400.0), 400.0) }   // >400 clamps to max red
    MetricType.Cadence    -> value?.let { heatColor(min(it, 120.0), 120.0) }   // 120 rpm = “red”
    MetricType.Speed      -> value?.let { heatColor(min(it, 50.0), 50.0) }     // 50 kph cap feels right indoors
    MetricType.Plain      -> Color(0xFF2C2C2C)
}

/** Green→Red ramp. Tweak sat/light if you want punchier colours. */
fun heatColor(value: Double, max: Double): Color {
    val v = if (max <= 0.0) 0.0 else (value / max).coerceIn(0.0, 1.0)
    val hue = 120f * (1f - v.toFloat())          // 120 = green, 0 = red
    return Color.hsl(hue = hue, saturation = 0.70f, lightness = 0.45f)
}

/** Pick black or white for best contrast against bg. */
fun bestOnColor(bg: Color): Color {
    // WCAG-ish quick check: choose the higher contrast of white vs black.
    fun contrast(fg: Color): Double {
        fun rl(c: Color): Double {
            // relative luminance (sRGB quick approx)
            val l = c.luminance().toDouble()
            return l
        }
        val l1 = max(rl(fg), rl(bg))
        val l2 = min(rl(fg), rl(bg))
        return (l1 + 0.05) / (l2 + 0.05)
    }
    val white = Color.White
    val black = Color.Black
    return if (contrast(white) >= contrast(black)) white else black
}