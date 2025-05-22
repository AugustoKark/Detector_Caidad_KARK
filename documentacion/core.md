# Resumen Detallado: Carpeta `core` - Núcleo del Sistema

La carpeta `core` contiene la infraestructura fundamental de la aplicación, manejando el ciclo de vida, permisos, servicios de fondo y la interfaz principal.

## **Guardian.kt - Servicio de Fondo Central**

### **Propósito**
Servicio foreground que mantiene la aplicación funcionando continuamente, incluso cuando está en segundo plano.

### **Inicialización del Sistema**
```kotlin
override fun onCreate()
```

**Componentes iniciados automáticamente:**
1. **Positioning.initiate(this)**: Sistema de geolocalización
2. **Detector.instance(this)**: Motor de detección de caídas
3. **Sampler.instance(this)**: Recolector de datos de sensores
4. **Alarm.instance(this)**: Sistema de alertas
5. **SafeZoneMonitoringService**: Monitoreo de zonas seguras (si está habilitado)
6. **ServerAdapter.initializeScheduledUploads()**: Uploads automáticos al servidor

### **Servicio Foreground Inteligente**
```kotlin
override fun onStartCommand(intent: Intent, flags: Int, startID: Int): Int
```

**Características:**
- **Notificación persistente**: "Guardian is active" siempre visible
- **Canal de notificación**: Compatible con Android 8.0+ (API 26)
- **Prioridad IMPORTANCE_NONE**: No interrumpe al usuario
- **START_STICKY**: El sistema reinicia automáticamente el servicio si es matado
- **Intent hacia Main**: Tap en notificación abre la app

### **Métodos de Utilidad**
```kotlin
internal fun initiate(context: Context)  // Inicia el servicio desde cualquier contexto
internal fun say(context: Context, level: Int, tag: String, message: String)  // Log + Toast
```

## **About.kt - Centro de Control y Permisos**

### **Interfaz de Estado del Sistema**
Fragment que funciona como "centro de control" mostrando el estado general de la aplicación.

#### **1. Gestión Completa de Permisos**
```kotlin
private val PERMISSIONS: Array<VersionedPermission>
```

**Permisos gestionados (14 permisos en total):**
- **Ubicación**: `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`
- **Comunicaciones**: `CALL_PHONE`, `SEND_SMS`, `RECEIVE_SMS`, `READ_PHONE_STATE`
- **Contactos**: `READ_CONTACTS`, `READ_CALL_LOG`
- **Audio**: `MODIFY_AUDIO_SETTINGS`
- **Red**: `INTERNET`, `CHANGE_WIFI_STATE`
- **Sistema**: `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`
- **Llamadas**: `ANSWER_PHONE_CALLS` (Android 8.0+)

#### **2. Sistema de Permisos Versionados**
```kotlin
class VersionedPermission(val permission: String, val version: Int)
```
- **Compatibilidad inteligente**: Solo solicita permisos disponibles en la versión actual
- **Solicitud por lotes**: RequestMultiplePermissions para mejor UX
- **Validación continua**: Verifica permisos cada vez que se muestra el fragment

#### **3. Indicador Visual de Estado**
```kotlin
private fun refreshPermissions(request: Boolean)
```
- **Verde**: "Guardian está activo" (todos los permisos otorgados)
- **Rojo**: "Permisos requeridos" (faltan permisos críticos)
- **Actualización automática**: Se actualiza en onStart() y después de solicitudes

#### **4. Botón de Emergencia Manual**
```kotlin
override fun onClick(view: View)
```
- **Activación directa**: `Alarm.alert()` sin pasar por detección de caídas
- **Testing fácil**: Permite probar el sistema de alertas manualmente
- **FloatingActionButton**: Botón prominente y fácil de alcanzar

## **Boot.kt - Auto-inicio del Sistema**

### **Funcionalidad**
```kotlin
class Boot : BroadcastReceiver()
```

**Propósito crítico:**
- **Inicio automático**: Se ejecuta cuando el dispositivo se enciende
- **Continuidad del servicio**: Garantiza que Guardian funcione 24/7
- **Cero intervención**: El usuario no necesita abrir la app después de reiniciar

**Implementación minimalista pero efectiva:**
```kotlin
if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
    Guardian.initiate(context)
}
```

## **Connectivity.kt - Gestor de Conectividad**

### **Upload Inteligente de Datos**
```kotlin
class Connectivity : BroadcastReceiver()
```

**Funcionalidad:**
- **Detección automática**: Monitorea cambios en conectividad de red
- **Upload oportunista**: Sube datos cuando hay conexión disponible
- **Eficiencia de batería**: Solo actúa cuando hay conectividad real

**Proceso:**
```kotlin
val info = manager.activeNetworkInfo
if (info != null && info.isConnected) {
    Upload.go(context.applicationContext, context.applicationContext.filesDir.path)
}
```

## **Main.kt - Actividad Principal**

### **Interfaz de Navegación**
Actividad principal que implementa navegación por pestañas usando Navigation Component.

#### **1. Estructura de Navegación**
```kotlin
val appBarConfiguration = AppBarConfiguration(setOf(R.id.about, R.id.signals, R.id.settings, R.id.safezone))
```

**Pestañas disponibles:**
- **About**: Centro de control y estado
- **Signals**: Monitoreo de señales de sensores
- **Settings**: Configuración de la aplicación
- **SafeZone**: Gestión de zonas seguras

#### **2. EULA (End User License Agreement)**
```kotlin
private fun eula(context: Context)
```

**Características:**
- **Primera ejecución**: Se muestra automáticamente en primer uso
- **Servicio iniciado**: `Guardian.initiate(this)` se ejecuta al mostrar EULA
- **Navegación automática**: Después de aceptar, va directo a Settings
- **Pantalla completa**: Diálogo que ocupa toda la pantalla

**Flujo de experiencia:**
1. Usuario abre la app por primera vez
2. Se muestra EULA en pantalla completa
3. Guardian se inicia en segundo plano
4. Usuario acepta términos
5. Navegación automática a Settings para configurar contacto

## **Arquitectura del Sistema Core**

### **Patrón de Iniciación en Cascada**
```
Boot.kt (sistema) → Guardian.initiate() → Guardian.onCreate() → Inicia todos los componentes
```

### **Gestión de Ciclo de Vida**
1. **Instalación/Primera vez**: Main.kt muestra EULA → Settings
2. **Uso normal**: Main.kt → Navegación libre entre pestañas
3. **Reinicio del dispositivo**: Boot.kt → Auto-inicio de Guardian
4. **Pérdida de conectividad**: Connectivity.kt → Upload automático cuando se recupera

### **Robustez del Sistema**
- **START_STICKY**: Android reinicia Guardian automáticamente
- **Foreground Service**: Prioridad alta, difícil de matar
- **Auto-inicio**: Funciona después de reiniciar dispositivo
- **Gestión de permisos**: Solicita automáticamente permisos faltantes

### **Optimizaciones**
- **Notificación silenciosa**: IMPORTANCE_NONE no molesta al usuario
- **Permisos versionados**: Solo solicita lo que está disponible
- **Upload diferido**: Espera conectividad para subir datos
- **Inicialización lazy**: Componentes se inician solo cuando es necesario

### **Puntos de Entrada al Sistema**
1. **Instalación**: Main.kt → EULA → Guardian.initiate()
2. **Auto-inicio**: Boot.kt → Guardian.initiate()
3. **Manual**: Usuario abre la app → Main.kt
4. **Emergencia manual**: About.kt → Alarm.alert()
5. **Emergencia automática**: Detector.kt → FallAlertActivity

### **Fortalezas del Core**
1. **Disponibilidad 24/7**: Funciona continuamente sin intervención
2. **Auto-recuperación**: Se reinicia automáticamente tras problemas
3. **UX fluida**: Navegación intuitiva y configuración guiada
4. **Compatibilidad amplia**: Funciona desde Android 4.1 (API 16)
5. **Gestión inteligente**: Permisos, conectividad y recursos optimizados

---

