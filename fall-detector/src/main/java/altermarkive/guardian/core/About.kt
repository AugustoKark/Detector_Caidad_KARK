package altermarkive.guardian.core

import altermarkive.guardian.alerts.Alarm
import altermarkive.guardian.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class About : Fragment(), View.OnClickListener {
    private var binding: View? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = inflater.inflate(R.layout.about, container, false)
        this.binding = binding

        // Configurar versión de la app
        val versionText = binding.findViewById<TextView>(R.id.app_version)
        versionText.text = "Versión ${getAppVersion()}"

        // Configurar botón de emergencia
        val emergency = binding.findViewById<ExtendedFloatingActionButton>(R.id.emergency)
        emergency.setOnClickListener(this)

        return binding
    }

    override fun onClick(view: View) {
        if (R.id.emergency == view.id) {
            Alarm.alert(requireActivity().applicationContext)
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPermissions(true)
    }

    private fun refreshPermissions(request: Boolean) {
        val statusText: String
        val statusColor: Int
        if (permitted(request)) {
            statusText = "Guardian está activo"
            statusColor = ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
        } else {
            statusText = "Permisos requeridos"
            statusColor = Color.RED
        }
        val binding = this.binding ?: return
        val status = binding.findViewById<TextView>(R.id.status)
        activity?.runOnUiThread {
            status.text = statusText
            status.setTextColor(statusColor)
        }
    }

    private fun permitted(request: Boolean): Boolean {
        val list: MutableList<String> = mutableListOf()
        var granted = true
        for (item: VersionedPermission in PERMISSIONS) {
            list.add(item.permission)
            if (Build.VERSION.SDK_INT >= item.version && ContextCompat.checkSelfPermission(
                    requireActivity().applicationContext,
                    item.permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                granted = false
            }
        }
        if (!granted) {
            if (request) {
                requestPermissions.launch(list.toTypedArray())
            }
        }
        return granted
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var granted = true
            for (permission in permissions) {
                granted = granted && permission.value
            }
            if (!granted) {
                Guardian.say(
                    requireActivity().applicationContext,
                    Log.ERROR,
                    TAG,
                    "ERROR: Permisos no otorgados"
                )
            }
            refreshPermissions(false)
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

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    class VersionedPermission(val permission: String, val version: Int)

    companion object {
        private val PERMISSIONS: Array<VersionedPermission> = arrayOf(
            VersionedPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                Manifest.permission.CALL_PHONE,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                Manifest.permission.CHANGE_WIFI_STATE,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                Manifest.permission.INTERNET,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                Manifest.permission.READ_CONTACTS,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                Manifest.permission.READ_PHONE_STATE,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                Manifest.permission.READ_CALL_LOG,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                "android.permission.ANSWER_PHONE_CALLS",
                Build.VERSION_CODES.O
            ),
            VersionedPermission(
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                Manifest.permission.RECEIVE_SMS,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                Manifest.permission.SEND_SMS,
                Build.VERSION_CODES.JELLY_BEAN
            ),
            VersionedPermission(
                "android.permission.FOREGROUND_SERVICE",
                Build.VERSION_CODES.P
            ),
        )

        private val TAG: String = About::class.java.simpleName
    }
}