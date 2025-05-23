package altermarkive.guardian.detection

// Digital filters (low-pass & high-pass) generated by the code of A.J. Fisher, see:
// http://www-users.cs.york.ac.uk/~fisher/mkfilter/

import altermarkive.guardian.alerts.Alarm
import altermarkive.guardian.BuildConfig
import altermarkive.guardian.alerts.FallAlertActivity
import altermarkive.guardian.utils.Log
import altermarkive.guardian.core.Guardian
import altermarkive.guardian.sensors.Battery
import altermarkive.guardian.sensors.Positioning
import altermarkive.guardian.storage.ServerAdapter
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlin.math.abs

class Detector private constructor() : SensorEventListener {
    var context: Guardian? = null

    companion object {
        internal var singleton: Detector = Detector()

        internal fun instance(context: Guardian): Detector {
            if (singleton.context != context) {
                singleton.initiateSensor(context)
            }
            return singleton
        }

        private val TAG: String = Detector::class.java.simpleName

        fun log(level: Int, entry: String) {
            if (BuildConfig.DEBUG) {
                Log.println(level, TAG, entry)
            }
        }

        internal const val INTERVAL_MS = 20  //Intervalo de muestreo: 20ms (50HZ)
        private const val DURATION_S = 10    //Duración de la ventana de muestreo: 10s
        internal const val N = DURATION_S * 1000 / INTERVAL_MS // Total de muestras en el buffer

        //Para la deteccion de caidas
        internal const val FALLING_WAIST_SV_TOT = 0.6  // Umbral para detectar la fase de caída libre
        internal const val IMPACT_WAIST_SV_TOT = 1.8  // Reducido para mayor sensibilidad
        internal const val IMPACT_WAIST_SV_D = 1.5     // Reducido para mayor sensibilidad
        internal const val IMPACT_WAIST_SV_MAX_MIN = 1.8  // Reducido
        internal const val IMPACT_WAIST_Z_2 = 1.2    // Reducido

        private const val SPAN_MAX_MIN = 100 / INTERVAL_MS
        private const val SPAN_FALLING = 1000 / INTERVAL_MS
        private const val SPAN_IMPACT = 2000 / INTERVAL_MS
        private const val SPAN_AVERAGING = 400 / INTERVAL_MS

        private const val FILTER_N_ZEROS = 2
        private const val FILTER_N_POLES = 2
        private const val FILTER_LPF_GAIN = 4.143204922e+03
        private const val FILTER_HPF_GAIN = 1.022463023e+00
        private const val FILTER_FACTOR_0 = -0.9565436765
        private const val FILTER_FACTOR_1 = +1.9555782403

        private const val G = 1.0

        private const val LYING_AVERAGE_Z_LPF = 0.5

        // Umbral para detectar posición horizontal
        private const val HORIZONTAL_THRESHOLD = 0.2  // Si Z < 0.2G, el dispositivo está casi horizontal

        internal const val BUFFER_X: Int = 0
        internal const val BUFFER_Y: Int = 1
        internal const val BUFFER_Z: Int = 2
        internal const val BUFFER_X_LPF: Int = 3
        internal const val BUFFER_Y_LPF: Int = 4
        internal const val BUFFER_Z_LPF: Int = 5
        internal const val BUFFER_X_HPF: Int = 6
        internal const val BUFFER_Y_HPF: Int = 7
        internal const val BUFFER_Z_HPF: Int = 8
        internal const val BUFFER_X_MAX_MIN: Int = 9
        internal const val BUFFER_Y_MAX_MIN: Int = 10
        internal const val BUFFER_Z_MAX_MIN: Int = 11
        internal const val BUFFER_SV_TOT: Int = 12
        internal const val BUFFER_SV_D: Int = 13
        internal const val BUFFER_SV_MAX_MIN: Int = 14
        internal const val BUFFER_Z_2: Int = 15
        internal const val BUFFER_FALLING: Int = 16
        internal const val BUFFER_IMPACT: Int = 17
        internal const val BUFFER_LYING: Int = 18
        internal const val BUFFER_COUNT: Int = 19
    }

    private var timeoutFalling: Int = -1
    private var timeoutImpact: Int = -1
    val buffers: Buffers = Buffers(BUFFER_COUNT, N, 0, Double.NaN)
    private val x: DoubleArray = buffers.buffers[BUFFER_X]   // Aceleracion en X
    private val y: DoubleArray = buffers.buffers[BUFFER_Y]   // Aceleracion en Y
    private val z: DoubleArray = buffers.buffers[BUFFER_Z]   // Aceleracion en Z
    private val xLPF: DoubleArray = buffers.buffers[BUFFER_X_LPF]   // Aceleracion en X (filtro pasa-bajos)
    private val yLPF: DoubleArray = buffers.buffers[BUFFER_Y_LPF]   // Aceleracion en Y (filtro pasa-bajos)
    private val zLPF: DoubleArray = buffers.buffers[BUFFER_Z_LPF]   // Aceleracion en Z (filtro pasa-bajos)
    private val xHPF: DoubleArray = buffers.buffers[BUFFER_X_HPF]   // Aceleracion en X (filtro pasa-altos)
    private val yHPF: DoubleArray = buffers.buffers[BUFFER_Y_HPF]   // Aceleracion en Y (filtro pasa-altos)
    private val zHPF: DoubleArray = buffers.buffers[BUFFER_Z_HPF]   // Aceleracion en Z (filtro pasa-altos)
    private val xMaxMin: DoubleArray = buffers.buffers[BUFFER_X_MAX_MIN]
    private val yMaxMin: DoubleArray = buffers.buffers[BUFFER_Y_MAX_MIN]
    private val zMaxMin: DoubleArray = buffers.buffers[BUFFER_Z_MAX_MIN]
    private val svTOT: DoubleArray = buffers.buffers[BUFFER_SV_TOT]
    private val svD: DoubleArray = buffers.buffers[BUFFER_SV_D]
    private val svMaxMin: DoubleArray = buffers.buffers[BUFFER_SV_MAX_MIN]
    private val z2: DoubleArray = buffers.buffers[BUFFER_Z_2]
    private val falling: DoubleArray = buffers.buffers[BUFFER_FALLING]
    private val impact: DoubleArray = buffers.buffers[BUFFER_IMPACT]
    private val lying: DoubleArray = buffers.buffers[BUFFER_LYING]
    private val xLpfXV = DoubleArray(FILTER_N_ZEROS + 1) { 0.0 }
    private val xLpfYV = DoubleArray(FILTER_N_POLES + 1) { 0.0 }
    private val yLpfXV = DoubleArray(FILTER_N_ZEROS + 1) { 0.0 }
    private val yLpfYV = DoubleArray(FILTER_N_POLES + 1) { 0.0 }
    private val zLpfXV = DoubleArray(FILTER_N_ZEROS + 1) { 0.0 }
    private val zLpfYV = DoubleArray(FILTER_N_POLES + 1) { 0.0 }
    private val xHpfXV = DoubleArray(FILTER_N_ZEROS + 1) { 0.0 }
    private val xHpfYV = DoubleArray(FILTER_N_POLES + 1) { 0.0 }
    private val yHpfXV = DoubleArray(FILTER_N_ZEROS + 1) { 0.0 }
    private val yHpfYV = DoubleArray(FILTER_N_POLES + 1) { 0.0 }
    private val zHpfXV = DoubleArray(FILTER_N_ZEROS + 1) { 0.0 }
    private val zHpfYV = DoubleArray(FILTER_N_POLES + 1) { 0.0 }
    private var anteX: Double = Double.NaN
    private var anteY: Double = Double.NaN
    private var anteZ: Double = Double.NaN
    private var anteTime: Long = 0
    private var regular: Long = 0

    // Variables para control de tiempo y falsos positivos
    private var beforeFallOrientation: Triple<Double, Double, Double>? = null
    private var afterFallOrientation: Triple<Double, Double, Double>? = null
    private var beforeInclinationAngle: Double? = null
    private var afterInclinationAngle: Double? = null
    private val ORIENTATION_THRESHOLD = 0.4  // Más sensible
    private var fallStartTime: Long = 0
    private var lastFallAlertTime: Long = 0  // Para evitar alertas repetidas

    // Variables para análisis de patrón
    private var peakAcceleration: Double = 0.0
    private var minAccelerationBeforePeak: Double = Double.MAX_VALUE
    private var consecutiveLowSVCount: Int = 0

    // Variables nuevas para mejoras anti-falsos positivos
    private var checkTimeStartTime: Long = 0
    private var phoneRaisePattern: Boolean = false
    private var smoothnessScore: Double = 0.0
    private var verticalToHorizontalTransition: Boolean = false
    private val recentOrientations = mutableListOf<Triple<Double, Double, Double>>()
    private var jerkMagnitude: Double = 0.0
    private var maxJerk: Double = 0.0

    // Enum para tipos de caída
    private enum class FallType {
        FORWARD, BACKWARD, LEFT, RIGHT, UNKNOWN
    }

    private fun linear(before: Long, ante: Double, after: Long, post: Double, now: Long): Double {
        return ante + (post - ante) * (now - before).toDouble() / (after - before).toDouble()
    }

    @Suppress("SameParameterValue")
    private fun at(array: DoubleArray, index: Int, size: Int): Double {
        return array[(index + size) % size]
    }

    private fun expire(timeout: Int): Int {
        return if (timeout > -1) {
            timeout - 1
        } else {
            -1
        }
    }

    private fun sv(x: Double, y: Double, z: Double): Double {
        return sqrt(x * x + y * y + z * z)
    }

    private fun min(array: DoubleArray): Double {
        var min: Double = at(array, buffers.position, N)
        for (i: Int in 1 until SPAN_MAX_MIN) {
            val value: Double = at(array, buffers.position - i, N)
            if (!value.isNaN() && value < min) {
                min = value
            }
        }
        return min
    }

    private fun max(array: DoubleArray): Double {
        var max: Double = at(array, buffers.position, N)
        for (i: Int in 1 until SPAN_MAX_MIN) {
            val value: Double = at(array, buffers.position - i, N)
            if (!value.isNaN() && max < value) {
                max = value
            }
        }
        return max
    }

    // Low-pass Butterworth filter, 2nd order, 50 Hz sampling rate, corner frequency 0.25 Hz
    private fun lpf(value: Double, xv: DoubleArray, yv: DoubleArray): Double {
        xv[0] = xv[1]
        xv[1] = xv[2]
        xv[2] = value / FILTER_LPF_GAIN
        yv[0] = yv[1]
        yv[1] = yv[2]
        yv[2] = (xv[0] + xv[2]) + 2 * xv[1] + (FILTER_FACTOR_0 * yv[0]) + (FILTER_FACTOR_1 * yv[1])
        return yv[2]
    }

    // High-pass Butterworth filter, 2nd order, 50 Hz sampling rate, corner frequency 0.25 Hz
    private fun hpf(value: Double, xv: DoubleArray, yv: DoubleArray): Double {
        xv[0] = xv[1]
        xv[1] = xv[2]
        xv[2] = value / FILTER_HPF_GAIN
        yv[0] = yv[1]
        yv[1] = yv[2]
        yv[2] = (xv[0] + xv[2]) - 2 * xv[1] + (FILTER_FACTOR_0 * yv[0]) + (FILTER_FACTOR_1 * yv[1])
        return yv[2]
    }

    private fun isInHorizontalPosition(): Boolean {
        // Calcular el promedio del eje Z filtrado en los últimos 400ms
        var sumZ = 0.0
        var count = 0

        for (i in 0 until SPAN_AVERAGING) {
            val zVal = at(zLPF, buffers.position - i, N)
            if (!zVal.isNaN()) {
                sumZ += abs(zVal)
                count++
            }
        }

        if (count == 0) return false

        val avgZ = sumZ / count

        // Si el valor absoluto de Z es menor que el umbral, el dispositivo está horizontal
        val isHorizontal = avgZ < HORIZONTAL_THRESHOLD

        if (isHorizontal) {
            log(android.util.Log.DEBUG, "Device is in horizontal position - Z average: $avgZ")
        }

        return isHorizontal
    }

    private fun detectFallType(): FallType {
        val at = buffers.position

        // Analizar los últimos 500ms de datos
        var xPeak = 0.0
        var yPeak = 0.0
        var zDrop = 0.0
        var xMin = 0.0
        var yMin = 0.0
        var xAvg = 0.0
        var yAvg = 0.0

        for (i in 0 until 25) { // 500ms de datos
            val idx = (at - i + N) % N
            if (x[idx] > xPeak) xPeak = x[idx]
            if (y[idx] > yPeak) yPeak = y[idx]
            if (x[idx] < xMin) xMin = x[idx]
            if (y[idx] < yMin) yMin = y[idx]
            if (z[idx] < zDrop) zDrop = z[idx]
            xAvg += x[idx]
            yAvg += y[idx]
        }

        xAvg /= 25
        yAvg /= 25

        // Umbrales ajustados para ser más sensibles
        val lateralThreshold = 0.8  // Reducido de 1.2
        val forwardBackwardThreshold = 0.8  // Reducido de 1.2

        // Considerar tanto picos como promedios para mejor detección
        val xMaxAbs = Math.max(abs(xPeak), abs(xMin))
        val yMaxAbs = Math.max(abs(yPeak), abs(yMin))

        // Determinar tipo de caída basado en los patrones
        return when {
            xMaxAbs > lateralThreshold && xMaxAbs > yMaxAbs * 0.8 -> {
                // Usar promedio para determinar dirección en caídas laterales
                if (xAvg > 0) FallType.RIGHT else FallType.LEFT
            }
            yMaxAbs > forwardBackwardThreshold && yMaxAbs > xMaxAbs * 0.8 -> {
                // Para caídas hacia atrás, el mínimo puede ser más significativo
                if (yMin < -forwardBackwardThreshold) FallType.BACKWARD
                else if (yPeak > forwardBackwardThreshold) FallType.FORWARD
                else FallType.UNKNOWN
            }
            // Si hay movimiento significativo en ambos ejes, determinar por el dominante
            xMaxAbs > 0.6 || yMaxAbs > 0.6 -> {
                if (xMaxAbs > yMaxAbs) {
                    if (xAvg > 0) FallType.RIGHT else FallType.LEFT
                } else {
                    if (yAvg < 0) FallType.BACKWARD else FallType.FORWARD
                }
            }
            else -> FallType.UNKNOWN
        }
    }

    private fun detectLateralFall(): Boolean {
        val at = buffers.position

        // En caídas laterales, típicamente:
        // - X (lateral) muestra un pico significativo
        // - Z (vertical) disminuye
        // - Y puede no cambiar mucho

        var xPeak = 0.0
        var zDrop = 0.0

        for (i in 0 until 25) { // 500ms de datos
            val idx = (at - i + N) % N
            if (abs(x[idx]) > abs(xPeak)) xPeak = x[idx]
            if (z[idx] < zDrop) zDrop = z[idx]
        }

        val isLateralFall = abs(xPeak) > 1.3 && zDrop < -0.3

        if (isLateralFall) {
            log(android.util.Log.DEBUG, "Lateral fall detected - X peak: $xPeak, Z drop: $zDrop")
        }

        return isLateralFall
    }

    private fun detectPhoneLookGesture(): Boolean {
        // Verificar si es el patrón típico de mirar el teléfono
        val at = buffers.position

        // 1. Analizar los últimos 800ms
        var yStartAvg = 0.0
        var yEndAvg = 0.0
        var yTransitionSmooth = true
        var zEndAvg = 0.0

        // Primeros 200ms (inicio del movimiento)
        for (i in 35 until 40) { // 700-800ms atrás
            val idx = (at - i + N) % N
            yStartAvg += y[idx]
        }
        yStartAvg /= 5

        // Últimos 200ms (final del movimiento)
        for (i in 0 until 10) { // 0-200ms atrás
            val idx = (at - i + N) % N
            yEndAvg += y[idx]
            zEndAvg += z[idx]
        }
        yEndAvg /= 10
        zEndAvg /= 10

        // 2. Verificar transición de vertical a horizontal
        val isVerticalToHorizontal = abs(yStartAvg) > 0.8 && abs(yEndAvg) < 0.5 && zEndAvg > 0.6

        // 3. Analizar la suavidad del movimiento
        var smoothness = 0.0
        var previousAccel = 0.0
        for (i in 1 until 30) {
            val idx = (at - i + N) % N
            val currentAccel = sv(x[idx], y[idx], z[idx])
            if (i > 1) {
                smoothness += abs(currentAccel - previousAccel)
            }
            previousAccel = currentAccel
        }
        smoothness /= 28

        // 4. Verificar que no hay impactos bruscos
        var maxJerkLocal = 0.0
        for (i in 1 until 30) {
            val idx = (at - i + N) % N
            val jerk = abs(svD[idx])
            if (jerk > maxJerkLocal) maxJerkLocal = jerk
        }

        // 5. Verificar duración del movimiento (típicamente 600-1200ms)
        val duration = System.currentTimeMillis() - fallStartTime
        val isDurationTypical = duration > 600 && duration < 1200

        // 6. Verificar patrón de aceleración característico
        // Al levantar el teléfono, primero se acelera y luego se desacelera suavemente
        var accelerationPattern = true
        var peakFound = false
        var peakIndex = -1
        for (i in 5 until 35) {
            val idx = (at - i + N) % N
            val accel = sv(x[idx], y[idx], z[idx])
            if (accel > 1.3 && !peakFound) {
                peakFound = true
                peakIndex = i
            }
            if (peakFound && i > peakIndex + 5 && accel > 1.3) {
                accelerationPattern = false // No debería haber múltiples picos
            }
        }

        // 7. Check rotación controlada (giroscopio si estuviera disponible, pero usando acelerómetro)
        var rotationControl = true
        for (i in 1 until 20) {
            val idx = (at - i + N) % N
            val xChange = abs(x[idx] - x[(idx - 1 + N) % N])
            val yChange = abs(y[idx] - y[(idx - 1 + N) % N])
            val zChange = abs(z[idx] - z[(idx - 1 + N) % N])

            // Si hay cambios muy bruscos en múltiples ejes simultáneamente, no es controlado
            if (xChange > 0.5 && yChange > 0.5 && zChange > 0.5) {
                rotationControl = false
            }
        }

        val isPhoneLookGesture = isVerticalToHorizontal &&
                smoothness < 0.3 &&
                maxJerkLocal < 1.0 &&
                isDurationTypical &&
                accelerationPattern &&
                rotationControl

        if (isPhoneLookGesture) {
            log(android.util.Log.DEBUG, "Phone look gesture detected - Y start: $yStartAvg, Y end: $yEndAvg, Z end: $zEndAvg, smoothness: $smoothness")
        }

        return isPhoneLookGesture
    }

    private fun checkChaosPattern(): Boolean {
        val at = buffers.position
        var chaosScore = 0.0

        // Verificar cambios erráticos en múltiples ejes
        for (i in 1 until 20) {
            val idx = (at - i + N) % N
            val prevIdx = (idx - 1 + N) % N

            val dx = abs(x[idx] - x[prevIdx])
            val dy = abs(y[idx] - y[prevIdx])
            val dz = abs(z[idx] - z[prevIdx])

            // Contar cambios significativos en múltiples ejes
            var axesChanged = 0
            if (dx > 0.3) axesChanged++
            if (dy > 0.3) axesChanged++
            if (dz > 0.3) axesChanged++

            if (axesChanged >= 2) {
                chaosScore += 1.0
            }
        }

        return chaosScore > 5  // Al menos 5 instancias de cambios multi-eje
    }

    private fun process() {
        val at: Int = buffers.position
        timeoutFalling = expire(timeoutFalling)
        timeoutImpact = expire(timeoutImpact)
        xLPF[at] = lpf(x[at], xLpfXV, xLpfYV)
        yLPF[at] = lpf(y[at], yLpfXV, yLpfYV)
        zLPF[at] = lpf(z[at], zLpfXV, zLpfYV)
        xHPF[at] = hpf(x[at], xHpfXV, xHpfYV)
        yHPF[at] = hpf(y[at], yHpfXV, yHpfYV)
        zHPF[at] = hpf(z[at], zHpfXV, zHpfYV)
        xMaxMin[at] = max(x) - min(x)
        yMaxMin[at] = max(y) - min(y)
        zMaxMin[at] = max(z) - min(z)
        val svTOTAt: Double = sv(x[at], y[at], z[at])
        svTOT[at] = svTOTAt
        val svDAt: Double = sv(xHPF[at], yHPF[at], zHPF[at])
        svD[at] = svDAt
        // Actualizar maxJerk
        if (svDAt > maxJerk) {
            maxJerk = svDAt
        }
        svMaxMin[at] = sv(xMaxMin[at], yMaxMin[at], zMaxMin[at])
        z2[at] = (svTOTAt * svTOTAt - svDAt * svDAt - G * G) / (2.0 * G)
        val svTOTBefore: Double = at(svTOT, at - 1, N)

        falling[at] = 0.0

        // Detección simple de caída libre
        if (FALLING_WAIST_SV_TOT <= svTOTBefore && svTOTAt < FALLING_WAIST_SV_TOT) {
            timeoutFalling = SPAN_FALLING
            falling[at] = 1.0
            fallStartTime = System.currentTimeMillis()
            minAccelerationBeforePeak = svTOTAt
            peakAcceleration = 0.0  // Reset para esta nueva caída
            captureOrientation(true)
            log(android.util.Log.DEBUG, "Fall detection started - svTOT: $svTOTAt")
        }

        impact[at] = 0.0
        if (-1 < timeoutFalling) {
            val svMaxMinAt: Double = svMaxMin[at]
            val z2At: Double = z2[at]

            // Guardar la aceleración máxima
            if (svTOTAt > peakAcceleration) {
                peakAcceleration = svTOTAt
            }

            // Detectar tipo de caída
            val fallType = detectFallType()
            val isLateralFall = fallType == FallType.LEFT || fallType == FallType.RIGHT

            // Umbrales más sensibles para caídas hacia atrás y laterales
            val impactThreshold = when (fallType) {
                FallType.LEFT, FallType.RIGHT -> 1.4  // Reducido de 1.6
                FallType.BACKWARD -> 1.3  // Más sensible para caídas hacia atrás
                FallType.FORWARD -> 1.5
                else -> IMPACT_WAIST_SV_TOT
            }

            val svdThreshold = when (fallType) {
                FallType.LEFT, FallType.RIGHT -> 1.1  // Reducido de 1.3
                FallType.BACKWARD -> 1.0  // Más sensible para caídas hacia atrás
                FallType.FORWARD -> 1.2
                else -> IMPACT_WAIST_SV_D
            }

            // Verificación lateral mejorada
            val lateralSpecificCheck = if (isLateralFall) {
                abs(xMaxMin[at]) > 1.5  // Reducido de 1.8
            } else {
                false
            }

            // Verificación específica para caídas hacia atrás
            val backwardSpecificCheck = if (fallType == FallType.BACKWARD) {
                abs(yMaxMin[at]) > 1.5 || (y[at] < -0.8 && svTOTAt > 1.3)
            } else {
                false
            }

            if (impactThreshold <= svTOTAt || svdThreshold <= svDAt ||
                IMPACT_WAIST_SV_MAX_MIN <= svMaxMinAt || IMPACT_WAIST_Z_2 <= z2At ||
                lateralSpecificCheck || backwardSpecificCheck) {

                timeoutImpact = SPAN_IMPACT
                impact[at] = 1.0
                log(android.util.Log.DEBUG, "Impact detected - Type: $fallType, svTOT: $svTOTAt, svD: $svDAt, Y: ${y[at]}")
            }
        }

        lying[at] = 0.0
        if (0 == timeoutImpact) {
            // Evitar alertas repetidas (mínimo 10 segundos entre alertas)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastFallAlertTime < 10000) {
                log(android.util.Log.DEBUG, "Skipping alert - too soon after last alert")
                return
            }

            captureOrientation(false)

            val orientationChanged = isOrientationChanged()
            val isHorizontal = isInHorizontalPosition()
            val isVertical = isInVerticalPosition()
            val fallType = detectFallType()
            val isPhoneLook = detectPhoneLookGesture()

            // Verificar patrón específico del bolsillo
            val isPocketPattern = checkPocketPattern()

            log(android.util.Log.DEBUG, "Fall check - Orient: $orientationChanged, Horiz: $isHorizontal, Vert: $isVertical, Type: $fallType, Peak: $peakAcceleration, PhoneLook: $isPhoneLook")

            // No confirmar caída si:
            // 1. El dispositivo terminó en posición vertical (bolsillo)
            // 2. Es el gesto de mirar el teléfono
            // 3. Es patrón de bolsillo
            if (isVertical || isPhoneLook || isPocketPattern) {
                log(android.util.Log.DEBUG, "Not a fall - Vertical: $isVertical, PhoneLook: $isPhoneLook, Pocket: $isPocketPattern")
                // Resetear variables
                peakAcceleration = 0.0
                consecutiveLowSVCount = 0
                maxJerk = 0.0
                return
            }

            // Confirmar caída con criterios ajustados para diferentes tipos
            val typeSpecificEvidence = when (fallType) {
                FallType.BACKWARD -> peakAcceleration > 1.8 || maxJerk > 1.0
                FallType.LEFT, FallType.RIGHT -> peakAcceleration > 1.9 || maxJerk > 1.1
                FallType.FORWARD -> peakAcceleration > 2.0 || maxJerk > 1.2
                else -> false
            }

            val hasStrongEvidence = (orientationChanged && peakAcceleration > 1.8) ||
                    (orientationChanged && typeSpecificEvidence) ||
                    (isHorizontal && maxJerk > 1.0) ||
                    (isHorizontal && peakAcceleration > 1.7) ||
                    (fallType != FallType.UNKNOWN && typeSpecificEvidence)

            if (hasStrongEvidence) {
                lying[at] = 1.0
                lastFallAlertTime = currentTime
                val context = this.context
                if (context != null) {
                    val fallReason = when {
                        isHorizontal -> "horizontal position"
                        fallType != FallType.UNKNOWN -> "fall type: $fallType"
                        else -> "orientation change with strong impact"
                    }
                    Guardian.say(context, android.util.Log.WARN, TAG, "Detected a fall - Reason: $fallReason, Peak: $peakAcceleration")
                    showFallAlert(context)
                }
            } else {
                log(android.util.Log.DEBUG, "Weak evidence for fall - skipping alert")
            }

            // Resetear variables
            peakAcceleration = 0.0
            consecutiveLowSVCount = 0
            maxJerk = 0.0
        }
    }

    // Android sampling is irregular, thus the signal is (linearly) resampled at 50 Hz
    private fun resample(postTime: Long, postX: Double, postY: Double, postZ: Double) {
        if (0L == anteTime) {
            regular = postTime + INTERVAL_MS
            return
        }
        while (regular < postTime) {
            val at: Int = buffers.position
            x[at] = linear(anteTime, anteX, postTime, postX, regular)
            y[at] = linear(anteTime, anteY, postTime, postY, regular)
            z[at] = linear(anteTime, anteZ, postTime, postZ, regular)
            process()
            buffers.position = (buffers.position + 1) % N
            regular += INTERVAL_MS
        }
    }

    private fun protect(postTime: Long, postX: Double, postY: Double, postZ: Double) {
        synchronized(buffers) {
            resample(postTime, postX, postY, postZ)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (Sensor.TYPE_ACCELEROMETER == sensor.type) {
            log(android.util.Log.INFO, "Accuracy of the accelerometer is now equal to $accuracy")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (Sensor.TYPE_ACCELEROMETER == event.sensor.type) {
            val postTime: Long = event.timestamp / 1000000
            val postX = event.values[0].toDouble() / SensorManager.STANDARD_GRAVITY
            val postY = event.values[1].toDouble() / SensorManager.STANDARD_GRAVITY
            val postZ = event.values[2].toDouble() / SensorManager.STANDARD_GRAVITY
            protect(postTime, postX, postY, postZ)
            anteTime = postTime
            anteX = postX
            anteY = postY
            anteZ = postZ
        }
    }

    private fun initiateSensor(context: Guardian) {
        this.context = context
        val manager: SensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager
        val sensor: Sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val vendor: String = sensor.vendor
        val name: String = sensor.name
        val delay: Int = sensor.minDelay
        val resolution: Float = sensor.resolution
        log(android.util.Log.INFO, "Sensor: $vendor, $name, $delay [us], $resolution")
        manager.registerListener(this, sensor, INTERVAL_MS * 1000)
    }

    private fun captureOrientation(before: Boolean) {
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        var count = 0.0
        for (i in 0 until SPAN_AVERAGING) {
            val xVal = at(xLPF, buffers.position - i, N)
            val yVal = at(yLPF, buffers.position - i, N)
            val zVal = at(zLPF, buffers.position - i, N)
            if (!xVal.isNaN() && !yVal.isNaN() && !zVal.isNaN()) {
                sumX += xVal
                sumY += yVal
                sumZ += zVal
                count++
            }
        }
        val orientation = Triple(sumX / count, sumY / count, sumZ / count)

        if (before) {
            beforeFallOrientation = orientation
            beforeInclinationAngle = calculateInclinationAngle(orientation)
        } else {
            afterFallOrientation = orientation
            afterInclinationAngle = calculateInclinationAngle(orientation)
        }
    }

    private fun calculateInclinationAngle(orientation: Triple<Double, Double, Double>): Double {
        // Calcular el ángulo respecto a la vertical
        val magnitude = Math.sqrt(
            orientation.first * orientation.first +
                    orientation.second * orientation.second +
                    orientation.third * orientation.third
        )

        // Evitar división por cero
        if (magnitude == 0.0) return 0.0

        // Calcular el ángulo en radianes (0 = vertical, π/2 = horizontal)
        return Math.acos(orientation.third / magnitude)
    }

    internal fun isOrientationChanged(): Boolean {
        val before = beforeFallOrientation
        val after = afterFallOrientation
        val beforeAngle = beforeInclinationAngle
        val afterAngle = afterInclinationAngle

        if (before == null || after == null || beforeAngle == null || afterAngle == null) {
            return false
        }

        // Primero verificar si es el gesto de mirar el teléfono
        if (detectPhoneLookGesture()) {
            log(android.util.Log.DEBUG, "Skipping orientation change - phone look gesture detected")
            return false
        }

        val dx = Math.abs(before.first - after.first)
        val dy = Math.abs(before.second - after.second)
        val dz = Math.abs(before.third - after.third)

        // Verificar cambio específico de Y (vertical a horizontal)
        val yVerticalToHorizontal = abs(before.second) > 0.8 && abs(after.second) < 0.5

        // Si es transición vertical a horizontal controlada, es probablemente mirar el teléfono
        if (yVerticalToHorizontal && peakAcceleration < 2.0) {
            log(android.util.Log.DEBUG, "Vertical to horizontal transition with low peak - likely phone look")
            return false
        }

        // Detectar tipo de cambio
        val isLateralChange = dx > dy && dx > dz
        val isForwardBackwardChange = dy > dx && dy > dz

        // Ajustar umbrales según el tipo de cambio - más sensibles
        val threshold = when {
            isLateralChange -> 0.25  // Más sensible para laterales
            isForwardBackwardChange -> 0.3  // Más sensible para atrás/adelante
            else -> ORIENTATION_THRESHOLD
        }

        // Calcular cambio en el ángulo de inclinación
        val angleChange = Math.abs(beforeAngle - afterAngle)
        val angleChangeDegrees = Math.toDegrees(angleChange)

        // Umbrales de ángulo más sensibles para diferentes tipos
        val angleThreshold = when {
            isLateralChange -> 15.0  // Más sensible para caídas laterales
            isForwardBackwardChange -> 20.0  // Más sensible para atrás/adelante
            else -> 30.0
        }

        // Calcular cambio total como magnitud vectorial
        val totalChange = Math.sqrt(dx*dx + dy*dy + dz*dz)

        // Verificar características adicionales de una caída real
        val hasImpactCharacteristics = peakAcceleration > 2.0 || maxJerk > 1.2  // Umbrales más sensibles
        val hasChaosPattern = checkChaosPattern()

        // Log para debugging
        log(android.util.Log.DEBUG, "Orientation change - dx: $dx, dy: $dy, dz: $dz")
        log(android.util.Log.DEBUG, "Total change: $totalChange, Angle change: $angleChangeDegrees degrees")
        log(android.util.Log.DEBUG, "Peak acceleration: $peakAcceleration, Has chaos: $hasChaosPattern")

        // Requiere menos evidencia para confirmar cambio de orientación en caídas
        return (totalChange > 0.5 && angleChangeDegrees > angleThreshold) ||
                (hasImpactCharacteristics && totalChange > 0.4) ||
                (hasChaosPattern && totalChange > 0.3) ||
                (angleChangeDegrees > angleThreshold * 1.5)  // Solo ángulo si es muy significativo
    }

    private fun isInVerticalPosition(): Boolean {
        // Verificar si el dispositivo está en posición vertical (típico del bolsillo)
        // Puede estar con USB arriba (Y positivo) o USB abajo (Y negativo)
        var sumZ = 0.0
        var sumX = 0.0
        var sumY = 0.0
        var count = 0

        for (i in 0 until SPAN_AVERAGING) {
            val zVal = at(zLPF, buffers.position - i, N)
            val xVal = at(xLPF, buffers.position - i, N)
            val yVal = at(yLPF, buffers.position - i, N)
            if (!zVal.isNaN() && !xVal.isNaN() && !yVal.isNaN()) {
                sumZ += zVal
                sumX += xVal
                sumY += yVal
                count++
            }
        }

        if (count == 0) return false

        val avgZ = sumZ / count
        val avgX = sumX / count
        val avgY = sumY / count

        // Usar valores absolutos para X y Z, pero mantener el signo de Y
        val absZ = abs(avgZ)
        val absX = abs(avgX)
        val absY = abs(avgY)  // Valor absoluto de Y para comparación

        // En posición vertical (dispositivo de pie):
        // - Y es dominante (gravedad actúa principalmente en Y, positivo o negativo)
        // - X y Z son bajos
        // Esto es típico cuando el teléfono está en el bolsillo
        val isVertical = absY > 0.75 && absX < 0.5 && absZ < 0.5

        if (isVertical) {
            val orientation = if (avgY > 0) "USB up" else "USB down"
            log(android.util.Log.DEBUG, "Device is in vertical position ($orientation) - Y: $avgY, X: $avgX, Z: $avgZ")
        }

        return isVertical
    }

    private fun checkPocketPattern(): Boolean {
        // Primero verificar si está en posición vertical
        if (isInVerticalPosition()) {
            log(android.util.Log.DEBUG, "Pocket pattern detected - device is vertical")
            return true
        }

        // Patrones típicos al meter en el bolsillo:
        // 1. Duración corta (menos de 600ms)
        // 2. No hay caída libre real prolongada
        // 3. Movimiento predominantemente en un eje
        // 4. Patrón de aceleración-desaceleración suave

        val duration = System.currentTimeMillis() - fallStartTime
        if (duration < 600) {
            log(android.util.Log.DEBUG, "Potential pocket pattern - short duration: $duration ms")
            return true
        }

        // Verificar si el movimiento fue predominantemente en un solo eje
        val at = buffers.position
        var xChange = 0.0
        var yChange = 0.0
        var zChange = 0.0

        for (i in 0 until 20) { // Últimos 400ms
            val idx = (at - i + N) % N
            xChange += abs(x[idx] - x[(idx - 1 + N) % N])
            yChange += abs(y[idx] - y[(idx - 1 + N) % N])
            zChange += abs(z[idx] - z[(idx - 1 + N) % N])
        }

        val totalChange = xChange + yChange + zChange
        val xRatio = xChange / totalChange
        val yRatio = yChange / totalChange
        val zRatio = zChange / totalChange

        // Si un eje domina más del 60%, es probablemente un movimiento controlado
        if (xRatio > 0.7 || yRatio > 0.7 || zRatio > 0.7) {
            log(android.util.Log.DEBUG, "Potential pocket pattern - single axis dominance")
            return true
        }

        return false
    }

    private fun showFallAlert(context: Context) {
        // Inicia la Activity de alerta por caída
        FallAlertActivity.start(context)
    }
}