package id.haonlabs.wakemate.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.presentation.diagnostics.AudioRoutingScreen
import id.haonlabs.wakemate.presentation.home.HomeScreen
import id.haonlabs.wakemate.presentation.live.LiveRoutesScreen
import id.haonlabs.wakemate.presentation.live.RouteLiveScreen
import id.haonlabs.wakemate.presentation.trip.ActiveTripScreen
import id.haonlabs.wakemate.presentation.trip.TripSetupScreen
import kotlinx.serialization.Serializable

private val SLIDE = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 280, easing = FastOutSlowInEasing)
private val FADE = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)

@Serializable
object LiveRoutes

@Serializable
data class RouteLive(val routeId: String)

/** Alur alarm GPS (KRL/MRT/TJ) yang lama. */
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

    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTab = destination?.hasRoute<LiveRoutes>() == true || destination?.hasRoute<Home>() == true

    // Insets diurus tiap layar sendiri seperti sebelumnya; Scaffold ini hanya menyisakan ruang
    // untuk bar tab saat bar itu tampil.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    NavigationBarItem(
                        selected = destination?.hasRoute<LiveRoutes>() == true,
                        onClick = { navController.switchTab(LiveRoutes) },
                        icon = { Icon(painterResource(R.drawable.ic_mode_tj), contentDescription = null) },
                        label = { Text("Live TJ") },
                    )
                    NavigationBarItem(
                        selected = destination?.hasRoute<Home>() == true,
                        onClick = { navController.switchTab(Home) },
                        icon = { Icon(painterResource(R.drawable.ic_alarm), contentDescription = null) },
                        label = { Text("Alarm GPS") },
                    )
                }
            }
        },
    ) { padding ->
        // Geser mendatar seperlunya: layar-layar ini satu alur maju-mundur, dan perpindahan yang
        // muncul begitu saja terasa seperti aplikasi yang tersendat.
        NavHost(
            navController = navController,
            startDestination = LiveRoutes,
            modifier = Modifier.padding(padding),
            enterTransition = { slideInHorizontally(SLIDE) { it / 5 } + fadeIn(FADE) },
            exitTransition = { slideOutHorizontally(SLIDE) { -it / 8 } + fadeOut(FADE) },
            popEnterTransition = { slideInHorizontally(SLIDE) { -it / 8 } + fadeIn(FADE) },
            popExitTransition = { slideOutHorizontally(SLIDE) { it / 5 } + fadeOut(FADE) },
        ) {
            composable<LiveRoutes> {
                LiveRoutesScreen(onRouteSelected = { navController.navigate(RouteLive(it)) })
            }
            composable<RouteLive> {
                RouteLiveScreen(onBack = { navController.popBackStack() })
            }
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
                    // Sesi yang dipulihkan saat app dibuka tidak lewat tab Alarm GPS, jadi Home
                    // belum tentu ada di back stack.
                    onFinished = {
                        if (!navController.popBackStack(Home, inclusive = false)) navController.popBackStack()
                    },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(tab: Any) = navigate(tab) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
