package id.haonlabs.wakemate.domain.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmSettings @Inject constructor(
    private val preferences: DataStore<Preferences>,
) {
    /**
     * Default menyala. User yang memilih menonaktifkan berarti sadar menukar keandalan dengan
     * ketenangan — dan itu haknya, tapi bukan pilihan yang boleh diambilkan diam-diam.
     */
    val speakerFallback: Flow<Boolean> = preferences.data.map { it[SPEAKER_FALLBACK] ?: true }

    suspend fun allowSpeakerFallback(): Boolean = speakerFallback.first()

    suspend fun setSpeakerFallback(allowed: Boolean) {
        preferences.edit { it[SPEAKER_FALLBACK] = allowed }
    }

    private companion object {
        val SPEAKER_FALLBACK = booleanPreferencesKey("alarm_speaker_fallback")
    }
}
