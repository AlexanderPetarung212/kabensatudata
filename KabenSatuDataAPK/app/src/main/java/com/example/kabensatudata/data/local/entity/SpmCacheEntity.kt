package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * JSON cache for SPM dashboard and sub-detail API responses.
 * Stores full JSON keyed by filter combination.
 *
 * cacheKey examples:
 *   - "spm_dashboard_2025_null_null"      (year=2025, no month, no village)
 *   - "spm_dashboard_2025_3_null"         (year=2025, month=3, no village)
 *   - "spm_dashboard_2025_null_5"         (year=2025, no month, village=5)
 *   - "spm_sub_detail_42_2025_3_null"     (subId=42, year=2025, month=3, no village)
 */
@Entity(tableName = "spm_cache")
data class SpmCacheEntity(
    @PrimaryKey val cacheKey: String,
    val jsonData: String,
    val lastSynced: Long = System.currentTimeMillis()
)
