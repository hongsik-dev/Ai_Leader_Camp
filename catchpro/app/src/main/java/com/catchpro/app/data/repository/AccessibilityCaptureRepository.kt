package com.catchpro.app.data.repository

import com.catchpro.app.data.local.dao.AccessibilityCaptureDao
import com.catchpro.app.data.local.CatchProDatabase
import com.catchpro.app.data.local.entity.AccessibilityCaptureEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class AccessibilityCaptureRepository @Inject constructor(
    private val accessibilityCaptureDao: AccessibilityCaptureDao,
    private val database: CatchProDatabase,
) {
    fun recentCaptures(limit: Int = 25): Flow<List<AccessibilityCaptureEntity>> =
        accessibilityCaptureDao.observeRecent(limit)

    val latestCapture: Flow<AccessibilityCaptureEntity?> = accessibilityCaptureDao.observeLatest()

    suspend fun saveCapture(capture: AccessibilityCaptureEntity) {
        accessibilityCaptureDao.insert(capture)
    }

    suspend fun pruneInsungOnly(
        retentionDays: Int,
        maxRows: Int,
        nowMillis: Long = System.currentTimeMillis(),
        compact: Boolean = false,
    ) {
        accessibilityCaptureDao.deletePackagesNotLike("%$InsungPackageKeyword%")
        val safeRetentionDays = retentionDays.coerceAtLeast(1)
        accessibilityCaptureDao.deleteOlderThan(nowMillis - safeRetentionDays * MillisPerDay)
        accessibilityCaptureDao.trimToRecent(maxRows.coerceAtLeast(1))
        accessibilityCaptureDao.trimDiagnosticsToRecent(MaxDiagnosticRows)
        if (compact) {
            database.openHelper.writableDatabase.execSQL("VACUUM")
        }
    }

    suspend fun clearAll() {
        accessibilityCaptureDao.deleteAll()
    }

    suspend fun deleteAllOlderThan(cutoffMillis: Long) {
        accessibilityCaptureDao.deleteAllOlderThan(cutoffMillis)
    }

    private companion object {
        const val InsungPackageKeyword = "insung"
        const val MillisPerDay = 24L * 60L * 60L * 1000L
        const val MaxDiagnosticRows = 5_000
    }
}
