# Resumen Detallado: Carpeta `utils` - Utilidades del Sistema

La carpeta `utils` contiene clases de soporte fundamentales que proporcionan funcionalidades transversales a toda la aplicación, incluyendo logging, vibración, configuración y estructuras de datos.

## **Batch.kt - Estructura de Datos para Base de Datos**

### **Propósito**
Clase simple que encapsula operaciones de base de datos para el sistema de cola.

```kotlin
class Batch(internal val table: String, internal val content: ContentValues)
```

**Uso en el sistema:**
- **Encapsulación**: Combina nombre de tabla con datos en una estructura única
- **Cola de escritura**: Usado por `Data.kt` para operaciones asíncronas en SQLite
- **Thread-safety**: Facilita el paso de datos entre threads de manera segura

**Integración:**
```kotlin
// En Data.kt
val content = ContentValues().apply {
    put("timestamp", timestamp)
    put("type", type)
    put("value0", x); put("value1", y); put("value2", z)
}
queue.add(Batch("data", content))  // Encapsula tabla + datos
```

## **FallDetectionSettings.kt - Gestión de Configuración**

### **Control Master de Detección**
Objeto singleton que centraliza el control de habilitación/deshabilitación de la detección de caídas.

```kotlin
object FallDetectionSettings {
    private const val FALL_DETECTION_ENABLED_KEY = "fall_detection_enabled"
    
    fun isFallDetectionEnabled(context: Context): Boolean
    fun setFallDetectionEnabled(context: Context, enabled: Boolean)
}
```

**Características:**
- **Configuración centralizada**: Un solo punto de control para toda la app
- **Valor por defecto seguro**: `true` - activo por defecto para máxima seguridad
- **SharedPreferences**: Persistencia automática entre sesiones
- **Thread-safe**: PreferenceManager maneja la concurrencia

**Integración crítica en Detector.kt:**
```kotlin
if (context != null && FallDetectionSettings.isFallDetectionEnabled(context)) {
    showFallAlert(context)  // Solo muestra alerta si está habilitado
} else {
    Guardian.say(it, android.util.Log.INFO, TAG, "Fall detected but detection is disabled in settings")
}
```

**Puntos de control:**
1. **Settings.kt**: Switch UI para habilitar/deshabilitar
2. **Detector.kt**: Verificación antes de mostrar alerta
3. **About.kt**: Posible indicador de estado

## **Log.kt - Sistema de Logging Dual**

### **Logging Híbrido Inteligente**
Sistema que combina logs de Android con persistencia en base de datos local.

```kotlin
object Log {
    fun println(level: Int, tag: String, entry: String) {
        android.util.Log.println(level, tag, entry)           // Log estándar Android
        Sampler.instance?.data()?.log(level, tag, entry)      // Persistencia en BD
    }
}
```

#### **1. Métodos de Conveniencia**
```kotlin
fun v(tag: String, entry: String)  // VERBOSE
fun d(tag: String, entry: String)  // DEBUG  
fun i(tag: String, entry: String)  // INFO
fun w(tag: String, entry: String)  // WARN
fun e(tag: String, entry: String)  // ERROR
```

#### **2. Doble Destino**
- **Logcat inmediato**: `android.util.Log` para debugging en tiempo real
- **Base de datos**: `Sampler.instance?.data()?.log()` para análisis posterior

#### **3. Ventajas del Sistema Dual**

**Para Desarrollo:**
- **Debugging inmediato**: Logcat estándar para desarrollo
- **Filtrado por nivel**: Verbose, Debug, Info, Warn, Error

**Para Producción:**
- **Análisis forense**: Logs persistentes para investigar incidentes
- **Upload al servidor**: Los logs se incluyen en archivos ZIP que se suben
- **Timeline completo**: Reconstruir secuencia de eventos de una caída

**Para Machine Learning:**
- **Datos de entrenamiento**: Logs proporcionan contexto para algoritmos
- **Patrones de comportamiento**: Análisis de frecuencia de eventos
- **Debugging remoto**: Diagnosticar problemas sin acceso físico al dispositivo

#### **4. Integración Null-Safe**
```kotlin
Sampler.instance?.data()?.log(level, tag, entry)
```
- **Safe navigation**: No falla si Sampler no está inicializado
- **Graceful degradation**: Si falla persistencia, continúa con log estándar

## **VibrationUtils.kt - Gestión de Vibración Moderna**

### **API de Vibración Unificada**
Utilidad que abstrae las diferencias entre versiones de Android para vibración.

#### **1. Compatibilidad Multi-API**
```kotlin
fun vibrate(context: Context, pattern: LongArray, repeat: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val vibrationEffect = VibrationEffect.createWaveform(pattern, repeat)
        vibrator.vibrate(vibrationEffect)
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, repeat)
    }
}
```

**Manejo de versiones:**
- **API 26+**: `VibrationEffect.createWaveform()` (método moderno)
- **API < 26**: `vibrator.vibrate(pattern, repeat)` (método legacy)

#### **2. Métodos Especializados**

**Vibración con patrón:**
```kotlin
fun vibrate(context: Context, pattern: LongArray, repeat: Int)
```
- **Pattern**: Array de duraciones [espera, vibra, espera, vibra, ...]
- **Repeat**: -1 = una vez, 0+ = repetir desde índice especificado

**Vibración simple:**
```kotlin
fun vibrateOnce(context: Context, milliseconds: Long)
```
- **Uso**: Feedback táctil simple (botones, confirmaciones)

**Control de vibración:**
```kotlin
fun stopVibration(context: Context)
```
- **Uso**: Detener vibraciones en progreso (cancelar alertas)

#### **3. Uso en Guardian**

**FallAlertActivity - Feedback de Emergencia:**
```kotlin
// Vibración de emergencia continua
VibrationUtils.vibrate(this, longArrayOf(0, 1000), 0)  // Vibra 1s, repite indefinidamente

// Feedback de progreso en SeekBar
VibrationUtils.vibrate(this, longArrayOf(0, 50), -1)   // Pulso corto una vez

// Vibración de cancelación exitosa
VibrationUtils.vibrate(this, longArrayOf(0, 100, 50, 100, 50, 100), -1)  // Triple pulso
```

**Patterns de vibración específicos:**
- **Emergencia**: `longArrayOf(0, 1000)` con repeat=0 (continuo)
- **Progreso**: `longArrayOf(0, 50)` (pulso corto)
- **Confirmación**: `longArrayOf(0, 100, 50, 100, 50, 100)` (triple pulso)

## **Surface.kt - Renderizado de Gráficos (Archivo Individual)**

### **Sistema de Visualización de Sensores**
Aunque está en el directorio raíz, Surface.kt es fundamental para la visualización de datos.

**Propósito:**
- **Renderizado en tiempo real**: Gráficos de señales de sensores
- **Integración con Zoomable**: Canvas interactivo con zoom/pan
- **Múltiples charts**: `Surface.CHARTS` array de diferentes visualizaciones

**Uso en Signals.kt:**
```kotlin
for (index in Surface.CHARTS.indices) {
    val tab = tabs.newTab()
    tab.text = Surface.CHARTS[index].label
    tabs.addTab(tab, index, index == 0)
}
tabs.addOnTabSelectedListener(view.findViewById(R.id.surface))
```

## **Arquitectura Transversal de Utils**

### **Flujo de Datos Integrado**
```
1. Eventos del sistema → Log.println() → Logcat + SQLite
2. SQLite → Data.sweep() → ZIP → Upload → Servidor
3. Configuración UI → FallDetectionSettings → Detector.kt verifica antes de alertar
4. Eventos de UI → VibrationUtils → Feedback táctil unificado
5. Operaciones BD → Batch → Cola thread-safe → SQLite
```

### **Patrones de Diseño Aplicados**

#### **1. Facade Pattern**
- **VibrationUtils**: Oculta complejidad de APIs de vibración
- **Log**: Unifica logging estándar con persistencia

#### **2. Singleton Pattern**
- **FallDetectionSettings**: Un solo punto de configuración
- **Log**: Estado global de logging

#### **3. Strategy Pattern**
- **VibrationUtils**: Selecciona implementación según API level
- **Log**: Múltiples destinos para mismos datos

#### **4. Data Transfer Object**
- **Batch**: Transporta datos entre layers de forma estructurada

### **Beneficios del Sistema Utils**

#### **1. Consistencia**
- **API unificada**: Misma interfaz independiente de versión Android
- **Comportamiento predecible**: Fallbacks automáticos

#### **2. Mantenibilidad**
- **Centralización**: Cambios en un lugar afectan toda la app
- **Abstracción**: Oculta detalles de implementación específicos de API

#### **3. Debuggability**
- **Logging dual**: Inmediato + persistente
- **Trazabilidad completa**: Todos los eventos quedan registrados

#### **4. User Experience**
- **Feedback táctil rico**: Diferentes vibraciones para diferentes contextos
- **Configuración granular**: Control fino sobre funcionalidades

#### **5. Compatibilidad**
- **Rango amplio de APIs**: Funciona desde Android 4.1+
- **Degradación elegante**: Si algo falla, el resto sigue funcionando

---
