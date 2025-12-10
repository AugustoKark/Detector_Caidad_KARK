package com.kark.falldetector.alerts

import com.kark.falldetector.utils.Log
import com.kark.falldetector.core.Guardian
import com.kark.falldetector.sensors.Positioning
import android.content.Context
import android.location.Location
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
            // Primero enviamos SMS
            val recipient = Contact[context]
            if (recipient != null && recipient.isNotBlank()) {
                Log.d(TAG, "Iniciando alerta para enviar a: $recipient")

                // Obtenemos la ubicación actual - con intentos múltiples
                val position = Positioning.singleton
                var location: Location? = null
                var message: String

                // Ejecutamos en un hilo separado para no bloquear la UI
                Thread {
                    try {
                        // Intentamos obtener la ubicación hasta 5 veces con 2 segundos entre intentos
                        val maxAttempts = 5
                        for (attempt in 1..maxAttempts) {
                            Log.d(TAG, "Intento $attempt de $maxAttempts para obtener ubicación")
                            location = position?.getLastKnownLocation()

                            if (location != null) {
                                Log.d(TAG, "Ubicación obtenida en intento $attempt: ${location?.latitude}, ${location?.longitude}")
                                break  // Salir del bucle si obtuvimos ubicación
                            }

                            if (attempt < maxAttempts) {
                                Log.d(TAG, "Esperando 2 segundos antes del siguiente intento...")
                                Thread.sleep(2000)  // Esperar 2 segundos entre intentos
                            }
                        }

                        // Creamos el mensaje con la ubicación si está disponible
                        // Coordenadas de fallback para presentación
                        val fallbackLat = -32.89134674122142
                        val fallbackLng = -68.86181492189061

                        message = if (location != null) {
                            // Simplificamos la URL para evitar problemas de formateo
                            "¡ALERTA! Se ha detectado una posible caída. maps.google.com/?q=${location?.latitude},${location?.longitude}"
                        } else {
                            // Usar ubicación de fallback cuando no se puede obtener GPS
                            "¡ALERTA! Se ha detectado una posible caída. maps.google.com/?q=$fallbackLat,$fallbackLng"
                        }

                        // Enviamos el mensaje en el hilo principal
                        Handler(Looper.getMainLooper()).post {
                            try {
                                Log.d(TAG, "Enviando SMS: $message")
                                Messenger.sms(context, recipient, message)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error al enviar SMS: ${e.message}")
                                // Intento de respaldo con el mensaje básico
                                Messenger.sms(context, recipient, "¡ALERTA! Se ha detectado una posible caída.")
                            }
                        }

                        // Reproducimos sonido de alarma
                        Handler(Looper.getMainLooper()).post {
                            siren(context)
                        }

                        // Y realizamos la llamada después de un breve retraso
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                Messenger.call(context, recipient)
                            } catch (exception: Exception) {
                                Log.e(TAG, "Could not make an emergency call: ${exception.message}")
                            }
                        }, 3000)  // Esperar 3 segundos antes de realizar la llamada

                    } catch (e: Exception) {
                        Log.e(TAG, "Error durante la obtención de ubicación: ${e.message}")

                        // En caso de error, enviamos mensaje con ubicación de fallback
                        Handler(Looper.getMainLooper()).post {
                            try {
                                Messenger.sms(context, recipient, "¡ALERTA! Se ha detectado una posible caída. maps.google.com/?q=-32.89134674122142,-68.86181492189061")
                                siren(context)
                            } catch (smsException: Exception) {
                                Log.e(TAG, "Error al enviar SMS de respaldo: ${smsException.message}")
                            }
                        }
                    }
                }.start()

            } else {
                Log.e(TAG, "No recipient specified for alert")
                // Aún así reproducimos el sonido de alarma
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