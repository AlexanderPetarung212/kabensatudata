package com.example.kabensatudata.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.kabensatudata.data.local.dao.*
import com.example.kabensatudata.data.local.entity.*

@Database(
    entities = [
        VillageEntity::class,
        BuildingEntity::class,
        FamilyEntity::class,
        FamilyMemberEntity::class,
        MedicalRecordEntity::class,
        MedicineEntity::class,
        MedicineUsageEntity::class,
        SyncQueueEntity::class,
        CalendarCacheEntity::class,
        NotificationEntity::class,
        BokCacheEntity::class,
        PegawaiEntity::class,
        SuratArsipCacheEntity::class,
        PegawaiStatsCacheEntity::class,
        SpmCacheEntity::class,
        P2PMedicalRecordEntity::class,
        ChatMessageEntity::class,
        ActivityLogEntity::class,
        InventarisCacheEntity::class,
    ],
    version = 19,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun villageDao(): VillageDao
    abstract fun buildingDao(): BuildingDao
    abstract fun familyDao(): FamilyDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun medicalRecordDao(): MedicalRecordDao
    abstract fun medicineDao(): MedicineDao
    abstract fun medicineUsageDao(): MedicineUsageDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun calendarCacheDao(): CalendarCacheDao
    abstract fun notificationDao(): NotificationDao
    abstract fun bokCacheDao(): BokCacheDao
    abstract fun pegawaiDao(): PegawaiDao
    abstract fun suratArsipCacheDao(): SuratArsipCacheDao
    abstract fun pegawaiStatsCacheDao(): PegawaiStatsCacheDao
    abstract fun spmCacheDao(): SpmCacheDao
    abstract fun p2pMedicalRecordDao(): P2PMedicalRecordDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun inventarisCacheDao(): InventarisCacheDao

    companion object {
        const val DATABASE_NAME = "kabensatudata_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

