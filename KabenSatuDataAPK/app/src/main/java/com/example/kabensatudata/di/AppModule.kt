package com.example.kabensatudata.di

import android.content.Context
import androidx.room.Room
import com.example.kabensatudata.data.local.AppDatabase
import com.example.kabensatudata.data.local.AuthTokenManager
import com.example.kabensatudata.data.local.dao.*
import com.example.kabensatudata.data.remote.ApiService
import com.example.kabensatudata.data.remote.BokApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Production server (kabensatudata)
    // Dev lokal: ganti ke http://192.168.3.68:8001/api/mobile/
    // Emulator: ganti ke http://10.0.2.2:8001/api/mobile/
    private const val BASE_URL = "https://kabensatudata.my.id/api/mobile/"

    // BOK (bokbaru) — Dev: pakai IP lokal (nanti ganti saat production)
    private const val BOK_BASE_URL = "https://puskesmaskaben.my.id/api/mobile/"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideVillageDao(db: AppDatabase): VillageDao = db.villageDao()
    @Provides fun provideBuildingDao(db: AppDatabase): BuildingDao = db.buildingDao()
    @Provides fun provideFamilyDao(db: AppDatabase): FamilyDao = db.familyDao()
    @Provides fun provideFamilyMemberDao(db: AppDatabase): FamilyMemberDao = db.familyMemberDao()
    @Provides fun provideMedicalRecordDao(db: AppDatabase): MedicalRecordDao = db.medicalRecordDao()
    @Provides fun provideMedicineDao(db: AppDatabase): MedicineDao = db.medicineDao()
    @Provides fun provideMedicineUsageDao(db: AppDatabase): MedicineUsageDao = db.medicineUsageDao()
    @Provides fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao = db.syncQueueDao()
    @Provides fun provideCalendarCacheDao(db: AppDatabase): CalendarCacheDao = db.calendarCacheDao()
    @Provides fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()
    @Provides fun provideBokCacheDao(db: AppDatabase): BokCacheDao = db.bokCacheDao()
    @Provides fun providePegawaiDao(db: AppDatabase): PegawaiDao = db.pegawaiDao()
    @Provides fun provideSuratArsipCacheDao(db: AppDatabase): SuratArsipCacheDao = db.suratArsipCacheDao()
    @Provides fun providePegawaiStatsCacheDao(db: AppDatabase): PegawaiStatsCacheDao = db.pegawaiStatsCacheDao()
    @Provides fun provideSpmCacheDao(db: AppDatabase): SpmCacheDao = db.spmCacheDao()
    @Provides fun provideP2PMedicalRecordDao(db: AppDatabase): P2PMedicalRecordDao = db.p2pMedicalRecordDao()
    @Provides fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides fun provideActivityLogDao(db: AppDatabase): ActivityLogDao = db.activityLogDao()
    @Provides fun provideInventarisCacheDao(db: AppDatabase): InventarisCacheDao = db.inventarisCacheDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenManager: AuthTokenManager): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS  // BODY → HEADERS: avoid buffering huge sync responses
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = tokenManager.getToken()
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/json")
                        .build()
                } else {
                    chain.request().newBuilder()
                        .addHeader("Accept", "application/json")
                        .build()
                }
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGson(): com.google.gson.Gson {
        return com.google.gson.GsonBuilder()
            // Handle Laravel returning booleans as 0/1 (MySQL tinyint)
            // Must register for both primitive boolean and boxed java.lang.Boolean (for Boolean?)
            .registerTypeAdapter(Boolean::class.java, com.google.gson.JsonDeserializer<Boolean> { json, _, _ ->
                try { json.asBoolean } catch (_: Exception) {
                    try { json.asInt != 0 } catch (_: Exception) {
                        try { json.asString.toBoolean() } catch (_: Exception) { false }
                    }
                }
            })
            .registerTypeAdapter(Boolean::class.javaObjectType, com.google.gson.JsonDeserializer<Boolean> { json, _, _ ->
                try { json.asBoolean } catch (_: Exception) {
                    try { json.asInt != 0 } catch (_: Exception) {
                        try { json.asString.toBoolean() } catch (_: Exception) { null }
                    }
                }
            })
            // Handle Laravel returning decimal fields as strings ("50.00" instead of 50.0)
            .registerTypeAdapter(Double::class.java, com.google.gson.JsonDeserializer<Double> { json, _, _ ->
                try { json.asDouble } catch (_: Exception) {
                    try { json.asString.toDouble() } catch (_: Exception) { null }
                }
            })
            .registerTypeAdapter(Double::class.javaObjectType, com.google.gson.JsonDeserializer<Double> { json, _, _ ->
                try { json.asDouble } catch (_: Exception) {
                    try { json.asString.toDouble() } catch (_: Exception) { null }
                }
            })
            .registerTypeAdapter(Int::class.java, com.google.gson.JsonDeserializer<Int> { json, _, _ ->
                try { json.asInt } catch (_: Exception) {
                    try { json.asString.toInt() } catch (_: Exception) { null }
                }
            })
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: com.google.gson.Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    // ─── BOK (bokbaru) Retrofit ──────────────────────────────────────────────

    @Provides
    @Singleton
    @Named("bokOkHttp")
    fun provideBokOkHttpClient(tokenManager: AuthTokenManager): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = tokenManager.getBokToken()
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/json")
                        .build()
                } else {
                    chain.request().newBuilder()
                        .addHeader("Accept", "application/json")
                        .build()
                }
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideBokApiService(
        @Named("bokOkHttp") okHttpClient: OkHttpClient,
        gson: com.google.gson.Gson
    ): BokApiService {
        return Retrofit.Builder()
            .baseUrl(BOK_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BokApiService::class.java)
    }
}
