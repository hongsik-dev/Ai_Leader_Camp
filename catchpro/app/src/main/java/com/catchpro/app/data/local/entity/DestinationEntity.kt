package com.catchpro.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val address: String,
    val zone: String,
    val note: String = "",
    val isDefault: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)
