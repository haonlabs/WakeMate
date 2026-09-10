package id.haonlabs.wakemate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [LineEntity::class, StationEntity::class, TripSessionEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WakeMateDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun lineDao(): LineDao
    abstract fun tripSessionDao(): TripSessionDao
}
