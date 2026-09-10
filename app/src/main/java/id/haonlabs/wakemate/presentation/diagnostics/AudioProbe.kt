package id.haonlabs.wakemate.presentation.diagnostics

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Alat ukur rute audio, hanya dipakai layar diagnostik di build debug.
 *
 * Ada karena satu pertanyaan desain alarm keras tidak bisa dijawab dari dokumentasi: apakah stream
 * alarm bisa ditahan agar tidak keluar lewat speaker saat TWS tersambung. Sebagian perangkat
 * memaksa alarm ke speaker apa pun permintaan aplikasi, dan itu bertentangan langsung dengan
 * kebutuhan memakai app ini di tempat umum.
 *
 * Jawabannya diukur, bukan didengar-dengar saja: [AudioDeviceInfo] yang dilaporkan
 * `MediaPlayer.getRoutedDevice()` menyebut ke mana audio benar-benar keluar.
 */
class AudioProbe(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null

    data class Environment(
        val outputs: List<String>,
        val ringerMode: String,
        val alarmVolume: String,
        val interruptionFilter: String,
        val hasAmplitudeControl: Boolean,
    )

    data class Probe(
        val label: String,
        val usage: Int,
        /** null berarti membiarkan sistem yang memilih rute. */
        val prefer: Int?,
    )

    fun environment(): Environment {
        val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
        return Environment(
            outputs = outputs().map { "${it.describe()}${if (it.isHeadset()) "  ← headset" else ""}" },
            ringerMode = when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "senyap"
                AudioManager.RINGER_MODE_VIBRATE -> "getar"
                else -> "normal"
            },
            alarmVolume = "${audioManager.getStreamVolume(AudioManager.STREAM_ALARM)}" +
                " / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)}",
            interruptionFilter = when (notificationManager.currentInterruptionFilter) {
                android.app.NotificationManager.INTERRUPTION_FILTER_ALL -> "semua lolos"
                android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "prioritas saja"
                android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarm saja"
                android.app.NotificationManager.INTERRUPTION_FILTER_NONE -> "senyap total"
                else -> "tidak diketahui"
            },
            hasAmplitudeControl = vibrator().hasAmplitudeControl(),
        )
    }

    /**
     * Membunyikan nada alarm bawaan sistem dengan atribut dan rute yang diminta, lalu melaporkan
     * rute yang benar-benar dipakai. Laporan bisa datang dua kali: sekali saat mulai, sekali lagi
     * kalau sistem memindahkan rutenya di tengah jalan.
     */
    fun play(probe: Probe, onRouted: (String) -> Unit) {
        stop()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return onRouted("perangkat tidak punya nada alarm bawaan")
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(probe.usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                probe.prefer?.let { wanted ->
                    val target = outputs().firstOrNull { it.type == wanted }
                    if (target == null) {
                        onRouted("perangkat yang diminta tidak tersedia")
                    } else if (!setPreferredDevice(target)) {
                        onRouted("sistem menolak permintaan rute")
                    }
                }
                addOnRoutingChangedListener({ routing ->
                    onRouted(routing.routedDevice?.describe() ?: "belum terlaporkan")
                }, null)
                prepare()
                start()
                // Rute sering baru terisi sesaat setelah mulai, jadi listener saja tidak cukup.
                postDelayed { onRouted(routedDevice?.describe() ?: "belum terlaporkan") }
            }
        }.onFailure {
            Log.w(TAG, "Gagal memutar nada uji", it)
            onRouted("gagal memutar: ${it.message}")
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    /**
     * Pola getar yang menguat bertahap — kalau speaker ditahan, getar naik jadi kanal utama, dan
     * satu semburan pendek ala notifikasi tidak cukup untuk membangunkan.
     */
    fun vibrate() {
        val timings = longArrayOf(0, 500, 250, 700, 250, 900, 250, 1200, 250, 1500)
        val amplitudes = intArrayOf(0, 90, 0, 140, 0, 190, 0, 230, 0, 255)
        val vibrator = vibrator()
        vibrator.vibrate(
            if (vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amplitudes, -1)
            else VibrationEffect.createWaveform(timings, -1)
        )
    }

    fun stopVibration() = vibrator().cancel()

    private fun outputs(): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private inline fun MediaPlayer.postDelayed(crossinline block: MediaPlayer.() -> Unit) {
        android.os.Handler(context.mainLooper).postDelayed({
            if (player === this) block()
        }, ROUTE_SETTLE_MS)
    }

    companion object {
        private const val TAG = "AudioProbe"
        private const val ROUTE_SETTLE_MS = 700L

        val PROBES = listOf(
            Probe("USAGE_ALARM, rute dibiarkan sistem", AudioAttributes.USAGE_ALARM, null),
            Probe("USAGE_ALARM, dipaksa ke headset", AudioAttributes.USAGE_ALARM, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP),
            Probe("USAGE_ALARM, dipaksa ke speaker", AudioAttributes.USAGE_ALARM, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
            Probe("USAGE_MEDIA, dipaksa ke headset", AudioAttributes.USAGE_MEDIA, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP),
        )

        fun AudioDeviceInfo.describe(): String {
            val name = when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker HP"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headset kabel"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Headphone kabel"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "Headset USB"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "Perangkat USB"
                AudioDeviceInfo.TYPE_TELEPHONY -> "Jalur telepon"
                AudioDeviceInfo.TYPE_HEARING_AID -> "Alat bantu dengar"
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    type == AudioDeviceInfo.TYPE_BLE_HEADSET
                ) "Bluetooth LE" else "Tipe $type"
            }
            return "$name — $productName"
        }

        fun AudioDeviceInfo.isHeadset(): Boolean = type in setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        ) || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
}
