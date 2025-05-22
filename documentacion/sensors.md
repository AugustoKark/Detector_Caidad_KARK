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