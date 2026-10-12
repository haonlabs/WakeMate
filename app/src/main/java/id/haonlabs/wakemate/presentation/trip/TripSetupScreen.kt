package id.haonlabs.wakemate.presentation.trip

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.presentation.TripPermissions
import id.haonlabs.wakemate.presentation.components.CircleIconButton
import id.haonlabs.wakemate.presentation.components.IconBadge
import id.haonlabs.wakemate.presentation.components.SectionLabel
import id.haonlabs.wakemate.presentation.iconRes
import java.util.Locale

/** GPS2: tujuan, ambang dengan jarak sebenarnya, speaker cadangan, lalu mulai. */
@Composable
fun TripSetupScreen(
    onStarted: () -> Unit,
    onBack: () -> Unit,
    viewModel: TripSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showForegroundDenied by remember { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (TripPermissions.hasForegroundLocation(context)) viewModel.onStart()
        else showForegroundDenied = true
    }

    LaunchedEffect(state.started) {
        if (state.started) onStarted()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircleIconButton(R.drawable.ic_arrow_back, "Kembali", onBack)
                    Text("Atur pengingat", style = MaterialTheme.typography.titleLarge)
                }

                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconBadge(state.station?.mode?.iconRes() ?: R.drawable.ic_mode_krl, size = 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text("TURUN DI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state.station?.name ?: "Memuat...", style = MaterialTheme.typography.titleLarge)
                            Text(state.lineName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                SectionLabel("BANGUNKAN SAYA")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TripSetupViewModel.STATION_OPTIONS.forEach { count ->
                        ThresholdOption(
                            selected = state.threshold == AlertThreshold.StationCount(count),
                            title = "$count stasiun",
                            // Angka jarak ini nyata, dihitung dari rute line yang dipilih.
                            distance = state.leadDistanceMeters[count]
                                ?.let { String.format(Locale.US, "± %.1f km", it / 1000).replace('.', ',') }
                                ?: "-",
                            recommended = count == 2,
                            onClick = { viewModel.onThresholdChanged(AlertThreshold.StationCount(count)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    "Jarak dihitung sepanjang rel, bukan garis lurus — \"2 stasiun\" di Lin Bogor bisa belasan kilometer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Speaker sebagai cadangan", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (state.speakerFallback) "Getar dan headset dulu. Speaker hanya bunyi kalau kamu tidak merespons."
                                else "Speaker tidak dipakai sama sekali. Tanpa headset di telinga, hanya getar yang membangunkan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = state.speakerFallback, onCheckedChange = viewModel::onSpeakerFallbackChanged)
                    }
                }

                if (TripPermissions.isBatteryOptimized(context)) BatteryOptimizationCard(context)
            }

            // Menempel di bawah: tombol mulai selalu terjangkau jempol tanpa menggulir.
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { startWithPermissions(context, foregroundLauncher::launch) { viewModel.onStart() } },
                    enabled = state.station != null,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(vertical = 18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text("Mulai perjalanan", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::onErrorShown,
            confirmButton = { TextButton(onClick = viewModel::onErrorShown) { Text("OK") } },
            title = { Text("Tidak bisa memulai") },
            text = { Text(message) },
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
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_battery_saver), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Optimasi baterai aktif", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Sebagian HP mematikan aplikasi di latar belakang. Kecualikan WakeMate agar alarm aman.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { context.startActivity(TripPermissions.batteryOptimizationSettings()) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Buka setelan", style = MaterialTheme.typography.titleSmall)
                }
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

/** Kartu ambang: amber saat terpilih, karena ini soal alarm. */
@Composable
private fun ThresholdOption(
    selected: Boolean,
    title: String,
    distance: String,
    recommended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
        label = "tepi",
    )
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "isi",
    )
    val accent = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = container,
        border = BorderStroke(if (selected) 2.dp else 1.5.dp, border),
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(if (recommended) "DISARANKAN" else " ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
            Text("sebelumnya", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(distance, style = MaterialTheme.typography.labelMedium, color = if (selected) accent else MaterialTheme.colorScheme.outline)
        }
    }
}
