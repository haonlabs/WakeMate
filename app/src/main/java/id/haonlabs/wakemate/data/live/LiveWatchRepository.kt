package id.haonlabs.wakemate.data.live

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import id.haonlabs.wakemate.domain.live.Approach
import id.haonlabs.wakemate.domain.live.LiveWatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class LiveWatchState(
    val watch: LiveWatch,
    /** Posisi bus terhadap halte yang dijaga; null sampai data live pertama masuk. */
    val approach: Approach? = null,
    val alertedAt: Long? = null,
)

/**
 * Sumber tunggal alarm live yang sedang berjalan: ditulis service, dibaca UI. Watch-nya juga
 * disimpan ke DataStore supaya service yang di-restart sistem bisa melanjutkannya.
 */
@Singleton
class LiveWatchRepository @Inject constructor(
    private val preferences: DataStore<Preferences>,
) {
    private val _state = MutableStateFlow<LiveWatchState?>(null)
    val state: StateFlow<LiveWatchState?> = _state.asStateFlow()

    suspend fun start(watch: LiveWatch) {
        preferences.edit { it[KEY] = json.encodeToString(LiveWatch.serializer(), watch) }
        _state.value = LiveWatchState(watch)
    }

    suspend fun restore(): LiveWatchState? {
        _state.value?.let { return it }
        val saved = preferences.data.first()[KEY] ?: return null
        val watch = runCatching { json.decodeFromString(LiveWatch.serializer(), saved) }.getOrNull()
            ?: return null.also { clear() }
        return LiveWatchState(watch).also { _state.value = it }
    }

    fun publish(approach: Approach?) = _state.update { it?.copy(approach = approach) }

    fun markAlerted(atMs: Long) = _state.update { it?.copy(alertedAt = atMs) }

    suspend fun clear() {
        _state.value = null
        preferences.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("live_watch")
        val json = Json { ignoreUnknownKeys = true }
    }
}
