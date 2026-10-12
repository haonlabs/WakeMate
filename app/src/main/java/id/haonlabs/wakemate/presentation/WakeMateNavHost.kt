package id.haonlabs.wakemate.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
import id.haonlabs.wakemate.data.live.LiveWatchState
import id.haonlabs.wakemate.domain.live.LiveWatch
import id.haonlabs.wakemate.domain.live.summary
import id.haonlabs.wakemate.domain.trip.ActiveTrip as GpsTrip
import id.haonlabs.wakemate.presentation.components.AlarmBar
import id.haonlabs.wakemate.presentation.diagnostics.AudioRoutingScreen
import id.haonlabs.wakemate.presentation.home.HomeScreen
import id.haonlabs.wakemate.presentation.live.LiveRoutesScreen
import id.haonlabs.wakemate.presentation.live.LiveTripScreen
import id.haonlabs.wakemate.presentation.live.RouteLiveScreen
import id.haonlabs.wakemate.presentation.trip.ActiveTripScreen
import id.haonlabs.wakemate.presentation.trip.TripSetupScreen
import kotlinx.serialization.Serializable

private val SLIDE = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 280, easing = FastOutSlowInEasing)
private val FADE = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)

@Serializable
object LiveRoutes

/** [boardBus] diisi dari "Ubah halte": sheet langsung terbuka di pemilihan halte untuk bus itu. */
@Serializable
data class RouteLive(val routeId: String, val boardBus: String? = null)

@Serializable
object LiveTrip

/** Alur alarm GPS (KRL/MRT/TJ). */
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
    val gpsTrip by viewModel.gpsTrip.collectAsStateWithLifecycle()
    val liveWatch by viewModel.liveWatch.collectAsStateWithLifecycle()

    // Sesi yang selamat dari app dibunuh sistem harus langsung terlihat lagi, bukan hilang diam-diam.
    LaunchedEffect(hasActiveTrip) {
        if (hasActiveTrip) navController.navigate(ActiveTrip) { popUpTo(Home); launchSingleTop = true }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTab = destination?.hasRoute<LiveRoutes>() == true || destination?.hasRoute<Home>() == true

    Box(Modifier.fillMaxSize()) {
        // Insets diurus tiap layar sendiri; Scaffold ini hanya menyisakan ruang untuk bar bawah.
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (onTab) {
                    Column {
                        // Status jaring pengaman tidak boleh hilang hanya karena user pindah tab.
                        AnimatedVisibility(
                            visible = liveWatch != null || gpsTrip != null,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                        ) {
                            TabAlarmBar(
                                liveWatch = liveWatch,
                                gpsTrip = gpsTrip,
                                onOpenLive = { navController.navigate(LiveTrip) },
                                onOpenGps = { navController.navigate(ActiveTrip) },
                                onStopLive = viewModel::onLiveAwake,
                                onStopGps = { viewModel.onGpsAwake() },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            val colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            )
                            NavigationBarItem(
                                selected = destination?.hasRoute<LiveRoutes>() == true,
                                onClick = { navController.switchTab(LiveRoutes) },
                                icon = { Icon(painterResource(R.drawable.ic_mode_tj), contentDescription = null) },
                                label = { Text("Live TJ") },
                                colors = colors,
                            )
                            NavigationBarItem(
                                selected = destination?.hasRoute<Home>() == true,
                                onClick = { navController.switchTab(Home) },
                                icon = { Icon(painterResource(R.drawable.ic_alarm), contentDescription = null) },
                                label = { Text("Alarm GPS") },
                                colors = colors,
                            )
                        }
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
                    RouteLiveScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLiveTrip = { navController.navigate(LiveTrip) { launchSingleTop = true } },
                    )
                }
                composable<LiveTrip> {
                    LiveTripScreen(
                        onBack = { navController.popBackStack() },
                        onChangeStop = { routeId, busNo ->
                            navController.navigate(RouteLive(routeId, boardBus = busNo)) { popUpTo(LiveTrip) { inclusive = true } }
                        },
                    )
                }
                composable<Home> {
                    HomeScreen(
                        onStationSelected = { station -> navController.navigate(TripSetup(station.id, station.lineId)) },
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
                        onBack = { navController.popBackStack() },
                        // Sesi yang dipulihkan saat app dibuka tidak lewat tab Alarm GPS, jadi Home
                        // belum tentu ada di back stack.
                        onFinished = {
                            if (!navController.popBackStack(Home, inclusive = false)) navController.popBackStack()
                        },
                    )
                }
            }
        }

        // LT7: alarm yang sedang berbunyi menutupi apa pun — satu-satunya hal yang penting adalah bangun.
        val ringingLive = liveWatch?.takeIf { it.alertedAt != null && it.watch is LiveWatch.Onboard }
        val ringingGps = gpsTrip?.takeIf { it.hasAlerted }
        when {
            ringingLive != null -> {
                val approach = ringingLive.approach
                val watch = ringingLive.watch as LiveWatch.Onboard
                RingingScreen(
                    destination = watch.halteName,
                    detail = approach?.summary() ?: "Bus sudah melewati halte tujuan",
                    meta = "Bus ${watch.busNo} · Rute ${watch.routeId}",
                    onAwake = viewModel::onLiveAwake,
                    onSnooze = if ((approach?.stopsAway ?: 0) > 1) ({ viewModel.onLiveSnooze() }) else null,
                )
            }
            ringingGps != null -> {
                val remaining = ringingGps.proximity?.stationsRemaining
                RingingScreen(
                    destination = ringingGps.destination.name,
                    detail = when {
                        remaining == null || remaining <= 0 -> "Kamu sudah sampai"
                        remaining == 1 -> "Stasiun berikutnya"
                        else -> "$remaining stasiun lagi"
                    },
                    meta = ringingGps.lineName,
                    onAwake = { viewModel.onGpsAwake() },
                    onSnooze = null,
                )
            }
        }
    }
}

@Composable
private fun TabAlarmBar(
    liveWatch: LiveWatchState?,
    gpsTrip: GpsTrip?,
    onOpenLive: () -> Unit,
    onOpenGps: () -> Unit,
    onStopLive: () -> Unit,
    onStopGps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (liveWatch != null) {
        val w = liveWatch.watch
        AlarmBar(
            title = when (w) {
                is LiveWatch.Onboard -> "Bangunkan sebelum ${w.halteName}"
                is LiveWatch.Arrival -> "Bus ${w.routeId} tiba di ${w.halteName}"
            },
            subtitle = liveWatch.approach?.let { "${it.bus.busNo} · ${it.summary()}" } ?: "Menunggu data live...",
            ringing = liveWatch.alertedAt != null,
            onClick = onOpenLive,
            onStop = onStopLive,
            modifier = modifier,
        )
    } else if (gpsTrip != null) {
        val remaining = gpsTrip.proximity?.stationsRemaining
        AlarmBar(
            title = "Menuju ${gpsTrip.destination.name}",
            subtitle = remaining?.let { if (it <= 0) "Sudah sampai" else "$it stasiun lagi · ${gpsTrip.lineName}" } ?: "Menunggu sinyal GPS...",
            ringing = gpsTrip.hasAlerted,
            onClick = onOpenGps,
            onStop = onStopGps,
            modifier = modifier,
        )
    }
}

private fun NavHostController.switchTab(tab: Any) = navigate(tab) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
