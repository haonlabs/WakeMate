package id.haonlabs.wakemate.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.haonlabs.wakemate.presentation.diagnostics.AudioRoutingScreen
import id.haonlabs.wakemate.presentation.home.HomeScreen
import id.haonlabs.wakemate.presentation.trip.ActiveTripScreen
import id.haonlabs.wakemate.presentation.trip.TripSetupScreen
import kotlinx.serialization.Serializable

@Serializable
object Home

@Serializable
data class TripSetup(val stationId: String, val lineId: String)

@Serializable
object ActiveTrip

@Serializable
object AudioDiagnostics

@Composable
fun WakeMateNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: AppViewModel = hiltViewModel(),
) {
    val hasActiveTrip by viewModel.hasActiveTrip.collectAsStateWithLifecycle()

    // Sesi yang selamat dari app dibunuh sistem harus langsung terlihat lagi, bukan hilang diam-diam.
    LaunchedEffect(hasActiveTrip) {
        if (hasActiveTrip) {
            navController.navigate(ActiveTrip) { popUpTo(Home) }
        }
    }

    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onStationSelected = { station ->
                    navController.navigate(TripSetup(station.id, station.lineId))
                },
                onOpenDiagnostics = { navController.navigate(AudioDiagnostics) },
            )
        }
        composable<TripSetup> {
            TripSetupScreen(
                onStarted = { navController.navigate(ActiveTrip) { popUpTo(Home) } },
                onBack = { navController.popBackStack() },
            )
        }
        composable<AudioDiagnostics> {
            AudioRoutingScreen(onBack = { navController.popBackStack() })
        }
        composable<ActiveTrip> {
            ActiveTripScreen(
                onFinished = { navController.popBackStack(Home, inclusive = false) },
            )
        }
    }
}
