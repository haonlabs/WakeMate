package id.haonlabs.wakemate.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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

private val SLIDE = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 280, easing = FastOutSlowInEasing)
private val FADE = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)

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

    // Geser mendatar seperlunya: layar-layar ini satu alur maju-mundur, dan perpindahan yang
    // muncul begitu saja terasa seperti aplikasi yang tersendat.
    NavHost(
        navController = navController,
        startDestination = Home,
        enterTransition = { slideInHorizontally(SLIDE) { it / 5 } + fadeIn(FADE) },
        exitTransition = { slideOutHorizontally(SLIDE) { -it / 8 } + fadeOut(FADE) },
        popEnterTransition = { slideInHorizontally(SLIDE) { -it / 8 } + fadeIn(FADE) },
        popExitTransition = { slideOutHorizontally(SLIDE) { it / 5 } + fadeOut(FADE) },
    ) {
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
