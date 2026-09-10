package id.haonlabs.wakemate.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Ke mana bunyi alarm boleh keluar. */
enum class AlarmOutput {
    /** Headset saja; kalau tidak ada headset, tidak ada bunyi sama sekali — getar tetap jalan. */
    HEADSET_ONLY,

    /** Speaker, dipakai sebagai eskalasi terakhir atau saat tidak ada headset. */
    SPEAKER,
}

/**
 * Memutar bunyi alarm dan getaran, dengan rute audio yang dikendalikan.
 *
 * Diukur di perangkat sungguhan (Galaxy Tab Active5, Android 14): `USAGE_ALARM` dengan rute
 * dibiarkan sistem keluar lewat **speaker** walau TWS tersambung — kebijakan alarm perangkat
 * memaksanya ke sana. Tapi `setPreferredDevice` ke headset **mengalahkan** paksaan itu. Jadi
 * `USAGE_ALARM` tetap dipakai (kekebalan mode senyap dan DND ada di sana) sambil rutenya
 * dikendalikan sendiri; `USAGE_MEDIA` tidak perlu, sehingga kekebalan DND tidak dikorbankan.
 *
 * Itu hasil dari satu perangkat, dan `setPreferredDevice` sifatnya permintaan. Karena itu rute
 * yang benar-benar dipakai diperiksa lagi setelah mulai lewat [MediaPlayer.getRoutedDevice], dan
 * kalau ternyata tetap keluar di speaker padahal user meminta jangan, bunyinya dihentikan — getar
 * yang menanggung. Lebih baik senyap daripada mengkhianati permintaan user di tempat umum.
 */
@Singleton
class AlarmPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    fun hasHeadset(): Boolean = audioManager
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .any { it.type in HEADSET_TYPES }

    /** @return true kalau bunyi benar-benar keluar lewat rute yang diminta. */
    @Synchronized
    fun play(output: AlarmOutput): Boolean {
        stopSound()
        val target = outputDevice(output) ?: return false
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return false
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        return runCatching {
            // Tanpa audio focus, podcast yang sedang diputar di TWS akan menimpa alarmnya.
            requestFocus(attributes)
            player = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(context, uri)
                isLooping = true
                setPreferredDevice(target)
                prepare()
                start()
            }
            true
        }.onFailure {
            Log.w(TAG, "Gagal memutar alarm", it)
            stopSound()
        }.getOrDefault(false)
    }

    /**
     * Rute yang benar-benar dipakai, baru terisi sesaat setelah pemutaran mulai. null berarti
     * belum terlaporkan — bukan berarti gagal.
     */
    @Synchronized
    fun routedToSpeaker(): Boolean? =
        player?.routedDevice?.let { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

    @Synchronized
    fun stopSound() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /**
     * Getar berulang sampai dihentikan. Eskalasinya lewat durasi dan irama, bukan amplitudo:
     * sebagian perangkat (termasuk tablet uji) tidak punya kendali amplitudo sama sekali, dan pola
     * yang menguat harus tetap terasa di sana.
     */
    fun startVibration() {
        val timings = longArrayOf(0, 400, 300, 700, 300, 1100, 300, 1600, 800)
        val amplitudes = intArrayOf(0, 120, 0, 170, 0, 215, 0, 255, 0)
        val vibrator = vibrator()
        runCatching {
            vibrator.vibrate(
                if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, REPEAT_FROM)
                } else {
                    VibrationEffect.createWaveform(timings, REPEAT_FROM)
                }
            )
        }.onFailure { Log.w(TAG, "Gagal menggetarkan", it) }
    }

    fun stopVibration() = runCatching { vibrator().cancel() }.let { }

    private fun outputDevice(output: AlarmOutput): AudioDeviceInfo? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return when (output) {
            AlarmOutput.HEADSET_ONLY -> outputs.firstOrNull { it.type in HEADSET_TYPES }
            AlarmOutput.SPEAKER -> outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }
    }

    private fun requestFocus(attributes: AudioAttributes) {
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
            .also { audioManager.requestAudioFocus(it) }
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private companion object {
        const val TAG = "AlarmPlayer"
        /** Ulangi pola dari awal, bukan dari jeda penutup. */
        const val REPEAT_FROM = 0

        val HEADSET_TYPES: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}
