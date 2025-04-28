package altermarkive.guardian.alerts

import altermarkive.guardian.utils.Log
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.widget.Toast

object Messenger {
    private val TAG = Messenger::class.java.name

    /**
     * Envía un SMS al contacto especificado
     * @param context Contexto de la aplicación
     * @param recipient Número de teléfono del destinatario
     * @param message Mensaje a enviar
     */
    fun sms(context: Context, recipient: String, message: String) {
        try {
            if (recipient.isNotBlank()) {
                Log.i(TAG, "Sending SMS to $recipient: $message")

                // Para API 30 y anteriores, usar el método tradicional
                @Suppress("DEPRECATION")
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(recipient, null, message, null, null)

                Toast.makeText(
                    context,
                    "Se ha enviado un SMS de emergencia",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Log.e(TAG, "No recipient specified for SMS")
                Toast.makeText(
                    context,
                    "Error: No se ha configurado un contacto de emergencia",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to send SMS: ${exception.message}")
            Toast.makeText(
                context,
                "Error al enviar SMS: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Realiza una llamada al contacto especificado
     * @param context Contexto de la aplicación
     * @param recipient Número de teléfono del destinatario
     */
    fun call(context: Context, recipient: String) {
        try {
            if (recipient.isNotBlank()) {
                Log.i(TAG, "Making emergency call to $recipient")

                // Crear un intent para llamar
                val callIntent = Intent(Intent.ACTION_CALL)
                callIntent.data = Uri.parse("tel:$recipient")
                callIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                // Verificar si se puede realizar la llamada
                if (callIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(callIntent)

                    Toast.makeText(
                        context,
                        "Realizando llamada de emergencia",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Log.e(TAG, "No activity found to handle call intent")
                    Toast.makeText(
                        context,
                        "Error: No se puede realizar la llamada",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Log.e(TAG, "No recipient specified for call")
                Toast.makeText(
                    context,
                    "Error: No se ha configurado un contacto de emergencia",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to make call: ${exception.message}")
            Toast.makeText(
                context,
                "Error al realizar llamada: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}