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

