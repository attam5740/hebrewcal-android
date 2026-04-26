package com.hebrewcal.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebrewcal.data.ZmanimKeys

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZmanimSelectionScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Zmanim") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choose which prayer times appear in your lockscreen notification. " +
                    "The next upcoming zman will be highlighted automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Group zmanim into sections for clarity
            item {
                ZmanimGroupHeader("Morning")
            }

            items(
                listOf(
                    ZmanimKeys.ALOT_HASHACHAR,
                    ZmanimKeys.MISHEYAKIR,
                    ZmanimKeys.HANETZ
                )
            ) { key -> ZmanCheckRow(key, prefs.selectedZmanim, viewModel::toggleZman) }

            item { ZmanimGroupHeader("Morning Prayers") }

            items(
                listOf(
                    ZmanimKeys.SOF_ZMAN_SHEMA_GRA,
                    ZmanimKeys.SOF_ZMAN_SHEMA_MGA,
                    ZmanimKeys.SOF_ZMAN_TEFILLA_GRA,
                    ZmanimKeys.SOF_ZMAN_TEFILLA_MGA
                )
            ) { key -> ZmanCheckRow(key, prefs.selectedZmanim, viewModel::toggleZman) }

            item { ZmanimGroupHeader("Afternoon") }

            items(
                listOf(
                    ZmanimKeys.CHATZOT,
                    ZmanimKeys.MINCHA_GEDOLA,
                    ZmanimKeys.MINCHA_KETANA,
                    ZmanimKeys.PLAG_HAMINCHA
                )
            ) { key -> ZmanCheckRow(key, prefs.selectedZmanim, viewModel::toggleZman) }

            item { ZmanimGroupHeader("Evening") }

            items(
                listOf(
                    ZmanimKeys.SHKIAH,
                    ZmanimKeys.TZET_HAKOCHAVIM,
                    ZmanimKeys.TZET_RABBEINU_TAM
                )
            ) { key -> ZmanCheckRow(key, prefs.selectedZmanim, viewModel::toggleZman) }

            item {
                Spacer(Modifier.height(8.dp))
                val count = prefs.selectedZmanim.size
                if (count == 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            "No zmanim selected — the zmanim section will be hidden in the notification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        "$count zman${if (count != 1) "im" else ""} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ZmanimGroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ZmanCheckRow(
    key: String,
    selectedZmanim: Set<String>,
    onToggle: (String, Boolean) -> Unit
) {
    val isChecked = key in selectedZmanim
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick  = { onToggle(key, !isChecked) },
        colors   = CardDefaults.cardColors(
            containerColor = if (isChecked)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                ZmanimKeys.displayName(key),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (isChecked) "Selected" else "Not selected",
                tint = if (isChecked) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
