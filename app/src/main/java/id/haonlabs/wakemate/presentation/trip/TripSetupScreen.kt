package id.haonlabs.wakemate.presentation.trip

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.presentation.TripPermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSetupScreen(
    onStarted: () -> Unit,
    onBack: () -> Unit,
    viewModel: TripSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showBackgroundRationale by remember { mutableStateOf(false) }
    var showForegroundDenied by remember { mutableStateOf(false) }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.onStart() } // Ditolak pun sesi tetap jalan, hanya terbatas saat layar menyala.

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (TripPermissions.hasForegroundLocation(context)) {
            if (TripPermissions.hasBackgroundLocation(context)) viewModel.onStart()
            else showBackgroundRationale = true
        } else {
            showForegroundDenied = true
        }
    }

    LaunchedEffect(state.started) {
        if (state.started) onStarted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Atur pengingat") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tujuan", style = MaterialTheme.typography.labelMedium)
                    Text(
                        state.station?.name ?: "Memuat...",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(state.lineName, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text("Bangunkan saya", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { count ->
                    FilterChip(
                        selected = state.threshold == AlertThreshold.StationCount(count),
                        onClick = { viewModel.onThresholdChanged(AlertThreshold.StationCount(count)) },
                        label = { Text("$count stasiun lagi") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1_000.0, 2_000.0).forEach { meters ->
                    FilterChip(
                        selected = state.threshold == AlertThreshold.Distance(meters),
                        onClick = { viewModel.onThresholdChanged(AlertThreshold.Distance(meters)) },
                        label = { Text("${(meters / 1000).toInt()} km lagi") },
                    )
                }
            }
            Text(
                "Jarak antar stasiun tidak seragam, jadi hitungan stasiun biasanya lebih bisa " +
                    "diandalkan daripada jarak kilometer.",
                style = MaterialTheme.typography.bodySmall,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Boleh pakai speaker", style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = state.speakerFallback,
                            onCheckedChange = viewModel::onSpeakerFallbackChanged,
                        )
                    }
                    Text(
                        if (state.speakerFallback) {
                            "Alarm dimulai dari getar dan headset. Speaker baru dipakai kalau " +
                                "beberapa menit berlalu tanpa tanda kamu bangun."
                        } else {
                            "Speaker tidak akan dipakai sama sekali. Kalau headset tidak terpasang " +
                                "atau tergeletak di tas, hanya getar yang membangunkan."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (TripPermissions.isBatteryOptimized(context)) {
                BatteryOptimizationCard(context)
            }

            Button(
                onClick = { startWithPermissions(context, foregroundLauncher::launch) { viewModel.onStart() } },
                enabled = state.station != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Mulai Perjalanan")
            }

            state.error?.let { message ->
                AlertDialog(
                    onDismissRequest = viewModel::onErrorShown,
                    confirmButton = { TextButton(onClick = viewModel::onErrorShown) { Text("OK") } },
                    title = { Text("Tidak bisa memulai") },
                    text = { Text(message) },
                )
            }
        }
    }

    if (showBackgroundRationale) {
        AlertDialog(
            onDismissRequest = { showBackgroundRationale = false },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundRationale = false
                    backgroundLauncher.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text("Izinkan") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBackgroundRationale = false
                    viewModel.onStart()
                }) { Text("Nanti saja") }
            },
            title = { Text("Izin lokasi latar belakang") },
            text = {
                Text(
                    "WakeMate perlu membaca lokasi walau layar mati — itu justru saat kamu tidur. " +
                        "Tanpa izin ini, pengingat hanya bekerja selama layar menyala."
                )
            },
        )
    }

    if (showForegroundDenied) {
        AlertDialog(
            onDismissRequest = { showForegroundDenied = false },
            confirmButton = { TextButton(onClick = { showForegroundDenied = false }) { Text("Mengerti") } },
            title = { Text("Izin lokasi ditolak") },
            text = { Text("Tanpa izin lokasi, WakeMate tidak bisa tahu kamu sudah sampai mana.") },
        )
    }
}

@Composable
private fun BatteryOptimizationCard(context: Context) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Optimasi baterai aktif", style = MaterialTheme.typography.titleSmall)
            Text(
                "Sebagian HP mematikan aplikasi latar belakang secara agresif dan bisa membuat " +
                    "pengingat gagal bunyi. Kecualikan WakeMate agar sesi aman.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { context.startActivity(TripPermissions.batteryOptimizationSettings()) }) {
                Text("Buka setelan")
            }
        }
    }
}

private inline fun startWithPermissions(
    context: Context,
    requestForeground: (Array<String>) -> Unit,
    start: () -> Unit,
) {
    when {
        !TripPermissions.hasForegroundLocation(context) || !TripPermissions.hasNotifications(context) ->
            requestForeground(TripPermissions.foreground)
        else -> start()
    }
}
