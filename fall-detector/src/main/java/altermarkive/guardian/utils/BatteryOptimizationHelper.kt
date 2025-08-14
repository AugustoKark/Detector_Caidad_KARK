package altermarkive.guardian.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import altermarkive.guardian.core.Guardian

object BatteryOptimizationHelper {
    private val TAG = BatteryOptimizationHelper::class.java.simpleName

    /**
     * Verifica si la aplicación está en la lista blanca de optimización de batería
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // En versiones anteriores no hay optimización de batería
        }
    }

    /**
     * Solicita al usuario que desactive la optimización de batería para esta aplicación
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations(context)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    Guardian.say(context, android.util.Log.INFO, TAG, 
                        "Solicitando desactivar optimización de batería para mejor funcionamiento en segundo plano")
                } catch (e: Exception) {
                    Guardian.say(context, android.util.Log.WARN, TAG, 
                        "No se pudo solicitar desactivar optimización de batería: ${e.message}")
                    // Fallback: mostrar configuración general de batería
                    showBatteryOptimizationSettings(context)
                }
            }
        }
    }

    /**
     * Abre la configuración general de optimización de batería
     */
    private fun showBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Guardian.say(context, android.util.Log.INFO, TAG, 
                    "Abriendo configuración de optimización de batería - busque '${context.applicationInfo.loadLabel(context.packageManager)}'")
            } catch (e: Exception) {
                Guardian.say(context, android.util.Log.ERROR, TAG, 
                    "Error abriendo configuración de batería: ${e.message}")
            }
        }
    }

    /**
     * Verifica el estado y solicita permisos si es necesario
     */
    fun checkAndRequestOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations(context)) {
                Guardian.say(context, android.util.Log.WARN, TAG, 
                    "La optimización de batería está activa - esto puede afectar la detección de caídas en segundo plano")
                // Solicitar automáticamente la desactivación
                requestIgnoreBatteryOptimizations(context)
            } else {
                Guardian.say(context, android.util.Log.INFO, TAG, 
                    "Optimización de batería desactivada correctamente")
            }
        }
    }
}