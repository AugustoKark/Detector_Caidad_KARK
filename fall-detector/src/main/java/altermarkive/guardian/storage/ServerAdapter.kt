package altermarkive.guardian.storage



import android.content.Context
import android.os.Build
import altermarkive.guardian.detection.Detector
import altermarkive.guardian.sensors.Report
import altermarkive.guardian.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.HttpURLConnection

/**
 * Adaptador para enviar datos al servidor Python
 */
class ServerAdapter private constructor() {
    companion object {
        private val TAG = ServerAdapter::class.java.name

        private const val SERVER_URL = "http://192.168.100.210:5000"  // Cambia a tu dominio o IP
        private var instance: ServerAdapter? = null
        private const val BATCH_SIZE = 50  // Número máximo de lecturas para enviar juntas
        private val accelerometerQueue = ArrayList<AccelerometerReading>()

        // Cliente HTTP para subir archivos
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        @Synchronized
        fun getInstance(): ServerAdapter {
            if (instance == null) {
                instance = ServerAdapter()
            }
            return instance!!
        }

        fun registerDevice(context: Context) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val deviceId = Report.id(context)
                    val jsonObject = JSONObject().apply {
                        put("device_id", deviceId)
                        put("user_name", "Usuario")  // Idealmente obtener del perfil
                        put("user_age", 65)  // Idealmente obtener del perfil
                        put("emergency_contact", "123456789")  // Idealmente obtener del perfil
                    }

                    val url = URL("$SERVER_URL/api/device/register")
//                    with(url.openConnection() as HttpsURLConnection) {
                    with(url.openConnection() as HttpURLConnection) {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        outputStream.write(jsonObject.toString().toByteArray())

                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            Log.i(TAG, "Dispositivo registrado correctamente")
                        } else {
                            Log.e(TAG, "Error al registrar dispositivo: $responseCode - $responseMessage")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error en registro del dispositivo: ${e.message}")
                }
            }
        }

        fun addAccelerometerReading(deviceId: String, timestamp: Long, x: Float, y: Float, z: Float) {
            synchronized(accelerometerQueue) {
                accelerometerQueue.add(AccelerometerReading(deviceId, timestamp, x, y, z))

                // Si alcanzamos BATCH_SIZE, enviamos inmediatamente
                if (accelerometerQueue.size >= BATCH_SIZE) {
                    sendAccelerometerBatch()
                }
            }
        }

        fun sendAccelerometerBatch() {
            if (accelerometerQueue.isEmpty()) return

            val readings: List<AccelerometerReading>
            synchronized(accelerometerQueue) {
                readings = ArrayList(accelerometerQueue)
                accelerometerQueue.clear()
            }

            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val jsonArray = JSONArray()
                    readings.forEach { reading ->
                        jsonArray.put(JSONObject().apply {
                            put("timestamp", reading.timestamp)
                            put("x", reading.x)
                            put("y", reading.y)
                            put("z", reading.z)
                        })
                    }

                    val jsonObject = JSONObject().apply {
                        put("device_id", readings[0].deviceId)
                        put("readings", jsonArray)
                    }

                    val url = URL("$SERVER_URL/api/data/accelerometer")
                    with(url.openConnection() as HttpURLConnection) {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        outputStream.write(jsonObject.toString().toByteArray())

                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            Log.i(TAG, "Datos de acelerómetro enviados: ${readings.size}")
                        } else {
                            Log.e(TAG, "Error al enviar datos: $responseCode - $responseMessage")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al enviar datos de acelerómetro: ${e.message}")
                    // Reinsertar las lecturas para intentar nuevamente
                    synchronized(accelerometerQueue) {
                        accelerometerQueue.addAll(0, readings)
                    }
                }
            }
        }

        fun reportFallEvent(context: Context, latitude: Double?, longitude: Double?, batteryLevel: Int) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val deviceId = Report.id(context)
                    val jsonObject = JSONObject().apply {
                        put("device_id", deviceId)
                        put("timestamp", System.currentTimeMillis())
                        put("latitude", latitude)
                        put("longitude", longitude)
                        put("battery_level", batteryLevel)
                        put("event_type", "fall")
                        put("description", "Caída detectada automaticamente")
                    }

                    val url = URL("$SERVER_URL/api/event/fall")
                    with(url.openConnection() as HttpURLConnection) {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        doOutput = true
                        outputStream.write(jsonObject.toString().toByteArray())

                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            Log.i(TAG, "Evento de caída reportado")
                        } else {
                            Log.e(TAG, "Error al reportar caída: $responseCode - $responseMessage")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al reportar caída: ${e.message}")
                }
            }
        }

        fun uploadFile(context: Context, filePath: String) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        Log.e(TAG, "Archivo no existe: $filePath")
                        return@launch
                    }

                    val deviceId = Report.id(context)

                    // Crear multipart request
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("device_id", deviceId)
                        .addFormDataPart(
                            "file",
                            file.name,
                            file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                        )
                        .build()

                    val request = Request.Builder()
                        .url("$SERVER_URL/api/upload/file")
                        .post(requestBody)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.i(TAG, "Archivo subido correctamente: ${file.name}")
                        } else {
                            Log.e(TAG, "Error al subir archivo: ${response.code} - ${response.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error durante la subida del archivo: ${e.message}")
                }
            }
        }

        fun initializeScheduledUploads(context: Context) {
            val scheduler = Executors.newScheduledThreadPool(1)

            // Programar envío de datos de acelerómetro pendientes cada 15 minutos
            scheduler.scheduleAtFixedRate({
                sendAccelerometerBatch()
            }, 15, 15, TimeUnit.MINUTES)

            // Registrar el dispositivo al iniciar
            registerDevice(context)
        }
    }

    // Clase para almacenar lecturas del acelerómetro
    data class AccelerometerReading(
        val deviceId: String,
        val timestamp: Long,
        val x: Float,
        val y: Float,
        val z: Float
    )
}