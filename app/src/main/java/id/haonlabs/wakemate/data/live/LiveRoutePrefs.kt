package id.haonlabs.wakemate.data.live

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rute favorit dan yang terakhir dibuka. Komuter naik rute yang sama setiap hari, jadi dua daftar
 * pendek ini yang membuat 240 rute terasa sedikit.
 */
@Singleton
class LiveRoutePrefs @Inject constructor(
    private val preferences: DataStore<Preferences>,
) {
    val favorites: Flow<Set<String>> = preferences.data.map { it[FAVORITES].orEmpty() }

    /** Terbaru di depan. */
    val recent: Flow<List<String>> = preferences.data.map { prefs ->
        prefs[RECENT]?.split(SEP)?.filter { it.isNotEmpty() }.orEmpty()
    }

    suspend fun toggleFavorite(routeId: String) {
        preferences.edit {
            val current = it[FAVORITES].orEmpty()
            it[FAVORITES] = if (routeId in current) current - routeId else current + routeId
        }
    }

    suspend fun markOpened(routeId: String) {
        preferences.edit {
            val current = it[RECENT]?.split(SEP)?.filter { id -> id.isNotEmpty() }.orEmpty()
            it[RECENT] = (listOf(routeId) + (current - routeId)).take(MAX_RECENT).joinToString(SEP)
        }
    }

    private companion object {
        val FAVORITES = stringSetPreferencesKey("live_favorite_routes")
        val RECENT = stringPreferencesKey("live_recent_routes")
        // Kode rute GTFS tidak memuat spasi atau koma.
        const val SEP = ","
        const val MAX_RECENT = 6
    }
}
