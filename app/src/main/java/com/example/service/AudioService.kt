package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.sin

class AudioService : Service() {

    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        const val CHANNEL_ID = "duaa_service_channel"
        const val NOTIFICATION_ID = 8080

        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_STOP = "com.example.action.STOP"
        const val EXTRA_SOUND_NAME = "extra_sound_name"
        const val EXTRA_DUAA_NAME = "extra_duaa_name"

        fun startService(context: Context, soundResName: String, duaaName: String) {
            val intent = Intent(context, AudioService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_SOUND_NAME, soundResName)
                putExtra(EXTRA_DUAA_NAME, duaaName)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("AudioService", "Failed to start service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopAudio()
            stopSelf()
            return START_NOT_STICKY
        }

        val soundName = intent?.getStringExtra(EXTRA_SOUND_NAME) ?: "wakeup"
        val duaaName = intent?.getStringExtra(EXTRA_DUAA_NAME) ?: "Дуа"

        val notification = createNotification(duaaName)
        startForeground(NOTIFICATION_ID, notification)

        if (action == ACTION_PLAY) {
            playDuaaSound(soundName)
        }

        return START_NOT_STICKY
    }

    private fun playDuaaSound(soundName: String) {
        stopAudio()

        val resolvedSoundName = when (soundName) {
            "wakeup" -> "timer"
            "enter_home" -> "in_home"
            "leave_home" -> "out_home"
            else -> soundName
        }

        serviceScope.launch(Dispatchers.Main) {
            val volume = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val app = applicationContext as? com.example.DuaaApplication
                    app?.database?.duaaDao()?.getAppSettings()?.volume ?: 0.5f
                } catch (e: Exception) {
                    0.5f
                }
            }

            val resId = resources.getIdentifier(resolvedSoundName, "raw", packageName)
            if (resId != 0) {
                Log.d("AudioService", "Found raw resource structure for $resolvedSoundName ($resId), playing via MediaPlayer with volume $volume")
                try {
                    mediaPlayer = MediaPlayer.create(this@AudioService, resId).apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setVolume(volume, volume)
                        setOnCompletionListener {
                            stopSelf()
                        }
                        start()
                    }
                } catch (e: Exception) {
                    Log.e("AudioService", "Error playing raw resource $resolvedSoundName via MediaPlayer, falling back to synth", e)
                    playSynthFallback(soundName, volume)
                }
            } else {
                Log.d("AudioService", "No raw resource found for $resolvedSoundName, playing dynamic synth fallback with volume $volume")
                playSynthFallback(soundName, volume)
            }
        }
    }

    private fun playSynthFallback(soundName: String, volume: Float) {
        serviceScope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val frequencies = when (soundName) {
                "wakeup" -> doubleArrayOf(261.63, 329.63, 392.00, 523.25) // C4, E4, G4, C5 (Bright rising chime)
                "enter_home" -> doubleArrayOf(392.00, 523.25, 659.25, 783.99) // G4, C5, E5, G5 (Welcoming warm chime)
                "leave_home" -> doubleArrayOf(783.99, 659.25, 523.25, 392.00) // G5, E5, C5, G4 (Gentle leaving chime)
                "car_connect" -> doubleArrayOf(440.00, 554.37, 659.25) // A4, C#5, E5 (Modern techy connection chime)
                "car_disconnect" -> doubleArrayOf(659.25, 554.37, 440.00) // E5, C#5, A4 (Gentle grounding chime)
                else -> doubleArrayOf(440.00, 440.00)
            }

            val toneDurationMs = 250 // duration per frequency
            val totalSamples = sampleRate * (toneDurationMs * frequencies.size) / 1000
            val buffer = ShortArray(totalSamples)

            var sampleIndex = 0
            for (freq in frequencies) {
                val numSamplesPerTone = sampleRate * toneDurationMs / 1000
                for (i in 0 until numSamplesPerTone) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / freq)
                    val progress = i.toDouble() / numSamplesPerTone.toDouble()
                    val envelope = if (progress < 0.1) {
                        progress / 0.1 // quick attack
                    } else {
                        1.0 - (progress - 0.1) / 0.9 // decay
                    }
                    val sample = (sin(angle) * Short.MAX_VALUE * 0.4 * envelope * volume).toInt()
                    buffer[sampleIndex++] = sample.toShort()
                }
            }

            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBufferSize, buffer.size * 2)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack = track
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_NON_BLOCKING)
                track.setVolume(volume)
                track.play()

                val totalDurationMs = toneDurationMs * frequencies.size
                kotlinx.coroutines.delay(totalDurationMs.toLong() + 500)
            } catch (e: Exception) {
                Log.e("AudioService", "Error playing dynamic synth audio", e)
            } finally {
                stopSelf()
            }
        }
    }

    private fun stopAudio() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AudioService", "Error stopping MediaPlayer", e)
        }

        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.e("AudioService", "Error stopping audio track", e)
        }
    }

    private fun createNotification(duaaName: String): Notification {
        val stopIntent = Intent(this, AudioService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Воспроизведение Дуа")
            .setContentText(duaaName)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Служба воспроизведения Дуа",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомление для фоновой работы службы воспроизведения Дуа"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        stopAudio()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
