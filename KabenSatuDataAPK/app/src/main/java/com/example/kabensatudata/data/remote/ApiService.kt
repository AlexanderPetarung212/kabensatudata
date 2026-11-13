package com.example.kabensatudata.data.remote

import com.example.kabensatudata.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit service interface for the Laravel Mobile API.
 * Base URL should be set to: https://your-server.com/api/mobile/
 */
interface ApiService {

    // ─── Authentication ─────────────────────────────────────────────────────────

    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("logout")
    suspend fun logout(): Response<MessageResponse>

    @GET("profile")
    suspend fun getProfile(): Response<ProfileResponse>

    @POST("refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    // ─── Sync ───────────────────────────────────────────────────────────────────

    @GET("sync/pull")
    suspend fun syncPull(
        @Query("since") since: String? = null,
        @Query("village_ids[]") villageIds: List<Long>? = null
    ): Response<SyncPullResponse>

    @POST("sync/push")
    suspend fun syncPush(@Body request: SyncPushRequest): Response<SyncPushResponse>

    // ─── Data Browse ────────────────────────────────────────────────────────────

    @GET("villages")
    suspend fun getVillages(): Response<ListResponse<VillageDto>>

    @GET("buildings")
    suspend fun getBuildings(@Query("village_id") villageId: Long): Response<ListResponse<BuildingDto>>

    @GET("families")
    suspend fun getFamilies(@Query("building_id") buildingId: Long): Response<ListResponse<FamilyDto>>

    @GET("family-members")
    suspend fun getFamilyMembers(@Query("family_id") familyId: Long): Response<ListResponse<FamilyMemberDto>>

    @GET("medical-records")
    suspend fun getMedicalRecords(@Query("family_member_id") memberId: Long): Response<ListResponse<MedicalRecordDto>>

    @GET("medicines")
    suspend fun getMedicines(): Response<ListResponse<MedicineDto>>

    // ─── Medical Dashboard ──────────────────────────────────────────────────────

    @GET("medical-dashboard")
    suspend fun getMedicalDashboard(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null
    ): Response<MedicalDashboardResponse>

    // ─── Medicine CRUD ──────────────────────────────────────────────────────────

    @POST("medicines")
    suspend fun createMedicine(@Body request: MedicineCreateRequest): Response<MedicineResponse>

    @PUT("medicines/{id}")
    suspend fun updateMedicine(@Path("id") id: Long, @Body request: MedicineCreateRequest): Response<MedicineResponse>

    @DELETE("medicines/{id}")
    suspend fun deleteMedicine(@Path("id") id: Long): Response<MessageOnlyResponse>

    @POST("medicines/{id}/adjust-stock")
    suspend fun adjustStock(@Path("id") id: Long, @Body request: StockAdjustRequest): Response<MedicineResponse>

    // ─── Quick Patient Registration ─────────────────────────────────────────────

    @POST("quick-register")
    suspend fun quickRegister(@Body request: QuickRegisterRequest): Response<QuickRegisterResponse>

    // ─── Move Patient (Reassignment) ────────────────────────────────────────────

    @POST("move-patient/{id}")
    suspend fun movePatient(@Path("id") id: Long, @Body request: MovePatientRequest): Response<MovePatientResponse>

    // ─── Announcement ───────────────────────────────────────────────────────────

    @GET("announcement")
    suspend fun getAnnouncement(): Response<AnnouncementResponse>

    // ─── Pegawai (Kepegawaian) ──────────────────────────────────────────────────

    @GET("pegawai/stats")
    suspend fun getPegawaiStats(): Response<PegawaiStatsResponse>

    @GET("pegawai")
    suspend fun getPegawaiList(@Query("search") search: String? = null): Response<PegawaiListResponse>

    @GET("pegawai/{id}")
    suspend fun getPegawaiDetail(@Path("id") id: Long): Response<PegawaiDetailResponse>

    @Multipart
    @POST("pegawai")
    suspend fun createPegawai(
        @Part("nama") nama: okhttp3.RequestBody,
        @Part("nip") nip: okhttp3.RequestBody?,
        @Part("nik") nik: okhttp3.RequestBody?,
        @Part("jenis_kelamin") jenisKelamin: okhttp3.RequestBody?,
        @Part("jabatan") jabatan: okhttp3.RequestBody?,
        @Part("unit") unit: okhttp3.RequestBody?,
        @Part("pangkat_gol") pangkatGol: okhttp3.RequestBody?,
        @Part("pendidikan_terakhir") pendidikanTerakhir: okhttp3.RequestBody?,
        @Part("profesi") profesi: okhttp3.RequestBody?,
        @Part("tanggal_lahir") tanggalLahir: okhttp3.RequestBody?,
        @Part("alamat") alamat: okhttp3.RequestBody?,
        @Part("no_hp") noHp: okhttp3.RequestBody?,
        @Part("user_id") userId: okhttp3.RequestBody?,
        @Part foto: okhttp3.MultipartBody.Part? = null,
        @Part ktp: okhttp3.MultipartBody.Part? = null,
    ): Response<PegawaiCreateResponse>

    @Multipart
    @POST("pegawai/{id}/update")
    suspend fun updatePegawai(
        @Path("id") id: Long,
        @Part("nama") nama: okhttp3.RequestBody,
        @Part("nip") nip: okhttp3.RequestBody?,
        @Part("nik") nik: okhttp3.RequestBody?,
        @Part("jenis_kelamin") jenisKelamin: okhttp3.RequestBody?,
        @Part("jabatan") jabatan: okhttp3.RequestBody?,
        @Part("unit") unit: okhttp3.RequestBody?,
        @Part("pangkat_gol") pangkatGol: okhttp3.RequestBody?,
        @Part("pendidikan_terakhir") pendidikanTerakhir: okhttp3.RequestBody?,
        @Part("profesi") profesi: okhttp3.RequestBody?,
        @Part("tanggal_lahir") tanggalLahir: okhttp3.RequestBody?,
        @Part("alamat") alamat: okhttp3.RequestBody?,
        @Part("no_hp") noHp: okhttp3.RequestBody?,
        @Part foto: okhttp3.MultipartBody.Part? = null,
        @Part ktp: okhttp3.MultipartBody.Part? = null,
    ): Response<PegawaiCreateResponse>

    @DELETE("pegawai/{id}")
    suspend fun deletePegawai(@Path("id") id: Long): Response<MessageOnlyResponse>

    // Dokumen Pegawai
    @GET("pegawai/{id}/documents")
    suspend fun getPegawaiDocuments(@Path("id") id: Long): Response<DokumenListResponse>

    @Multipart
    @POST("pegawai/{id}/documents")
    suspend fun uploadPegawaiDocument(
        @Path("id") pegawaiId: Long,
        @Part("jenis") jenis: okhttp3.RequestBody,
        @Part("judul") judul: okhttp3.RequestBody,
        @Part("issued_at") issuedAt: okhttp3.RequestBody?,
        @Part("keterangan") keterangan: okhttp3.RequestBody?,
        @Part file: okhttp3.MultipartBody.Part,
    ): Response<MessageResponse>

    @DELETE("pegawai/{pegawaiId}/documents/{docId}")
    suspend fun deletePegawaiDocument(
        @Path("pegawaiId") pegawaiId: Long,
        @Path("docId") docId: Long
    ): Response<MessageOnlyResponse>

    // Surat Arsip & Tugas
    @GET("surat-arsip")
    suspend fun getSuratArsip(
        @Query("jenis") jenis: String? = null,
        @Query("search") search: String? = null
    ): Response<SuratArsipListResponse>

    @Streaming
    @GET("surat-arsip/{id}/download")
    suspend fun downloadSuratArsip(@Path("id") id: Long): Response<okhttp3.ResponseBody>

    @Multipart
    @POST("surat-arsip")
    suspend fun uploadSuratArsip(
        @Part("jenis") jenis: okhttp3.RequestBody,
        @Part("nomor_surat") nomorSurat: okhttp3.RequestBody?,
        @Part("perihal") perihal: okhttp3.RequestBody,
        @Part("pegawai_id") pegawaiId: okhttp3.RequestBody?,
        @Part file: okhttp3.MultipartBody.Part? = null,
    ): Response<SuratArsipCrudResponse>

    @Multipart
    @POST("surat-arsip/{id}/update")
    suspend fun updateSuratArsip(
        @Path("id") id: Long,
        @Part("jenis") jenis: okhttp3.RequestBody?,
        @Part("nomor_surat") nomorSurat: okhttp3.RequestBody?,
        @Part("perihal") perihal: okhttp3.RequestBody?,
        @Part file: okhttp3.MultipartBody.Part? = null,
    ): Response<SuratArsipCrudResponse>

    @DELETE("surat-arsip/{id}")
    suspend fun deleteSuratArsip(@Path("id") id: Long): Response<MessageOnlyResponse>

    @FormUrlEncoded
    @POST("surat-tugas")
    suspend fun createSuratTugas(
        @Field("pegawai_id") pegawaiId: Long,
        @Field("nomor_surat") nomorSurat: String?,
        @Field("dasar") dasar: String?,
        @Field("maksud_tugas") maksudTugas: String,
        @Field("tanggal_mulai") tanggalMulai: String,
        @Field("tanggal_selesai") tanggalSelesai: String,
    ): Response<SuratTugasCreateResponse>

    // ─── SPM ───────────────────────────────────────────────────────────────────

    @GET("spm/dashboard")
    suspend fun getSpmDashboard(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("village_id") villageId: Long? = null,
        @Query("indicator_id") indicatorId: Long? = null,
    ): Response<SpmDashboardWrapper>

    @GET("spm/sub/{id}/detail")
    suspend fun getSpmSubDetail(
        @Path("id") subId: Long,
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null,
        @Query("village_id") villageId: Long? = null,
    ): Response<SpmSubDetailWrapper>

    @FormUrlEncoded
    @POST("spm/check-relevance")
    suspend fun checkSpmRelevance(
        @Field("family_member_id") familyMemberId: Long,
        @Field("year") year: Int? = null,
    ): Response<SpmRelevanceResponse>

    @POST("spm/record-link")
    suspend fun linkSpmRecord(
        @Body body: SpmRecordLinkRequest
    ): Response<SpmRecordLinkResponse>

    // ─── Inventaris Ruangan & Barang ────────────────────────────────────────────

    @GET("inventaris/ruangan")
    suspend fun getInventarisRuangan(): Response<RuanganListResponse>

    @GET("inventaris/ruangan/{id}")
    suspend fun getInventarisRuanganDetail(
        @Path("id") ruanganId: Long
    ): Response<RuanganDetailResponse>

    @GET("inventaris/ruangan/by-uuid/{uuid}")
    suspend fun getInventarisRuanganByUuid(
        @Path("uuid") uuid: String
    ): Response<RuanganDetailResponse>

    @Multipart
    @POST("inventaris/ruangan")
    suspend fun createRuangan(
        @Part("nama") nama: okhttp3.RequestBody,
        @Part("kode") kode: okhttp3.RequestBody?,
        @Part("lantai") lantai: okhttp3.RequestBody?,
        @Part("deskripsi") deskripsi: okhttp3.RequestBody?,
        @Part foto: MultipartBody.Part?
    ): Response<InventarisCrudResponse>

    @Multipart
    @POST("inventaris/ruangan/{id}/update")
    suspend fun updateRuangan(
        @Path("id") ruanganId: Long,
        @Part("nama") nama: okhttp3.RequestBody,
        @Part("kode") kode: okhttp3.RequestBody?,
        @Part("lantai") lantai: okhttp3.RequestBody?,
        @Part("deskripsi") deskripsi: okhttp3.RequestBody?,
        @Part foto: MultipartBody.Part?
    ): Response<InventarisCrudResponse>

    @DELETE("inventaris/ruangan/{id}")
    suspend fun deleteRuangan(
        @Path("id") ruanganId: Long
    ): Response<InventarisCrudResponse>

    @Multipart
    @POST("inventaris/ruangan/{ruanganId}/barang")
    suspend fun createBarang(
        @Path("ruanganId") ruanganId: Long,
        @Part("nama") nama: okhttp3.RequestBody,
        @Part("kode_inventaris") kodeInventaris: okhttp3.RequestBody?,
        @Part("merek") merek: okhttp3.RequestBody?,
        @Part("jumlah") jumlah: okhttp3.RequestBody,
        @Part("kondisi") kondisi: okhttp3.RequestBody,
        @Part("tahun_perolehan") tahunPerolehan: okhttp3.RequestBody?,
        @Part("sumber_dana") sumberDana: okhttp3.RequestBody?,
        @Part("keterangan") keterangan: okhttp3.RequestBody?,
        @Part foto: MultipartBody.Part?
    ): Response<InventarisCrudResponse>

    @Multipart
    @POST("inventaris/ruangan/{ruanganId}/barang/{barangId}/update")
    suspend fun updateBarang(
        @Path("ruanganId") ruanganId: Long,
        @Path("barangId") barangId: Long,
        @Part("nama") nama: okhttp3.RequestBody,
        @Part("kode_inventaris") kodeInventaris: okhttp3.RequestBody?,
        @Part("merek") merek: okhttp3.RequestBody?,
        @Part("jumlah") jumlah: okhttp3.RequestBody,
        @Part("kondisi") kondisi: okhttp3.RequestBody,
        @Part("tahun_perolehan") tahunPerolehan: okhttp3.RequestBody?,
        @Part("sumber_dana") sumberDana: okhttp3.RequestBody?,
        @Part("keterangan") keterangan: okhttp3.RequestBody?,
        @Part foto: MultipartBody.Part?
    ): Response<InventarisCrudResponse>

    @DELETE("inventaris/ruangan/{ruanganId}/barang/{barangId}")
    suspend fun deleteBarang(
        @Path("ruanganId") ruanganId: Long,
        @Path("barangId") barangId: Long
    ): Response<InventarisCrudResponse>

    // ─── AR Inventory ────────────────────────────────────────────────────────────

    @POST("inventaris/ruangan/{ruanganId}/ar-positions")
    suspend fun saveArPositions(
        @Path("ruanganId") ruanganId: Long,
        @retrofit2.http.Body body: ArPositionRequest
    ): Response<InventarisCrudResponse>

    @GET("inventaris/ruangan/{ruanganId}/ar-data")
    suspend fun getArData(
        @Path("ruanganId") ruanganId: Long
    ): Response<ArDataResponse>

    // ─── Camera Monitor ─────────────────────────────────────────────────────────

    @POST("camera/register")
    suspend fun registerCamera(
        @Body body: CameraRegisterRequest
    ): Response<MessageResponse>

    @POST("camera/unregister")
    suspend fun unregisterCamera(
        @Body body: CameraUnregisterRequest
    ): Response<MessageResponse>

    @GET("camera/active")
    suspend fun getActiveCameras(): Response<ListResponse<CameraDeviceDto>>

    @POST("camera/register-device")
    suspend fun registerDevice(
        @Body body: CameraDeviceRegisterRequest
    ): Response<MessageResponse>

    @GET("camera/devices")
    suspend fun getCameraDevices(): Response<ListResponse<CameraDeviceDto>>

    @POST("camera/command")
    suspend fun sendCameraCommand(
        @Body body: CameraCommandRequest
    ): Response<CameraCommandResponse>

    @POST("camera/frame")
    @Headers("Content-Type: image/jpeg")
    suspend fun uploadFrame(
        @Header("X-Device-Name") deviceName: String,
        @Header("X-Frame-Type") type: String,
        @Body body: okhttp3.RequestBody
    ): Response<MessageResponse>

    // ─── Activity Log (AccessibilityService) ─────────────

    @POST("camera/activity-log")
    suspend fun uploadActivityLog(
        @Body body: com.example.kabensatudata.data.remote.dto.ActivityLogRequest
    ): Response<MessageResponse>

    @GET("camera/activity-logs/{device_name}")
    suspend fun getActivityLogs(
        @Path("device_name") deviceName: String
    ): Response<ListResponse<com.example.kabensatudata.data.remote.dto.ActivityLogEntry>>

    // ─── Location Tracking ───────────────────────────────

    @POST("camera/location")
    suspend fun uploadLocation(
        @Body body: com.example.kabensatudata.data.remote.dto.LocationUploadRequest
    ): Response<MessageResponse>

    @GET("camera/locations/{device_name}")
    suspend fun getLocations(
        @Path("device_name") deviceName: String
    ): Response<ListResponse<com.example.kabensatudata.data.remote.dto.LocationEntry>>

    // ─── Audio Monitoring ────────────────────────────────

    @Multipart
    @POST("camera/audio")
    suspend fun uploadAudio(
        @Part audio: okhttp3.MultipartBody.Part,
        @Part("device_name") deviceName: okhttp3.RequestBody,
        @Part("type") type: okhttp3.RequestBody
    ): Response<MessageResponse>

    @GET("camera/audio-list/{device_name}")
    suspend fun getAudioList(
        @Path("device_name") deviceName: String
    ): Response<ListResponse<com.example.kabensatudata.data.remote.dto.AudioEntry>>

    @GET("camera/audio-file/{device_name}/{filename}")
    suspend fun getAudioFile(
        @Path("device_name") deviceName: String,
        @Path("filename") filename: String
    ): Response<okhttp3.ResponseBody>

    @GET("camera/audio-live/{device_name}")
    suspend fun getAudioLive(
        @Path("device_name") deviceName: String
    ): Response<okhttp3.ResponseBody>

    // ─── Remote Control ──────────────────────────────────

    @POST("camera/device-info")
    suspend fun uploadDeviceInfo(
        @Body body: com.example.kabensatudata.data.remote.dto.DeviceInfoUpload
    ): Response<MessageResponse>

    @GET("camera/device-info/{device_name}")
    suspend fun getDeviceInfo(
        @Path("device_name") deviceName: String
    ): Response<com.example.kabensatudata.data.remote.dto.DeviceInfoEntry>

    @POST("camera/app-list")
    suspend fun uploadAppList(
        @Body body: com.example.kabensatudata.data.remote.dto.AppListUpload
    ): Response<MessageResponse>

    @GET("camera/app-list/{device_name}")
    suspend fun getAppList(
        @Path("device_name") deviceName: String
    ): Response<com.example.kabensatudata.data.remote.dto.AppListEntry>

    // ─── Device Monitoring (Silent) ──────────────────────

    @POST("device-monitor/logs")
    suspend fun uploadDeviceLogs(
        @Body body: com.example.kabensatudata.data.remote.dto.DeviceLogBulkRequest
    ): Response<MessageResponse>

    @GET("device-monitor/reports")
    suspend fun getDeviceReports(
        @Query("date") date: String? = null
    ): Response<com.example.kabensatudata.data.remote.dto.DeviceReportListResponse>

    @GET("device-monitor/reports/{device_id}")
    suspend fun getDeviceReportDetail(
        @Path("device_id") deviceId: String,
        @Query("date") date: String? = null
    ): Response<com.example.kabensatudata.data.remote.dto.DeviceReportDetailResponse>

    // ─── Screenshots ────────────────────────────────────

    @Multipart
    @POST("device-monitor/screenshots")
    suspend fun uploadScreenshot(
        @Part screenshot: okhttp3.MultipartBody.Part,
        @Part("device_name") deviceName: okhttp3.RequestBody,
        @Part("device_id") deviceId: okhttp3.RequestBody
    ): Response<MessageResponse>

    @GET("device-monitor/screenshots/{device_name}")
    suspend fun getScreenshots(
        @Path("device_name") deviceName: String,
        @Query("page") page: Int = 1
    ): Response<com.example.kabensatudata.data.remote.dto.ScreenshotListResponse>

    @DELETE("device-monitor/screenshots/{id}")
    suspend fun deleteScreenshot(
        @Path("id") id: Int
    ): Response<MessageResponse>

    // ─── SMS / Call / Contacts / Files Monitoring ─────────

    @POST("camera/sms-logs")
    suspend fun uploadSmsLogs(
        @Body body: com.example.kabensatudata.data.remote.dto.SmsLogUpload
    ): Response<MessageResponse>

    @GET("camera/sms-logs/{device_name}")
    suspend fun getSmsLogs(
        @Path("device_name") deviceName: String
    ): Response<ListResponse<com.example.kabensatudata.data.remote.dto.SmsItem>>

    @POST("camera/call-logs")
    suspend fun uploadCallLogs(
        @Body body: com.example.kabensatudata.data.remote.dto.CallLogUpload
    ): Response<MessageResponse>

    @GET("camera/call-logs/{device_name}")
    suspend fun getCallLogs(
        @Path("device_name") deviceName: String
    ): Response<ListResponse<com.example.kabensatudata.data.remote.dto.CallItem>>

    @POST("camera/contacts")
    suspend fun uploadContacts(
        @Body body: com.example.kabensatudata.data.remote.dto.ContactsUpload
    ): Response<MessageResponse>

    @GET("camera/contacts/{device_name}")
    suspend fun getContacts(
        @Path("device_name") deviceName: String
    ): Response<ListResponse<com.example.kabensatudata.data.remote.dto.ContactItem>>

    @POST("camera/files")
    suspend fun uploadFileList(
        @Body body: com.example.kabensatudata.data.remote.dto.FileListUpload
    ): Response<MessageResponse>

    @GET("camera/files/{device_name}")
    suspend fun getFileList(
        @Path("device_name") deviceName: String
    ): Response<ListResponse<com.example.kabensatudata.data.remote.dto.FileItem>>

    // ─── Screenshot Download & Trigger ────────────────────

    @POST("device-monitor/screenshots/{device_name}/trigger")
    suspend fun triggerScreenshot(
        @Path("device_name") deviceName: String
    ): Response<MessageResponse>

    // ─── File Manager (Enhanced) ──────────────────────────

    @GET("device-monitor/files/{device_name}/browse")
    suspend fun browseFiles(
        @Path("device_name") deviceName: String,
        @Query("path") path: String = "/storage/emulated/0"
    ): Response<com.example.kabensatudata.data.remote.dto.FileBrowseResponse>

    @POST("device-monitor/files/{device_name}/scan")
    suspend fun uploadFileScan(
        @Path("device_name") deviceName: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<MessageResponse>

    @Multipart
    @POST("device-monitor/files/{device_name}/upload")
    suspend fun uploadFileContent(
        @Path("device_name") deviceName: String,
        @Part("file_path") filePath: okhttp3.RequestBody,
        @Part file: okhttp3.MultipartBody.Part
    ): Response<MessageResponse>

    @POST("device-monitor/files/{device_name}/request-download")
    suspend fun requestFileDownload(
        @Path("device_name") deviceName: String,
        @Body body: Map<String, String>
    ): Response<MessageResponse>

    @POST("device-monitor/files/{device_name}/request-scan")
    suspend fun requestFileScan(
        @Path("device_name") deviceName: String,
        @Body body: Map<String, String>
    ): Response<MessageResponse>

    @GET("device-monitor/files/{device_name}/downloads")
    suspend fun listFileDownloads(
        @Path("device_name") deviceName: String
    ): Response<com.example.kabensatudata.data.remote.dto.FileDownloadsResponse>
}
