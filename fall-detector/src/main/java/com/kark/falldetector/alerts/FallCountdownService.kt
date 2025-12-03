package com.kark.falldetector.alerts

import com.kark.falldetector.R
import com.kark.falldetector.core.Guardian
import com.kark.falldetector.sensors.Battery
import com.kark.falldetector.sensors.Positioning
import com.kark.falldetector.storage.ServerAdapter
import com.kark.falldetector.utils.VibrationUtils
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat

class FallCountdownService : Service() {
    companion object {
        private const val TAG = "FallCountdownService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "fall_countdown_channel"
        private const val DEFAULT_COUNTDOWN_SECONDS = 30
        
        const val ACTION_START_COUNTDOWN = "START_COUNTDOWN"
        const val ACTION_CANCEL_COUNTDOWN = "CANCEL_COUNTDOWN"
        const val ACTION_GET_STATUS = "GET_STATUS"
        
        const val EXTRA_IS_FALSE_POSITIVE = "IS_FALSE_POSITIVE"
        
        // Estado global del servicio
        @Volatile
        var isCountdownActive: Boolean = false
        @Volatile
        var secondsRemaining: Int = 0
        private var countdownTimer: CountDownTimer? = null
        private var serviceInstance: FallCountdownService? = null
        
        fun startCountdown(context: Context, isFalsePositive: Boolean = false) {
            val intent = Intent(context, FallCountdownService::class.java).apply {
                action = ACTION_START_COUNTDOWN
                putExtra(EXTRA_IS_FALSE_POSITIVE, isFalsePositive)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun cancelCountdown(context: Context) {
            val intent = Intent(context, FallCountdownService::class.java).apply {
                action = ACTION_CANCEL_COUNTDOWN
            }
            context.startService(intent)
        }
        
        fun getCountdownStatus(): Pair<Boolean, Int> {
            return Pair(isCountdownActive, secondsRemaining)
        }
    }
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: VibrationUtils? = null
    private var countdownMediaPlayer: MediaPlayer? = null
    private var countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS
    private var isFalsePositive: Boolean = false
    
    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        createNotificationChannel()
        
        // Adquirir wake lock para mantener el servicio activo
        acquireWakeLock()
        
        // Obtener configuración de countdown desde preferencias
        val prefs = getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
        countdownSeconds = prefs.getInt("fall_detection_delay", DEFAULT_COUNTDOWN_SECONDS)
        
        Log.i(TAG, "FallCountdownService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_COUNTDOWN -> {
                if (!isCountdownActive) {
                    isFalsePositive = intent.getBooleanExtra(EXTRA_IS_FALSE_POSITIVE, false)
                    startCountdown()
                }
            }
            ACTION_CANCEL_COUNTDOWN -> {
                cancelCountdown()
            }
            ACTION_GET_STATUS -> {
                // No hacer nada, solo para verificar el estado
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
        cancelCountdown()
        releaseWakeLock()
        Log.i(TAG, "FallCountdownService destroyed")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alerta de Caída",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones para alertas de caída"
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
            }
            
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun startCountdown() {
        isCountdownActive = true
        secondsRemaining = countdownSeconds

        Log.i(TAG, "Starting fall countdown: $countdownSeconds seconds")

        // Iniciar vibración y sonido
        startEmergencyAlerts()

        // Crear notificación persistente
        startForeground(NOTIFICATION_ID, createCountdownNotification(secondsRemaining))

        // Lanzar la actividad de alerta para mostrar la UI
        launchFallAlertActivity()
        
        // Iniciar el countdown timer
        countdownTimer = object : CountDownTimer((countdownSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining = (millisUntilFinished / 1000).toInt()
                
                // Actualizar notificación
                val notification = createCountdownNotification(secondsRemaining)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
                
                Log.d(TAG, "Countdown: $secondsRemaining seconds remaining")
            }
            
            override fun onFinish() {
                secondsRemaining = 0
                Log.w(TAG, "Countdown finished - sending emergency alert")
                
                // Detener alertas
                stopEmergencyAlerts()
                
                // Enviar SMS y hacer llamada
                sendEmergencyAlert()
                
                // Finalizar servicio
                finishCountdown(false)
            }
        }.start()
    }
    
    private fun cancelCountdown() {
        if (isCountdownActive) {
            Log.i(TAG, "Cancelling fall countdown")
            
            countdownTimer?.cancel()
            countdownTimer = null
            
            stopEmergencyAlerts()
            
            // Reportar cancelación
            reportCancellation()
            
            finishCountdown(true)
        }
    }
    
    private fun finishCountdown(wasCancelled: Boolean) {
        isCountdownActive = false
        secondsRemaining = 0
        
        // Detener servicio foreground
        stopForeground(true)
        stopSelf()
        
        Log.i(TAG, "Fall countdown finished - cancelled: $wasCancelled")
    }
    
    private fun startEmergencyAlerts() {
        try {
            // Vibración continua
            val emergencyPattern = longArrayOf(0, 1000, 200, 1000, 200, 1000, 200, 1000)
            VibrationUtils.vibrate(this, emergencyPattern, 0) // Repetir indefinidamente
            
            // Sonido de alarma continuo
            startCountdownSound()
            
            Log.i(TAG, "Emergency alerts started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting emergency alerts: ${e.message}")
        }
    }
    
    private fun stopEmergencyAlerts() {
        try {
            // Detener vibración
            VibrationUtils.stopVibration(this)
            
            // Detener sonido
            releaseMediaPlayer()
            
            Log.i(TAG, "Emergency alerts stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping emergency alerts: ${e.message}")
        }
    }
    
    private fun startCountdownSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            countdownMediaPlayer = MediaPlayer.create(this, alarmUri)
            
            countdownMediaPlayer?.apply {
                isLooping = false
                setAudioStreamType(AudioManager.STREAM_ALARM)
                
                // Configurar volumen máximo
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                    0
                )
                
                start()
                
                // Repetir cada 2 segundos
                Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
                    override fun run() {
                        if (isCountdownActive && countdownMediaPlayer != null) {
                            try {
                                if (!countdownMediaPlayer!!.isPlaying) {
                                    countdownMediaPlayer?.start()
                                }
                                Handler(Looper.getMainLooper()).postDelayed(this, 2000)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error playing countdown sound: ${e.message}")
                            }
                        }
                    }
                }, 2000)
            }
            
            Log.d(TAG, "Countdown sound started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting countdown sound: ${e.message}")
        }
    }
    
    private fun releaseMediaPlayer() {
        countdownMediaPlayer?.apply {
            try {
                if (isPlaying) {
                    stop()
                }
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing media player: ${e.message}")
            }
        }
        countdownMediaPlayer = null
    }
    
    private fun sendEmergencyAlert() {
        try {
            // Enviar SMS y hacer llamada usando la clase Alarm
            Alarm.alert(applicationContext)
            
            // Reportar el evento al servidor
            val position = Positioning.singleton
            val location = position?.getLastKnownLocation()
            
            ServerAdapter.reportFallEvent(
                applicationContext,
                location?.latitude,
                location?.longitude,
                Battery.level(applicationContext)
            )
            
            Guardian.say(applicationContext, Log.WARN, TAG, "Emergency alert sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending emergency alert: ${e.message}")
        }
    }
    
    private fun reportCancellation() {
        try {
            // Reportar la cancelación al servidor para análisis de falsos positivos
            val position = Positioning.singleton
            val location = position?.getLastKnownLocation()
            
            ServerAdapter.reportFallAlertCancelled(
                applicationContext,
                location?.latitude,
                location?.longitude,
                Battery.level(applicationContext)
            )
            
            Guardian.say(applicationContext, Log.INFO, TAG, "Fall alert cancellation reported")
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting cancellation: ${e.message}")
        }
    }
    
    private fun createCountdownNotification(secondsRemaining: Int): Notification {
        val cancelIntent = Intent(this, FallCountdownService::class.java).apply {
            action = ACTION_CANCEL_COUNTDOWN
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 
            0, 
            cancelIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val openAppIntent = Intent(this, FallAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val title = if (isFalsePositive) "Falso Positivo Detectado" else "¡CAÍDA DETECTADA!"
        val text = "Se enviará alerta en $secondsRemaining segundos"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("Toca para abrir o cancelar")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "Cancelar",
                cancelPendingIntent
            )
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .build()
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Guardian:FallCountdownWakeLock"
            )
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire(10*60*1000L /*10 minutes max*/)
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock: ${e.message}")
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}")
        }
    }

    private fun launchFallAlertActivity() {
        try {
            val intent = Intent(this, FallAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            Log.i(TAG, "FallAlertActivity launched from service")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching FallAlertActivity: ${e.message}")
        }
    }
}