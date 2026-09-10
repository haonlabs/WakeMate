package id.haonlabs.wakemate.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.haonlabs.wakemate.data.local.WakeMateDatabase
import id.haonlabs.wakemate.data.seed.DatabaseSeeder
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.proximity.ProximityCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Menguji asset seed yang benar-benar di-bundle, bukan fixture: isi datanya bagian dari produk. */
@RunWith(AndroidJUnit4::class)
class DatabaseSeederTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WakeMateDatabase
    private lateinit var seeder: DatabaseSeeder
    private lateinit var preferencesFile: File

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WakeMateDatabase::class.java).build()
        preferencesFile = File.createTempFile("seed-test", ".preferences_pb").also { it.delete() }
        seeder = DatabaseSeeder(
            context = context,
            database = database,
            preferences = PreferenceDataStoreFactory.create { preferencesFile },
        )
    }

    @After
    fun tearDown() {
        database.close()
        preferencesFile.delete()
    }

    @Test
    fun seedsEveryModeFromBundledAsset() = runTest {
        seeder.ensureSeeded()

        assertTrue(database.stationDao().count() > 400)
        TransitMode.entries.forEach { mode ->
            assertTrue("$mode punya line", database.lineDao().getLines(mode).isNotEmpty())
        }
    }

    @Test
    fun seedingTwiceDoesNotDuplicate() = runTest {
        seeder.ensureSeeded()
        val afterFirst = database.stationDao().count()

        seeder.ensureSeeded()

        assertEquals(afterFirst, database.stationDao().count())
    }

    @Test
    fun mrtLineIsOrderedAndFeedsTheCalculator() = runTest {
        seeder.ensureSeeded()

        val stations = database.stationDao().getStationsInLine("mrt-north-south")
            .map { id.haonlabs.wakemate.domain.model.Station(
                it.id, it.name, it.mode, it.lat, it.lng, it.lineId, it.sequenceInLine) }

        assertEquals(13, stations.size)
        assertEquals(stations.indices.toList(), stations.map { it.sequenceInLine })

        // Data seed harus langsung bisa dipakai ProximityCalculator tanpa penyesuaian.
        val calculator = ProximityCalculator(stations, stations.last().id, TransitMode.MRT)
        val state = calculator.onLocation(
            id.haonlabs.wakemate.domain.proximity.GeoPoint(stations[0].lat, stations[0].lng), 0)
        assertEquals(12, state.stationsRemaining)
    }

    @Test
    fun searchFindsStationOnEveryLineThatServesIt() = runTest {
        seeder.ensureSeeded()

        val results = database.stationDao().search(TransitMode.KRL, "Manggarai").first()

        // Manggarai dilewati banyak line — FR-3 mengharuskan semuanya muncul, bukan satu saja.
        assertTrue("ketemu ${results.size} baris", results.size >= 3)
        assertTrue(results.map { it.lineName }.toSet().size >= 3)
    }
}
