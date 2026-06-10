package com.example.mediarecorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

interface AudioRecorder {
    fun start(outputFile: File)
    fun stop()
    fun getMaxAmplitude(): Int
}

class AndroidAudioRecorder(
    private val context: Context
) : AudioRecorder {

    private var recorder: MediaRecorder? = null

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    override fun start(outputFile: File) {
        if (recorder != null) return

        try {
            createRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(192000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)

                prepare()
                start()
                recorder = this
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Cleanup on error
            recorder?.reset()
            recorder?.release()
            recorder = null
        }
    }

    override fun stop() {
        try {
            recorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recorder?.reset()
            recorder?.release()
            recorder = null
        }
    }

    override fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
