package altermarkive.guardian.safezone

import altermarkive.guardian.R
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay

class SafeZoneMapFragment : Fragment(), MapEventsReceiver {

    private var mapView: MapView? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val safeZoneMarkers = mutableListOf<Marker>()
    private val safeZoneCircles = mutableListOf<Polygon>()
    private var currentLocationMarker: Marker? = null
    private var isMapInitialized = false
    private val handler = Handler(Looper.getMainLooper())
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            enableMyLocation()
        } else {
            context?.let {
//                //Toast.makeText(
//                    it,
//                    "Se necesitan permisos de ubicación para mostrar tu posición",
//                    //Toast.LENGTH_LONG
//                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar osmdroid de manera segura
        try {
            context?.let { ctx ->
                Configuration.getInstance().load(
                    ctx,
                    PreferenceManager.getDefaultSharedPreferences(ctx)
                )
            }
        } catch (e: Exception) {
            // Ignorar errores de configuración
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_safezone_map, container, false)
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar el mapa de manera segura
        initializeMap(view)

        // Configurar botones
        setupButtons(view)
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun initializeMap(view: View) {
        try {
            mapView = view.findViewById<MapView>(R.id.map)
            mapView?.let { map ->
                setupMap(map)
                isMapInitialized = true

                // Cargar datos en un hilo separado para evitar ANR
                lifecycleScope.launch {
                    loadSafeZonesAsync()

                    // Habilitar ubicación si tenemos permisos
                    if (checkLocationPermissions()) {
                        enableMyLocation()
                    }
                }
            }
        } catch (e: Exception) {
            context?.let {
//                //Toast.makeText(it, "Error al inicializar el mapa", //Toast.LENGTH_SHORT).show()
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun setupButtons(view: View) {
        val refreshButton = view.findViewById<android.widget.Button>(R.id.refreshMapButton)
        val centerLocationButton = view.findViewById<android.widget.Button>(R.id.centerLocationButton)

        refreshButton?.setOnClickListener {
            refreshMap()
            context?.let {
//                //Toast.makeText(it, "Mapa actualizado", //Toast.LENGTH_SHORT).show()
            }
        }

        centerLocationButton?.setOnClickListener {
            centerOnCurrentLocation()
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun setupMap(map: MapView) {
        try {
            map.apply {
                // Configurar el mapa
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)

                // Configurar zoom
                controller.setZoom(15.0)

                // Centrar en una ubicación por defecto (Madrid, España)
                controller.setCenter(GeoPoint(40.4168, -3.7038))

                // Agregar overlay para eventos de mapa de manera segura
                try {
                    val mapEventsOverlay = MapEventsOverlay(this@SafeZoneMapFragment)
                    overlays.add(mapEventsOverlay)
                } catch (e: Exception) {
                    // Ignorar si no se puede agregar el overlay
                }
            }
        } catch (e: Exception) {
            // Manejar errores de configuración del mapa
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private suspend fun loadSafeZonesAsync() {
        if (!isMapInitialized || mapView == null) return

        try {
            // Obtener zonas seguras en un hilo de fondo
            val safeZones = withContext(Dispatchers.IO) {
                context?.let { SafeZoneManager.getSafeZones(it) } ?: emptyList()
            }

            // Actualizar UI en el hilo principal
            withContext(Dispatchers.Main) {
                if (isAdded && mapView != null) {
                    loadSafeZonesOnMap(safeZones)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                context?.let {
//                    //Toast.makeText(it, "Error al cargar zonas seguras", //Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun loadSafeZonesOnMap(safeZones: List<SafeZone>) {
        val map = mapView ?: return

        try {
            // Limpiar marcadores y círculos existentes
            clearMapElements()

            if (safeZones.isEmpty()) {
                context?.let {
                   // //Toast.makeText(it, "No hay zonas seguras configuradas", //Toast.LENGTH_SHORT).show()
                }
                return
            }

            val geoPoints = mutableListOf<GeoPoint>()

            safeZones.forEach { safeZone ->
                try {
                    val geoPoint = GeoPoint(safeZone.latitude, safeZone.longitude)
                    geoPoints.add(geoPoint)

                    // Crear marcador
                    val marker = Marker(map)
                    marker.position = geoPoint
                    marker.title = safeZone.name
                    marker.snippet = "Radio: ${safeZone.radius}m - ${if (safeZone.enabled) "Activa" else "Inactiva"}"

                    // Configurar icono del marcador según el estado
                    val markerIcon = if (safeZone.enabled) {
                        createMarkerDrawable(Color.GREEN)
                    } else {
                        createMarkerDrawable(Color.GRAY)
                    }
                    marker.icon = markerIcon

                    // Configurar evento de click
                    marker.setOnMarkerClickListener { _, _ ->
                        showSafeZoneInfo(safeZone)
                        true
                    }

                    map.overlays.add(marker)
                    safeZoneMarkers.add(marker)

                    // Crear círculo de radio
                    val circle = Polygon()
                    circle.points = createCirclePoints(geoPoint, safeZone.radius.toDouble())

                    val circleColor = if (safeZone.enabled) {
                        Color.argb(50, 0, 255, 0) // Verde transparente
                    } else {
                        Color.argb(50, 128, 128, 128) // Gris transparente
                    }

                    circle.fillColor = circleColor
                    circle.strokeColor = if (safeZone.enabled) Color.GREEN else Color.GRAY
                    circle.strokeWidth = 2f

                    map.overlays.add(circle)
                    safeZoneCircles.add(circle)
                } catch (e: Exception) {
                    // Continuar con las siguientes zonas si hay error
                }
            }

            // Ajustar vista para mostrar todas las zonas
            if (geoPoints.isNotEmpty()) {
                try {
                    val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
                    map.zoomToBoundingBox(boundingBox, true, 100)
                } catch (e: Exception) {
                    // Si hay error, centrar en la primera zona
                    map.controller.setCenter(geoPoints.first())
                    map.controller.setZoom(15.0)
                }
            }

            map.invalidate()
        } catch (e: Exception) {
            context?.let {
                //Toast.makeText(it, "Error al mostrar zonas en el mapa", //Toast.LENGTH_SHORT).show()
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun createCirclePoints(center: GeoPoint, radiusInMeters: Double): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val earthRadius = 6371000.0 // Radio de la Tierra en metros

        try {
            for (i in 0..360 step 10) {
                val angle = Math.toRadians(i.toDouble())
                val lat = Math.asin(
                    Math.sin(Math.toRadians(center.latitude)) * Math.cos(radiusInMeters / earthRadius) +
                            Math.cos(Math.toRadians(center.latitude)) * Math.sin(radiusInMeters / earthRadius) * Math.cos(angle)
                )
                val lon = Math.toRadians(center.longitude) + Math.atan2(
                    Math.sin(angle) * Math.sin(radiusInMeters / earthRadius) * Math.cos(Math.toRadians(center.latitude)),
                    Math.cos(radiusInMeters / earthRadius) - Math.sin(Math.toRadians(center.latitude)) * Math.sin(lat)
                )

                points.add(GeoPoint(Math.toDegrees(lat), Math.toDegrees(lon)))
            }
        } catch (e: Exception) {
            // Retornar lista vacía si hay error
        }

        return points
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun createMarkerDrawable(color: Int): Drawable {
        return try {
            val drawable = ResourcesCompat.getDrawable(resources, android.R.drawable.ic_menu_mylocation, null)?.mutate()
            drawable?.setTint(color)
            drawable ?: ResourcesCompat.getDrawable(resources, android.R.drawable.ic_menu_mylocation, null)!!
        } catch (e: Exception) {
            ResourcesCompat.getDrawable(resources, android.R.drawable.ic_menu_mylocation, null)!!
        }
    }

    private fun clearMapElements() {
        val map = mapView ?: return

        try {
            // Remover marcadores y círculos de manera segura
            safeZoneMarkers.forEach { marker ->
                try {
                    map.overlays.remove(marker)
                } catch (e: Exception) {
                    // Continuar con los siguientes
                }
            }
            safeZoneMarkers.clear()

            safeZoneCircles.forEach { circle ->
                try {
                    map.overlays.remove(circle)
                } catch (e: Exception) {
                    // Continuar con los siguientes
                }
            }
            safeZoneCircles.clear()

            currentLocationMarker?.let {
                try {
                    map.overlays.remove(it)
                } catch (e: Exception) {
                    // Ignorar error
                }
            }
            currentLocationMarker = null

            map.invalidate()
        } catch (e: Exception) {
            // Manejar errores de limpieza
        }
    }

    private fun showSafeZoneInfo(safeZone: SafeZone) {
        try {
            val status = if (safeZone.enabled) "Activada" else "Desactivada"
            val message = """
                Zona: ${safeZone.name}
                Radio: ${safeZone.radius} metros
                Estado: $status
                Coordenadas: ${String.format("%.6f", safeZone.latitude)}, ${String.format("%.6f", safeZone.longitude)}
            """.trimIndent()

            context?.let {
                //Toast.makeText(it, message, //Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // Ignorar errores de display
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return context?.let {
            ContextCompat.checkSelfPermission(
                it,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } ?: false
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun requestLocationPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun enableMyLocation() {
        if (!checkLocationPermissions() || mapView == null) {
            return
        }

        try {
            val map = mapView!!

            // Configurar overlay de ubicación de manera segura
            context?.let { ctx ->
                myLocationOverlay?.let { overlay ->
                    overlay.disableMyLocation()
                    map.overlays.remove(overlay)
                }

                myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), map)
                myLocationOverlay?.let { overlay ->
                    overlay.enableMyLocation()
                    map.overlays.add(overlay)
                }
            }

            // Obtener ubicación actual en un hilo separado
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val location = getCurrentLocationSafe()

                    withContext(Dispatchers.Main) {
                        if (isAdded && mapView != null && location != null) {
                            updateCurrentLocationMarker(location)
                        }
                    }
                } catch (e: Exception) {
                    // Manejar errores de ubicación
                }
            }

        } catch (e: SecurityException) {
            context?.let {
                //Toast.makeText(it, "Error al obtener ubicación: ${e.message}", //Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCurrentLocationSafe(): android.location.Location? {
        return try {
            activity?.let { act ->
                val locationManager = act.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                var currentLocation: android.location.Location? = null

                if (ActivityCompat.checkSelfPermission(
                        act,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    }

                    if (currentLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        currentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    }
                }

                currentLocation
            }
        } catch (e: Exception) {
            null
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun updateCurrentLocationMarker(location: android.location.Location) {
        val map = mapView ?: return

        try {
            val currentGeoPoint = GeoPoint(location.latitude, location.longitude)

            // Verificar si está en alguna zona segura
            val isInSafeZone = context?.let {
                SafeZoneManager.isInAnySafeZone(it, location)
            } ?: false

            val markerTitle = if (isInSafeZone) {
                "Mi ubicación (En zona segura)"
            } else {
                "Mi ubicación (Fuera de zona segura)"
            }

            // Crear marcador de ubicación actual
            currentLocationMarker?.let { map.overlays.remove(it) }
            currentLocationMarker = Marker(map).apply {
                position = currentGeoPoint
                title = markerTitle
                icon = createMarkerDrawable(Color.BLUE)
            }

            currentLocationMarker?.let { map.overlays.add(it) }
            map.invalidate()
        } catch (e: Exception) {
            // Manejar errores de marcador
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    private fun centerOnCurrentLocation() {
        if (!checkLocationPermissions()) {
            requestLocationPermissions()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val location = getCurrentLocationSafe()

                withContext(Dispatchers.Main) {
                    if (isAdded && mapView != null) {
                        if (location != null) {
                            val currentGeoPoint = GeoPoint(location.latitude, location.longitude)
                            mapView?.controller?.animateTo(currentGeoPoint)
                            mapView?.controller?.setZoom(16.0)
                        } else {
                            context?.let {
                                //Toast.makeText(it, "No se pudo obtener la ubicación actual", //Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    context?.let {
                        //Toast.makeText(it, "Error de permisos: ${e.message}", //Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    override fun onResume() {
        super.onResume()

        // Resumir el mapa de manera segura
        mapView?.onResume()

        // Solo recargar si el mapa está inicializado
        if (isMapInitialized && isAdded) {
            lifecycleScope.launch {
                loadSafeZonesAsync()
                if (checkLocationPermissions()) {
                    enableMyLocation()
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    override fun onPause() {
        super.onPause()

        // Pausar el mapa de manera segura
        mapView?.onPause()

        // Limpiar overlay de ubicación
        myLocationOverlay?.let { overlay ->
            try {
                overlay.disableMyLocation()
            } catch (e: Exception) {
                // Ignorar errores
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Limpiar recursos de manera segura
        try {
            myLocationOverlay?.let { overlay ->
                overlay.disableMyLocation()
                mapView?.overlays?.remove(overlay)
            }

            clearMapElements()

            // Limpiar el mapa
            mapView?.overlays?.clear()
            mapView = null
            myLocationOverlay = null
            isMapInitialized = false
        } catch (e: Exception) {
            // Ignorar errores de limpieza
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    fun refreshMap() {
        if (isMapInitialized && isAdded) {
            lifecycleScope.launch {
                loadSafeZonesAsync()
                if (checkLocationPermissions()) {
                    enableMyLocation()
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)

    // Implementación de MapEventsReceiver
    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
        try {
            p?.let { geoPoint ->
                val message = "Ubicación: ${String.format("%.6f", geoPoint.latitude)}, ${String.format("%.6f", geoPoint.longitude)}"
                context?.let {
                    //Toast.makeText(it, message, //Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Ignorar errores
        }
        return true
    }

    override fun longPressHelper(p: GeoPoint?): Boolean {
        // Podría implementarse para crear zonas seguras con long press
        return false
    }
}