package com.hebrewcal.ui.settings

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hebrewcal.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToZmanimSelection: () -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsState()
    val geocodeStatus by viewModel.geocodeStatus.collectAsState()
    var cityInput by remember { mutableStateOf("") }

    val nm = context.getSystemService(NotificationManager::class.java)
    val hasDndAccess = nm.isNotificationPolicyAccessGranted

    // Detect OxygenOS/Android master lockscreen-notifications switch
    val lockscreenNotificationsEnabled = android.provider.Settings.Secure.getInt(
        context.contentResolver, "lock_screen_show_notifications", 1
    ) != 0
    var cityDropdownExpanded by remember { mutableStateOf(false) }
    val citySuggestions = remember(cityInput) { CityDatabase.search(cityInput) }

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

            // ── Lockscreen master switch warning ──────────────────────────
            if (!lockscreenNotificationsEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 2.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Lock screen notifications are OFF",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                "Your device has \"Show notifications on lock screen\" disabled globally. No app can show on the lockscreen until you turn this on.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_SETTINGS)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Open Notification Settings") }
                        }
                    }
                }
            }

            // ── DND / Bedtime warning ─────────────────────────────────────
            if (!hasDndAccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Bedtime / Do Not Disturb blocks lockscreen",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "If Bedtime mode is on, the notification won't appear on the lockscreen. Grant Do Not Disturb access so the Hebrew Calendar can show through.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Grant Do Not Disturb Access") }
                        }
                    }
                }
            }

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
                        viewModel.setZmanimLocationSource(
                            if (idx == 0) ZmanimLocationSource.GPS else ZmanimLocationSource.MANUAL
                        )
                    }
                )

                // GPS mode — detect nearest city
                if (prefs.zmanimLocationSource == ZmanimLocationSource.GPS) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!viewModel.hasLocationPermission) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.LocationOff, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error)
                                    Text(
                                        "Location permission required. Tap below to grant.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Button(
                                    onClick  = onRequestLocationPermission,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Grant Location Permission") }
                            } else {
                                Button(
                                    onClick  = { viewModel.detectNearestCity() },
                                    enabled  = geocodeStatus !is SettingsViewModel.GeoCodeStatus.Loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp))
                                    Text("Detect Nearest City")
                                }
                                GeoStatusRow(geocodeStatus, prefs.zmanimManualCity)
                            }
                        }
                    }
                }

                // Manual mode — autocomplete city picker
                if (prefs.zmanimLocationSource == ZmanimLocationSource.MANUAL) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Search City", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ExposedDropdownMenuBox(
                                expanded = cityDropdownExpanded && citySuggestions.isNotEmpty(),
                                onExpandedChange = { cityDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value         = cityInput,
                                    onValueChange = { cityInput = it; cityDropdownExpanded = true },
                                    modifier      = Modifier.fillMaxWidth().menuAnchor(),
                                    placeholder   = { Text("Type a city name…") },
                                    singleLine    = true,
                                    trailingIcon  = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = cityDropdownExpanded && citySuggestions.isNotEmpty()
                                        )
                                    }
                                )
                                ExposedDropdownMenu(
                                    expanded = cityDropdownExpanded && citySuggestions.isNotEmpty(),
                                    onDismissRequest = { cityDropdownExpanded = false }
                                ) {
                                    citySuggestions.forEach { city ->
                                        DropdownMenuItem(
                                            text    = { Text(city.displayName) },
                                            onClick = {
                                                cityInput = city.displayName
                                                cityDropdownExpanded = false
                                                viewModel.selectCity(city)
                                            }
                                        )
                                    }
                                }
                            }
                            GeoStatusRow(geocodeStatus, prefs.zmanimManualCity)
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

@Composable
private fun GeoStatusRow(status: SettingsViewModel.GeoCodeStatus, currentCity: String) {
    when (status) {
        is SettingsViewModel.GeoCodeStatus.Loading ->
            LinearProgressIndicator(Modifier.fillMaxWidth())
        is SettingsViewModel.GeoCodeStatus.Success ->
            Text("✓ ${status.city}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall)
        is SettingsViewModel.GeoCodeStatus.Error ->
            Text(status.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        is SettingsViewModel.GeoCodeStatus.Idle ->
            if (currentCity.isNotEmpty()) {
                Text("Current: $currentCity",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
    }
}
