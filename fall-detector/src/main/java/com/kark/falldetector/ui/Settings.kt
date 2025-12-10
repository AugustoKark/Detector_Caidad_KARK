package com.kark.falldetector.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kark.falldetector.R
import com.kark.falldetector.alerts.Alarm
import com.kark.falldetector.alerts.Contact
import androidx.preference.SwitchPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.core.content.ContextCompat

class Settings : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Configurar click en probar alerta
        findPreference<Preference>("test_alert")?.setOnPreferenceClickListener {
            // Usar la misma función que el botón de emergencia
            Alarm.alert(requireActivity().applicationContext)
           // Toast.makeText(context, "Alerta de prueba enviada", Toast.LENGTH_SHORT).show()
            true
        }
        
        // Configurar click en Términos y Condiciones
        findPreference<Preference>("privacy_policy")?.setOnPreferenceClickListener {
            showTermsAndConditions()
            true
        }
        
        // Verificar estado del contacto de emergencia
        updateContactPreference()

        // Configurar versión de la app
        findPreference<Preference>("app_version")?.apply {
            summary = "Guardian v${getAppVersion()}"
        }

        // Configurar tiempo de espera antes de notificar caída
        findPreference<EditTextPreference>("delay_seconds")?.apply {
            // Obtener valor guardado o usar 30 como predeterminado
            val prefs = requireContext().getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
            val savedValue = prefs.getInt("fall_detection_delay", 30)
            text = savedValue.toString()

            // Mostrar valor actual en el summary
            summary = "Actualmente: $savedValue segundos"

            // Validar y actualizar cuando cambie
            setOnPreferenceChangeListener { preference, newValue ->
                val seconds = newValue.toString().toIntOrNull() ?: 30
                if (seconds in 10..120) {
                    // Guardar el nuevo valor en SharedPreferences
                    prefs.edit().putInt("fall_detection_delay", seconds).apply()
                    preference.summary = "Actualmente: $seconds segundos"
                    true
                } else {
//                    Toast.makeText(
//                        context,
//                        "El tiempo debe estar entre 10 y 120 segundos",
//                        Toast.LENGTH_SHORT
//                    ).show()
                    false
                }
            }
        }

        findPreference<SwitchPreference>("fall_detection_enabled")?.apply {
            // Mostrar estado actual en el summary
            updateFallDetectionSummary(isChecked)

            setOnPreferenceChangeListener { preference, newValue ->
                val isEnabled = newValue as Boolean
                updateFallDetectionSummary(isEnabled)

                // Mostrar mensaje de confirmación
                val message = if (isEnabled) {
                    "Detección de caídas activada"
                } else {
                    "Detección de caídas desactivada"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                true
            }
        }

        findPreference<SwitchPreference>("safe_zone_monitoring_enabled")?.apply {
            // Obtener estado desde SafeZoneManager
            val safeZoneManager = com.kark.falldetector.safezone.SafeZoneManager
            isChecked = safeZoneManager.isMonitoringEnabled(requireContext())
            
            // Mostrar estado actual en el summary
            updateSafeZoneMonitoringSummary(isChecked)

            setOnPreferenceChangeListener { preference, newValue ->
                val isEnabled = newValue as Boolean
                
                // Usar la misma función que el switch en SafeZoneFragment
                safeZoneManager.setMonitoringEnabled(requireContext(), isEnabled)
                
                // Iniciar o detener el servicio según corresponda
                if (isEnabled) {
                    com.kark.falldetector.safezone.SafeZoneMonitoringService.startService(requireContext())
                } else {
                    com.kark.falldetector.safezone.SafeZoneMonitoringService.stopService(requireContext())
                }
                
                updateSafeZoneMonitoringSummary(isEnabled)

                // Mostrar mensaje de confirmación
                val message = if (isEnabled) {
                    "Monitoreo de zonas seguras activado"
                } else {
                    "Monitoreo de zonas seguras desactivado"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                true
            }
        }

        // Configurar intervalo de notificaciones de zona segura
        findPreference<ListPreference>("safe_zone_notification_interval")?.apply {
            val safeZoneManager = com.kark.falldetector.safezone.SafeZoneManager

            // Obtener el valor actual en minutos desde SafeZoneManager
            val currentIntervalMs = safeZoneManager.getNotificationIntervalMs(requireContext())
            val currentIntervalMinutes = (currentIntervalMs / 60000).toInt()

            // Establecer el valor seleccionado
            value = currentIntervalMinutes.toString()

            // Actualizar el summary con el valor actual
            updateNotificationIntervalSummary(currentIntervalMinutes)

            setOnPreferenceChangeListener { _, newValue ->
                val minutes = newValue.toString().toInt()
                val intervalMs = minutes * 60000L

                // Guardar el nuevo valor
                safeZoneManager.setNotificationIntervalMs(requireContext(), intervalMs)

                // Actualizar el summary
                updateNotificationIntervalSummary(minutes)

                true
            }
        }
    }

    private fun updateNotificationIntervalSummary(minutes: Int) {
        findPreference<ListPreference>("safe_zone_notification_interval")?.apply {
            summary = when (minutes) {
                1 -> "Alertas cada 1 minuto"
                60 -> "Alertas cada 1 hora"
                else -> "Alertas cada $minutes minutos"
            }
        }
    }

    private fun updateFallDetectionSummary(isEnabled: Boolean) {
        findPreference<SwitchPreference>("fall_detection_enabled")?.apply {
            summary = if (isEnabled) {
                "Guardian está monitoreando caídas activamente"
            } else {
                "La detección de caídas está desactivada"
            }
        }
    }

    private fun updateSafeZoneMonitoringSummary(isEnabled: Boolean) {
        findPreference<SwitchPreference>("safe_zone_monitoring_enabled")?.apply {
            summary = if (isEnabled) {
                "Guardian está monitoreando las zonas seguras configuradas"
            } else {
                "El monitoreo de zonas seguras está desactivado"
            }
        }
    }


    private fun showTermsAndConditions() {
        val dialog = Dialog(requireContext(), R.style.Theme_FallDetector)
        dialog.setContentView(R.layout.eula)
        dialog.setTitle("Términos y Condiciones")
        
        // Buscar el botón de aceptar y cambiar su comportamiento
        val acceptButton = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.acceptButton)
        acceptButton.text = "Cerrar"
        acceptButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Configurar el diálogo para que sea de pantalla completa
        val layout = WindowManager.LayoutParams()
        val window = dialog.window
        window?.let {
            layout.copyFrom(it.attributes)
            layout.width = WindowManager.LayoutParams.MATCH_PARENT
            layout.height = WindowManager.LayoutParams.MATCH_PARENT
            it.attributes = layout
        }
        
        dialog.show()
    }

    private fun updateContactPreference() {
        val contactKey = getString(R.string.contact)
        findPreference<Preference>(contactKey)?.apply {
            val savedContact = Contact[requireContext()]
            
            if (savedContact.isNullOrEmpty()) {
                // No hay contacto configurado - usar texto con color rojo
                summary = "⚠️ No se ha configurado ningún teléfono de contacto"
                // Usar icono de emergencia existente para indicar advertencia
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_emergency)
            } else {
                // Hay contacto configurado
                val maskedPhone = maskPhoneNumber(savedContact)
                summary = "Contacto configurado: $maskedPhone"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_phone_24)
            }
        }
    }
    
    private fun maskPhoneNumber(phone: String): String {
        // Mostrar solo los últimos 4 dígitos por privacidad
        return if (phone.length > 4) {
            "***-***-" + phone.takeLast(4)
        } else {
            phone
        }
    }

    private fun getAppVersion(): String {
        return try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
                .versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Actualizar el estado del contacto cuando regrese a esta pantalla
        updateContactPreference()
        
        // Actualizar estado del switch de zonas seguras
        findPreference<SwitchPreference>("safe_zone_monitoring_enabled")?.apply {
            val safeZoneManager = com.kark.falldetector.safezone.SafeZoneManager
            isChecked = safeZoneManager.isMonitoringEnabled(requireContext())
            updateSafeZoneMonitoringSummary(isChecked)
        }

        // Actualizar intervalo de notificaciones
        findPreference<ListPreference>("safe_zone_notification_interval")?.apply {
            val safeZoneManager = com.kark.falldetector.safezone.SafeZoneManager
            val currentIntervalMs = safeZoneManager.getNotificationIntervalMs(requireContext())
            val currentIntervalMinutes = (currentIntervalMs / 60000).toInt()
            value = currentIntervalMinutes.toString()
            updateNotificationIntervalSummary(currentIntervalMinutes)
        }
    }
}