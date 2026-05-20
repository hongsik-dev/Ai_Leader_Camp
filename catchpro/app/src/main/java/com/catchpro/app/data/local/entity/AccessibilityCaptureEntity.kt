package com.catchpro.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accessibility_captures",
    indices = [
        Index("packageName"),
        Index("capturedAtMillis"),
    ],
)
data class AccessibilityCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val eventType: String,
    val screenTitle: String?,
    val summaryText: String,
    val nodeCount: Int,
    val rawHierarchy: String,
    val capturedAtMillis: Long = System.currentTimeMillis(),
)
