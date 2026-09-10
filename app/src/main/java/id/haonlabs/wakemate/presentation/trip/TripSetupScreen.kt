package id.haonlabs.wakemate.presentation.trip

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.presentation.iconRes
import java.util.Locale
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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Kembali",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(
                    Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        state.station?.let {
                            Icon(
                                painterResource(it.mode.iconRes()),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "TUJUAN",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            state.station?.name ?: "Memuat...",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            state.lineName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_alarm),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                        Text("Bangunkan saya saat", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TripSetupViewModel.STATION_OPTIONS.forEach { count ->
                            ThresholdOption(
                                selected = state.threshold == AlertThreshold.StationCount(count),
                                title = if (count == 1) "1 stasiun" else "$count stasiun",
                                // Angka jarak ini nyata, dihitung dari rute line yang dipilih.
                                subtitle = state.leadDistanceMeters[count]
                                    ?.let { String.format(Locale.US, "%.1f km", it / 1000) }
                                    ?: "-",
                                recommended = count == 2,
                                onClick = {
                                    viewModel.onThresholdChanged(AlertThreshold.StationCount(count))
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Text(
                        "Jarak antar stasiun tidak seragam, jadi hitungan stasiun lebih bisa " +
                            "diandalkan daripada kilometer. Angka di bawah tiap pilihan adalah " +
                            "jarak sebenarnya ke tujuan di line ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Mulai perjalanan & pasang alarm", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Icon(
                    painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_battery_saver),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Hemat baterai: pelacakan dipertajam hanya saat mendekati tujuan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun ThresholdOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    recommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
        label = "tepi",
    )
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f) else Color.Transparent,
        label = "isi",
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (recommended) {
                Text(
                    "DISARANKAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
