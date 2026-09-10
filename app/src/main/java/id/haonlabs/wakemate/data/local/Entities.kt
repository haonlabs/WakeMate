package id.haonlabs.wakemate.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import id.haonlabs.wakemate.domain.model.AlertThresholdType
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.model.TripStatus

@Entity(tableName = "line")
data class LineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mode: TransitMode,
)

@Entity(
    tableName = "station",
    foreignKeys = [
        ForeignKey(
            entity = LineEntity::class,
            parentColumns = ["id"],
            childColumns = ["lineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lineId"), Index("mode", "name")],
)
data class StationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mode: TransitMode,
    val lat: Double,
    val lng: Double,
    val lineId: String,
    val sequenceInLine: Int,
)

@Entity(tableName = "trip_session")
data class TripSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destinationStationId: String,
    val lineId: String,
    val mode: TransitMode,
    val alertThresholdType: AlertThresholdType,
    val alertThresholdValue: Float,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: TripStatus,
)

/** Hasil join stasiun + nama line, dipakai layar pencarian (FR-3). */
data class StationWithLineRow(
    val id: String,
    val name: String,
    val mode: TransitMode,
    val lat: Double,
    val lng: Double,
    val lineId: String,
    val sequenceInLine: Int,
    val lineName: String,
)

class Converters {
    @TypeConverter fun toMode(value: String) = TransitMode.valueOf(value)
    @TypeConverter fun fromMode(value: TransitMode) = value.name

    @TypeConverter fun toStatus(value: String) = TripStatus.valueOf(value)
    @TypeConverter fun fromStatus(value: TripStatus) = value.name

    @TypeConverter fun toThresholdType(value: String) = AlertThresholdType.valueOf(value)
    @TypeConverter fun fromThresholdType(value: AlertThresholdType) = value.name
}
