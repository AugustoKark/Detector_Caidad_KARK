package com.kark.falldetector.alerts

import com.kark.falldetector.utils.Log
import com.kark.falldetector.core.Guardian
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi

class Alarm private constructor(private val context: Guardian) {
    private var player: MediaPlayer? = null
    private var finished: Boolean = true

    private fun stopPlayerIfNeeded() {
        player?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (ignored: Exception) {
            }
            player = null
        }
    }

    internal fun sound(context: Context) {
        synchronized(this) {
            if (!finished) {
                stopPlayerIfNeeded()
            }
            finished = false
            try {
                // Maximizamos el volumen
                val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                manager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    manager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    0
                )
                val ringtone: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                player = MediaPlayer.create(context, ringtone)
                player?.isLooping = true
                player?.start()
                // Detenemos después de 5 minutos si no se ha detenido antes
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        stopPlayerIfNeeded()
                        finished = true
                    } catch (ignored: Exception) {
                    }
                }, (5 * 60 * 1000).toLong())
            } catch (exception: Exception) {
                Log.e(TAG, "Alert sounder failed: ${exception.message}")
            }
        }
    }

    internal fun stop() {
        synchronized(this) {
            stopPlayerIfNeeded()
            finished = true
        }
    }

    companion object {
        private val TAG = Alarm::class.java.name
        private var singleton: Alarm? = null

        fun instance(guardian: Guardian): Alarm {
            val existing = singleton
            if (existing != null) {
                return existing
            }
            val created = Alarm(guardian)
            singleton = created
            return created
        }

        @RequiresApi(Build.VERSION_CODES.M)
        fun alert(context: Context) {
            val recipient = Contact[context]
            if (recipient != null && recipient.isNotBlank()) {
                Log.d(TAG, "Iniciando alerta para enviar a: $recipient")

                // Enviamos SMS inmediatamente sin ubicación
                try {
                    val message = "¡ALERTA! Se ha detectado una posible caída."
                    Log.d(TAG, "Enviando SMS: $message")
                    Messenger.sms(context, recipient, message)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al enviar SMS: ${e.message}")
                }

                // Reproducimos sonido de alarma
                siren(context)

                // Realizamos la llamada después de un breve retraso
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        Messenger.call(context, recipient)
                    } catch (exception: Exception) {
                        Log.e(TAG, "Could not make an emergency call: ${exception.message}")
                    }
                }, 3000)

            } else {
                Log.e(TAG, "No recipient specified for alert")
                siren(context)
            }
        }
        // Nueva implementación para evitar referencia circular
        fun siren(context: Context) {
            val alarm = if (context is Guardian) {
                instance(context)
            } else {
                val app = context.applicationContext
                if (app is Guardian) {
                    instance(app)
                } else {
                    Log.e(TAG, "Could not get Guardian instance")
                    return
                }
            }
            alarm.sound(context)
        }

        /**
         * Establece el volumen de un stream de audio al máximo
         */
        fun loudest(context: Context, streamType: Int) {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(
                    streamType,
                    audioManager.getStreamMaxVolume(streamType),
                    0
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to set volume to maximum: ${exception.message}")
            }
        }
    }
}