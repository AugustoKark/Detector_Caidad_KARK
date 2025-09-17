package com.kark.falldetector.core

import com.kark.falldetector.alerts.Alarm
import com.kark.falldetector.detection.Detector
import com.kark.falldetector.utils.Log
import com.kark.falldetector.sensors.Positioning
import com.kark.falldetector.R
import com.kark.falldetector.detection.Sampler
import com.kark.falldetector.safezone.SafeZoneManager
import com.kark.falldetector.safezone.SafeZoneMonitoringService
import com.kark.falldetector.storage.ServerAdapter
import com.kark.falldetector.utils.BatteryOptimizationHelper
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class Guardian : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        
        // Verificar y solicitar desactivación de optimización de batería
        BatteryOptimizationHelper.checkAndRequestOptimizations(this)
        
        // Adquirir wake lock para mantener los sensores activos
        acquireWakeLock()
        
        Positioning.initiate(this)
        Detector.instance(this)
        Sampler.instance(this)
        Alarm.instance(this)
        // Iniciar servicio de monitorización de zona segura si está habilitado
        if (SafeZoneManager.isMonitoringEnabled(this)) {
            SafeZoneMonitoringService.startService(this)
        }
        ServerAdapter.initializeScheduledUploads(this)
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Guardian:FallDetectionWakeLock"
            )
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire()
            say(this, android.util.Log.INFO, "Guardian", "Wake lock acquired for fall detection")
        } catch (e: Exception) {
            say(this, android.util.Log.ERROR, "Guardian", "Error acquiring wake lock: ${e.message}")
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    say(this, android.util.Log.INFO, "Guardian", "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            say(this, android.util.Log.ERROR, "Guardian", "Error releasing wake lock: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = resources.getString(R.string.app)
        val channelName = "$channelId Background Service"
        val channel = NotificationChannel(
            channelId,
            channelName, NotificationManager.IMPORTANCE_NONE
        )
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(channel)
        return channelId
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startID: Int): Int {
        val now = System.currentTimeMillis()
        val app = resources.getString(R.string.app)
        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }
        val main = Intent(this, Main::class.java)
        val pending = PendingIntent.getActivity(this, 0, main, 0)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(app)
            .setContentText("Detectando caídas en segundo plano")
            .setSubText("Sensores activos - Pantalla puede estar bloqueada")
            .setWhen(now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending).build()
        startForeground(1, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        say(this, android.util.Log.INFO, "Guardian", "Guardian service destroyed")
    }

    companion object {
        internal fun initiate(context: Context) {
            val intent = Intent(context, Guardian::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        internal fun say(context: Context, level: Int, tag: String, message: String) {
            Log.println(level, tag, message)
//            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}