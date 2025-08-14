package altermarkive.guardian.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import altermarkive.guardian.R
import altermarkive.guardian.alerts.Alarm
import androidx.preference.SwitchPreference

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


    private fun getAppVersion(): String {
        return try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0)
                .versionName
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}