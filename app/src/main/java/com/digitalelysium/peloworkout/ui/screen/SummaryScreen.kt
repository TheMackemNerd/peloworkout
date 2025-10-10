// ui/screen/SummaryScreen.kt
package com.digitalelysium.peloworkout.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.digitalelysium.peloworkout.ui.util.formatElapsed
import com.digitalelysium.peloworkout.ui.util.fmt
import com.digitalelysium.peloworkout.ui.workout.WorkoutSummary
import java.time.LocalDate

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SummaryScreen(
    data: WorkoutSummary,
    onUpload: (title: String) -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Workout summary") }) },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f)
                ) { Text("Close") }

                Button(
                    onClick = { onUpload("Peloton workout ${LocalDate.now()}") },
                    modifier = Modifier.weight(1f)
                ) { Text("Upload to Strava") }
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Big numbers at the top
            Text(
                text = formatElapsed(data.elapsed),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))

            // Simple two-column grid
            SummaryRow("Distance", "${fmt(data.distanceKm, 2)} km")
            SummaryRow("Avg power", "${data.avgPowerW} W")
            SummaryRow("Top power", "${data.topPowerW} W")
            SummaryRow("Top speed", "${fmt(data.topSpeedKph, 1)} kph")
            SummaryRow("Total output", "${fmt(data.totalKJ, 1)} kJ")
            SummaryRow("Est. calories", "${data.estKcal} kcal")

            Spacer(Modifier.weight(1f))

            // Optional: hint text
            Text(
                text = "Review your ride, then upload to Strava or close to return.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}


