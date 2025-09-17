package com.kark.falldetector.utils

import android.content.Context
import androidx.preference.PreferenceManager

object FallDetectionSettings {
    private const val FALL_DETECTION_ENABLED_KEY = "fall_detection_enabled"

    /**
     * Verifica si la detección de caídas está habilitada en la configuración
     */
    fun isFallDetectionEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(FALL_DETECTION_ENABLED_KEY, true) // true por defecto
    }

    /**
     * Activa o desactiva la detección de caídas
     */
    fun setFallDetectionEnabled(context: Context, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(FALL_DETECTION_ENABLED_KEY, enabled).apply()
    }
}

