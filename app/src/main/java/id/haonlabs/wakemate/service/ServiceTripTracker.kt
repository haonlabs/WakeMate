package id.haonlabs.wakemate.service

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import id.haonlabs.wakemate.domain.trip.TripTracker
import javax.inject.Inject

class ServiceTripTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : TripTracker {

    override fun start() {
        ContextCompat.startForegroundService(
            context,
            TripTrackingService.intent(context, TripTrackingService.ACTION_START),
        )
    }

    override fun stop() {
        // ACTION_STOP, bukan ACTION_CANCEL: status sesi sudah diurus use case pemanggilnya.
        context.startService(TripTrackingService.intent(context, TripTrackingService.ACTION_STOP))
    }
}
