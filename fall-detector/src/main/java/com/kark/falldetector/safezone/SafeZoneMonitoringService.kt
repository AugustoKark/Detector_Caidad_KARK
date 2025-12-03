package com.kark.falldetector.safezone

import com.kark.falldetector.alerts.Contact
import com.kark.falldetector.alerts.Messenger
import com.kark.falldetector.core.Guardian
import com.kark.falldetector.sensors.Battery
import com.kark.falldetector.sensors.Positioning
import com.kark.falldetector.utils.Log
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.navigation.NavDeepLinkBuilder
import com.kark.falldetector.R
import com.kark.falldetector.core.Main
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SafeZoneMonitoringService : Service(), LocationListener {
    private lateinit var locationManager: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var monitoringActive = false

    companion object {
        private const val TAG = "SafeZoneMonitoring"
        private const val CHANNEL_ID = "SafeZoneChannel"
        private const val NOTIFICATION_ID = 2  // Diferente del servicio Guardian principal
        private const val LOCATION_UPDATE_INTERVAL = 10 * 60 * 1000L  // 10 minutos
        private const val MIN_DISTANCE_CHANGE = 100.0f  // 100 metros

        // Intervalo para verificaciones periódicas (utilizado si no hay actualizaciones de ubicación)
        private const val PERIODIC_CHECK_INTERVAL = 15 * 60 * 1000L  // 15 minutos

        fun startService(context: Context) {
            val intent = Intent(context, SafeZoneMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SafeZoneMonitoringService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        setupLocationMonitoring()
        startPeriodicChecks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createNotification("Monitorizando ubicación")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Monitorización de zona segura"
            val descriptionText = "Monitoriza si el usuario sale de las zonas seguras"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): android.app.Notification {

        val intent = Intent(this, Main::class.java)

        // Crear un intent usando NavDeepLinkBuilder para navegar directamente al fragmento de SafeZone
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Guardian - Zona Segura")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun setupLocationMonitoring() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "No se tienen permisos de ubicación")
            return
        }

        // Registrar listener para actualizaciones de ubicación
        try {
            // Primero GPS para mayor precisión
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    MIN_DISTANCE_CHANGE,
                    this
                )
                monitoringActive = true
                Log.i(TAG, "Monitorización por GPS activa")
            }

            // También utilizar Network provider como respaldo
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    MIN_DISTANCE_CHANGE,
                    this
                )
                monitoringActive = true
                Log.i(TAG, "Monitorización por red activa")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al configurar monitorización de ubicación: ${e.message}")
        }
    }

    private fun startPeriodicChecks() {
        // Verificaciones periódicas como respaldo
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (SafeZoneManager.isMonitoringEnabled(applicationContext)) {
                    checkLocationManuallySafe()

                    // Reprogramar la próxima verificación
                    handler.postDelayed(this, PERIODIC_CHECK_INTERVAL)
                }
            }
        }, PERIODIC_CHECK_INTERVAL)
    }

    private fun checkLocationManuallySafe() {
        try {
            checkLocationManually()
        } catch (e: Exception) {
            Log.e(TAG, "Error en verificación periódica: ${e.message}")
        }
    }

    private fun checkLocationManually() {
        // Este método se llama periódicamente como respaldo

        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Intentar obtener la ubicación actual
        var lastKnownLocation: Location? = null

        // Primero intentar con GPS
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }

        // Si no hay ubicación por GPS, intentar con Network
        if (lastKnownLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }

        // Si se obtuvo ubicación, verificar
        if (lastKnownLocation != null) {
            // Solo verificar si es una ubicación reciente (menos de 30 minutos)
            val locationAge = System.currentTimeMillis() - lastKnownLocation.time
            if (locationAge < TimeUnit.MINUTES.toMillis(30)) {
                checkSafeZoneStatus(lastKnownLocation)
            } else {
                Log.i(TAG, "Ubicación demasiado antigua para verificar: ${TimeUnit.MILLISECONDS.toMinutes(locationAge)} minutos")
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        // Llamado cuando se recibe una actualización de ubicación
        Log.d(TAG, "Ubicación actualizada: ${location.latitude}, ${location.longitude}")
        checkSafeZoneStatus(location)
    }

    private fun checkSafeZoneStatus(location: Location) {
        if (SafeZoneManager.shouldNotifyUserOutsideSafeZone(applicationContext, location)) {
            // El usuario está fuera de la zona segura y debemos notificar
            sendOutsideSafeZoneAlert(location)
        }
    }

    private fun sendOutsideSafeZoneAlert(location: Location) {
        // Obtener contacto de emergencia
        val contact = Contact[applicationContext]
        if (contact != null && contact.isNotBlank()) {
            // Formato de fecha legible
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val currentTime = dateFormat.format(Date())

            // Preparar mensaje
            val message = "AVISO: La persona monitoreada ha salido de la zona segura. " +
                    "Ubicación actual: https://maps.google.com/?q=${location.latitude},${location.longitude} " +
                    "Batería: ${Battery.level(applicationContext)}%. " +
                    "Hora: $currentTime"

            // Enviar SMS
            try {
                Messenger.sms(applicationContext, contact, message)
                Log.i(TAG, "Alerta enviada por estar fuera de zona segura")
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar alerta: ${e.message}")
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Implementación para versiones antiguas de Android
    }

    override fun onProviderEnabled(provider: String) {
        Log.i(TAG, "Proveedor habilitado: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.i(TAG, "Proveedor deshabilitado: $provider")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Detener actualizaciones de ubicación
        locationManager.removeUpdates(this)

        // Detener verificaciones periódicas
        handler.removeCallbacksAndMessages(null)

        Log.i(TAG, "Servicio de monitorización de zona segura detenido")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
