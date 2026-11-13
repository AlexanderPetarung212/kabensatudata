package com.example.kabensatudata.data.local.dao

import androidx.room.*
import com.example.kabensatudata.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class EducationCount(val education: String, val cnt: Int)

data class MemberWithRecordCount(
    val memberId: Long,
    val memberName: String,
    val memberGender: String?,
    val memberNik: String?,
    val memberBirthDate: String?,
    val recordCount: Int,
    val lastVisitDate: String?
)


@Dao
interface VillageDao {
    @Query("SELECT * FROM villages ORDER BY name")
    fun getAll(): Flow<List<VillageEntity>>

    @Query("SELECT * FROM villages ORDER BY name")
    suspend fun getAllSuspend(): List<VillageEntity>

    @Query("SELECT COUNT(*) FROM villages")
    suspend fun countAll(): Int

    @Query("SELECT * FROM villages WHERE id = :id")
    suspend fun getById(id: Long): VillageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(villages: List<VillageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(village: VillageEntity): Long

    @Query("DELETE FROM villages")
    suspend fun deleteAll()
}

@Dao
interface BuildingDao {
    @Query("SELECT * FROM buildings WHERE villageId = :villageId ORDER BY buildingNumber")
    fun getByVillage(villageId: Long): Flow<List<BuildingEntity>>

    @Query("SELECT * FROM buildings ORDER BY villageId, buildingNumber")
    suspend fun getAllSuspend(): List<BuildingEntity>

    @Query("SELECT * FROM buildings WHERE villageId = :villageId ORDER BY buildingNumber")
    suspend fun getByVillageSuspend(villageId: Long): List<BuildingEntity>

    @Query("SELECT * FROM buildings WHERE id = :id")
    suspend fun getById(id: Long): BuildingEntity?

    @Query("SELECT * FROM buildings WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): BuildingEntity?

    @Query("SELECT * FROM buildings WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<BuildingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(buildings: List<BuildingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(building: BuildingEntity): Long

    @Update
    suspend fun update(building: BuildingEntity)

    @Delete
    suspend fun delete(building: BuildingEntity)

    @Query("SELECT COUNT(*) FROM buildings WHERE villageId = :villageId")
    suspend fun countByVillage(villageId: Long): Int

    @Query("SELECT COUNT(*) FROM buildings")
    suspend fun countAll(): Int
}

@Dao
interface FamilyDao {
    @Query("SELECT * FROM families WHERE buildingId = :buildingId ORDER BY familyNumber")
    fun getByBuilding(buildingId: Long): Flow<List<FamilyEntity>>

    @Query("SELECT * FROM families ORDER BY familyNumber")
    suspend fun getAllSuspend(): List<FamilyEntity>

    @Query("SELECT * FROM families WHERE buildingId = :buildingId ORDER BY familyNumber")
    suspend fun getByBuildingSuspend(buildingId: Long): List<FamilyEntity>

    @Query("SELECT * FROM families WHERE id = :id")
    suspend fun getById(id: Long): FamilyEntity?

    @Query("SELECT * FROM families WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): FamilyEntity?

    @Query("SELECT * FROM families WHERE familyNumber = :familyNumber LIMIT 1")
    suspend fun getByFamilyNumber(familyNumber: String): FamilyEntity?

    @Query("SELECT * FROM families WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<FamilyEntity>

    @Query("SELECT COUNT(*) FROM families WHERE hasCleanWater = 1")
    suspend fun countWithCleanWater(): Int

    @Query("SELECT COUNT(*) FROM families WHERE isWaterProtected = 1")
    suspend fun countWithProtectedWater(): Int

    @Query("SELECT COUNT(*) FROM families WHERE hasToilet = 1")
    suspend fun countWithToilet(): Int

    @Query("SELECT COUNT(*) FROM families WHERE isToiletSanitary = 1")
    suspend fun countWithSanitaryToilet(): Int

    @Query("SELECT COUNT(*) FROM families WHERE buildingId IN (SELECT id FROM buildings WHERE villageId = :villageId)")
    suspend fun countByVillage(villageId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(families: List<FamilyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(family: FamilyEntity): Long

    @Update
    suspend fun update(family: FamilyEntity)

    @Delete
    suspend fun delete(family: FamilyEntity)

    @Query("SELECT COUNT(*) FROM families WHERE buildingId = :buildingId")
    suspend fun countByBuilding(buildingId: Long): Int

    @Query("SELECT COUNT(*) FROM families")
    suspend fun countAll(): Int
}

@Dao
interface FamilyMemberDao {
    @Query("SELECT * FROM family_members WHERE familyId = :familyId ORDER BY sequenceNumberInFamily")
    fun getByFamily(familyId: Long): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members ORDER BY name")
    suspend fun getAllSuspend(): List<FamilyMemberEntity>

    @Query("SELECT * FROM family_members WHERE familyId = :familyId ORDER BY sequenceNumberInFamily")
    suspend fun getByFamilySuspend(familyId: Long): List<FamilyMemberEntity>

    @Query("SELECT * FROM family_members WHERE familyId IN (SELECT id FROM families WHERE buildingId = :buildingId)")
    suspend fun getByBuilding(buildingId: Long): List<FamilyMemberEntity>

    @Query("SELECT * FROM family_members WHERE id = :id")
    suspend fun getById(id: Long): FamilyMemberEntity?

    @Query("SELECT * FROM family_members WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): FamilyMemberEntity?

    @Query("SELECT * FROM family_members WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<FamilyMemberEntity>

    @Query("SELECT * FROM family_members WHERE name LIKE '%' || :query || '%' OR nik LIKE '%' || :query || '%' ORDER BY name LIMIT 30")
    fun search(query: String): Flow<List<FamilyMemberEntity>>

    @Query("SELECT COUNT(*) FROM family_members")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM family_members WHERE familyId = :familyId")
    suspend fun countByFamily(familyId: Long): Int

    // ─── Statistics queries ─────────────────────────
    @Query("SELECT COUNT(*) FROM family_members WHERE gender = :gender")
    suspend fun countByGender(gender: String): Int

    @Query("SELECT COUNT(*) FROM family_members WHERE hasTuberculosis = 1")
    suspend fun countWithTuberculosis(): Int

    @Query("SELECT COUNT(*) FROM family_members WHERE hasHypertension = 1")
    suspend fun countWithHypertension(): Int

    @Query("SELECT COUNT(*) FROM family_members WHERE isSmoker = 1")
    suspend fun countSmokers(): Int

    @Query("SELECT COUNT(*) FROM family_members WHERE isPregnant = 1")
    suspend fun countPregnant(): Int

    @Query("SELECT COUNT(*) FROM family_members WHERE hasChronicCough = 1")
    suspend fun countWithChronicCough(): Int

    @Query("SELECT education, COUNT(*) as cnt FROM family_members WHERE education IS NOT NULL GROUP BY education ORDER BY cnt DESC")
    suspend fun getEducationDistribution(): List<EducationCount>

    @Query("SELECT birthDate FROM family_members WHERE birthDate IS NOT NULL")
    suspend fun getAllBirthDates(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<FamilyMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: FamilyMemberEntity): Long

    @Update
    suspend fun update(member: FamilyMemberEntity)

    @Delete
    suspend fun delete(member: FamilyMemberEntity)

    // ─── Export / Enrichment ─────────────────────────────────
    @Query("SELECT * FROM family_members WHERE nik IN (:niks)")
    suspend fun getByNiks(niks: List<String>): List<FamilyMemberEntity>
}

@Dao
interface MedicalRecordDao {
    @Query("SELECT * FROM medical_records WHERE familyMemberId = :memberId ORDER BY visitDate DESC")
    fun getByMember(memberId: Long): Flow<List<MedicalRecordEntity>>

    @Query("SELECT * FROM medical_records WHERE id = :id")
    suspend fun getById(id: Long): MedicalRecordEntity?

    @Query("SELECT * FROM medical_records WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): MedicalRecordEntity?

    @Query("SELECT * FROM medical_records WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<MedicalRecordEntity>

    @Query("SELECT COUNT(*) FROM medical_records WHERE visitDate = :date")
    suspend fun countByDate(date: String): Int

    @Query("SELECT COUNT(*) FROM medical_records")
    suspend fun countAll(): Int

    @Query("""
        SELECT fm.id AS memberId, fm.name AS memberName, fm.gender AS memberGender,
               fm.nik AS memberNik, fm.birthDate AS memberBirthDate,
               COUNT(mr.id) AS recordCount, MAX(mr.visitDate) AS lastVisitDate
        FROM family_members fm
        INNER JOIN medical_records mr ON mr.familyMemberId = fm.id
        GROUP BY fm.id
        ORDER BY lastVisitDate DESC
    """)
    fun getMembersWithRecords(): Flow<List<MemberWithRecordCount>>

    @Query("""
        SELECT fm.id AS memberId, fm.name AS memberName, fm.gender AS memberGender,
               fm.nik AS memberNik, fm.birthDate AS memberBirthDate,
               COUNT(mr.id) AS recordCount, MAX(mr.visitDate) AS lastVisitDate
        FROM family_members fm
        INNER JOIN medical_records mr ON mr.familyMemberId = fm.id
        WHERE fm.name LIKE '%' || :query || '%' OR fm.nik LIKE '%' || :query || '%'
        GROUP BY fm.id
        ORDER BY lastVisitDate DESC
    """)
    fun searchMembersWithRecords(query: String): Flow<List<MemberWithRecordCount>>

    @Query("SELECT COUNT(DISTINCT familyMemberId) FROM medical_records")
    suspend fun countDistinctMembers(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<MedicalRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: MedicalRecordEntity): Long

    @Update
    suspend fun update(record: MedicalRecordEntity)

    @Delete
    suspend fun delete(record: MedicalRecordEntity)
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun getPendingCount(): Flow<Int>

    @Insert
    suspend fun insert(entry: SyncQueueEntity): Long

    @Delete
    suspend fun delete(entry: SyncQueueEntity)

    @Update
    suspend fun update(entry: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue")
    suspend fun deleteAll()
}

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY name")
    fun getAll(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE isActive = 1 AND stockQuantity > 0 ORDER BY name")
    fun getActive(): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE isActive = 1 AND stockQuantity > 0 ORDER BY name")
    suspend fun getActiveSuspend(): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun getById(id: Long): MedicineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medicines: List<MedicineEntity>)

    @Query("DELETE FROM medicines")
    suspend fun deleteAll()

    @Query("UPDATE medicines SET stockQuantity = MAX(0, stockQuantity - :qty) WHERE id = :id")
    suspend fun deductStock(id: Long, qty: Int)
}

@Dao
interface MedicineUsageDao {
    @Query("SELECT * FROM medicine_usages WHERE medicalRecordId = :recordId")
    fun getByRecord(recordId: Long): Flow<List<MedicineUsageEntity>>

    @Query("SELECT * FROM medicine_usages WHERE medicalRecordId = :recordId")
    suspend fun getByRecordSuspend(recordId: Long): List<MedicineUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(usage: MedicineUsageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<MedicineUsageEntity>)

    @Query("DELETE FROM medicine_usages WHERE medicalRecordId = :recordId")
    suspend fun deleteByRecord(recordId: Long)

    @Query("SELECT * FROM medicine_usages WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<MedicineUsageEntity>

    @Update
    suspend fun update(usage: MedicineUsageEntity)
}

@Dao
interface CalendarCacheDao {
    @Query("SELECT * FROM calendar_cache WHERE year = :year")
    suspend fun getByYear(year: Int): CalendarCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: CalendarCacheEntity)

    @Query("DELETE FROM calendar_cache")
    suspend fun deleteAll()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: NotificationEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM notifications WHERE serverId = :serverId)")
    suspend fun existsByServerId(serverId: Long): Boolean

    @Delete
    suspend fun delete(notification: NotificationEntity)
}

@Dao
interface BokCacheDao {
    @Query("SELECT * FROM bok_cache WHERE cacheKey = :key")
    suspend fun get(key: String): BokCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: BokCacheEntity)

    @Query("DELETE FROM bok_cache WHERE cacheKey LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM bok_cache")
    suspend fun deleteAll()

    @Query("DELETE FROM bok_cache WHERE lastSynced < :before")
    suspend fun deleteOlderThan(before: Long)
}

// ─── Pegawai Cache ───────────────────────────────────────────────────────────

@Dao
interface PegawaiDao {
    @Query("SELECT * FROM pegawai_cache ORDER BY nama")
    fun getAllFlow(): Flow<List<PegawaiEntity>>

    @Query("SELECT * FROM pegawai_cache ORDER BY nama")
    suspend fun getAll(): List<PegawaiEntity>

    @Query("SELECT * FROM pegawai_cache WHERE nama LIKE '%' || :query || '%' OR nip LIKE '%' || :query || '%' OR jabatan LIKE '%' || :query || '%' ORDER BY nama")
    suspend fun search(query: String): List<PegawaiEntity>

    @Query("SELECT * FROM pegawai_cache WHERE id = :id")
    suspend fun getById(id: Long): PegawaiEntity?

    @Query("SELECT COUNT(*) FROM pegawai_cache")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pegawai: List<PegawaiEntity>)

    @Query("UPDATE pegawai_cache SET dokumenJson = :json WHERE id = :id")
    suspend fun updateDokumenJson(id: Long, json: String)

    @Query("DELETE FROM pegawai_cache")
    suspend fun deleteAll()
}

@Dao
interface SuratArsipCacheDao {
    @Query("SELECT * FROM surat_arsip_cache ORDER BY tanggal DESC")
    suspend fun getAll(): List<SuratArsipCacheEntity>

    @Query("SELECT * FROM surat_arsip_cache WHERE jenis = :jenis ORDER BY tanggal DESC")
    suspend fun getByJenis(jenis: String): List<SuratArsipCacheEntity>

    @Query("SELECT * FROM surat_arsip_cache WHERE perihal LIKE '%' || :query || '%' OR nomor LIKE '%' || :query || '%' ORDER BY tanggal DESC")
    suspend fun search(query: String): List<SuratArsipCacheEntity>

    @Query("SELECT * FROM surat_arsip_cache WHERE (:jenis IS NULL OR jenis = :jenis) AND (:query IS NULL OR perihal LIKE '%' || :query || '%' OR nomor LIKE '%' || :query || '%') ORDER BY tanggal DESC")
    suspend fun getFiltered(jenis: String?, query: String?): List<SuratArsipCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(arsip: List<SuratArsipCacheEntity>)

    @Query("UPDATE surat_arsip_cache SET isFileCached = :cached WHERE id = :arsipId")
    suspend fun updateFileCached(arsipId: Long, cached: Boolean)

    @Query("DELETE FROM surat_arsip_cache")
    suspend fun deleteAll()
}

@Dao
interface PegawaiStatsCacheDao {
    @Query("SELECT * FROM pegawai_stats_cache WHERE id = 1")
    suspend fun get(): PegawaiStatsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: PegawaiStatsCacheEntity)

    @Query("DELETE FROM pegawai_stats_cache")
    suspend fun deleteAll()
}

// ─── SPM Cache ───────────────────────────────────────────────────────────────

@Dao
interface SpmCacheDao {
    @Query("SELECT * FROM spm_cache WHERE cacheKey = :key")
    suspend fun get(key: String): SpmCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: SpmCacheEntity)

    @Query("DELETE FROM spm_cache WHERE cacheKey LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM spm_cache")
    suspend fun deleteAll()

    @Query("DELETE FROM spm_cache WHERE lastSynced < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface P2PMedicalRecordDao {
    @Query("SELECT * FROM p2p_medical_records WHERE direction = 'INBOX' ORDER BY createdAt DESC")
    fun getInbox(): Flow<List<P2PMedicalRecordEntity>>

    @Query("SELECT * FROM p2p_medical_records WHERE direction = 'OUTBOX' ORDER BY createdAt DESC")
    fun getOutbox(): Flow<List<P2PMedicalRecordEntity>>

    @Query("SELECT * FROM p2p_medical_records WHERE recordUuid = :uuid")
    suspend fun getByUuid(uuid: String): P2PMedicalRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: P2PMedicalRecordEntity)

    @Query("UPDATE p2p_medical_records SET stage = :stage, payloadJson = :json, direction = :direction WHERE recordUuid = :uuid")
    suspend fun updateStage(uuid: String, stage: String, json: String, direction: String)

    @Query("DELETE FROM p2p_medical_records WHERE recordUuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("SELECT * FROM p2p_medical_records WHERE stage = 'DISPENSED' AND syncedToServer = 0")
    suspend fun getUnsyncedCompleted(): List<P2PMedicalRecordEntity>

    @Query("UPDATE p2p_medical_records SET syncedToServer = 1 WHERE recordUuid = :uuid")
    suspend fun markSynced(uuid: String)

    @Query("DELETE FROM p2p_medical_records")
    suspend fun deleteAll()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :peerId ORDER BY timestamp ASC")
    fun getConversation(peerId: String): Flow<List<ChatMessageEntity>>

    @Query("""
        SELECT cm.* FROM chat_messages cm
        INNER JOIN (
            SELECT conversationId, MAX(timestamp) as maxTs
            FROM chat_messages
            GROUP BY conversationId
        ) latest ON cm.conversationId = latest.conversationId AND cm.timestamp = latest.maxTs
        ORDER BY cm.timestamp DESC
    """)
    fun getLatestPerConversation(): Flow<List<ChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :peerId AND isMine = 0 AND read = 0")
    fun getUnreadCount(peerId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE isMine = 0 AND read = 0")
    fun getTotalUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET delivered = 1 WHERE messageId = :messageId")
    suspend fun markDelivered(messageId: String)

    @Query("UPDATE chat_messages SET read = 1 WHERE conversationId = :peerId AND isMine = 0")
    suspend fun markRead(peerId: String)

    @Query("SELECT * FROM chat_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): ChatMessageEntity?

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM chat_messages WHERE conversationId = :peerId")
    suspend fun deleteConversation(peerId: String)

    /** Mark all messages we sent to this peer as read (called when peer sends CHAT_READ) */
    @Query("UPDATE chat_messages SET read = 1 WHERE conversationId = :peerId AND isMine = 1")
    suspend fun markAllRead(peerId: String)

    /** Mark a message as failed to send */
    @Query("UPDATE chat_messages SET sendFailed = 1 WHERE messageId = :messageId")
    suspend fun markSendFailed(messageId: String)

    /** Clear send failed status (for retry) */
    @Query("UPDATE chat_messages SET sendFailed = 0 WHERE messageId = :messageId")
    suspend fun clearSendFailed(messageId: String)
}

// ─── Activity Log (Device Monitoring) ────────────────────────────────────────

@Dao
interface ActivityLogDao {
    @Insert
    suspend fun insert(log: ActivityLogEntity)

    @Insert
    suspend fun insertAll(logs: List<ActivityLogEntity>)

    @Query("SELECT * FROM activity_logs WHERE synced = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int = 100): List<ActivityLogEntity>

    @Query("UPDATE activity_logs SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM activity_logs WHERE synced = 1 AND timestamp < :before")
    suspend fun deleteSyncedBefore(before: Long)

    @Query("SELECT COUNT(*) FROM activity_logs WHERE synced = 0")
    suspend fun countUnsynced(): Int

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<ActivityLogEntity>
}

// ─── Inventaris Cache ────────────────────────────────────────────────────────

@Dao
interface InventarisCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InventarisCacheEntity)

    @Query("SELECT * FROM inventaris_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): InventarisCacheEntity?

    @Query("DELETE FROM inventaris_cache WHERE cacheKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM inventaris_cache")
    suspend fun clearAll()
}
