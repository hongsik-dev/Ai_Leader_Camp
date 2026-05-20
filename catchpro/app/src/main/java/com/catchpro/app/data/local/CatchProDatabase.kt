package com.catchpro.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.catchpro.app.data.local.dao.AccessibilityCaptureDao
import com.catchpro.app.data.local.dao.DestinationDao
import com.catchpro.app.data.local.dao.OrderEventDao
import com.catchpro.app.data.local.dao.OperationLogDao
import com.catchpro.app.data.local.dao.PresetDao
import com.catchpro.app.data.local.dao.TmapQueueDao
import com.catchpro.app.data.local.entity.AccessibilityCaptureEntity
import com.catchpro.app.data.local.entity.DestinationEntity
import com.catchpro.app.data.local.entity.OrderEventEntity
import com.catchpro.app.data.local.entity.OperationLogEntity
import com.catchpro.app.data.local.entity.PresetEntity
import com.catchpro.app.data.local.entity.TmapQueueEntryEntity

@Database(
    entities = [
        PresetEntity::class,
        DestinationEntity::class,
        OrderEventEntity::class,
        AccessibilityCaptureEntity::class,
        TmapQueueEntryEntity::class,
        OperationLogEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(CatchProTypeConverters::class)
abstract class CatchProDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao
    abstract fun destinationDao(): DestinationDao
    abstract fun orderEventDao(): OrderEventDao
    abstract fun accessibilityCaptureDao(): AccessibilityCaptureDao
    abstract fun tmapQueueDao(): TmapQueueDao
    abstract fun operationLogDao(): OperationLogDao
}
