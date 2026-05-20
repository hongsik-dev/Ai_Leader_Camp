package com.catchpro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.catchpro.app.data.local.entity.AccessibilityCaptureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessibilityCaptureDao {
    @Query(
        """
        SELECT * FROM accessibility_captures
        ORDER BY capturedAtMillis DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<AccessibilityCaptureEntity>>

    @Query(
        """
        SELECT * FROM accessibility_captures
        ORDER BY capturedAtMillis DESC
        LIMIT 1
        """,
    )
    fun observeLatest(): Flow<AccessibilityCaptureEntity?>

    @Insert
    suspend fun insert(capture: AccessibilityCaptureEntity)

    @Query(
        """
        DELETE FROM accessibility_captures
        WHERE eventType NOT LIKE 'Diagnostic:%'
            AND packageName NOT LIKE :packageNamePattern
        """,
    )
    suspend fun deletePackagesNotLike(packageNamePattern: String)

    @Query(
        """
        DELETE FROM accessibility_captures
        WHERE eventType NOT LIKE 'Diagnostic:%'
            AND capturedAtMillis < :cutoffMillis
        """,
    )
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM accessibility_captures WHERE capturedAtMillis < :cutoffMillis")
    suspend fun deleteAllOlderThan(cutoffMillis: Long)

    @Query(
        """
        DELETE FROM accessibility_captures
        WHERE eventType NOT LIKE 'Diagnostic:%'
            AND id NOT IN (
            SELECT id FROM accessibility_captures
            WHERE eventType NOT LIKE 'Diagnostic:%'
            ORDER BY capturedAtMillis DESC
            LIMIT :maxRows
        )
        """,
    )
    suspend fun trimToRecent(maxRows: Int)

    @Query(
        """
        DELETE FROM accessibility_captures
        WHERE eventType LIKE 'Diagnostic:%'
            AND id NOT IN (
            SELECT id FROM accessibility_captures
            WHERE eventType LIKE 'Diagnostic:%'
            ORDER BY capturedAtMillis DESC
            LIMIT :maxRows
        )
        """,
    )
    suspend fun trimDiagnosticsToRecent(maxRows: Int)

    @Query("DELETE FROM accessibility_captures")
    suspend fun deleteAll()
}
