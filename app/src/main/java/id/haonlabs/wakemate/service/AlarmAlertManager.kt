package id.haonlabs.wakemate.service

import android.util.Log
import id.haonlabs.wakemate.domain.settings.AlarmSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alarm bertahap yang menahan speaker selama mungkin.
 *
 * App ini dipakai di gerbong penuh, jadi alarm yang meraung lewat speaker punya ongkos sosial yang
 * membuat orang berhenti memakainya — dan alarm yang ditinggalkan user bukan jaring pengaman.
 * Tapi menahan speaker sepenuhnya punya lubang sendiri: TWS yang tersambung tapi tergeletak di
 * dalam tas. Android tidak punya cara andal untuk tahu TWS sedang di telinga atau tidak, jadi
 * lubang itu tidak bisa ditutup dengan deteksi — hanya dengan waktu.
 *
 * Karena itu bentuknya eskalasi, bukan pilihan sekali jalan: getar dan headset dulu, speaker hanya
 * kalau setelah beberapa menit tidak ada tanda user bangun. Untuk user yang sadar, speaker
 * praktis tidak pernah berbunyi.
 */
/**
 * Apa yang harus dilakukan alarm, dipisah dari cara melakukannya.
 *
 * @param speakerAfterMs null berarti speaker tidak boleh dipakai sama sekali.
 */
data class AlarmPlan(val useHeadset: Boolean, val speakerAfterMs: Long?)

/** Sudah bunyi di telinga; beri waktu lebih lama sebelum mengganggu penumpang lain. */
const val SPEAKER_AFTER_HEADSET_MS = 3 * 60_000L

/** Tidak ada headset, jadi sejauh ini hanya getar yang menanggung — jangan menunggu selama itu. */
const val SPEAKER_WITHOUT_HEADSET_MS = 45_000L

/**
 * Aturannya dipisah supaya bisa diuji tanpa memutar apa pun: permintaan user untuk tidak memakai
 * speaker adalah janji yang paling mudah dilanggar diam-diam oleh bug penjadwalan.
 */
fun alarmPlan(hasHeadset: Boolean, allowSpeaker: Boolean): AlarmPlan = AlarmPlan(
    useHeadset = hasHeadset,
    speakerAfterMs = when {
        !allowSpeaker -> null
        hasHeadset -> SPEAKER_AFTER_HEADSET_MS
        else -> SPEAKER_WITHOUT_HEADSET_MS
    },
)

@Singleton
class AlarmAlertManager @Inject constructor(
    private val player: AlarmPlayer,
    private val notifications: TripNotifications,
    private val settings: AlarmSettings,
) : AlertManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var escalation: Job? = null
    private var alertId: Int? = null

    // ponytail: satu alarm untuk seluruh app; dua sesi yang berbunyi bersamaan (GPS + live) saling
    // mematikan saat salah satunya dihentikan. Pisahkan per sesi kalau itu ternyata terjadi.
    override fun alert(alert: WakeAlert) {
        notifications.postAlert(alert)
        alertId = alert.notificationId
        escalation?.cancel()
        escalation = scope.launch {
            // Getar lebih dulu dan tanpa syarat: ini satu-satunya kanal yang tidak bergantung pada
            // rute audio, dan HP di saku menyampaikannya ke badan.
            player.startVibration()

            val plan = alarmPlan(
                hasHeadset = player.hasHeadset(),
                allowSpeaker = settings.allowSpeakerFallback(),
            )
            if (plan.useHeadset && player.play(AlarmOutput.HEADSET_ONLY) && routedToSpeakerAnyway()) {
                // setPreferredDevice sifatnya permintaan. Kalau perangkat ini mengabaikannya,
                // lebih baik senyap daripada mengkhianati permintaan user di tempat umum —
                // speaker tetap akan datang lewat eskalasi kalau memang dibutuhkan.
                Log.w(TAG, "Rute headset diabaikan perangkat; bunyi dihentikan sampai eskalasi")
                player.stopSound()
            }

            val speakerAfter = plan.speakerAfterMs ?: return@launch
            delay(speakerAfter)
            player.play(AlarmOutput.SPEAKER)
        }
    }

    override fun stop() {
        escalation?.cancel()
        escalation = null
        player.stopSound()
        player.stopVibration()
        alertId?.let(notifications::clearAlert)
        alertId = null
    }

    /** Rute baru terlaporkan sesaat setelah pemutaran mulai, jadi diperiksa setelah jeda pendek. */
    private suspend fun routedToSpeakerAnyway(): Boolean {
        delay(ROUTE_SETTLE_MS)
        return player.routedToSpeaker() == true
    }

    private companion object {
        const val TAG = "AlarmAlert"
        const val ROUTE_SETTLE_MS = 700L
    }
}
