package com.zamerplan.app.widget

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.util.Log
import java.io.File

class VoicePlaybackService : Service() {

    private var player: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filePath = intent?.getStringExtra("voice_file_path")
        if (filePath.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val file = File(filePath)
        if (!file.exists()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    player = null
                    stopSelf()
                }
                setOnErrorListener { _, _, _ ->
                    release()
                    player = null
                    stopSelf()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("VoicePlaybackService", "Ошибка воспроизведения", e)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
