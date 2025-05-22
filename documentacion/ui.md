# Resumen Detallado: Carpeta `ui` - Interfaz de Usuario

La carpeta `ui` contiene los componentes de interfaz gráfica que permiten al usuario interactuar con el sistema, monitorear señales en tiempo real y configurar la aplicación.

## **Settings.kt - Panel de Configuración Avanzado**

### **Funcionalidades de Configuración**
Fragment basado en PreferenceFragmentCompat que proporciona una interfaz moderna para configurar todos los aspectos de Guardian.

#### **1. Configuración de Tiempo de Alerta**
```kotlin
findPreference<EditTextPreference>("delay_seconds")
```

**Características:**
- **Rango válido**: 10-120 segundos (configurable)
- **Valor por defecto**: 30 segundos
- **Validación en tiempo real**: Rechaza valores fuera de rango
- **Persistencia**: SharedPreferences con clave "fall_detection_delay"
- **Feedback visual**: Summary actualizado automáticamente ("Actualmente: X segundos")

**Proceso de validación:**
```kotlin
val seconds = newValue.toString().toIntOrNull() ?: 30
if (seconds in 10..120) {
    prefs.edit().putInt("fall_detection_delay", seconds).apply()
    true
} else {
    Toast.makeText(context, "El tiempo debe estar entre 10 y 120 segundos", Toast.LENGTH_SHORT).show()
    false
}
```

#### **2. Control de Detección de Caídas**
```kotlin
findPreference<SwitchPreference>("fall_detection_enabled")
```

**Funcionalidad:**
- **Switch principal**: Habilita/deshabilita completamente la detección
- **Feedback inmediato**: Toast de confirmación al cambiar
- **Summary dinámico**:
    - Activado: "Guardian está monitoreando caídas activamente"
    - Desactivado: "La detección de caídas está desactivada"

#### **3. Función de Prueba de Alertas**
```kotlin
findPreference<Preference>("test_alert")?.setOnPreferenceClickListener
```

**Características:**
- **Testing completo**: Ejecuta `Alarm.alert()` - la misma función que una emergencia real
- **Confirmación visual**: Toast "Alerta de prueba enviada"
- **Propósito**: Permite al usuario verificar que SMS, llamada y sonido funcionan correctamente

#### **4. Información de Versión**
```kotlin
findPreference<Preference>("app_version")?.apply { summary = "Guardian v${getAppVersion()}" }
```

**Funcionalidad:**
- **Versión automática**: Lee del PackageInfo del sistema
- **Fallback**: "1.0.0" si hay error al leer
- **Display**: "Guardian vX.X.X" en el summary

### **Integración con Sistema**
- **Preferencias XML**: Utiliza `R.xml.preferences` para layout declarativo
- **Persistencia automática**: PreferenceManager maneja el almacenamiento
- **Validation layer**: Lógica de validación personalizada para cada setting

## **Signals.kt - Monitor de Señales en Tiempo Real**

### **Interfaz de Monitoreo**
Fragment que proporciona visualización en tiempo real de las señales de los sensores.

#### **1. Sistema de Pestañas Dinámicas**
```kotlin
for (index in Surface.CHARTS.indices) {
    val tab = tabs.newTab()
    tab.text = Surface.CHARTS[index].label
    tabs.addTab(tab, index, index == 0)
}
```

**Características:**
- **Pestañas automáticas**: Se generan basándose en `Surface.CHARTS`
- **Primera pestaña activa**: `index == 0` selecciona la primera por defecto
- **Labels dinámicos**: Cada chart tiene su propio label descriptivo

#### **2. Integración con Surface**
```kotlin
tabs.addOnTabSelectedListener(view.findViewById(R.id.surface))
```

**Funcionalidad:**
- **Cambio dinámico**: Al seleccionar pestaña, cambia el gráfico mostrado
- **Surface view**: Utiliza el componente `Surface` para renderizado
- **Tiempo real**: Muestra datos de sensores en vivo

## **Zoomable.kt - Canvas Interactivo Avanzado**

### **Sistema de Visualización Complejo**
Clase abstracta que implementa un SurfaceView con capacidades de zoom y pan usando gestos multi-touch.

#### **1. Arquitectura de Renderizado**
```kotlin
abstract class Zoomable : SurfaceView, SurfaceHolder.Callback, Runnable, OnTouchListener
```

**Componentes:**
- **SurfaceView**: Renderizado de alta performance en thread separado
- **SurfaceHolder.Callback**: Gestión del ciclo de vida de la superficie
- **Runnable**: Loop de renderizado a 25 FPS (cada 40ms)
- **OnTouchListener**: Manejo de gestos multi-touch

#### **2. Sistema de Transformaciones con Matrix**
```kotlin
private var reference: Matrix? = null
private var operation: Matrix? = null
```

**Matemáticas de transformación:**
- **reference**: Matrix de estado base antes del gesto actual
- **operation**: Matrix resultante después de aplicar transformaciones
- **rawOnTouch/rawOnPaint**: Arrays para extraer/aplicar valores de matrix

#### **3. Detección de Gestos Multi-Touch**
```kotlin
private fun pinchSpan(event: MotionEvent)
private fun pinchPrepare(event: MotionEvent, width: Int, height: Int)  
private fun pinchProcess(event: MotionEvent, width: Int, height: Int)
```

**Algoritmo de pinch-to-zoom:**

**pinchSpan()**: Analiza todos los puntos de contacto
```kotlin
for (i in 0 until count) {
    val x = event.getX(i)
    val y = event.getY(i)
    sumX += x; sumY += y
    // Calcula min/max para determinar span del gesto
}
```

**pinchPrepare()**: Calcula factores de escala
```kotlin
scaleX = deltaXNow / deltaX  // Factor de zoom horizontal
scaleY = deltaYNow / deltaY  // Factor de zoom vertical
```

**pinchProcess()**: Aplica transformaciones
```kotlin
operation.postTranslate(centerXNow - centerX, centerYNow - centerY)  // Pan
operation.postScale(scaleX, scaleY, centerXNow, centerYNow)          // Zoom
```

#### **4. Sistema de Limitación de Viewport**
```kotlin
// Evita zoom menor a 1x
if (rawOnTouch[Matrix.MSCALE_X] < 1f) rawOnTouch[Matrix.MSCALE_X] = 1f

// Evita pan fuera de límites
if (rawOnTouch[Matrix.MTRANS_X] > limitX) rawOnTouch[Matrix.MTRANS_X] = limitX

// Calcula límites dinámicos basados en escala actual
val thresholdX = limitX - (rawOnTouch[Matrix.MSCALE_X] - 1) * width
```

#### **5. Loop de Renderizado Optimizado**
```kotlin
override fun run() {
    val gfx = surfaceHolder.lockCanvas()
    if (gfx != null) {
        try {
            synchronized(surfaceHolder) { surfaceDrawInternal(gfx) }
        } finally {
            surfaceHolder.unlockCanvasAndPost(gfx)
        }
    }
}
```

**Características del rendering:**
- **Thread separado**: No bloquea UI thread
- **25 FPS**: Scheduled a 40ms intervalos
- **Thread-safe**: Synchronized para evitar race conditions
- **Resource management**: Proper lock/unlock del canvas

#### **6. Integración con Clases Derivadas**
```kotlin
abstract fun surfaceDraw(gfx: Canvas)
```

**Patrón Template Method:**
- **Zoomable** maneja toda la complejidad de gestos y transformaciones
- **Clases derivadas** solo implementan `surfaceDraw()` con la lógica de dibujo específica
- **Transformaciones automáticas**: Canvas ya viene con translate/scale aplicados

### **Casos de Uso en Guardian**

#### **1. Visualización de Señales de Sensores**
- **Gráficos en tiempo real**: Acelerómetro, giroscopio, etc.
- **Zoom temporal**: Ver detalles específicos de eventos
- **Pan horizontal**: Navegar por historial de datos

#### **2. Análisis de Eventos de Caída**
- **Zoom en evento**: Analizar los momentos antes/durante/después de caída
- **Comparación de ejes**: Ver patrones en X, Y, Z simultáneamente
- **Investigación forense**: Revisar falsos positivos/negativos

## **Arquitectura de UI Completa**

### **Flujo de Navegación**
```
Main.kt → BottomNavigationView → 4 pestañas:
├── About (core/About.kt)     - Estado y emergencia manual
├── Signals (ui/Signals.kt)   - Monitoreo en tiempo real  
├── Settings (ui/Settings.kt) - Configuración del sistema
└── SafeZone                  - Gestión de zonas seguras
```

### **Integración con Surface.kt**
```kotlin
// Surface.kt coordina la visualización
tabs.addOnTabSelectedListener(surface)  // Signals.kt
surface.render(gfx, chart)              // Zoomable.kt
```

### **Manejo de Estados**
- **Settings**: SharedPreferences para persistencia
- **Signals**: Tiempo real desde detectores
- **Zoomable**: Estado de transformaciones en memoria

### **Fortalezas del Sistema UI**

#### **1. Performance**
- **SurfaceView**: Renderizado en thread separado, no bloquea UI
- **25 FPS constantes**: Smooth para análisis de señales rápidas
- **Matrix optimizada**: Transformaciones hardware-accelerated cuando disponible

#### **2. Usabilidad**
- **Gestos naturales**: Pinch-to-zoom y pan como aplicaciones nativas
- **Configuración intuitiva**: PreferenceScreen estándar de Android
- **Testing integrado**: Botón de prueba de alertas

#### **3. Flexibilidad**
- **Charts configurables**: Sistema extensible de visualizaciones
- **Validación personalizable**: Rangos de configuración ajustables
- **Zoom sin límites**: Sistema de viewport inteligente

#### **4. Debugging y Análisis**
- **Visualización en tiempo real**: Ver exactamente qué detecta el algoritmo
- **Zoom temporal**: Analizar eventos específicos en detalle
- **Historia navegable**: Pan para revisar eventos pasados

---

## Próximos pasos
Continuar con análisis de carpetas `safezone`, `utils` y archivos individuales para completar el panorama del sistema.
"""