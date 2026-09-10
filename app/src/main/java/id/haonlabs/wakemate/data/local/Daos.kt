package id.haonlabs.wakemate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.haonlabs.wakemate.domain.model.TransitMode
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query(
        """
        SELECT station.*, line.name AS lineName FROM station
        JOIN line ON line.id = station.lineId
        WHERE station.mode = :mode AND station.name LIKE '%' || :query || '%'
        ORDER BY station.name, line.name
        LIMIT 60
        """
    )
    fun search(mode: TransitMode, query: String): Flow<List<StationWithLineRow>>

    @Query("SELECT * FROM station WHERE lineId = :lineId ORDER BY sequenceInLine ASC")
    suspend fun getStationsInLine(lineId: String): List<StationEntity>

    @Query("SELECT * FROM station WHERE id = :id")
    suspend fun findById(id: String): StationEntity?

    @Query("SELECT COUNT(*) FROM station")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<StationEntity>)

    @Query("DELETE FROM station")
    suspend fun deleteAll()
}

@Dao
interface LineDao {
    @Query("SELECT * FROM line WHERE mode = :mode ORDER BY name")
    suspend fun getLines(mode: TransitMode): List<LineEntity>

    @Query("SELECT * FROM line WHERE id = :id")
    suspend fun findById(id: String): LineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lines: List<LineEntity>)

    @Query("DELETE FROM line")
    suspend fun deleteAll()
}

@Dao
interface TripSessionDao {
    @Insert
    suspend fun insert(session: TripSessionEntity): Long

    @Update
    suspend fun update(session: TripSessionEntity)

    /** Dipakai untuk recovery: kalau app dibunuh sistem, UI bisa menyusul sesi yang masih jalan. */
    @Query("SELECT * FROM trip_session WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): TripSessionEntity?

    @Query("SELECT * FROM trip_session ORDER BY startedAt DESC LIMIT 50")
    fun history(): Flow<List<TripSessionEntity>>
}
