package altermarkive.guardian.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

object VibrationUtils {
    /**
     * Obtiene el servicio de vibración compatible con API 30 y anteriores
     */
    fun getVibrator(context: Context): Vibrator {
        return context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Hace vibrar el dispositivo con un patrón específico
     */
    fun vibrate(context: Context, pattern: LongArray, repeat: Int) {
        val vibrator = getVibrator(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createWaveform(pattern, repeat)
            vibrator.vibrate(vibrationEffect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, repeat)
        }
    }

    /**
     * Hace vibrar el dispositivo una vez
     */
    fun vibrateOnce(context: Context, milliseconds: Long) {
        val vibrator = getVibrator(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createOneShot(
                milliseconds,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
            vibrator.vibrate(vibrationEffect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }
    }

    /**
     * Detiene cualquier vibración en curso
     */
    fun stopVibration(context: Context) {
        val vibrator = getVibrator(context)
        vibrator.cancel()
    }
}