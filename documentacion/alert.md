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

