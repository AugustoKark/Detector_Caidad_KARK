package com.kark.falldetector.alerts

import com.kark.falldetector.R
import com.kark.falldetector.core.Guardian
import com.kark.falldetector.sensors.Battery
import com.kark.falldetector.sensors.Positioning
import com.kark.falldetector.storage.ServerAdapter
import com.kark.falldetector.utils.VibrationUtils
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity

class FallAlertActivity : AppCompatActivity() {
    private val TAG = "FallAlertActivity"
    private var countdownSeconds: Int = 30 // Valor por defecto
    private var countdownTimer: CountDownTimer? = null
    private var isAlertCancelled = false
    private var countdownMediaPlayer: MediaPlayer? = null
    private var isConnectedToService = false
    private var serviceCheckHandler = Handler(Looper.getMainLooper())
    private var serviceCheckRunnable: Runnable? = null
    private var launchedFromMainActivity = false

    companion object {
        private const val VIBRATION_PATTERN_DURATION_MS = 1000L
        private const val VIBRATION_REPEAT = 0 // Repetir indefinidamente

        fun start(context: Context) {
            val intent = Intent(context, FallAlertActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force light theme regardless of system setting
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)

        val prefs = getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
        countdownSeconds = prefs.getInt("fall_detection_delay", 30)
        
        // Verificar si fue lanzada desde MainActivity
        launchedFromMainActivity = intent.getBooleanExtra("FROM_MAIN_ACTIVITY", false)

        // CRÍTICO: Configurar la pantalla para emergencia ANTES de setContentView
        setupScreenForEmergency()

        setContentView(R.layout.activity_fall_alert)

        // Despertar y desbloquear el dispositivo programáticamente
        wakeUpAndUnlockDevice()

        // Configurar el SeekBar
        setupSeekBarListener()

        // Verificar si hay un countdown en progreso
        val (isActive, remainingSeconds) = FallCountdownService.getCountdownStatus()
        
        if (isActive) {
            // Conectarse al servicio en progreso
            connectToRunningService(remainingSeconds)
            Log.i(TAG, "FallAlertActivity conectada a servicio en progreso - $remainingSeconds segundos restantes")
        } else {
            // No hay servicio en progreso
            if (launchedFromMainActivity) {
                // Si fue lanzada desde MainActivity pero no hay countdown, regresar
                Log.i(TAG, "Lanzada desde MainActivity pero no hay countdown activo - regresando")
                goBackToMainActivity()
                return
            } else {
                // Iniciar countdown normal (standalone)
                startStandaloneCountdown()
                Log.i(TAG, "FallAlertActivity iniciada como countdown standalone")
            }
        }
    }

    private fun setupScreenForEmergency() {
        // Para API 27 y posteriores, usar los nuevos métodos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)

            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(this, null)
            }
        } else {
            // Para versiones anteriores, usar los flags tradicionales
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        // Configuraciones adicionales para asegurar que aparezca por encima de todo
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        // Establecer alta prioridad
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = resources.getColor(R.color.black)
        }

        // Aumentar el volumen al máximo para la alarma
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        Log.d(TAG, "Configuración de pantalla de emergencia completada")
    }

    private fun wakeUpAndUnlockDevice() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

            // Despertar la pantalla si está apagada
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                    "Guardian:FallAlert"
                )
                wakeLock.acquire(10000) // 10 segundos máximo

                // Liberar el wakeLock después de un tiempo
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        if (wakeLock.isHeld) {
                            wakeLock.release()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error releasing wake lock: ${e.message}")
                    }
                }, 8000)
            }

            // Intentar desbloquear el keyguard
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (keyguardManager.isKeyguardLocked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            super.onDismissSucceeded()
                            Log.d(TAG, "Keyguard dismissed successfully")
                        }

                        override fun onDismissError() {
                            super.onDismissError()
                            Log.w(TAG, "Failed to dismiss keyguard")
                        }

                        override fun onDismissCancelled() {
                            super.onDismissCancelled()
                            Log.w(TAG, "Keyguard dismiss cancelled")
                        }
                    })
                }
            }

            Log.d(TAG, "Dispositivo despertado y desbloqueado")
        } catch (e: Exception) {
            Log.e(TAG, "Error waking up device: ${e.message}")
        }
    }

    private fun startVibration() {
        try {
            // Crear un patrón de vibración más intenso para emergencia
            val pattern = longArrayOf(0, VIBRATION_PATTERN_DURATION_MS, 200, VIBRATION_PATTERN_DURATION_MS)

            // Iniciar la vibración con patrón repetitivo
            VibrationUtils.vibrate(this, pattern, VIBRATION_REPEAT)

            Log.d(TAG, "Vibración de emergencia iniciada")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration: ${e.message}")
        }
    }

    private fun startCountdownSound() {
        try {
            // Usar tono de alarma más fuerte para emergencias
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

                // Repetir cada 2 segundos para mayor urgencia
                Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
                    override fun run() {
                        if (!isAlertCancelled && countdownMediaPlayer != null) {
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

            Log.d(TAG, "Sonido de cuenta regresiva iniciado")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting countdown sound: ${e.message}")
        }
    }

    private fun setupSeekBarListener() {
        val cancelSeekBar = findViewById<SeekBar>(R.id.cancelSeekBar)
        val sliderInstructionText = findViewById<android.widget.TextView>(R.id.sliderInstructionText)

        // Establecer configuración inicial
        cancelSeekBar.progress = 0
        sliderInstructionText.text = "Desliza para cancelar"

        // Configurar el listener del SeekBar
        cancelSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Actualizar texto de instrucción según el progreso
                    when {
                        progress >= 100 -> {
                            // Vibración de éxito (3 pulsos cortos)
                            VibrationUtils.vibrate(this@FallAlertActivity, longArrayOf(0, 100, 50, 100, 50, 100), -1)
                            cancelAlert()
                        }
                        progress >= 75 -> {
                            sliderInstructionText.text = "¡Suelta para cancelar!"
                            // Vibración de progreso (2 pulsos cortos)
                            if (progress == 75) {
                                VibrationUtils.vibrate(this@FallAlertActivity, longArrayOf(0, 50, 50, 50), -1)
                            }
                        }
                        progress >= 50 -> {
                            sliderInstructionText.text = "Continúa deslizando..."
                            // Vibración de progreso (1 pulso corto)
                            if (progress == 50) {
                                VibrationUtils.vibrate(this@FallAlertActivity, longArrayOf(0, 50), -1)
                            }
                        }
                        progress >= 25 -> {
                            sliderInstructionText.text = "Un poco más..."
                            // Vibración de inicio (1 pulso muy corto)
                            if (progress == 25) {
                                VibrationUtils.vibrate(this@FallAlertActivity, longArrayOf(0, 30), -1)
                            }
                        }
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // No es necesario implementar
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Si el usuario suelta antes de llegar al final, volver a 0
                val progress = seekBar?.progress ?: 0
                if (progress < 100) {
                    seekBar?.progress = 0
                    sliderInstructionText.text = "Desliza para cancelar"
                }
            }
        })
    }

    private fun startCountdown() {
        val countdownTextView = findViewById<android.widget.TextView>(R.id.countdownText)
        val infoTextView = findViewById<android.widget.TextView>(R.id.infoText)

        countdownTimer = object : CountDownTimer((countdownSeconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                countdownTextView.text = secondsRemaining.toString()

                // Cambiar el mensaje cuando queden pocos segundos para aumentar la urgencia
                if (secondsRemaining <= 10) {
                    infoTextView.text = "¡URGENTE! SE ENVIARÁ UNA ALERTA EN ${secondsRemaining}s"

                    // Hacer parpadear el texto de cuenta regresiva para último tramo
                    if (secondsRemaining % 2 == 0L) {
                        countdownTextView.alpha = 0.4f
                    } else {
                        countdownTextView.alpha = 1.0f
                    }
                }
            }

            override fun onFinish() {
                if (!isAlertCancelled) {
                    // Tiempo agotado, enviar alerta
                    countdownTextView.text = "0"
                    infoTextView.text = "Enviando alerta..."
                    sendAlert()
                }
            }
        }.start()

        Log.d(TAG, "Cuenta regresiva iniciada: ${countdownSeconds} segundos")
    }

    private fun connectToRunningService(remainingSeconds: Int) {
        isConnectedToService = true
        
        // No iniciar nuevas alertas de vibración/sonido ya que el servicio las maneja
        
        // Mostrar el countdown conectado al servicio
        startServiceConnectedCountdown(remainingSeconds)
    }
    
    private fun startStandaloneCountdown() {
        isConnectedToService = false
        
        // Iniciar vibración
        startVibration()

        // Iniciar sonido de alerta
        startCountdownSound()

        // Iniciar la cuenta regresiva standalone
        startCountdown()
    }
    
    private fun startServiceConnectedCountdown(initialSeconds: Int) {
        val countdownTextView = findViewById<android.widget.TextView>(R.id.countdownText)
        val infoTextView = findViewById<android.widget.TextView>(R.id.infoText)
        
        // Inicializar con los segundos del servicio
        countdownTextView.text = initialSeconds.toString()
        infoTextView.text = "Se detectó una caída. Se enviará alerta automáticamente."
        
        // Monitorear el estado del servicio cada segundo
        serviceCheckRunnable = object : Runnable {
            override fun run() {
                val (isActive, secondsRemaining) = FallCountdownService.getCountdownStatus()
                
                if (isActive && !isAlertCancelled) {
                    // Actualizar UI con el estado del servicio
                    countdownTextView.text = secondsRemaining.toString()
                    
                    // Cambiar el mensaje cuando queden pocos segundos
                    if (secondsRemaining <= 10) {
                        infoTextView.text = "¡URGENTE! SE ENVIARÁ UNA ALERTA EN ${secondsRemaining}s"
                        
                        // Hacer parpadear el texto de cuenta regresiva para último tramo
                        if (secondsRemaining % 2 == 0) {
                            countdownTextView.alpha = 0.4f
                        } else {
                            countdownTextView.alpha = 1.0f
                        }
                    }
                    
                    // Continuar monitoreando
                    serviceCheckHandler.postDelayed(this, 1000)
                } else {
                    // El servicio terminó o se canceló
                    if (!isAlertCancelled) {
                        countdownTextView.text = "0"
                        infoTextView.text = "Enviando alerta..."
                        // Cerrar la actividad después de un breve delay
                        serviceCheckHandler.postDelayed({
                            if (launchedFromMainActivity) {
                                goBackToMainActivity()
                            } else {
                                finish()
                            }
                        }, 2000)
                    }
                }
            }
        }
        serviceCheckRunnable?.let { serviceCheckHandler.post(it) }
    }
    
    private fun goBackToMainActivity() {
        try {
            val intent = Intent(this, com.kark.falldetector.core.Main::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            finish()
            Log.i(TAG, "Regresando a MainActivity")
        } catch (e: Exception) {
            Log.e(TAG, "Error regresando a MainActivity: ${e.message}")
            finish() // Fallback: cerrar la actividad
        }
    }

    private fun cancelAlert() {
        isAlertCancelled = true
        
        if (isConnectedToService) {
            // Cancelar el servicio
            FallCountdownService.cancelCountdown(this)
            Log.i(TAG, "Servicio de countdown cancelado por el usuario")
        } else {
            // Cancelar countdown standalone
            countdownTimer?.cancel()
            VibrationUtils.stopVibration(this)
            releaseMediaPlayer()
            
            // Reportar la cancelación al servidor para análisis de falsos positivos
            val position = Positioning.singleton
            val location = position?.getLastKnownLocation()
            try {
                ServerAdapter.reportFallAlertCancelled(
                    applicationContext,
                    location?.latitude,
                    location?.longitude,
                    Battery.level(applicationContext)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error reporting cancellation: ${e.message}")
            }
        }
        
        // Detener monitoreo del servicio
        serviceCheckRunnable?.let { serviceCheckHandler.removeCallbacks(it) }
        
        Log.i(TAG, "Alerta de caída cancelada por el usuario")
        Guardian.say(applicationContext, Log.INFO, TAG, "Alerta de caída cancelada")

        // Si fue lanzada desde MainActivity, regresar a ella
        if (launchedFromMainActivity) {
            goBackToMainActivity()
        } else {
            finish()
        }
    }

    private fun sendAlert() {
        // Enviar SMS y hacer llamada a través de la clase Alarm
        Log.w(TAG, "Enviando alerta de caída")
        Guardian.say(applicationContext, Log.WARN, TAG, "Enviando alerta de caída")

        // Detener sonido de cuenta regresiva
        releaseMediaPlayer()

        // Enviar SMS y realizar llamada usando la clase Alarm
        Alarm.alert(applicationContext)

        // Reportar el evento al servidor si es necesario
        val position = Positioning.singleton
        val location = position?.getLastKnownLocation()
        try {
            ServerAdapter.reportFallEvent(
                applicationContext,
                location?.latitude,
                location?.longitude,
                Battery.level(applicationContext)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting fall event: ${e.message}")
        }

        finish()
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

    override fun onDestroy() {
        Log.d(TAG, "FallAlertActivity destruida")
        countdownTimer?.cancel()
        VibrationUtils.stopVibration(this)
        releaseMediaPlayer()
        
        // Limpiar monitoreo del servicio
        serviceCheckRunnable?.let { serviceCheckHandler.removeCallbacks(it) }
        
        super.onDestroy()
    }

    // Si el usuario presiona el botón de retroceso, cancelar la alerta
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        cancelAlert()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "FallAlertActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "FallAlertActivity paused")
    }
}