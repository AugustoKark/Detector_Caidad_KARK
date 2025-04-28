package altermarkive.guardian.alerts

import altermarkive.guardian.R
import altermarkive.guardian.core.Guardian
import altermarkive.guardian.sensors.Battery
import altermarkive.guardian.sensors.Positioning
import altermarkive.guardian.storage.ServerAdapter
//import altermarkive.guardian.utils.Log
import altermarkive.guardian.utils.VibrationUtils
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
    private var countdownSeconds = 30 // Tiempo de cuenta regresiva
    private var countdownTimer: CountDownTimer? = null
    private var isAlertCancelled = false
    private var countdownMediaPlayer: MediaPlayer? = null

    companion object {
        private const val VIBRATION_PATTERN_DURATION_MS = 1000L
        private const val VIBRATION_REPEAT = 0 // Repetir indefinidamente

        fun start(context: Context) {
            val intent = Intent(context, FallAlertActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar la pantalla para emergencia - debe hacerse antes de setContentView
        setupScreenForEmergency()

        setContentView(R.layout.activity_fall_alert)

        // Iniciar vibración
        startVibration()

        // Iniciar sonido de alerta
        startCountdownSound()

        // Configurar el SeekBar
        setupSeekBarListener()

        // Iniciar la cuenta regresiva
        startCountdown()
    }

    private fun setupScreenForEmergency() {
        // En API 30 y anteriores, usamos flags tradicionales
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        // Aumentar el volumen al máximo para la alarma
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )
    }

    private fun startVibration() {
        // Crear un patrón de vibración: esperar 0ms, vibrar 1000ms, repetir
        val pattern = longArrayOf(0, VIBRATION_PATTERN_DURATION_MS)

        // Iniciar la vibración con patrón repetitivo
        VibrationUtils.vibrate(this, pattern, VIBRATION_REPEAT)
    }

    private fun startCountdownSound() {
        try {
            // Obtener tono de notificación predeterminado (diferente al de la alarma final)
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            countdownMediaPlayer = MediaPlayer.create(this, notification)

            // Reproducir a intervalos regulares durante la cuenta regresiva
            countdownMediaPlayer?.apply {
                isLooping = false  // No lo hacemos en loop, sino que lo repetimos manualmente
                start()

                // Programar que suene cada 3 segundos
                Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
                    override fun run() {
                        if (!isAlertCancelled) {
                            if (countdownMediaPlayer != null && !countdownMediaPlayer!!.isPlaying) {
                                countdownMediaPlayer?.start()
                            }
                            Handler(Looper.getMainLooper()).postDelayed(this, 3000)
                        }
                    }
                }, 3000)
            }
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
    }

    private fun cancelAlert() {
        isAlertCancelled = true
        countdownTimer?.cancel()
        VibrationUtils.stopVibration(this)

        // Detener sonido de cuenta regresiva
        releaseMediaPlayer()

        // Registrar que se canceló la alerta
        Log.i(TAG, "Alerta de caída cancelada por el usuario")
        Guardian.say(applicationContext, Log.INFO, TAG, "Alerta de caída cancelada")

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

        finish()
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
            if (isPlaying) {
                stop()
            }
            release()
        }
        countdownMediaPlayer = null
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        VibrationUtils.stopVibration(this)
        releaseMediaPlayer()
        super.onDestroy()
    }

    // Si el usuario presiona el botón de retroceso, cancelar la alerta
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        cancelAlert()
    }
}