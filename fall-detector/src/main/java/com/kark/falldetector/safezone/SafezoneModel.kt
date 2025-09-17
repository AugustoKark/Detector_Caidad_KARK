package com.kark.falldetector.safezone

import android.content.Context
import android.location.Location
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.Date

/**
 * Representa una zona segura configurada por el usuario
 */
data class SafeZone(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Int = 500, // Radio en metros
    val enabled: Boolean = true
) {
    /**
     * Comprueba si una ubicación está dentro de esta zona segura
     */
    fun isLocationInZone(location: Location): Boolean {
        val zoneLocation = Location("SafeZone")
        zoneLocation.latitude = latitude
        zoneLocation.longitude = longitude

        // Calcular distancia en metros
        val distance = location.distanceTo(zoneLocation)
        return distance <= radius
    }
}

/**
 * Representa un horario de excepción donde no se debe notificar
 * incluso si el usuario está fuera de la zona segura
 */
data class ExceptionSchedule(
    val name: String,
    val daysOfWeek: List<Int>, // 1 = Domingo, 2 = Lunes, ..., 7 = Sábado (formato Calendar)
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabled: Boolean = true
) {
    /**
     * Comprueba si la hora actual está dentro de este horario de excepción
     */
    fun isCurrentTimeInSchedule(): Boolean {
        if (!enabled) return false

        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Verificar si hoy es uno de los días configurados
        if (!daysOfWeek.contains(currentDayOfWeek)) return false

        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        // Convertir a minutos para facilitar la comparación
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val startTimeInMinutes = startHour * 60 + startMinute
        val endTimeInMinutes = endHour * 60 + endMinute

        return currentTimeInMinutes in startTimeInMinutes..endTimeInMinutes
    }

    /**
     * Devuelve una representación legible de los días de la semana
     */
    fun getDaysOfWeekString(): String {
        val dayNames = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
        return daysOfWeek.map { dayNames[it - 1] }.joinToString(", ")
    }

    /**
     * Devuelve una representación legible del horario
     */
    fun getTimeRangeString(): String {
        val startTimeStr = String.format("%02d:%02d", startHour, startMinute)
        val endTimeStr = String.format("%02d:%02d", endHour, endMinute)
        return "$startTimeStr - $endTimeStr"
    }
}

/**
 * Clase para gestionar las zonas seguras y los horarios de excepción
 */
object SafeZoneManager {
    private const val PREF_SAFE_ZONES = "safe_zones"
    private const val PREF_EXCEPTION_SCHEDULES = "exception_schedules"
    private const val PREF_SAFE_ZONE_MONITORING_ENABLED = "safe_zone_monitoring_enabled"
    private const val PREF_LAST_NOTIFICATION_TIME = "last_safe_zone_notification_time"
    private const val NOTIFICATION_COOLDOWN_MS = 3600000 // 1 hora en milisegundos

    /**
     * Guarda una zona segura en las preferencias
     */
    fun saveSafeZone(context: Context, safeZone: SafeZone) {
        val safeZones = getSafeZones(context).toMutableList()

        // Buscar y reemplazar si ya existe una zona con el mismo nombre
        val existingIndex = safeZones.indexOfFirst { it.name == safeZone.name }
        if (existingIndex >= 0) {
            safeZones[existingIndex] = safeZone
        } else {
            safeZones.add(safeZone)
        }

        saveSafeZones(context, safeZones)
    }

    /**
     * Obtiene todas las zonas seguras guardadas
     */
    fun getSafeZones(context: Context): List<SafeZone> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val gson = Gson()
        val json = prefs.getString(PREF_SAFE_ZONES, "[]")
        val type = object : TypeToken<List<SafeZone>>() {}.type
        return gson.fromJson(json, type) ?: listOf()
    }

    /**
     * Guarda la lista completa de zonas seguras
     */
    private fun saveSafeZones(context: Context, safeZones: List<SafeZone>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val gson = Gson()
        val json = gson.toJson(safeZones)
        prefs.edit().putString(PREF_SAFE_ZONES, json).apply()
    }

    /**
     * Guarda un horario de excepción
     */
    fun saveExceptionSchedule(context: Context, schedule: ExceptionSchedule) {
        val schedules = getExceptionSchedules(context).toMutableList()

        // Buscar y reemplazar si ya existe un horario con el mismo nombre
        val existingIndex = schedules.indexOfFirst { it.name == schedule.name }
        if (existingIndex >= 0) {
            schedules[existingIndex] = schedule
        } else {
            schedules.add(schedule)
        }

        saveExceptionSchedules(context, schedules)
    }

    /**
     * Obtiene todos los horarios de excepción guardados
     */
    fun getExceptionSchedules(context: Context): List<ExceptionSchedule> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val gson = Gson()
        val json = prefs.getString(PREF_EXCEPTION_SCHEDULES, "[]")
        val type = object : TypeToken<List<ExceptionSchedule>>() {}.type
        return gson.fromJson(json, type) ?: listOf()
    }

    /**
     * Guarda la lista completa de horarios de excepción
     */
    private fun saveExceptionSchedules(context: Context, schedules: List<ExceptionSchedule>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val gson = Gson()
        val json = gson.toJson(schedules)
        prefs.edit().putString(PREF_EXCEPTION_SCHEDULES, json).apply()
    }

    /**
     * Activa o desactiva la monitorización de zonas seguras
     */
    fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(PREF_SAFE_ZONE_MONITORING_ENABLED, enabled).apply()
    }

    /**
     * Comprueba si la monitorización está activada
     */
    fun isMonitoringEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(PREF_SAFE_ZONE_MONITORING_ENABLED, false)
    }

    /**
     * Verifica si el usuario está en alguna zona segura activa
     */
    fun isInAnySafeZone(context: Context, location: Location): Boolean {
        val safeZones = getSafeZones(context).filter { it.enabled }
        return safeZones.any { it.isLocationInZone(location) }
    }

    /**
     * Verifica si hay algún horario de excepción activo en este momento
     */
    fun isInExceptionSchedule(context: Context): Boolean {
        val schedules = getExceptionSchedules(context).filter { it.enabled }
        return schedules.any { it.isCurrentTimeInSchedule() }
    }

    /**
     * Comprueba si se debe enviar una notificación por estar fuera de la zona segura,
     * considerando horarios de excepción y período de enfriamiento entre notificaciones
     */
    fun shouldNotifyUserOutsideSafeZone(context: Context, location: Location): Boolean {
        // Si la monitorización está desactivada, no notificar
        if (!isMonitoringEnabled(context)) return false

        // Si estamos en un horario de excepción, no notificar
        if (isInExceptionSchedule(context)) return false

        // Si estamos en alguna zona segura, no notificar
        if (isInAnySafeZone(context, location)) return false

        // Comprobar período de enfriamiento entre notificaciones
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lastNotificationTime = prefs.getLong(PREF_LAST_NOTIFICATION_TIME, 0)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastNotificationTime < NOTIFICATION_COOLDOWN_MS) {
            // Aún estamos en período de enfriamiento
            return false
        }

        // Actualizar tiempo de última notificación
        prefs.edit().putLong(PREF_LAST_NOTIFICATION_TIME, currentTime).apply()

        // Si llegamos aquí, debemos notificar
        return true
    }

    /**
     * Elimina una zona segura por su nombre
     */
    fun deleteSafeZone(context: Context, name: String) {
        val safeZones = getSafeZones(context).toMutableList()
        safeZones.removeAll { it.name == name }
        saveSafeZones(context, safeZones)
    }

    /**
     * Elimina un horario de excepción por su nombre
     */
    fun deleteExceptionSchedule(context: Context, name: String) {
        val schedules = getExceptionSchedules(context).toMutableList()
        schedules.removeAll { it.name == name }
        saveExceptionSchedules(context, schedules)
    }
}