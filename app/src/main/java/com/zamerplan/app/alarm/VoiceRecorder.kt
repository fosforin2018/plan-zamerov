package com.zamerplan.app.alarm

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File

class VoiceRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentPath: String? = null

    fun start(file: File) {
        stop()
        currentPath = file.absolutePath
        try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            recorder = null
        }
    }

    fun stop() {
        try {
            recorder?.apply { stop(); release() }
        } catch (e: Exception) { 
            e.printStackTrace()
        }
        recorder = null
    }

    fun play(file: File, onComplete: () -> Unit = {}): Boolean {
        if (!file.exists() || file.length() == 0L) {
            onComplete()
            return false
        }
        
        try {
            stopPlayer()
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { 
                    release()
                    player = null
                    onComplete() 
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    player = null
                    onComplete()
                    true
                }
                prepare()
                start()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete()
            return false
        }
    }

    private fun stopPlayer() {
        try {
            player?.apply { 
                if (isPlaying) stop()
                release() 
            }
        } catch (e: Exception) { }
        player = null
    }

    fun isRecording(): Boolean = recorder != null
    fun isPlaying(): Boolean = player?.isPlaying == true
}
