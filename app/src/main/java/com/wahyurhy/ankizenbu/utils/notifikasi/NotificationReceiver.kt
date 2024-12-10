package com.wahyurhy.ankizenbu.utils.notifikasi

import android.app.PendingIntent
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wahyurhy.ankizenbu.MainActivity

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val word = intent?.getStringExtra("word") ?: return

        // Periksa izin POST_NOTIFICATIONS
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // Jika izin tidak diberikan, tidak mengirim notifikasi
            return
        }

        // Kirim notifikasi
        val notificationId = word.hashCode()
        val notification = NotificationCompat.Builder(context, "word_reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Reminder: $word")
            .setContentText("Ingat kata ini!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    Intent(context, MainActivity::class.java).apply {
                        action = "SEND_WORD"
                        putExtra("word", word)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)

        // Jadwalkan ulang dengan interval eksponensial
        val interval = intent.getLongExtra("interval", 24 * 60 * 60 * 1000L) // Default 1 hari
        scheduleReminder(context, word, interval * 2)
    }

    companion object {
        fun scheduleReminder(context: Context, word: String, interval: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("word", word)
                putExtra("interval", interval)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                word.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerTime = System.currentTimeMillis() + interval
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    // Jadwalkan alarm
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    // Log atau beri tahu pengguna bahwa izin diperlukan
                    println("Tidak dapat menjadwalkan alarm karena izin tidak diberikan.")
                }
            } else {
                // Jadwalkan alarm untuk Android versi di bawah API 31
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }
}