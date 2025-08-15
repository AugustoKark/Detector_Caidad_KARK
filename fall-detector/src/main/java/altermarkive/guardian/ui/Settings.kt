package altermarkive.guardian.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import altermarkive.guardian.R
import altermarkive.guardian.alerts.Alarm
import altermarkive.guardian.alerts.Contact
import androidx.preference.SwitchPreference
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


    private fun showTermsAndConditions() {
        val dialog = Dialog(requireContext())
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
                .versionName
        } catch (e: Exception) {
            "1.0.0"
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Actualizar el estado del contacto cuando regrese a esta pantalla
        updateContactPreference()
    }
}