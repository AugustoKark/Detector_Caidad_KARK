package altermarkive.guardian.safezone

import altermarkive.guardian.R
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import java.io.IOException
import java.util.Locale

class SafeZoneFragment : Fragment() {

    private lateinit var mainSwitch: Switch
    private lateinit var tabLayout: TabLayout
    private lateinit var safeZonesListView: ListView
    private lateinit var exceptionSchedulesListView: ListView
    private lateinit var safeZonesContainer: View
    private lateinit var schedulesContainer: View
    private lateinit var mapContainer: View
    private lateinit var addSafeZoneButton: FloatingActionButton
    private lateinit var addScheduleButton: FloatingActionButton

    private lateinit var safeZoneAdapter: SafeZoneAdapter
    private lateinit var scheduleAdapter: ExceptionScheduleAdapter
    private var mapFragment: SafeZoneMapFragment? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // Todos los permisos concedidos
            updateServiceStatus()
        } else {
            // Algunos permisos denegados
//            Toast.makeText(
//                requireContext(),
//                "Se necesitan permisos de ubicación para la monitorización de zona segura",
//                Toast.LENGTH_LONG
//            ).show()
            mainSwitch.isChecked = false
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_safezone, container, false)

        // Inicializar vistas
        mainSwitch = view.findViewById(R.id.monitoringSwitch)
        tabLayout = view.findViewById(R.id.tabLayout)
        safeZonesListView = view.findViewById(R.id.safeZonesList)
        exceptionSchedulesListView = view.findViewById(R.id.schedulesList)
        safeZonesContainer = view.findViewById(R.id.safeZonesContainer)
        schedulesContainer = view.findViewById(R.id.schedulesContainer)
        mapContainer = view.findViewById(R.id.mapContainer)
        addSafeZoneButton = view.findViewById(R.id.addSafeZoneButton)
        addScheduleButton = view.findViewById(R.id.addScheduleButton)

        // Configurar adaptadores
        safeZoneAdapter = SafeZoneAdapter(requireContext())
        scheduleAdapter = ExceptionScheduleAdapter(requireContext())

        safeZonesListView.adapter = safeZoneAdapter
        exceptionSchedulesListView.adapter = scheduleAdapter

        // Configurar TabLayout
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateTabVisibility(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Configurar switch principal
        mainSwitch.isChecked = SafeZoneManager.isMonitoringEnabled(requireContext())
        mainSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !checkLocationPermissions()) {
                requestLocationPermissions()
                return@setOnCheckedChangeListener
            }

            SafeZoneManager.setMonitoringEnabled(requireContext(), isChecked)
            updateServiceStatus()
        }

        // Configurar botones de añadir
        addSafeZoneButton.setOnClickListener {
            showAddSafeZoneDialog()
        }

        addScheduleButton.setOnClickListener {
            showAddScheduleDialog()
        }

        // Configurar click en elementos de listas
        safeZonesListView.setOnItemClickListener { _, _, position, _ ->
            val safeZone = safeZoneAdapter.getItem(position)
            if (safeZone != null) {
                showEditSafeZoneDialog(safeZone)
            }
        }

        exceptionSchedulesListView.setOnItemClickListener { _, _, position, _ ->
            val schedule = scheduleAdapter.getItem(position)
            if (schedule != null) {
                showEditScheduleDialog(schedule)
            }
        }

        // Configurar visibilidad inicial
        updateTabVisibility(0)

        // Actualizar estado del servicio
        updateServiceStatus()

        return view
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun updateTabVisibility(tabPosition: Int) {
        when (tabPosition) {
            0 -> { // Zonas Seguras
                safeZonesContainer.visibility = View.VISIBLE
                schedulesContainer.visibility = View.GONE
                mapContainer.visibility = View.GONE
                addSafeZoneButton.show()
                addScheduleButton.hide()

                // Limpiar el mapa cuando no está visible para evitar problemas
                if (mapFragment != null) {
                    try {
                        childFragmentManager.beginTransaction()
                            .hide(mapFragment!!)
                            .commit()
                    } catch (e: Exception) {
                        // Ignorar errores
                    }
                }
            }
            1 -> { // Horarios de Excepción
                safeZonesContainer.visibility = View.GONE
                schedulesContainer.visibility = View.VISIBLE
                mapContainer.visibility = View.GONE
                addSafeZoneButton.hide()
                addScheduleButton.show()

                // Limpiar el mapa cuando no está visible
                if (mapFragment != null) {
                    try {
                        childFragmentManager.beginTransaction()
                            .hide(mapFragment!!)
                            .commit()
                    } catch (e: Exception) {
                        // Ignorar errores
                    }
                }
            }
            2 -> { // Mapa
                safeZonesContainer.visibility = View.GONE
                schedulesContainer.visibility = View.GONE
                mapContainer.visibility = View.VISIBLE
                addSafeZoneButton.hide()
                addScheduleButton.hide()

                // Cargar fragmento del mapa de manera segura
                try {
                    if (mapFragment == null) {
                        mapFragment = SafeZoneMapFragment()
                        childFragmentManager.beginTransaction()
                            .replace(R.id.mapContainer, mapFragment!!)
                            .commit()
                    } else {
                        childFragmentManager.beginTransaction()
                            .show(mapFragment!!)
                            .commit()

                        // Refrescar el mapa después de un breve delay para asegurar que esté visible
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (isAdded && mapContainer.visibility == View.VISIBLE) {
                                mapFragment?.refreshMap()
                            }
                        }, 300)
                    }
                } catch (e: Exception) {
//                    Toast.makeText(requireContext(), "Error al cargar el mapa", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun updateServiceStatus() {
        if (SafeZoneManager.isMonitoringEnabled(requireContext()) && checkLocationPermissions()) {
            SafeZoneMonitoringService.startService(requireContext())
        } else {
            SafeZoneMonitoringService.stopService(requireContext())
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    override fun onResume() {
        super.onResume()
        updateLists()
        mainSwitch.isChecked = SafeZoneManager.isMonitoringEnabled(requireContext())

        // Solo refrescar el mapa si está visible y ha sido creado
        if (mapContainer.visibility == View.VISIBLE && mapFragment != null && isAdded) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isAdded && mapContainer.visibility == View.VISIBLE) {
                    try {
                        mapFragment?.refreshMap()
                    } catch (e: Exception) {
                        // Ignorar errores de refresh del mapa
                    }
                }
            }, 500)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun updateLists() {
        safeZoneAdapter.clear()
        safeZoneAdapter.addAll(SafeZoneManager.getSafeZones(requireContext()))
        safeZoneAdapter.notifyDataSetChanged()

        scheduleAdapter.clear()
        scheduleAdapter.addAll(SafeZoneManager.getExceptionSchedules(requireContext()))
        scheduleAdapter.notifyDataSetChanged()

        // Mostrar texto de "no hay datos" si las listas están vacías
        val emptySafeZonesView = view?.findViewById<TextView>(R.id.emptySafeZonesText)
        emptySafeZonesView?.visibility = if (safeZoneAdapter.count == 0) View.VISIBLE else View.GONE

        val emptySchedulesView = view?.findViewById<TextView>(R.id.emptySchedulesText)
        emptySchedulesView?.visibility = if (scheduleAdapter.count == 0) View.VISIBLE else View.GONE

        // Refrescar el mapa si está cargado
        mapFragment?.refreshMap()
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun showAddSafeZoneDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_safe_zone, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.zoneNameEditText)
        val addressEditText = dialogView.findViewById<EditText>(R.id.addressEditText)
        val radiusSeekBar = dialogView.findViewById<SeekBar>(R.id.radiusSeekBar)
        val radiusValueText = dialogView.findViewById<TextView>(R.id.radiusValueText)
        val useCurrentLocationButton = dialogView.findViewById<Button>(R.id.useCurrentLocationButton)

        // Configurar SeekBar
        radiusSeekBar.progress = 500 // Valor predeterminado: 500m
        radiusValueText.text = "500 metros"
        radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val radius = Math.max(100, progress) // Mínimo 100m
                radiusValueText.text = "$radius metros"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Configurar botón de ubicación actual
        useCurrentLocationButton.setOnClickListener {
            getCurrentLocation { location ->
                // Convertir coordenadas a dirección
                try {
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val addressText = address.getAddressLine(0) ?: "Ubicación actual"
                        addressEditText.setText(addressText)
                    } else {
                        addressEditText.setText("${location.latitude}, ${location.longitude}")
                    }
                } catch (e: IOException) {
                    addressEditText.setText("${location.latitude}, ${location.longitude}")
                }
            }
        }

        // Construir diálogo
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Añadir zona segura")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val name = nameEditText.text.toString()
                val address = addressEditText.text.toString()

                if (name.isBlank()) {
//                    Toast.makeText(requireContext(), "Por favor, introduce un nombre", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (address.isBlank()) {
//                    Toast.makeText(requireContext(), "Por favor, introduce una dirección", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Convertir dirección a coordenadas
                getCoordinatesFromAddress(address) { location ->
                    if (location != null) {
                        val radius = Math.max(100, radiusSeekBar.progress)
                        val safeZone = SafeZone(name, location.latitude, location.longitude, radius)
                        SafeZoneManager.saveSafeZone(requireContext(), safeZone)
                        updateLists()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "No se pudo obtener la ubicación de la dirección",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun showEditSafeZoneDialog(safeZone: SafeZone) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_safe_zone, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.zoneNameEditText)
        val addressEditText = dialogView.findViewById<EditText>(R.id.addressEditText)
        val radiusSeekBar = dialogView.findViewById<SeekBar>(R.id.radiusSeekBar)
        val radiusValueText = dialogView.findViewById<TextView>(R.id.radiusValueText)
        val enabledSwitch = dialogView.findViewById<Switch>(R.id.enabledSwitch)

        // Pre-rellenar campos
        nameEditText.setText(safeZone.name)
        nameEditText.isEnabled = false  // No permitir cambiar el nombre (identificador único)

        // Convertir coordenadas a dirección
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(safeZone.latitude, safeZone.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                val addressText = address.getAddressLine(0) ?: "${safeZone.latitude}, ${safeZone.longitude}"
                addressEditText.setText(addressText)
            } else {
                addressEditText.setText("${safeZone.latitude}, ${safeZone.longitude}")
            }
        } catch (e: IOException) {
            addressEditText.setText("${safeZone.latitude}, ${safeZone.longitude}")
        }

        // Configurar radio
        radiusSeekBar.progress = safeZone.radius
        radiusValueText.text = "${safeZone.radius} metros"
        radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val radius = Math.max(100, progress)
                radiusValueText.text = "$radius metros"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Estado activado/desactivado
        enabledSwitch.isChecked = safeZone.enabled

        // Construir diálogo
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Editar zona segura")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val address = addressEditText.text.toString()

                if (address.isBlank()) {
                    Toast.makeText(requireContext(), "Por favor, introduce una dirección", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Convertir dirección a coordenadas
                getCoordinatesFromAddress(address) { location ->
                    val finalLocation = location ?: Location("").apply {
                        latitude = safeZone.latitude
                        longitude = safeZone.longitude
                    }

                    val radius = Math.max(100, radiusSeekBar.progress)
                    val updatedSafeZone = SafeZone(
                        safeZone.name,
                        finalLocation.latitude,
                        finalLocation.longitude,
                        radius,
                        enabledSwitch.isChecked
                    )

                    SafeZoneManager.saveSafeZone(requireContext(), updatedSafeZone)
                    updateLists()
                }
            }
            .setNeutralButton("Eliminar") { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Eliminar zona segura")
                    .setMessage("¿Estás seguro de que quieres eliminar esta zona segura?")
                    .setPositiveButton("Sí") { _, _ ->
                        SafeZoneManager.deleteSafeZone(requireContext(), safeZone.name)
                        updateLists()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun showAddScheduleDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_schedule, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.scheduleNameEditText)
        val startHourEditText = dialogView.findViewById<EditText>(R.id.startHourEditText)
        val startMinuteEditText = dialogView.findViewById<EditText>(R.id.startMinuteEditText)
        val endHourEditText = dialogView.findViewById<EditText>(R.id.endHourEditText)
        val endMinuteEditText = dialogView.findViewById<EditText>(R.id.endMinuteEditText)

        // Obtener checkboxes para los días de la semana
        val checkBoxes = ArrayList<androidx.appcompat.widget.AppCompatCheckBox>()
        checkBoxes.add(dialogView.findViewById(R.id.sundayCheckBox))   // Domingo - Calendar.SUNDAY (1)
        checkBoxes.add(dialogView.findViewById(R.id.mondayCheckBox))   // Lunes - Calendar.MONDAY (2)
        checkBoxes.add(dialogView.findViewById(R.id.tuesdayCheckBox))  // Martes - Calendar.TUESDAY (3)
        checkBoxes.add(dialogView.findViewById(R.id.wednesdayCheckBox)) // Miércoles - Calendar.WEDNESDAY (4)
        checkBoxes.add(dialogView.findViewById(R.id.thursdayCheckBox)) // Jueves - Calendar.THURSDAY (5)
        checkBoxes.add(dialogView.findViewById(R.id.fridayCheckBox))   // Viernes - Calendar.FRIDAY (6)
        checkBoxes.add(dialogView.findViewById(R.id.saturdayCheckBox)) // Sábado - Calendar.SATURDAY (7)

        // Construir diálogo
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Añadir horario de excepción")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val name = nameEditText.text.toString()

                if (name.isBlank()) {
                    Toast.makeText(requireContext(), "Por favor, introduce un nombre", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Validar horas y minutos
                var startHour = 0
                var startMinute = 0
                var endHour = 0
                var endMinute = 0

                try {
                    startHour = startHourEditText.text.toString().toInt()
                    startMinute = startMinuteEditText.text.toString().toInt()
                    endHour = endHourEditText.text.toString().toInt()
                    endMinute = endMinuteEditText.text.toString().toInt()

                    if (startHour < 0 || startHour > 23 || startMinute < 0 || startMinute > 59 ||
                        endHour < 0 || endHour > 23 || endMinute < 0 || endMinute > 59
                    ) {
                        throw NumberFormatException("Valores fuera de rango")
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(
                        requireContext(),
                        "Por favor, introduce valores válidos para las horas (0-23) y minutos (0-59)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                // Obtener días seleccionados
                val daysOfWeek = mutableListOf<Int>()
                for (i in checkBoxes.indices) {
                    if (checkBoxes[i].isChecked) {
                        daysOfWeek.add(i + 1) // Calendar.SUNDAY es 1, MONDAY es 2, etc.
                    }
                }

                if (daysOfWeek.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Por favor, selecciona al menos un día de la semana",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                // Crear y guardar horario
                val schedule = ExceptionSchedule(
                    name,
                    daysOfWeek,
                    startHour,
                    startMinute,
                    endHour,
                    endMinute
                )

                SafeZoneManager.saveExceptionSchedule(requireContext(), schedule)
                updateLists()
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun showEditScheduleDialog(schedule: ExceptionSchedule) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_schedule, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.scheduleNameEditText)
        val startHourEditText = dialogView.findViewById<EditText>(R.id.startHourEditText)
        val startMinuteEditText = dialogView.findViewById<EditText>(R.id.startMinuteEditText)
        val endHourEditText = dialogView.findViewById<EditText>(R.id.endHourEditText)
        val endMinuteEditText = dialogView.findViewById<EditText>(R.id.endMinuteEditText)
        val enabledSwitch = dialogView.findViewById<Switch>(R.id.enabledSwitch)

        // Obtener checkboxes para los días de la semana
        val checkBoxes = ArrayList<androidx.appcompat.widget.AppCompatCheckBox>()
        checkBoxes.add(dialogView.findViewById(R.id.sundayCheckBox))
        checkBoxes.add(dialogView.findViewById(R.id.mondayCheckBox))
        checkBoxes.add(dialogView.findViewById(R.id.tuesdayCheckBox))
        checkBoxes.add(dialogView.findViewById(R.id.wednesdayCheckBox))
        checkBoxes.add(dialogView.findViewById(R.id.thursdayCheckBox))
        checkBoxes.add(dialogView.findViewById(R.id.fridayCheckBox))
        checkBoxes.add(dialogView.findViewById(R.id.saturdayCheckBox))

        // Pre-rellenar campos
        nameEditText.setText(schedule.name)
        nameEditText.isEnabled = false  // No permitir cambiar el nombre (identificador único)

        startHourEditText.setText(schedule.startHour.toString())
        startMinuteEditText.setText(schedule.startMinute.toString())
        endHourEditText.setText(schedule.endHour.toString())
        endMinuteEditText.setText(schedule.endMinute.toString())

        // Marcar días seleccionados
        for (day in schedule.daysOfWeek) {
            // Calendar.SUNDAY es 1, MONDAY es 2, etc.
            checkBoxes[day - 1].isChecked = true
        }

        // Estado activado/desactivado
        enabledSwitch.isChecked = schedule.enabled

        // Construir diálogo
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Editar horario de excepción")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                // Validar horas y minutos
                var startHour = 0
                var startMinute = 0
                var endHour = 0
                var endMinute = 0

                try {
                    startHour = startHourEditText.text.toString().toInt()
                    startMinute = startMinuteEditText.text.toString().toInt()
                    endHour = endHourEditText.text.toString().toInt()
                    endMinute = endMinuteEditText.text.toString().toInt()

                    if (startHour < 0 || startHour > 23 || startMinute < 0 || startMinute > 59 ||
                        endHour < 0 || endHour > 23 || endMinute < 0 || endMinute > 59
                    ) {
                        throw NumberFormatException("Valores fuera de rango")
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(
                        requireContext(),
                        "Por favor, introduce valores válidos para las horas (0-23) y minutos (0-59)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                // Obtener días seleccionados
                val daysOfWeek = mutableListOf<Int>()
                for (i in checkBoxes.indices) {
                    if (checkBoxes[i].isChecked) {
                        daysOfWeek.add(i + 1) // Calendar.SUNDAY es 1, MONDAY es 2, etc.
                    }
                }

                if (daysOfWeek.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Por favor, selecciona al menos un día de la semana",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                // Actualizar y guardar horario
                val updatedSchedule = ExceptionSchedule(
                    schedule.name,
                    daysOfWeek,
                    startHour,
                    startMinute,
                    endHour,
                    endMinute,
                    enabledSwitch.isChecked
                )

                SafeZoneManager.saveExceptionSchedule(requireContext(), updatedSchedule)
                updateLists()
            }
            .setNeutralButton("Eliminar") { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Eliminar horario")
                    .setMessage("¿Estás seguro de que quieres eliminar este horario de excepción?")
                    .setPositiveButton("Sí") { _, _ ->
                        SafeZoneManager.deleteExceptionSchedule(requireContext(), schedule.name)
                        updateLists()
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun getCurrentLocation(callback: (Location) -> Unit) {
        val locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                requireContext(),
                "Se necesitan permisos de ubicación",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Intentar obtener primero la ubicación GPS (más precisa)
        var location: Location? = null

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }

        // Si no se pudo obtener por GPS, intentar por red
        if (location == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }

        // Si se encontró una ubicación, llamar al callback
        if (location != null) {
            callback(location)
        } else {
            Toast.makeText(
                requireContext(),
                "No se pudo obtener la ubicación actual. Inténtalo más tarde.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun getCoordinatesFromAddress(address: String, callback: (Location?) -> Unit) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())

            // Para coordenadas directas (lat, lng)
            if (address.contains(",")) {
                val parts = address.split(",")
                try {
                    val lat = parts[0].trim().toDouble()
                    val lng = parts[1].trim().toDouble()

                    val location = Location("").apply {
                        latitude = lat
                        longitude = lng
                    }

                    callback(location)
                    return
                } catch (e: NumberFormatException) {
                    // No son coordenadas, continuar con geocodificación
                }
            }

            // Para todas las versiones de Android, usamos el método sincrónico
            // que está disponible en todas las versiones
            @Suppress("DEPRECATION")
            Thread {
                try {
                    val addresses = geocoder.getFromLocationName(address, 1)
                    requireActivity().runOnUiThread {
                        if (addresses != null && addresses.isNotEmpty()) {
                            val location = Location("").apply {
                                latitude = addresses[0].latitude
                                longitude = addresses[0].longitude
                            }
                            callback(location)
                        } else {
                            callback(null)
                        }
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            "Error al obtener coordenadas: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        callback(null)
                    }
                }
            }.start()
        } catch (e: IOException) {
            Toast.makeText(
                requireContext(),
                "Error al obtener coordenadas: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            callback(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Limpiar el fragmento del mapa de manera segura
        try {
            if (mapFragment != null) {
                childFragmentManager.beginTransaction()
                    .remove(mapFragment!!)
                    .commitAllowingStateLoss()
                mapFragment = null
            }
        } catch (e: Exception) {
            // Ignorar errores de limpieza
        }
    }
}