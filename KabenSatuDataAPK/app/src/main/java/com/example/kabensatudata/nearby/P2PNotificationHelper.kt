package com.example.kabensatudata.nearby

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shows local notifications for P2P events:
 * - Incoming chat messages
 * - Medical record stage transitions
 */
@Singleton
class P2PNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_CHAT = "p2p_chat"
        const val CHANNEL_MEDICAL = "p2p_medical"
        private var chatNotifId = 2000
        private var medicalNotifId = 3000
    }

    init { createChannels() }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)

            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_CHAT, "Chat P2P",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Pesan chat dari perangkat P2P"
                enableVibration(true)
            })

            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_MEDICAL, "Rekam Medis P2P",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi rekam medis dari perangkat P2P"
                enableVibration(true)
            })
        }
    }

    fun notifyChat(senderName: String, message: String) {
        if (!hasPermission()) return
        val notif = NotificationCompat.Builder(context, CHANNEL_CHAT)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("💬 $senderName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        NotificationManagerCompat.from(context).notify(chatNotifId++, notif)
    }

    fun notifyMedicalRegistration(patientName: String) {
        showMedical("📋 Pendaftaran Baru", "Data pendaftaran $patientName diterima. Lanjutkan ke diagnosis.")
    }

    fun notifyMedicalDiagnosis(patientName: String) {
        showMedical("💊 Diagnosis Selesai", "Diagnosis & resep untuk $patientName diterima. Lanjutkan ke farmasi.")
    }

    fun notifyMedicalDispensed(patientName: String) {
        showMedical("✅ Obat Diberikan", "Obat untuk $patientName telah diberikan. Rekam medis selesai.")
    }

    private fun showMedical(title: String, body: String) {
        if (!hasPermission()) return
        val notif = NotificationCompat.Builder(context, CHANNEL_MEDICAL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .build()

        NotificationManagerCompat.from(context).notify(medicalNotifId++, notif)
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }
}
