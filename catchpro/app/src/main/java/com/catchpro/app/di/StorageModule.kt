package com.catchpro.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.catchpro.app.data.local.CatchProDatabase
import com.catchpro.app.data.local.dao.AccessibilityCaptureDao
import com.catchpro.app.data.local.dao.DestinationDao
import com.catchpro.app.data.local.dao.OrderEventDao
import com.catchpro.app.data.local.dao.OperationLogDao
import com.catchpro.app.data.local.dao.PresetDao
import com.catchpro.app.data.local.dao.TmapQueueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CatchProDatabase {
        return Room.databaseBuilder(
            context,
            CatchProDatabase::class.java,
            "catchpro.db",
        )
            .addMigrations(Migration3To4)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun providePresetDao(database: CatchProDatabase): PresetDao = database.presetDao()

    @Provides
    fun provideDestinationDao(database: CatchProDatabase): DestinationDao = database.destinationDao()

    @Provides
    fun provideOrderEventDao(database: CatchProDatabase): OrderEventDao = database.orderEventDao()

    @Provides
    fun provideAccessibilityCaptureDao(
        database: CatchProDatabase,
    ): AccessibilityCaptureDao = database.accessibilityCaptureDao()

    @Provides
    fun provideTmapQueueDao(database: CatchProDatabase): TmapQueueDao = database.tmapQueueDao()

    @Provides
    fun provideOperationLogDao(database: CatchProDatabase): OperationLogDao = database.operationLogDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("catchpro_settings.preferences_pb") },
        )
    }

    private val Migration3To4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `operation_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `eventType` TEXT NOT NULL,
                    `status` TEXT,
                    `orderSignature` TEXT,
                    `mode` TEXT,
                    `source` TEXT,
                    `region` TEXT,
                    `clientText` TEXT,
                    `orderTitle` TEXT,
                    `originSummary` TEXT,
                    `destinationSummary` TEXT,
                    `requesterLocation` TEXT,
                    `pickupAddress` TEXT,
                    `dropoffAddress` TEXT,
                    `detailNote` TEXT,
                    `price` INTEGER,
                    `currentToPickupDistanceKm` REAL,
                    `pickupToDropoffStraightKm` REAL,
                    `estimatedPickupToDropoffRoadKm` REAL,
                    `pickupRoadDistanceKm` REAL,
                    `destinationMatchDistanceKm` REAL,
                    `farePerStraightKm` REAL,
                    `farePerEstimatedRoadKm` REAL,
                    `shouldConfirm` INTEGER,
                    `confirmed` INTEGER,
                    `manualInputRequired` INTEGER,
                    `manualReviewRequired` INTEGER,
                    `reason` TEXT,
                    `clickDiagnostic` TEXT,
                    `screenSummary` TEXT,
                    `rawContext` TEXT,
                    `createdAtMillis` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_operation_logs_createdAtMillis` ON `operation_logs` (`createdAtMillis`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_operation_logs_eventType` ON `operation_logs` (`eventType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_operation_logs_status` ON `operation_logs` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_operation_logs_mode` ON `operation_logs` (`mode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_operation_logs_orderSignature` ON `operation_logs` (`orderSignature`)")
        }
    }
}
