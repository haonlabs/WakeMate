package id.haonlabs.wakemate.presentation.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Layar diagnostik rute audio (build debug).
 *
 * Menjawab satu pertanyaan yang menentukan desain alarm keras: bisakah bunyi alarm ditahan agar
 * tidak keluar lewat speaker saat TWS tersambung. Kalau tidak bisa, alarm keras harus dirancang
 * ulang sebelum ditulis, bukan sesudah.
 *
 * Cara pakai: sambungkan TWS, tekan tiap tombol, dan bandingkan rute yang dilaporkan dengan yang
 * benar-benar terdengar. Ulangi dengan TWS di dalam tas — itu skenario yang paling menentukan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRoutingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val probe = remember { AudioProbe(context) }
    var environment by remember { mutableStateOf(probe.environment()) }
    var log by remember { mutableStateOf(listOf<String>()) }
    var playing by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            probe.stop()
            probe.stopVibration()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostik audio") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Kondisi sekarang", style = MaterialTheme.typography.titleSmall)
                    Text("Mode dering: ${environment.ringerMode}", style = MaterialTheme.typography.bodySmall)
                    Text("Volume alarm: ${environment.alarmVolume}", style = MaterialTheme.typography.bodySmall)
                    Text("Do Not Disturb: ${environment.interruptionFilter}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Getar beramplitudo: ${if (environment.hasAmplitudeControl) "didukung" else "tidak didukung"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Output tersedia", style = MaterialTheme.typography.titleSmall)
                    environment.outputs.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { environment = probe.environment() }) {
                        Text("Muat ulang")
                    }
                }
            }

            Text("Uji rute", style = MaterialTheme.typography.titleMedium)
            AudioProbe.PROBES.forEach { item ->
                Button(
                    onClick = {
                        playing = item.label
                        log = log + "▶ ${item.label}"
                        probe.play(item) { routed ->
                            // Rute dilaporkan lebih dari sekali (listener + pemeriksaan susulan).
                            // Yang menarik hanya kalau berubah — itu berarti sistem memindahkan
                            // rutenya di tengah jalan.
                            val line = "   keluar lewat: $routed"
                            if (log.lastOrNull() != line) log = log + line
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(item.label)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        probe.stop()
                        playing = null
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Hentikan bunyi")
                }
                OutlinedButton(onClick = { probe.vibrate() }, modifier = Modifier.weight(1f)) {
                    Text("Uji getar")
                }
            }

            playing?.let {
                Text("Sedang berbunyi: $it", style = MaterialTheme.typography.bodySmall)
            }

            if (log.isNotEmpty()) {
                Text("Hasil", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        log.takeLast(30).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                OutlinedButton(onClick = { log = emptyList() }) { Text("Bersihkan hasil") }
            }

            Text(
                "Ulangi tiap uji dua kali: TWS di telinga, lalu TWS di dalam tas. Skenario kedua " +
                    "yang menentukan — kalau alarm hanya keluar lewat TWS yang tergeletak di tas, " +
                    "pengingatnya tidak sampai ke siapa pun.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
