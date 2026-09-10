package id.haonlabs.wakemate.data.seed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import id.haonlabs.wakemate.data.local.LineEntity
import id.haonlabs.wakemate.data.local.StationEntity
import id.haonlabs.wakemate.data.local.WakeMateDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mengisi Room dari asset JSON yang di-bundle (PRD §8: data stasiun statis, tanpa API berbayar).
 *
 * Idempoten: aman dipanggil di setiap app start. Re-seed hanya terjadi kalau versi file berubah
 * (rilis dengan data baru) atau tabel kosong, sehingga tidak perlu flag terpisah yang bisa
 * kedaluwarsa terhadap isi database sebenarnya.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: WakeMateDatabase,
    private val preferences: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stationDao = database.stationDao()
            val seededVersion = preferences.data.first()[SEED_VERSION]
            val seed = context.assets.open(ASSET_NAME).bufferedReader()
                .use { json.decodeFromString<SeedFile>(it.readText()) }
            if (seededVersion == seed.version && stationDao.count() > 0) return@withLock

            val lines = seed.modes.flatMap { mode ->
                mode.lines.map { LineEntity(it.id, it.name, mode.mode) }
            }
            val stations = seed.modes.flatMap { mode ->
                mode.lines.flatMap { line ->
                    line.stations.map {
                        StationEntity(it.id, it.name, mode.mode, it.lat, it.lng, line.id, it.sequence)
                    }
                }
            }
            database.withTransaction {
                stationDao.deleteAll()
                database.lineDao().deleteAll()
                database.lineDao().insertAll(lines)
                stationDao.insertAll(stations)
            }
            preferences.edit { it[SEED_VERSION] = seed.version }
        }
    }

    private companion object {
        const val ASSET_NAME = "stations_seed.json"
        val SEED_VERSION = intPreferencesKey("seed_version")
    }
}
