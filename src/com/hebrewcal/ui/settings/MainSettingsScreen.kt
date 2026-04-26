package com.hebrewcal.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebrewcal.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToZmanimSelection: () -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    val prefs by viewModel.preferences.collectAsState()
    val geocodeStatus by viewModel.geocodeStatus.collectAsState()
    var cityInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("לוח שנה עברי", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Hebrew Calendar Settings", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── Calendar Section ──────────────────────────────────────────
            SettingsSectionHeader(icon = Icons.Default.CalendarMonth, title = "Calendar")

            // Language
            SegmentedSettingRow(
                label   = "Display Language",
                options = listOf("English", "עברית"),
                selected = if (prefs.language == CalendarLanguage.ENGLISH) 0 else 1,
                onSelect = { idx ->
                    viewModel.setLanguage(if (idx == 0) CalendarLanguage.ENGLISH else CalendarLanguage.HEBREW)
                }
            )

            // Location
            SegmentedSettingRow(
                label    = "Holiday Location",
                subtitle = "Affects holiday duration and Yom Tov rules",
                options  = listOf("Diaspora", "Israel"),
                selected = if (prefs.location == CalendarLocation.DIASPORA) 0 else 1,
                onSelect = { idx ->
                    viewModel.setLocation(if (idx == 0) CalendarLocation.DIASPORA else CalendarLocation.ISRAEL)
                }
            )

            // Show Parsha
            SwitchSettingRow(
                label    = "Show Weekly Parsha",
                subtitle = "Displayed on Shabbat",
                checked  = prefs.showParsha,
                onCheck  = viewModel::setShowParsha
            )

            // Show Gregorian
            SwitchSettingRow(
                label   = "Show Gregorian Date",
                checked = prefs.showGregorianDate,
                onCheck = viewModel::setShowGregorian
            )

            Spacer(Modifier.height(8.dp))

            // ── Zmanim Section ────────────────────────────────────────────
            SettingsSectionHeader(icon = Icons.Default.Schedule, title = "Zmanim (Prayer Times)")

            SwitchSettingRow(
                label   = "Show Zmanim",
                checked = prefs.showZmanim,
                onCheck = viewModel::setShowZmanim
            )

            if (prefs.showZmanim) {

                // Time format
                SegmentedSettingRow(
                    label    = "Time Format",
                    options  = listOf("12-hour", "24-hour"),
                    selected = if (prefs.zmanimTimeFormat == ZmanimTimeFormat.TWELVE_HOUR) 0 else 1,
                    onSelect = { idx ->
                        viewModel.setZmanimTimeFormat(
                            if (idx == 0) ZmanimTimeFormat.TWELVE_HOUR else ZmanimTimeFormat.TWENTY_FOUR_HOUR
                        )
                    }
                )

                // Location source
                SegmentedSettingRow(
                    label    = "Location for Zmanim",
                    subtitle = "Used to calculate accurate prayer times",
                    options  = listOf("GPS (Auto)", "Manual City"),
                    selected = if (prefs.zmanimLocationSource == ZmanimLocationSource.GPS) 0 else 1,
                    onSelect = { idx ->
                        val src = if (idx == 0) ZmanimLocationSource.GPS else ZmanimLocationSource.MANUAL
                        viewModel.setZmanimLocationSource(src)
                        if (src == ZmanimLocationSource.GPS) onRequestLocationPermission()
                    }
                )

                // Manual city input
                if (prefs.zmanimLocationSource == ZmanimLocationSource.MANUAL) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("City Name", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value         = cityInput.ifEmpty { prefs.zmanimManualCity },
                                    onValueChange = { cityInput = it },
                                    modifier      = Modifier.weight(1f),
                                    placeholder   = { Text("e.g. Jerusalem, New York") },
                                    singleLine    = true
                                )
                                Button(
                                    onClick  = { viewModel.geocodeAndSaveCity(cityInput) },
                                    enabled  = cityInput.isNotBlank() &&
                                            geocodeStatus !is SettingsViewModel.GeoCodeStatus.Loading
                                ) {
                                    Text("Set")
                                }
                            }
                            when (val status = geocodeStatus) {
                                is SettingsViewModel.GeoCodeStatus.Loading ->
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                is SettingsViewModel.GeoCodeStatus.Success ->
                                    Text("✓ Location set: ${status.city}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall)
                                is SettingsViewModel.GeoCodeStatus.Error ->
                                    Text(status.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall)
                                else -> {}
                            }
                        }
                    }
                }

                // GPS permission hint
                if (prefs.zmanimLocationSource == ZmanimLocationSource.GPS &&
                    !viewModel.hasLocationPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.LocationOff, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                "Location permission required for GPS mode. Tap to grant.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Navigate to zmanim selection
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick  = onNavigateToZmanimSelection
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Select Zmanim to Display",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium)
                            val count = prefs.selectedZmanim.size
                            Text("$count zman${if (count != 1) "im" else ""} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp))
        Text(title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun SwitchSettingRow(
    label: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheck: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheck)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedSettingRow(
    label: String,
    subtitle: String? = null,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { idx, option ->
                    SegmentedButton(
                        shape    = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                        onClick  = { onSelect(idx) },
                        selected = selected == idx,
                        label    = { Text(option) }
                    )
                }
            }
        }
    }
}
