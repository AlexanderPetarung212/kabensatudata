package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Generic JSON cache for BOK (bokbaru) API responses.
 * Stores the full JSON response string keyed by a unique cacheKey.
 *
 * cacheKey examples:
 *   - "calendar_detail_123_3"  (poaId=123, month=3)
 *   - "poa_list_2026_1__"     (year=2026, page=1, no filter)
 *   - "poa_detail_123"        (poaId=123)
 *   - "employees"             (all employees)
 */
@Entity(tableName = "bok_cache")
data class BokCacheEntity(
    @PrimaryKey val cacheKey: String,
    val jsonData: String,
    val lastSynced: Long = System.currentTimeMillis()
)
