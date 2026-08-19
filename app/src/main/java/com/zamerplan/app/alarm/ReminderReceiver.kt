package com.zamerplan.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.zamerplan.app.MainActivity
import com.zamerplan.app.model.Storage
import com.zamerplan.app.model.ZamerStatus
import java.io.File

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("zamer_id", 0L)
        if (id == 0L) return
        val storage = Storage(ctx)
        val z = storage.load().firstOrNull { it.id == id } ?: return
        if (z.status != ZamerStatus.PLANNED) return

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chId = "zamer_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(chId, "Напоминания о замерах",
                NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }

        val tap = PendingIntent.getActivity(
            ctx, id.toInt(),
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "📏 Замер: " + (z.name.ifBlank { z.address })
        val text = z.timeText() + " · " + z.address

        val n = NotificationCompat.Builder(ctx, chId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(id.toInt() and 0x7FFFFFFF, n)

        // Голос → потом мелодия
        val voice = File(ctx.filesDir, "voice_$id.m4a")
        if (voice.exists()) {
            playAudio(voice.absolutePath) {
                playRingtone(ctx, SettingsStore(ctx).ringtoneUri)
            }
        } else {
            playRingtone(ctx, SettingsStore(ctx).ringtoneUri)
        }
    }

    private fun playAudio(path: String, onComplete: () -> Unit) {
        try {
            val mp = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { release(); onComplete() }
                prepare()
                start()
            }
        } catch (e: Exception) { onComplete() }
    }

    private fun playRingtone(ctx: Context, uriStr: String) {
        try {
            val uri = if (uriStr.isNotBlank()) Uri.parse(uriStr)
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val mp = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
                setDataSource(ctx, uri)
                setOnCompletionListener { release() }
                prepare()
                start()
            }
        } catch (e: Exception) { }
    }
}
