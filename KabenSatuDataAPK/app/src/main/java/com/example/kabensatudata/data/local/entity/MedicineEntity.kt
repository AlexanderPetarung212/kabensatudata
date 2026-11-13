package com.example.kabensatudata.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medicines")
data class MedicineEntity(
    @PrimaryKey val id: Long = 0,
    val name: String,
    val genericName: String? = null,
    val strength: String? = null,
    val stockQuantity: Int = 0,
    val unit: String = "tablet",
    val minimumStock: Int = 0,
    val isActive: Boolean = true
) {
    val fullName: String
        get() = if (strength.isNullOrBlank()) name else "$name $strength"
}
