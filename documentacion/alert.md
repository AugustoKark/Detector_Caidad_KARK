# Resumen Detallado: Carpeta `detection` - App Detección de Caídas

## Arquitectura General

La carpeta `detection` contiene el núcleo del sistema de detección de caídas, implementando un algoritmo sofisticado basen análisis de acelerómetro en tiempo real.

### **Buffers.kt**
- **Propósito**: Manejo de buffers circulares para almacenar datos de sensores
- **Funcionalidad**:
    - Crea arrays bidimensionales para almacenar múltiples canales de datos
    - Implementa copia segura entre buffers con validación de tamaños
    - Mantiene posición actual en el buffer circular
    - Inicializa todos los valores con un valor predeterminado

### **Sampler.kt**
- **Propósito**: Recolección y distribución de datos de sensores
- **Funcionalidades**:
    - Gestiona WakeLock para mantener sensores activos
    - Registra listener para acelerómetro
    - Distribuye datos a sistema de almacenamiento
    - Envía lecturas al servidor (ServerAdapter)
    - Realiza sondeo de capacidades del dispositivo
- **Integración**:
    - Singleton thread-safe
    - Conecta sensores con el detector y sistema de storage

## **Detector.kt - Análisis Detallado del Motor de Detección**

### **Configuración Técnica**
```
- Frecuencia de muestreo: 50Hz (20ms entre muestras)
- Ventana de análisis: 10 segundos (500 muestras)
- Filtros digitales: Butterworth 2do orden (0.25Hz corner frequency)
- Total de buffers: 19 canales de datos
```

### **Proceso de Detección de Caídas - Algoritmo de 3 Fases**

#### **FASE 1: Detección de Caída Libre**
```
Condición: svTOT < 0.6G (FALLING_WAIST_SV_TOT)
Duración timeout: 1000ms (SPAN_FALLING)
```

**¿Qué ocurre?**
1. **Monitoreo continuo**: El sistema analiza la magnitud del vector de aceleración total (svTOT)
2. **Detección del umbral**: Cuando la aceleración baja de 0.6G, indica posible caída libre
3. **Activación del timeout**: Se inicia un contador de 1000ms para buscar el impacto
4. **Captura de orientación inicial**: Se guarda la orientación del dispositivo antes de la caída
5. **Reseteo de variables**: Se preparan variables para analizar el patrón de caída

**Código clave:**
```kotlin
if (FALLING_WAIST_SV_TOT <= svTOTBefore && svTOTAt < FALLING_WAIST_SV_TOT) {
    timeoutFalling = SPAN_FALLING
    falling[at] = 1.0
    fallStartTime = System.currentTimeMillis()
    captureOrientation(true)
}
```

#### **FASE 2: Detección de Impacto**
```
Solo se ejecuta si FASE 1 fue activada (timeoutFalling > -1)
```

**Análisis Multivariable:**
1. **Detección del tipo de caída** en tiempo real:
    - **Lateral (LEFT/RIGHT)**: Pico significativo en eje X
    - **Hacia atrás (BACKWARD)**: Valor negativo prominente en eje Y
    - **Hacia adelante (FORWARD)**: Valor positivo prominente en eje Y

2. **Umbrales adaptativos** según tipo de caída:
   ```
   - Caídas laterales: impacto > 1.4G, svD > 1.1G
   - Caídas hacia atrás: impacto > 1.3G, svD > 1.0G  
   - Caídas hacia adelante: impacto > 1.5G, svD > 1.2G
   ```

3. **Verificaciones específicas por tipo**:
    - **Lateral**: `abs(xMaxMin[at]) > 1.5`
    - **Hacia atrás**: `abs(yMaxMin[at]) > 1.5 || (y[at] < -0.8 && svTOTAt > 1.3)`

4. **Múltiples criterios de impacto**:
   ```kotlin
   if (impactThreshold <= svTOTAt || svdThreshold <= svDAt ||
       IMPACT_WAIST_SV_MAX_MIN <= svMaxMinAt || IMPACT_WAIST_Z_2 <= z2At ||
       lateralSpecificCheck || backwardSpecificCheck) {
       
       timeoutImpact = SPAN_IMPACT  // 2000ms timeout
       impact[at] = 1.0
   }
   ```

#### **FASE 3: Verificación Final y Confirmación**
```
Se ejecuta cuando timeoutImpact llega a 0 (después de 2000ms del impacto)
```

**Proceso de Confirmación:**

1. **Prevención de alertas repetidas**:
   ```kotlin
   if (currentTime - lastFallAlertTime < 10000) return
   ```

2. **Captura de orientación final**:
   ```kotlin
   captureOrientation(false)  // Orientación después de la caída
   ```

3. **Análisis de Falsos Positivos** (sistema anti-falsos):
    - **Posición vertical final**: ¿Terminó en posición de bolsillo?
    - **Gesto de mirar teléfono**: ¿Patrón de movimiento controlado?
    - **Patrón de bolsillo**: ¿Movimiento de guardarlo en bolsillo?

4. **Verificación de evidencia fuerte**:
   ```kotlin
   val hasStrongEvidence = (orientationChanged && peakAcceleration > 1.8) ||
                          (orientationChanged && typeSpecificEvidence) ||
                          (isHorizontal && maxJerk > 1.0) ||
                          (isHorizontal && peakAcceleration > 1.7) ||
                          (fallType != FallType.UNKNOWN && typeSpecificEvidence)
   ```

### **Sistemas Anti-Falsos Positivos**

#### **1. Detección de Gesto "Mirar Teléfono"**
```kotlin
private fun detectPhoneLookGesture(): Boolean
```
- Analiza transición vertical → horizontal
- Verifica suavidad del movimiento
- Controla duración típica (600-1200ms)
- Detecta patrón de aceleración controlada

#### **2. Detección de Patrón de Bolsillo**
```kotlin
private fun checkPocketPattern(): Boolean
```
- Verifica posición vertical final
- Analiza duración corta del movimiento
- Detecta movimiento predominante en un solo eje

#### **3. Análisis de Orientación**
```kotlin
private fun isOrientationChanged(): Boolean
```
- Compara orientación antes/después usando filtros pasa-bajos
- Calcula cambio en ángulo de inclinación
- Aplica umbrales específicos por tipo de caída
- Verifica características de impacto real

### **Procesamiento de Señales**

#### **Filtros Digitales Butterworth**
```kotlin
// Pasa-bajos: Elimina ruido de alta frecuencia
private fun lpf(value: Double, xv: DoubleArray, yv: DoubleArray): Double

// Pasa-altos: Resalta cambios bruscos (impactos)
private fun hpf(value: Double, xv: DoubleArray, yv: DoubleArray): Double
```

#### **Resampling a 50Hz**
```kotlin
private fun resample(postTime: Long, postX: Double, postY: Double, postZ: Double)
```
- Interpola linealmente entre muestras irregulares del Android
- Garantiza frecuencia constante para algoritmos

### **Variables de Control Clave**

```kotlin
// Estados del detector
private var timeoutFalling: Int = -1    // Timer fase caída libre
private var timeoutImpact: Int = -1     // Timer fase impacto

// Análisis de patrón
private var peakAcceleration: Double = 0.0
private var maxJerk: Double = 0.0
private var fallStartTime: Long = 0

// Control de orientación
private var beforeFallOrientation: Triple<Double, Double, Double>?
private var afterFallOrientation: Triple<Double, Double, Double>?
```

### **Flujo Completo de Detección**

```
1. onSensorChanged() → protect() → resample() → process()
2. process() ejecuta las 3 fases secuencialmente
3. Cada muestra actualiza 19 buffers diferentes
4. Se aplican filtros digitales en tiempo real
5. Se verifica cada condición de las 3 fases
6. Al confirmar caída: showFallAlert(context)
```

### **Fortalezas del Sistema**
1. **Robustez**: Múltiples verificaciones y filtros anti-falsos positivos
2. **Precisión**: Algoritmos específicos para diferentes tipos de caída
3. **Eficiencia**: Uso optimizado de memoria con buffers circulares
4. **Adaptabilidad**: Umbrales ajustables según contexto y tipo de caída
5. **Tiempo real**: Procesamiento continuo a 50Hz sin bloqueos

---

# Resumen Detallado: Carpeta `sensors` - Sistema de Sensores

La carpeta `sensors` maneja la recolección de datos ambientales y de contexto que complementan la detección de caídas con información crítica sobre ubicación, batería y capacidades del dispositivo.

## **Battery.kt - Monitor de Energía**

### **Propósito**
Objeto singleton que monitorea el nivel de batería del dispositivo para incluirlo en reportes de emergencia.

### **Funcionalidad Principal**
```kotlin
internal fun level(context: Context): Int
```

**Proceso:**
1. **Registro de BroadcastReceiver**: Se conecta al sistema para recibir actualizaciones de batería
2. **Cálculo de porcentaje**: `(level * 100.0 / scale).toInt()`
3. **Manejo de errores**: Retorna -1 si no puede obtener información

**Uso en el sistema:**
- Se incluye en mensajes SMS de emergencia: `"Battery: ${battery}%"`
- Permite a los contactos de emergencia conocer el estado del dispositivo
- Útil para determinar si el dispositivo puede seguir funcionando

## **Positioning.kt - Sistema de Geolocalización Inteligente**

### **Arquitectura**
- **Patrón**: Singleton con LocationListener
- **Proveedores duales**: GPS y Network para máxima precisión
- **Gestión automática**: Activación de WiFi y GPS cuando es necesario

### **Proceso de Localización**

#### **1. Activación (trigger())**
```kotlin
internal fun trigger()
```
- Se ejecuta cuando se detecta una caída
- Resetea listeners y inicia búsqueda activa de ubicación
- Establece timeout de 120 segundos para respuesta

#### **2. Gestión Dual de Proveedores**
```kotlin
private var gps: Location? = null
private var network: Location? = null
```

**Estrategia de precisión:**
- **GPS**: Mayor precisión pero puede tardar más
- **Network**: Más rápido pero menos preciso
- **Selección inteligente**: Usa el más preciso disponible

#### **3. Formato de Mensaje de Emergencia**
```
"Battery: 85% Location (2024.05.22 14:30:15): -32.89084,-68.84582 ~15 m ^760 m 45 deg 0 km/h 
http://maps.google.com/?q=-32.89084,-68.84582"
```

**Información incluida:**
- Coordenadas con 5 decimales de precisión
- Precisión estimada en metros
- Altitud y orientación
- Velocidad actual
- Enlace directo a Google Maps

#### **4. Sistema de Enforcement (Auto-activación)**
```kotlin
private fun enforce(context: Context)
```

**Características:**
- **WiFi automático**: Activa WiFi para mejorar localización Network
- **GPS inteligente**: Intenta activar GPS de forma sigilosa
- **Método stealth**: Usa broadcast interno si está disponible
- **Fallback**: Abre configuración de ubicación si falla el método stealth

### **Algoritmo de Precisión**
```kotlin
private fun accuracy(location: Location?): Float
```
- Compara precisión entre GPS y Network
- Selecciona automáticamente el más preciso
- Maneja casos donde no hay precisión disponible

### **Control de Frecuencia**
```kotlin
private const val METERS_10: Long = 10
private const val MINUTES_10: Float = 600000f
```
- Después del primer reporte, cambia a actualizaciones cada 10 metros o 10 minutos
- Optimiza batería después de la emergencia inicial

## **Report.kt - Análisis de Capacidades del Dispositivo**

### **Propósito**
Genera un reporte completo de las capacidades de sensores del dispositivo para optimización y debugging.

### **Información Recolectada**

#### **1. Datos del Dispositivo**
```kotlin
private fun reportDevice(context: Context): JSONObject
```
**Incluye:**
- ID hasheado del dispositivo (SHA-256)
- Fabricante, marca, modelo
- Información de hardware y build
- Fingerprint único del sistema

#### **2. Inventario Completo de Sensores**
```kotlin
fun probe(context: Context): JSONObject
```

**Para cada sensor detectado:**
- **Tipo**: Clasificación del sensor
- **Vendor**: Fabricante del sensor
- **Nombre**: Identificación específica
- **Resolución**: Precisión de medición
- **Delay mínimo**: Frecuencia máxima de muestreo
- **Rango**: Valores máximos detectables
- **Consumo**: Energía requerida

### **Proceso de Generación**
1. **Detección automática**: Escanea todos los sensores disponibles
2. **Recolección de metadatos**: Extrae especificaciones técnicas
3. **Formato JSON estructurado**: Organiza información para análisis
4. **Hash de privacidad**: Protege identidad del dispositivo

### **Uso en el Sistema**
- **Debugging**: Ayuda a identificar problemas de compatibilidad
- **Optimización**: Permite ajustar algoritmos según capacidades
- **Análisis de flota**: Entender variabilidad entre dispositivos
- **Soporte técnico**: Información detallada para resolución de problemas

## **Integración con Sistema de Detección**

### **Flujo en Emergencia**
```
1. Detector.kt detecta caída
2. Positioning.trigger() inicia localización
3. Battery.level() obtiene estado energético
4. Se envía SMS con ubicación y batería
5. Sistema continúa monitoreando ubicación
```

### **Optimizaciones Implementadas**
- **Gestión de energía**: Activación selectiva de sensores
- **Tolerancia a fallos**: Funciona aunque algunos sensores fallen
- **Privacidad**: Hash de identificadores sensibles
- **Eficiencia**: Reuso de última ubicación conocida cuando es apropiado

### **Fortalezas del Sistema de Sensores**
1. **Redundancia**: Múltiples proveedores de ubicación
2. **Automatización**: Auto-activación de servicios necesarios
3. **Información completa**: Contexto rico para emergencias
4. **Eficiencia energética**: Optimizado para uso prolongado
5. **Compatibilidad**: Funciona en amplia gama de dispositivos

---

# Resumen Detallado: Carpeta `alerts` - Sistema de Alertas de Emergencia

La carpeta `alerts` implementa un sistema completo de notificación y comunicación de emergencia, con múltiples canales de alerta y una interfaz de usuario intuitiva para gestión de crisis.

## **Alarm.kt - Coordinador Central de Emergencias**

### **Propósito**
Clase singleton que orquesta toda la respuesta de emergencia cuando se confirma una caída.

### **Proceso de Alerta Completo**
```kotlin
@RequiresApi(Build.VERSION_CODES.M)
fun alert(context: Context)
```

**Secuencia de emergencia (ejecutada en hilo separado):**

1. **Preparación inicial**:
    - Maximiza volumen de alarma al 100%
    - Obtiene contacto de emergencia configurado

2. **Obtención de ubicación (hasta 5 intentos)**:
   ```kotlin
   for (attempt in 1..maxAttempts) {
       location = position?.getLastKnownLocation()
       if (location != null) break
       Thread.sleep(2000)  // Espera 2 segundos entre intentos
   }
   ```

3. **Envío de SMS de emergencia**:
   ```
   Mensaje con ubicación: "¡ALERTA! Se ha detectado una posible caída. maps.google.com/?q=lat,lng"
   Mensaje sin ubicación: "¡ALERTA! Se ha detectado una posible caída. No se pudo obtener la ubicación."
   ```

4. **Activación de sirena**: Reproduce alarma en loop por máximo 5 minutos

5. **Llamada automática**: Después de 3 segundos, inicia llamada al contacto

### **Gestión de Audio**
```kotlin
internal fun sound(context: Context)
```
- **Tipo de sonido**: Usa RingtoneManager.TYPE_ALARM (más fuerte que notificaciones)
- **Volumen**: Configura al máximo automáticamente
- **Duración**: Máximo 5 minutos con auto-detención
- **Loop**: Reproducción continua hasta cancelación manual

## **Contact.kt - Gestión de Contactos de Emergencia**

### **Funcionalidades**

#### **1. Interfaz de Usuario**
- **Activity completa** con layout para configurar contacto
- **Botón de búsqueda**: Acceso directo a contactos del teléfono
- **Validación**: Entrada manual o selección desde agenda

#### **2. Integración con Contactos del Sistema**
```kotlin
private val contactPicker = registerForActivityResult(StartActivityForResult())
```
- **Permisos**: Requiere READ_CONTACTS
- **Picker nativo**: Usa intent del sistema para selección
- **Extracción automática**: Obtiene número de teléfono del contacto seleccionado

#### **3. Persistencia**
```kotlin
internal operator fun get(context: Context): String?
internal operator fun set(context: Context, contact: String?)
```
- **Storage**: SharedPreferences para persistencia
- **Validación**: PhoneNumberUtils.compare() para verificar coincidencias
- **Acceso global**: Métodos companion para uso desde cualquier parte de la app

## **FallAlertActivity.kt - Interfaz de Confirmación de Emergencia**

### **Diseño de UX para Crisis**
Esta Activity implementa un diseño centrado en **prevenir falsos positivos** mientras mantiene **accesibilidad en emergencias reales**.

#### **1. Configuración de Pantalla de Emergencia**
```kotlin
private fun setupScreenForEmergency()
```
**Características:**
- **Pantalla siempre encendida**: `FLAG_KEEP_SCREEN_ON`
- **Activación automática**: `FLAG_TURN_SCREEN_ON`
- **Bypass de bloqueo**: `FLAG_SHOW_WHEN_LOCKED` + `FLAG_DISMISS_KEYGUARD`
- **Volumen máximo**: Auto-configuración de audio de alarma

#### **2. Sistema de Cancelación Inteligente**
```kotlin
private fun setupSeekBarListener()
```

**Mecánica del SeekBar:**
- **Gesto completo requerido**: Debe deslizar de 0% a 100%
- **Feedback progresivo**:
    - 25%: "Un poco más..." + vibración corta
    - 50%: "Continúa deslizando..." + pulso de vibración
    - 75%: "¡Suelta para cancelar!" + doble pulso
    - 100%: Cancelación exitosa + triple pulso de confirmación

**Ventajas del diseño:**
- **Previene cancelaciones accidentales**: Requiere gesto intencional completo
- **Funciona con trauma**: Gesto simple incluso con lesiones leves
- **Feedback claro**: Vibración y texto guían al usuario

#### **3. Cuenta Regresiva Configurable**
```kotlin
private fun startCountdown()
```
- **Duración personalizable**: Por defecto 30 segundos, configurable en settings
- **Escalada de urgencia**: Cambio de mensaje a los 10 segundos finales
- **Efectos visuales**: Parpadeo del contador en últimos segundos
- **Sonido diferenciado**: Tono de notificación cada 3 segundos (no alarma)

#### **4. Sistema de Vibración Avanzado**
```kotlin
private fun startVibration()
```
- **Patrón de emergencia**: 1 segundo de vibración continua, repetitivo
- **Vibración de progreso**: Feedback táctil en cada etapa del SeekBar
- **Detención inteligente**: Se detiene automáticamente al cancelar

#### **5. Reporte de Datos**
```kotlin
ServerAdapter.reportFallAlertCancelled() // Si se cancela
ServerAdapter.reportFallEvent()         // Si se confirma
```
- **Machine Learning**: Datos para mejorar algoritmo de detección
- **Ubicación incluida**: Coordenadas para análisis de contexto
- **Estado de batería**: Para correlacionar con fallos de dispositivo

## **Messenger.kt - Motor de Comunicaciones**

### **SMS Inteligente**
```kotlin
fun sms(context: Context, recipient: String, message: String)
```

**Características:**
- **Partición automática**: `divideMessage()` para mensajes largos
- **Envío multipart**: `sendMultipartTextMessage()` para enlaces de ubicación
- **Manejo de errores**: Toast informativo para usuario y logs detallados
- **Validación**: Verifica que hay contacto configurado antes de enviar

### **Llamadas de Emergencia**
```kotlin
fun call(context: Context, recipient: String)
```

**Proceso:**
- **Intent ACTION_CALL**: Llamada directa sin confirmación
- **Flags apropiados**: `FLAG_ACTIVITY_NEW_TASK` para emergencias
- **Validación de capacidad**: Verifica que el dispositivo puede realizar llamadas
- **Feedback inmediato**: Toast confirmando acción

## **Telephony.kt - Respuesta Automática a Llamadas**

### **Sistema Anti-Intrusión Inteligente**

**Funcionalidad principal**: Responde automáticamente **solo a llamadas del contacto de emergencia**.

#### **1. Detección de Llamadas**
```kotlin
override fun onReceive(context: Context, intent: Intent)
```

**Estados monitoreados:**
- **CALL_STATE_RINGING**: Llamada entrante detectada
- **CALL_STATE_OFFHOOK**: Llamada en curso (activa altavoz)
- **CALL_STATE_IDLE**: Llamada terminada (desactiva altavoz)

#### **2. Verificación de Contacto**
```kotlin
if (Contact.check(context, contact)) {
    answer(context)
}
```
- **Seguridad**: Solo responde al contacto de emergencia configurado
- **Comparación inteligente**: Usa PhoneNumberUtils.compare() para formatos diferentes

#### **3. Respuesta Automática Multi-API**
```kotlin
private fun answer(context: Context)
```

**Estrategia escalonada por versión de Android:**
- **API 26+**: `telecomManager.acceptRingingCall()` (método oficial)
- **API 21-25**: `throughMediaController()` (MediaSessionManager)
- **API 19-20**: `throughAudioManager()` (KeyEvent simulation)
- **Fallback**: Múltiples métodos de compatibilidad (reflection, shell commands)

#### **4. Auto-Altavoz**
```kotlin
internal fun speakerphone(context: Context, on: Boolean)
```
- **Activación automática**: En llamadas de emergencia se activa el altavoz
- **Volumen máximo**: Configura STREAM_VOICE_CALL al máximo
- **Modo de llamada**: Configura AudioManager.MODE_IN_CALL

#### **5. Métodos de Respuesta Alternativos**

**Para máxima compatibilidad:**
- **Reflexión**: Acceso directo a ITelephony service
- **Eventos de teclado**: Simulación de KEYCODE_HEADSETHOOK
- **MediaController**: Para Android 5.0+
- **Shell commands**: Como último recurso
- **Broadcast simulation**: Para casos específicos (HTC)

## **Arquitectura del Sistema de Alertas**

### **Flujo Completo de Emergencia**
```
1. Detector.kt detecta caída confirmada
2. FallAlertActivity.start() muestra interfaz de cancelación
3. Usuario tiene 30s para cancelar deslizando SeekBar
4. Si no cancela: Alarm.alert() ejecuta secuencia completa
5. SMS + Ubicación → Sirena → Llamada automática
6. Telephony.kt responde automáticamente si el contacto devuelve la llamada
```

### **Características de Robustez**
1. **Tolerancia a fallos**: Múltiples métodos de respaldo para cada función
2. **Compatibilidad amplia**: Soporte desde Android 4.4 hasta las últimas versiones
3. **Feedback continuo**: Usuario siempre informado del estado del sistema
4. **Prevención de falsas alarmas**: Sistema de cancelación intuitivo pero seguro
5. **Optimización de batería**: Timeouts automáticos para evitar drenaje excesivo

### **Aspectos de Seguridad**
- **Validación de contactos**: Solo responde a números autorizados
- **Permisos granulares**: Solicita solo los permisos necesarios
- **Logs auditables**: Registro completo de todas las acciones para análisis
- **Datos de ubicación**: Solo se envían en emergencias confirmadas

---

