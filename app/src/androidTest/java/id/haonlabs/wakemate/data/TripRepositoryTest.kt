package id.haonlabs.wakemate.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.haonlabs.wakemate.data.local.WakeMateDatabase
import id.haonlabs.wakemate.data.repository.TripRepositoryImpl
import id.haonlabs.wakemate.data.seed.DatabaseSeeder
import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.TripStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TripRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WakeMateDatabase
    private lateinit var repository: TripRepositoryImpl
    private lateinit var preferencesFile: File
    private lateinit var destination: Station

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(context, WakeMateDatabase::class.java).build()
        preferencesFile = File.createTempFile("trip-test", ".preferences_pb").also { it.delete() }
        DatabaseSeeder(
            context = context,
            database = database,
            preferences = PreferenceDataStoreFactory.create { preferencesFile },
        ).ensureSeeded()
        repository = TripRepositoryImpl(
            database.tripSessionDao(), database.stationDao(), database.lineDao()
        )
        destination = database.stationDao().getStationsInLine("mrt-north-south").last()
            .let { Station(it.id, it.name, it.mode, it.lat, it.lng, it.lineId, it.sequenceInLine) }
    }

    @After
    fun tearDown() {
        database.close()
        preferencesFile.delete()
    }

    @Test
    fun startPersistsSessionAndExposesIt() = runTest {
        val trip = repository.start(destination, AlertThreshold.StationCount(2))

        assertEquals(trip, repository.activeTrip.value)
        assertEquals("Bundaran HI Bank Jakarta", trip.destination.name)
        assertEquals("MRT Lin Utara-Selatan", trip.lineName)
        assertNotNull(database.tripSessionDao().getActiveSession())
    }

    @Test
    fun thresholdSurvivesTheRoundTripThroughRoom() = runTest {
        repository.start(destination, AlertThreshold.Distance(1_500.0))

        val restored = afterProcessDeath().restoreActiveSession()

        assertEquals(AlertThreshold.Distance(1_500.0), restored?.session?.threshold)
    }

    @Test
    fun restoreRecoversSessionAfterProcessDeath() = runTest {
        repository.start(destination, AlertThreshold.StationCount(2))
        val reborn = afterProcessDeath()
        assertNull("state in-memory ikut mati bersama proses", reborn.activeTrip.value)

        val restored = reborn.restoreActiveSession()

        assertEquals(destination.id, restored?.session?.destinationStationId)
        assertEquals(restored, reborn.activeTrip.value)
    }

    @Test
    fun completeMarksSessionCompletedNotCancelled() = runTest {
        repository.start(destination, AlertThreshold.StationCount(2))

        repository.complete()

        assertNull(repository.activeTrip.value)
        assertNull(database.tripSessionDao().getActiveSession())
        assertEquals(TripStatus.COMPLETED, latestStatus())
    }

    @Test
    fun cancelMarksSessionCancelled() = runTest {
        repository.start(destination, AlertThreshold.StationCount(2))

        repository.cancel()

        assertEquals(TripStatus.CANCELLED, latestStatus())
    }

    @Test
    fun startingAgainClosesTheSessionLeftBehind() = runTest {
        repository.start(destination, AlertThreshold.StationCount(2))

        repository.start(destination, AlertThreshold.StationCount(1))

        // Hanya satu sesi boleh ACTIVE; sisanya dianggap ditinggalkan user.
        val sessions = database.tripSessionDao().history().first()
        assertEquals(1, sessions.count { it.status == TripStatus.ACTIVE })
        assertEquals(1, sessions.count { it.status == TripStatus.CANCELLED })
    }

    /** Instance baru dengan database yang sama: persis kondisi setelah app dibunuh lalu dibuka lagi. */
    private fun afterProcessDeath() = TripRepositoryImpl(
        database.tripSessionDao(), database.stationDao(), database.lineDao()
    )

    private suspend fun latestStatus(): TripStatus =
        database.tripSessionDao().history().first().first().status
}
