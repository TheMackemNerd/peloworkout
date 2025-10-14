package com.digitalelysium.peloworkout.ui.screen.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.digitalelysium.peloworkout.data.Gender
import com.digitalelysium.peloworkout.data.UserProfile

@Composable
fun UserProfileDialog(
    initial: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {

    var massText by rememberSaveable { mutableStateOf("") }
    var ageText by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf(initial.gender) }

    LaunchedEffect(initial) {
        massText = initial.massKg?.toString().orEmpty()
        ageText = initial.age?.toString().orEmpty()
        gender = initial.gender
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("User profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Info box
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp
                ) {
                    Text(
                        "Optional. Stored only on this device. Used to refine calorie estimates.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = massText,
                    onValueChange = { massText = it },
                    label = { Text("Mass (kg)") },
                    placeholder = { Text("e.g. 82.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it },
                    label = { Text("Age") },
                    placeholder = { Text("years") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Gender toggle
                Text("Gender", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gender == Gender.Male,
                        onClick = { gender = Gender.Male },
                        label = { Text("Male") }
                    )
                    FilterChip(
                        selected = gender == Gender.Female,
                        onClick = { gender = Gender.Female },
                        label = { Text("Female") }
                    )
                    FilterChip(
                        selected = gender == Gender.Unspecified,
                        onClick = { gender = Gender.Unspecified },
                        label = { Text("N/A") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mass = massText.toDoubleOrNull()
                val age = ageText.toIntOrNull()
                onSave(UserProfile(massKg = mass, age = age, gender = gender))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
