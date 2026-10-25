package com.hebrewcal.ui.settings

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hebrewcal.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToZmanimSelection: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onSaveSettings: () -> Unit,
    onRestoreSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsState()
    val geocodeStatus by viewModel.geocodeStatus.collectAsState()
    var cityInput by remember(prefs.zmanimManualCity) { mutableStateOf(prefs.zmanimManualCity) }

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
                                "Your device has \"Show notifications on lock screen\" disabled globally. Go to Settings → Notifications & status bar → Lock screen and enable \"Show notifications\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                            OutlinedButton(
                                onClick = {
                                    val lockScreenIntent = Intent("android.settings.LOCK_SCREEN_SETTINGS")
                                    if (lockScreenIntent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(lockScreenIntent)
                                    } else {
                                        context.startActivity(
                                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Open Lock Screen Settings") }
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

            // Nusach
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                    Text("Custom (Nusach)", style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    Text("Which siddur the prayer texts follow", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                    listOf(
                        Nusach.ASHKENAZ to "Ashkenaz",
                        Nusach.SEFARD to "Sefard",
                        Nusach.EDOT_HAMIZRACH to "Edot HaMizrach",
                        Nusach.CHABAD to "Chabad (Siddur Torah Or)"
                    ).forEach { (n, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = prefs.nusach == n, role = Role.RadioButton) { viewModel.setNusach(n) }
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = prefs.nusach == n, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Show Parsha
            SwitchSettingRow(
                label    = "Show Weekly Parsha",
                subtitle = "Displayed on Shabbat",
                checked  = prefs.showParsha,
                onCheck  = viewModel::setShowParsha
            )

            if (prefs.showParsha) {
                SwitchSettingRow(
                    label    = "Show upcoming parsha on weekdays",
                    subtitle = "Displays next Shabbat's reading throughout the week",
                    checked  = prefs.showParshaOnWeekdays,
                    onCheck  = viewModel::setShowParshaOnWeekdays
                )
            }

            // Show Omer
            SwitchSettingRow(
                label    = "Show Omer Count",
                subtitle = "Displayed during the 49 days between Pesach and Shavuot",
                checked  = prefs.showOmer,
                onCheck  = viewModel::setShowOmer
            )

            // Show Gregorian
            SwitchSettingRow(
                label   = "Show Gregorian Date",
                checked = prefs.showGregorianDate,
                onCheck = viewModel::setShowGregorian
            )

            Spacer(Modifier.height(8.dp))

            // ── Reader Buttons Section ────────────────────────────────────
            SettingsSectionHeader(icon = Icons.Default.MenuBook, title = "Torah & Tehillim Reader")

            SwitchSettingRow(
                label    = "תהילים יומי · Daily Tehillim",
                subtitle = "Button on the widget and notification, opens today's chapters",
                checked  = prefs.showTehillimButton,
                onCheck  = viewModel::setShowTehillimButton
            )

            SwitchSettingRow(
                label    = "פרשת השבוע · Weekly Parsha",
                subtitle = "Button on the widget and notification, opens the upcoming parsha",
                checked  = prefs.showParshaButton,
                onCheck  = viewModel::setShowParshaButton
            )

            SwitchSettingRow(
                label    = "תהילים לאלול · Elul Tehillim",
                subtitle = "Three additional daily chapters from Rosh Chodesh Elul through Yom Kippur, shown in the daily Tehillim reader",
                checked  = prefs.showElulTehillim,
                onCheck  = viewModel::setShowElulTehillim
            )

            // Offline downloads — one row decides on/off and network; horizons only
            // appear once downloads are enabled, keeping the section compact.
            val networks = listOf(PrefetchNetwork.OFF, PrefetchNetwork.WIFI, PrefetchNetwork.ANY)
            SegmentedSettingRow(
                label    = "Download texts ahead",
                subtitle = "Fetch upcoming Tehillim and parsha in the background so they open offline (Any = Wi-Fi or mobile data)",
                options  = listOf("Off", "Wi-Fi only", "Any"),
                selected = networks.indexOf(prefs.prefetchNetwork).coerceAtLeast(0),
                onSelect = { viewModel.setPrefetchNetwork(networks[it]) }
            )

            if (prefs.prefetchNetwork != PrefetchNetwork.OFF) {
                val tehillimDays = listOf(3, 7, 14)
                SegmentedSettingRow(
                    label    = "Tehillim ahead",
                    options  = listOf("3 days", "1 week", "2 weeks"),
                    selected = tehillimDays.indexOf(prefs.prefetchTehillimDays).coerceAtLeast(0),
                    onSelect = { viewModel.setPrefetchTehillimDays(tehillimDays[it]) }
                )
                val parshaWeeks = listOf(1, 3, 6)
                SegmentedSettingRow(
                    label    = "Parsha ahead",
                    options  = listOf("1 week", "3 weeks", "6 weeks"),
                    selected = parshaWeeks.indexOf(prefs.prefetchParshaWeeks).coerceAtLeast(0),
                    onSelect = { viewModel.setPrefetchParshaWeeks(parshaWeeks[it]) }
                )
            }

            // Widget appearance
            OpacitySettingRow(
                label    = "Widget background opacity",
                subtitle = "How much of the wallpaper shows through the home-screen widget",
                percent  = prefs.widgetOpacity,
                onChange = viewModel::setWidgetOpacity
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
                                                cityInput = city.name
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

            Spacer(Modifier.height(8.dp))

            // ── Shabbat & Celestial Section ──────────────────────────────
            SettingsSectionHeader(icon = Icons.Default.WbSunny, title = "Shabbat & Celestial Features")

            SwitchSettingRow(
                label    = "Shabbat & Chag Dynamic Lockscreen",
                subtitle = "Active countdown on Friday and dignified Shabbat Shalom / Havdalah banner",
                checked  = prefs.showShabbatBanner,
                onCheck  = viewModel::setShowShabbatBanner
            )

            val candleOffsets = listOf(18, 20, 30, 40)
            SegmentedSettingRow(
                label    = "Candle Lighting Offset",
                subtitle = "Minutes before sunset on Friday / Erev Yom Tov",
                options  = candleOffsets.map { "${it}m" },
                selected = candleOffsets.indexOf(prefs.candleLightingOffsetMinutes).coerceAtLeast(0),
                onSelect = { idx -> viewModel.setCandleLightingOffset(candleOffsets[idx]) }
            )

            val havdalahOptions = listOf(
                HavdalahOpinion.STANDARD_8_5 to "8.5°",
                HavdalahOpinion.MINUTES_42 to "42 min",
                HavdalahOpinion.MINUTES_50 to "50 min",
                HavdalahOpinion.MINUTES_72_RT to "72m RT"
            )
            SegmentedSettingRow(
                label    = "Havdalah Opinion",
                subtitle = "Calculation used for Motzei Shabbat and Yom Tov",
                options  = havdalahOptions.map { it.second },
                selected = havdalahOptions.indexOfFirst { it.first == prefs.havdalahOpinion }.coerceAtLeast(0),
                onSelect = { idx -> viewModel.setHavdalahOpinion(havdalahOptions[idx].first) }
            )

            SwitchSettingRow(
                label    = "Show Celestial Arc & Astrolabe",
                subtitle = "Visual sun trajectory, shaot zmaniyot clock, and moon phase in Day Sheet",
                checked  = prefs.showCelestialArc,
                onCheck  = viewModel::setShowCelestialArc
            )

            Spacer(Modifier.height(24.dp))
            SettingsSectionHeader(icon = Icons.Default.Notifications, title = "Prayer Buttons")
            // One switch; when on, a compact checklist of which prayers to offer opens below it.
            SwitchSettingRow(
                label    = "Display siddur at prayer times",
                subtitle = "A notification button opens each prayer from your custom's siddur while its time is open",
                checked  = prefs.prayerButtonsOn,
                onCheck  = viewModel::setPrayerButtonsOn
            )
            AnimatedVisibility(visible = prefs.prayerButtonsOn) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        listOf(
                            Triple(PrayerButton.BIRCHOT_HASHACHAR, "ברכות השחר · Morning Blessings", "dawn – sof zman Shema"),
                            Triple(PrayerButton.SHACHARIT, "שחרית · Shacharit", "dawn – midday"),
                            Triple(PrayerButton.MINCHA, "מנחה · Mincha", "mincha gedola – nightfall"),
                            Triple(PrayerButton.CANDLE_LIGHTING, "הדלקת נרות · Candle Lighting", "1 h before – 1 h after"),
                            Triple(PrayerButton.HAVDALAH, "הבדלה · Havdalah", "3 h from Havdalah"),
                            Triple(PrayerButton.ARVIT, "ערבית · Arvit", "plag – dawn"),
                            Triple(PrayerButton.SHEMA_AL_HAMITA, "קריאת שמע על המיטה · Bedtime Shema", "nightfall – dawn")
                        ).forEach { (button, label, window) ->
                            val checked = button.key in prefs.prayerButtons
                            Row(
                                Modifier.fillMaxWidth()
                                    .toggleable(value = checked, role = Role.Checkbox) { on -> viewModel.setPrayerButton(button.key, on) }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                    Text(window, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SettingsSectionHeader(icon = Icons.Default.SettingsBackupRestore, title = "Backup")

            val backupStatus by viewModel.backupStatus.collectAsState()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Settings file", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Save every setting to a file, or restore from one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onSaveSettings) { Text("Save") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onRestoreSettings) { Text("Restore") }
            }
            backupStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(24.dp))
            AboutSection()

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
fun OpacitySettingRow(label: String, subtitle: String? = null, percent: Int, onChange: (Int) -> Unit) {
    // Local value while dragging; the preference (and widget redraw) updates on release.
    var value by remember(percent) { mutableStateOf(percent.toFloat()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text("${value.toInt()}%", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = value,
                onValueChange = { value = (it / 10f).toInt() * 10f },
                onValueChangeFinished = { onChange(value.toInt().coerceIn(10, 100)) },
                valueRange = 10f..100f,
                steps = 8
            )
        }
    }
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
                        // No check mark: the fill already shows the choice, and the icon took
                        // enough width to wrap longer labels into a taller segment.
                        icon     = {},
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

private const val SOURCE_URL = "https://github.com/attam5740/hebrewcal-android"

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    // This version's release notes, bundled at build time from the F-Droid changelog.
    val whatsNew = remember {
        runCatching { context.assets.open("whats_new.txt").bufferedReader().use { it.readText().trim() } }.getOrNull()
    }
    SettingsSectionHeader(icon = Icons.Default.Info, title = "About")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hebrew Calendar $version", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (whatsNew != null) {
                Text("What's new", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(whatsNew, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Free and open source (MIT). Texts from Sefaria; Chabad's Shabbat-eve prayers from Siddur Torah Or on Hebrew Wikisource (CC BY-SA); zmanim by KosherJava.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(SOURCE_URL)))
                }) { Text("Source code") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("$SOURCE_URL/blob/main/CHANGELOG.md")))
                }) { Text("Full changelog") }
            }
        }
    }
}
