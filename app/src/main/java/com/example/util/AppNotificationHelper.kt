package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import java.text.NumberFormat
import java.util.Locale

object AppNotificationHelper {

    private const val CHANNEL_ID = "bku_transaction_channel"
    private const val CHANNEL_NAME = "Transaksi Buku Kas (BKU)"
    private const val CHANNEL_DESC = "Notifikasi pencatatan pemasukan dan pengeluaran kas"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showTransactionNotification(
        context: Context,
        type: String,
        title: String,
        amount: Double,
        fundSource: String,
        recordedBy: String
    ) {
        // Cek izin notifikasi pada Android 13+ (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
        val formattedAmount = currencyFormat.format(amount).replace(",00", "")

        val isIncome = type.equals("PEMASUKAN", ignoreCase = true) || type.equals("INCOME", ignoreCase = true)
        val notifTitle = if (isIncome) {
            "🟢 Pemasukan Kas Baru: $formattedAmount"
        } else {
            "🔴 Pengeluaran Kas Baru: $formattedAmount"
        }

        val approvalSuffix = if (amount >= 50000.0) " (perlu persetujuan kepala sekolah)" else ""
        val notifBody = "$title • $fundSource$approvalSuffix"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notifTitle)
            .setContentText(notifBody)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$notifBody\nTransaksi berhasil disimpan di Buku Kas Umum ($fundSource)."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            with(NotificationManagerCompat.from(context)) {
                val notifId = (System.currentTimeMillis() % 100000).toInt()
                notify(notifId, builder.build())
            }
        } catch (e: SecurityException) {
            // Permission not granted or revoked
        }
    }
}
