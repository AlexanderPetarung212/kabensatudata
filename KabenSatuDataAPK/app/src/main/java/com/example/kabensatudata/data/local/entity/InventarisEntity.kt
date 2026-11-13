package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache for inventaris ruangan list (JSON blob pattern, like SpmCacheEntity).
 */
@Entity(tableName = "inventaris_cache")
data class InventarisCacheEntity(
    @PrimaryKey val cacheKey: String,   // e.g. "ruangan_list" or "ruangan_detail_5"
    val jsonData: String,
    val cachedAt: Long = System.currentTimeMillis()
)
