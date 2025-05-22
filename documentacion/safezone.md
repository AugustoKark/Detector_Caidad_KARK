# Resumen Detallado: Carpeta `safezone` - Sistema de Zonas Seguras

La carpeta `safezone` implementa un sistema completo de geolocalización inteligente para notificar cuando el usuario sale de áreas designadas como seguras, con horarios de excepción y monitoreo continuo.

## **SafezoneModel.kt - Modelos de Datos y Lógica de Negocio**

### **Clase SafeZone - Definición de Áreas Seguras**
```kotlin
data class SafeZone(val name: String, val latitude: Double, val longitude: Double, val radius: Int = 500, val enabled: Boolean = true)
```

**Funcionalidades:**
- **Detección geográfica**: `isLocationInZone(location: Location)` usando `location.distanceTo()`
- **Radio configurable**: Por defecto 500 metros, ajustable desde 100m hasta valores grandes
- **Estado activable**: Puede habilitarse/deshabilitarse individualmente

**Algoritmo de detección:**
```kotlin
fun isLocationInZone(location: Location): Boolean {
    val zoneLocation = Location("SafeZone")
    zoneLocation.latitude = latitude; zoneLocation.longitude = longitude
    return location.distanceTo(zoneLocation) <= radius
}
```

### **Clase ExceptionSchedule - Horarios de Excepción**
```kotlin
data class ExceptionSchedule(val name: String, val daysOfWeek: List<Int>, val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int, val enabled: Boolean = true)
```

**Propósito**: Períodos donde NO se debe alertar aunque el usuario esté fuera de zona segura (ej: horario de trabajo, actividades programadas).

**Características:**
- **Días flexibles**: Array de días de semana (formato Calendar: 1=Domingo, 2=Lunes, etc.)
- **Horario preciso**: Hora y minuto de inicio/fin
- **Validación temporal**: `isCurrentTimeInSchedule()` verifica si es momento de excepción

**Algoritmo de validación temporal:**
```kotlin
fun isCurrentTimeInSchedule(): Boolean {
    val calendar = Calendar.getInstance()
    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    
    if (!daysOfWeek.contains(currentDayOfWeek)) return false
    
    val currentTimeInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    val startTimeInMinutes = startHour * 60 + startMinute
    val endTimeInMinutes = endHour * 60 + endMinute
    
    return currentTimeInMinutes in startTimeInMinutes..endTimeInMinutes
}
```

### **Objeto SafeZoneManager - Gestor Central**

#### **1. Persistencia con Gson**
```kotlin
private const val PREF_SAFE_ZONES = "safe_zones"
private const val PREF_EXCEPTION_SCHEDULES = "exception_schedules"
```

**Almacenamiento:**
- **JSON serialization**: Usa Gson para convertir objetos complejos a/desde JSON
- **SharedPreferences**: Almacenamiento persistente entre sesiones
- **Type-safe deserialization**: `TypeToken<List<SafeZone>>()` para tipos genéricos

#### **2. Lógica de Decisión Central**
```kotlin
fun shouldNotifyUserOutsideSafeZone(context: Context, location: Location): Boolean
```

**Algoritmo de decisión en cascada:**
1. **Monitoreo activado**: `if (!isMonitoringEnabled(context)) return false`
2. **Horario de excepción**: `if (isInExceptionSchedule(context)) return false`
3. **Dentro de zona segura**: `if (isInAnySafeZone(context, location)) return false`
4. **Cooldown de notificaciones**: Previene spam (1 hora entre alertas)

#### **3. Sistema Anti-Spam**
```kotlin
private const val NOTIFICATION_COOLDOWN_MS = 3600000 // 1 hora
```

**Prevención de notificaciones repetidas:**
- Guarda timestamp de última notificación
- Compara con tiempo actual antes de permitir nueva alerta
- Actualiza timestamp solo cuando se envía realmente una notificación

## **SafeZoneFragment.kt - Interfaz de Usuario Completa**

### **Arquitectura de UI con Pestañas**
Fragment con sistema de pestañas que alterna entre gestión de zonas seguras y horarios de excepción.

#### **1. Sistema de Pestañas Inteligente**
```kotlin
private fun updateTabVisibility(tabPosition: Int)
```

**Intercambia dinámicamente:**
- **Pestaña 0**: Lista de zonas seguras + FAB para añadir zonas
- **Pestaña 1**: Lista de horarios de excepción + FAB para añadir horarios
- **FloatingActionButton adaptativo**: Se muestra/oculta según pestaña activa

#### **2. Gestión de Permisos Moderna**
```kotlin
private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
```

**Características:**
- **Activity Result API**: Método moderno (no deprecated)
- **Permisos múltiples**: ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION
- **Validación continua**: Verifica permisos antes de activar monitoreo
- **Feedback inmediato**: Toast informativos sobre estado de permisos

#### **3. Diálogos de Configuración Avanzados**

**Para Zonas Seguras:**
```kotlin
private fun showAddSafeZoneDialog()
```

**Características:**
- **Geocoding bidireccional**: Dirección ↔ Coordenadas
- **Ubicación actual**: Botón para usar GPS/Network actual
- **SeekBar para radio**: Mínimo 100m, máximo configurable
- **Validación de entrada**: Nombres y direcciones obligatorios

**Para Horarios de Excepción:**
```kotlin
private fun showAddScheduleDialog()
```

**Características:**
- **7 checkboxes**: Uno por cada día de la semana
- **Validación temporal**: Horas 0-23, minutos 0-59
- **Al menos un día**: Requiere seleccionar mínimo un día
- **Formato legible**: Muestra horarios como "08:30 - 17:00"

#### **4. Integración con Geocoding**
```kotlin
private fun getCoordinatesFromAddress(address: String, callback: (Location?) -> Unit)
```

**Funcionalidades:**
- **Múltiples formatos**: Acepta direcciones de texto o coordenadas "lat,lng"
- **Thread separado**: Geocoding en background para no bloquear UI
- **Fallback robusto**: Si falla Geocoder, mantiene coordenadas originales
- **Compatibilidad total**: Usa método syncróno compatible con todas las versiones Android

#### **5. Obtención de Ubicación Actual**
```kotlin
private fun getCurrentLocation(callback: (Location) -> Unit)
```

**Estrategia dual:**
- **GPS primero**: Máxima precisión cuando está disponible
- **Network fallback**: Si GPS no disponible, usa torres/WiFi
- **Validación de permisos**: Verifica antes de intentar acceso
- **Manejo de errores**: Toast informativo si no se puede obtener ubicación

## **SafeZoneMonitoringService.kt - Servicio de Monitoreo Continuo**

### **Servicio Foreground Especializado**
Servicio dedicado exclusivamente al monitoreo de zonas seguras, separado del servicio principal Guardian.

#### **1. Configuración de Ubicación Dual**
```kotlin
private fun setupLocationMonitoring()
```

**Estrategia de providers:**
- **GPS_PROVIDER**: Máxima precisión, actualizaciones cada 10 minutos o 100 metros
- **NETWORK_PROVIDER**: Respaldo más rápido usando torres/WiFi
- **Configuración robusta**: Maneja casos donde providers están deshabilitados

#### **2. Sistema de Verificación Híbrido**
```kotlin
// Actualizaciones activas
locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_UPDATE_INTERVAL, MIN_DISTANCE_CHANGE, this)

// Verificaciones periódicas como respaldo
handler.postDelayed(periodicCheck, PERIODIC_CHECK_INTERVAL)
```

**Arquitectura redundante:**
- **Actualizaciones pasivas**: Sistema operativo notifica cambios de ubicación
- **Verificaciones activas**: Cada 15 minutos verifica manualmente usando `getLastKnownLocation()`
- **Validación temporal**: Solo usa ubicaciones recientes (< 30 minutos)

#### **3. Notificación Foreground Informativa**
```kotlin
private fun createNotification(contentText: String)
```

**Características:**
- **Canal de baja prioridad**: IMPORTANCE_LOW para no molestar
- **Ícono descriptivo**: `ic_menu_mylocation` indica función de ubicación
- **Intent de navegación**: Tap lleva directamente al fragment de SafeZone
- **Contenido dinámico**: Actualiza texto según estado de monitoreo

#### **4. Generación de Alertas Inteligentes**
```kotlin
private fun sendOutsideSafeZoneAlert(location: Location)
```

**Mensaje completo:**
```
"AVISO: La persona monitoreada ha salido de la zona segura. 
Ubicación actual: https://maps.google.com/?q=lat,lng 
Batería: X%. 
Hora: dd/MM/yyyy HH:mm"
```

**Información incluida:**
- **Contexto claro**: "AVISO" vs "ALERTA" (menos alarmante que caída)
- **Enlace directo**: Google Maps con coordenadas exactas
- **Estado del dispositivo**: Nivel de batería para contexto
- **Timestamp legible**: Fecha y hora del evento

## **Adaptadores de Listas Especializados**

### **SafeZoneAdapter.kt**
```kotlin
class SafeZoneAdapter(context: Context) : ArrayAdapter<SafeZone>(context, 0)
```

**Visualización:**
- **Nombre prominente**: Identificador único de la zona
- **Radio descriptivo**: "Radio: X metros"
- **Indicador visual**: Verde si habilitada, gris si deshabilitada

### **ExceptionScheduleAdapter.kt**
```kotlin
class ExceptionScheduleAdapter(context: Context) : ArrayAdapter<ExceptionSchedule>(context, 0)
```

**Visualización compleja:**
- **Nombre del horario**: Identificador único
- **Rango temporal**: "08:30 - 17:00"
- **Días activos**: "Lun, Mar, Mié, Jue, Vie"
- **Estado en tiempo real**: Color azul si está activo AHORA
- **Indicador de habilitación**: Verde/gris según estado

## **Arquitectura Completa del Sistema SafeZone**

### **Flujo de Monitoreo Continuo**
```
1. Usuario configura zonas y horarios → SafeZoneFragment
2. Activa monitoreo → SafeZoneMonitoringService inicia
3. Servicio recibe actualizaciones de ubicación cada 10 min/100m
4. Verificaciones adicionales cada 15 min como respaldo
5. SafeZoneManager.shouldNotifyUserOutsideSafeZone() evalúa:
   - ¿Monitoreo activo? → ¿Horario de excepción? → ¿En zona segura? → ¿Cooldown?
6. Si debe notificar → SMS con ubicación al contacto de emergencia
```

### **Casos de Uso Reales**

#### **Monitoreo de Persona Mayor**
- **Zona segura**: Casa + radio 500m (paseos por el barrio)
- **Horario excepción**: "Fisioterapia" martes/jueves 14:00-16:00
- **Resultado**: Solo alerta si sale del barrio fuera del horario de fisio

#### **Cuidado Diurno**
- **Zona segura**: Centro de día + radio 200m
- **Horario excepción**: "Excursión" viernes 09:00-17:00
- **Resultado**: No molesta durante excursiones programadas

### **Fortalezas del Sistema SafeZone**

#### **1. Flexibilidad Extrema**
- **Múltiples zonas**: Casa, centro médico, casa de familiares
- **Horarios complejos**: Diferentes excepciones para diferentes días
- **Radios variables**: Zona urbana pequeña vs zona rural grande

#### **2. Inteligencia Anti-Spam**
- **Cooldown de 1 hora**: Previene bombardeo de SMS
- **Validación temporal**: Solo usa ubicaciones recientes
- **Horarios de excepción**: Reconoce actividades programadas

#### **3. UX Intuitiva**
- **Geocoding bidireccional**: Fácil configuración por dirección o coordenadas
- **Ubicación actual**: Un botón para usar posición GPS actual
- **Validación en tiempo real**: Muestra horarios activos AHORA

#### **4. Robustez Técnica**
- **Dual providers**: GPS + Network para máxima cobertura
- **Verificación híbrida**: Pasiva + activa para garantizar funcionamiento
- **Persistencia JSON**: Configuración sobrevive reinicios
- **Servicio independiente**: No interfiere con detección de caídas

#### **5. Información Rica en Emergencias**
- **Contexto completo**: Ubicación + batería + timestamp
- **Enlaces directos**: Google Maps para respuesta rápida
- **Mensajes descriptivos**: "AVISO" vs "ALERTA" según severidad

---

## Próximos pasos
Continuar con análisis de carpetas `utils` y archivos individuales para completar el panorama del sistema.
"""