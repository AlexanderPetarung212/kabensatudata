package com.example.kabensatudata.data.remote

import com.example.kabensatudata.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit service interface for the bokbaru Mobile API.
 * Base URL: http://<BOK_SERVER>/api/mobile/
 */
interface BokApiService {

    // ─── Authentication ─────────────────────────────────────────────────────────

    @POST("login")
    suspend fun login(@Body request: BokLoginRequest): Response<BokLoginResponse>

    // ─── Calendar ───────────────────────────────────────────────────────────────

    @GET("calendar")
    suspend fun getCalendar(@Query("year") year: Int): Response<CalendarResponse>

    @GET("calendar/{poaId}/detail/{month}")
    suspend fun getCalendarDetail(
        @Path("poaId") poaId: Long,
        @Path("month") month: Int
    ): Response<CalendarDetailResponse>

    // ─── Claim ──────────────────────────────────────────────────────────────────

    @GET("calendar/{poaId}/claim-prep/{month}")
    suspend fun getClaimPrep(
        @Path("poaId") poaId: Long,
        @Path("month") month: Int
    ): Response<ClaimPrepResponse>

    @POST("calendar/{poaId}/claim")
    suspend fun executeClaim(
        @Path("poaId") poaId: Long,
        @Body request: ClaimRequest
    ): Response<ClaimResponse>

    // ─── Downloads ──────────────────────────────────────────────────────────────

    @GET("lpj/{lpjId}/download")
    @Streaming
    suspend fun downloadLpj(@Path("lpjId") lpjId: Long): Response<okhttp3.ResponseBody>

    @GET("tiba-berangkat/{tbId}/download")
    @Streaming
    suspend fun downloadTibaBerangkat(@Path("tbId") tbId: Long): Response<okhttp3.ResponseBody>

    @GET("item-opsional/{itemId}/download")
    @Streaming
    suspend fun downloadItemOpsional(@Path("itemId") itemId: Long): Response<okhttp3.ResponseBody>

    // ─── POA ────────────────────────────────────────────────────────────────────

    @GET("poa")
    suspend fun getPoaList(
        @Query("year") year: Int,
        @Query("q") q: String? = null,
        @Query("komponen") komponen: String? = null,
        @Query("month") month: Int? = null,
        @Query("page") page: Int = 1
    ): Response<PoaListResponse>

    @GET("poa/{poaId}")
    suspend fun getPoaDetail(@Path("poaId") poaId: Long): Response<PoaDetailResponse>

    @POST("poa")
    suspend fun createPoa(@Body request: PoaCreateRequest): Response<GenericResponse>

    @PUT("poa/{poaId}")
    suspend fun updatePoa(@Path("poaId") poaId: Long, @Body request: PoaCreateRequest): Response<GenericResponse>

    @DELETE("poa/{poaId}")
    suspend fun deletePoa(@Path("poaId") poaId: Long): Response<GenericResponse>

    @POST("poa/{poaId}/toggle-mark")
    suspend fun poaToggleMark(@Path("poaId") poaId: Long, @Body request: ToggleRequest): Response<GenericResponse>

    @POST("poa/{poaId}/toggle-claim-lock")
    suspend fun poaToggleClaimLock(@Path("poaId") poaId: Long, @Body request: ToggleRequest): Response<GenericResponse>

    @POST("poa/{poaId}/toggle-claim-label")
    suspend fun poaToggleClaimLabel(@Path("poaId") poaId: Long, @Body request: ToggleRequest): Response<GenericResponse>

    @POST("poa/{poaId}/upsert-month")
    suspend fun poaUpsertMonth(@Path("poaId") poaId: Long, @Body request: MonthMetaRequest): Response<GenericResponse>

    @POST("poa/{poaId}/item-progress")
    suspend fun poaItemProgress(@Path("poaId") poaId: Long, @Body request: ItemProgressRequest): Response<GenericResponse>

    @POST("poa/{poaId}/carry-over")
    suspend fun poaCarryOver(@Path("poaId") poaId: Long, @Body request: CarryOverRequest): Response<GenericResponse>

    @POST("poa/bulk-lock")
    suspend fun poaBulkLock(@Body request: BulkLockRequest): Response<GenericResponse>

    @GET("poa/available-rabs")
    suspend fun getAvailableRabs(
        @Query("year") year: Int,
        @Query("exclude_poa_id") excludePoaId: Long? = null
    ): Response<List<RabOption>>

    @GET("employees")
    suspend fun getEmployees(): Response<List<EmployeeDto>>

    // ─── CMS: Posts (Berita) ────────────────────────────────────────────────────

    @GET("cms/posts")
    suspend fun getCmsPosts(
        @Query("search") search: String? = null,
        @Query("category_id") categoryId: Int? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1
    ): Response<PostListResponse>

    @GET("cms/posts/{id}")
    suspend fun getCmsPost(@Path("id") id: Int): Response<PostDetailResponse>

    @Multipart
    @POST("cms/posts")
    suspend fun createCmsPost(
        @Part("title") title: okhttp3.RequestBody,
        @Part("content") content: okhttp3.RequestBody,
        @Part("category_id") categoryId: okhttp3.RequestBody? = null,
        @Part("excerpt") excerpt: okhttp3.RequestBody? = null,
        @Part("is_published") isPublished: okhttp3.RequestBody? = null,
        @Part("author") author: okhttp3.RequestBody? = null,
        @Part featuredImage: okhttp3.MultipartBody.Part? = null
    ): Response<PostDetailResponse>

    @Multipart
    @POST("cms/posts/{id}")
    suspend fun updateCmsPost(
        @Path("id") id: Int,
        @Part("title") title: okhttp3.RequestBody,
        @Part("content") content: okhttp3.RequestBody,
        @Part("category_id") categoryId: okhttp3.RequestBody? = null,
        @Part("excerpt") excerpt: okhttp3.RequestBody? = null,
        @Part("is_published") isPublished: okhttp3.RequestBody? = null,
        @Part("author") author: okhttp3.RequestBody? = null,
        @Part featuredImage: okhttp3.MultipartBody.Part? = null
    ): Response<PostDetailResponse>

    @DELETE("cms/posts/{id}")
    suspend fun deleteCmsPost(@Path("id") id: Int): Response<SimpleMessageResponse>

    // ─── CMS: Galleries (Galeri) ────────────────────────────────────────────────

    @GET("cms/galleries")
    suspend fun getCmsGalleries(
        @Query("category_id") categoryId: Int? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1
    ): Response<GalleryListResponse>

    @Multipart
    @POST("cms/galleries")
    suspend fun createCmsGallery(
        @Part("title") title: okhttp3.RequestBody? = null,
        @Part("category_id") categoryId: okhttp3.RequestBody? = null,
        @Part("description") description: okhttp3.RequestBody? = null,
        @Part("event_date") eventDate: okhttp3.RequestBody? = null,
        @Part("is_published") isPublished: okhttp3.RequestBody? = null,
        @Part images: List<okhttp3.MultipartBody.Part>
    ): Response<GalleryBatchResponse>

    @Multipart
    @POST("cms/galleries/{id}")
    suspend fun updateCmsGallery(
        @Path("id") id: Int,
        @Part("title") title: okhttp3.RequestBody,
        @Part("category_id") categoryId: okhttp3.RequestBody? = null,
        @Part("description") description: okhttp3.RequestBody? = null,
        @Part("event_date") eventDate: okhttp3.RequestBody? = null,
        @Part("is_published") isPublished: okhttp3.RequestBody? = null,
        @Part image: okhttp3.MultipartBody.Part? = null
    ): Response<GalleryBatchResponse>

    @DELETE("cms/galleries/{id}")
    suspend fun deleteCmsGallery(@Path("id") id: Int): Response<SimpleMessageResponse>

    // ─── CMS: Messages (Pesan) ──────────────────────────────────────────────────

    @GET("cms/messages")
    suspend fun getCmsMessages(@Query("page") page: Int = 1): Response<MessageListResponse>

    @GET("cms/messages/unread-count")
    suspend fun getCmsUnreadCount(): Response<UnreadCountResponse>

    @GET("cms/messages/{id}")
    suspend fun getCmsMessage(@Path("id") id: Int): Response<MessageDetailResponse>

    @DELETE("cms/messages/{id}")
    suspend fun deleteCmsMessage(@Path("id") id: Int): Response<SimpleMessageResponse>

    // ─── CMS: Categories ────────────────────────────────────────────────────────

    @GET("cms/categories")
    suspend fun getCmsCategories(@Query("type") type: String): Response<CategoryListResponse>
}
