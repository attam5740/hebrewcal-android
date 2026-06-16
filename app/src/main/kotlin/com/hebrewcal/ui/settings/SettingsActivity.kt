package com.hebrewcal.ui.settings

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hebrewcal.ui.theme.HebrewCalendarTheme

class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled in UI flow */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HebrewCalendarTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "settings") {
                    composable("settings") {
                        MainSettingsScreen(
                            viewModel   = viewModel,
                            onNavigateToZmanimSelection = { navController.navigate("zmanim_selection") },
                            onRequestLocationPermission = {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        )
                    }
                    composable("zmanim_selection") {
                        ZmanimSelectionScreen(
                            viewModel = viewModel,
                            onBack    = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
