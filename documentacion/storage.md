# Resumen Detallado: Carpeta `storage` - Sistema de Almacenamiento y Sincronización

La carpeta `storage` implementa un sistema completo de almacenamiento local, sincronización con servidor y gestión de datos con características empresariales.

## **Data.kt - Base de Datos Local Inteligente**

### **Arquitectura de Almacenamiento**
Sistema de base de datos SQLite con **rotación automática diaria** y **compresión temporal**.

#### **1. Base de Datos Temporal**
```kotlin
private fun find(): SQLiteDatabase
```

**Estrategia por días:**
- **Archivo diario**: `yyyy-MM-dd.sqlite3` (ej: `2024-05-22.sqlite3`)
- **Rotación automática**: Nueva base de datos cada día a medianoche
- **Transición suave**: Cierra la anterior y abre la nueva automáticamente

#### **2. Schema de Datos**
```sql
-- Tabla de datos de sensores
CREATE TABLE IF NOT EXISTS data(
    stamp INTEGER,      -- Timestamp local de inserción
    timestamp INTEGER,  -- Timestamp del sensor
    type INTEGER,      -- Tipo de sensor (acelerómetro, etc.)
    value0 REAL,       -- Valor X
    value1 REAL,       -- Valor Y  
    value2 REAL,       -- Valor Z
    value3 REAL,       -- Valores adicionales (hasta 6 total)
    value4 REAL,
    value5 REAL
);

-- Tabla de logs del sistema
CREATE TABLE IF NOT EXISTS logs(
    stamp INTEGER,     -- Timestamp
    priority INTEGER,  -- Nivel de log (DEBUG, INFO, WARN, ERROR)
    tag VARCHAR,       -- Tag del componente
    entry VARCHAR      -- Mensaje del log
);
```

#### **3. Sistema de Cola Thread-Safe**
```kotlin
private val queue = ConcurrentLinkedQueue<Batch>()
```

**Procesamiento asíncrono:**
- **Cola sin bloqueo**: ConcurrentLinkedQueue para múltiples productores
- **Flush continuo**: Hilo dedicado que vacía la cola cada segundo
- **Inserción por lotes**: ContentValues optimizadas para SQLite

#### **4. Gestión Automática del Ciclo de Vida**
```kotlin
private fun sweep()
```

**Programador con 2 tareas:**
1. **Compresión (cada hora)**:
    - Archivos `.sqlite3` → `.zip` (excepto el actual)
    - Elimina archivos originales después de comprimir
    - Elimina journals de SQLite (`-journal`)

2. **Limpieza (cada hora)**:
    - Elimina archivos `.zip` más antiguos de 7 días
    - Mantiene solo una semana de datos locales

### **Flujo de Datos**
```
Sensores → dispatch() → ConcurrentQueue → flush() → SQLite → sweep() → ZIP → Upload
```

## **ServerAdapter.kt - Sincronización Inteligente con Servidor**

### **API REST Completa**
Sistema de comunicación con servidor Python usando **JSON** y **HTTP multipart**.

#### **1. Registro de Dispositivos**
```kotlin
fun registerDevice(context: Context)
```

**Payload de registro:**
```json
{
    "device_id": "SHA256_hash_of_android_id",
    "user_name": "Usuario",
    "user_age": 65,
    "emergency_contact": "123456789"
}
```

**Endpoint**: `POST /api/device/register`

#### **2. Sistema de Batching para Acelerómetro**
```kotlin
fun addAccelerometerReading(deviceId: String, timestamp: Long, x: Float, y: Float, z: Float)
```

**Características:**
- **Batch inteligente**: Acumula hasta 50 lecturas antes de enviar
- **Envío automático**: Se activa al alcanzar BATCH_SIZE
- **Cola thread-safe**: ArrayList sincronizado para múltiples sensores
- **Reintento automático**: Si falla, reinserta las lecturas

**Payload de acelerómetro:**
```json
{
    "device_id": "device_hash",
    "readings": [
        {"timestamp": 1234567890, "x": 0.1, "y": 0.2, "z": 9.8},
        {"timestamp": 1234567891, "x": 0.1, "y": 0.3, "z": 9.7}
    ]
}
```

#### **3. Reportes de Eventos de Emergencia**
```kotlin
fun reportFallEvent(context: Context, latitude: Double?, longitude: Double?, batteryLevel: Int)
```

**Información de caída:**
```json
{
    "device_id": "device_hash",
    "timestamp": 1234567890,
    "latitude": -32.89084,
    "longitude": -68.84582,
    "battery_level": 85,
    "event_type": "fall",
    "description": "Caída detectada automaticamente"
}
```

#### **4. Análisis de Falsos Positivos**
```kotlin
fun reportFallAlertCancelled(context: Context, latitude: Double?, longitude: Double?, batteryLevel: Int)
```

**Machine Learning feedback:**
- Reporta cuando el usuario cancela una alerta
- Incluye ubicación y nivel de batería
- Permite mejorar algoritmo de detección
- Datos para entrenamiento de IA

#### **5. Upload de Archivos**
```kotlin
fun uploadFile(context: Context, filePath: String)
```

**Características:**
- **HTTP Multipart**: Soporte para archivos grandes
- **OkHttp**: Cliente robusto con timeouts configurables
- **Async**: Corrutinas para no bloquear UI
- **Validación**: Verifica existencia antes de subir

### **Configuración de Cliente HTTP**
```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()
```

## **Storage.kt - Utilidades de Sistema de Archivos**

### **Operaciones de Archivo Thread-Safe**
Objeto singleton con operaciones básicas de archivos optimizadas.

#### **1. Compresión ZIP**
```kotlin
internal fun zip(prefix: String, file: String): Boolean
```

**Proceso:**
- Lee archivo original con buffer de 4KB
- Crea ZIP con entrada única
- Manejo robusto de errores con logs detallados
- Limpieza automática de streams

#### **2. Filtros de Archivo**
```kotlin
internal val FILTER_SQLITE = FilenameFilter { _, name -> name.endsWith(".sqlite3") }
internal val FILTER_ZIP = FilenameFilter { _, name -> name.endsWith(".zip") }
```

#### **3. Gestión de Metadatos**
```kotlin
internal fun age(prefix: String, file: String): Long  // lastModified timestamp
internal fun list(prefix: String, filter: FilenameFilter): Array<String>?  // Lista filtrada
internal fun delete(prefix: String, file: String): Boolean  // Eliminación segura
```

## **Upload.kt - Coordinador de Sincronización**

### **Sistema de Upload Diferido**
```kotlin
internal fun go(context: Context, root: String)
```

**Proceso:**
1. **Busca archivos ZIP**: Solo sube archivos ya comprimidos
2. **Ordenamiento**: Arrays.sort() para subir cronológicamente
3. **Upload secuencial**: Un archivo a la vez para estabilidad
4. **Integración con ServerAdapter**: Usa uploadFile() para cada archivo

### **Programación Automática**
```kotlin
fun initializeScheduledUploads(context: Context)
```

**Tareas programadas:**
- **Cada 15 minutos**: Envía lote de acelerómetro pendiente
- **Al iniciar**: Registra automáticamente el dispositivo

## **Arquitectura Completa del Sistema de Storage**

### **Flujo de Datos de Sensores**
```
1. Sensor genera evento → Sampler.onSensorChanged()
2. ServerAdapter.addAccelerometerReading() → Cola de batching
3. Al llegar a 50 lecturas → sendAccelerometerBatch()
4. Data.dispatch() → ConcurrentQueue → SQLite diario
5. Cada hora: SQLite → ZIP → Upload.go() → Servidor
```

### **Flujo de Eventos de Emergencia**
```
1. Detector confirma caída → FallAlertActivity
2. Si no se cancela → Alarm.alert()
3. ServerAdapter.reportFallEvent() → Servidor inmediatamente
4. Si se cancela → ServerAdapter.reportFallAlertCancelled()
```

### **Gestión de Almacenamiento**
```
Datos en tiempo real → SQLite diario → ZIP después de 1 día → Upload → Limpieza después de 7 días
```

### **Características de Robustez**

#### **1. Tolerancia a Fallos**
- **Reintento automático**: Si falla upload, mantiene datos localmente
- **Validación de archivos**: Verifica existencia antes de procesar
- **Manejo de excepciones**: Logs detallados para debugging
- **Fallback local**: Funciona sin conexión a internet

#### **2. Eficiencia de Recursos**
- **Compresión automática**: ZIP reduce espacio en disco significativamente
- **Limpieza temporal**: Solo mantiene 7 días de datos locales
- **Batching inteligente**: Reduce peticiones HTTP
- **Timeouts configurables**: Evita bloqueos indefinidos

#### **3. Escalabilidad**
- **Base de datos por día**: Previene archivos SQLite gigantes
- **Cola thread-safe**: Soporta múltiples sensores simultáneos
- **Programación diferida**: No interfiere con detección en tiempo real
- **Upload asíncrono**: No bloquea funcionalidad crítica

### **Integración con Machine Learning**
- **Datos de entrenamiento**: Reportes de falsos positivos
- **Contexto completo**: Ubicación, batería, timestamp en eventos
- **Mejora continua**: Cada cancelación mejora el algoritmo
- **Analytics**: Patrones de uso y efectividad del sistema

### **Fortalezas del Sistema de Storage**
1. **Disponibilidad offline**: Funciona sin conexión constante
2. **Integridad de datos**: Sistema robusto con múltiples respaldos
3. **Eficiencia**: Compresión y limpieza automática
4. **Escalabilidad**: Arquitectura preparada para múltiples dispositivos
5. **Machine Learning**: Retroalimentación para mejorar detección

---

