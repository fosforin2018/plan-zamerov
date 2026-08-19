package com.zamerplan.app.alarm

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File

class VoiceRecorder(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var currentPath: String? = null

    fun start(file: File) {
        stop()
        currentPath = file.absolutePath
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    fun stop() {
        try {
            recorder?.apply { stop(); release() }
        } catch (e: Exception) { }
        recorder = null
    }

    fun play(file: File, onComplete: () -> Unit = {}) {
        MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { release(); onComplete() }
            prepare()
            start()
        }
    }
}
