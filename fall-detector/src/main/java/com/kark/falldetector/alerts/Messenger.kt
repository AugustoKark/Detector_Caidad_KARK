package com.kark.falldetector.alerts

import com.kark.falldetector.utils.Log
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
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
//                val smsManager = SmsManager.getDefault()
//                smsManager.sendTextMessage(recipient, null, message, null, null)
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)


//                Toast.makeText(
//                    context,
//                    "Se ha enviado un SMS de emergencia",
//                    Toast.LENGTH_LONG
//                ).show()
            } else {
                Log.e(TAG, "No recipient specified for SMS")
//                Toast.makeText(
//                    context,
//                    "Error: No se ha configurado un contacto de emergencia",
//                    Toast.LENGTH_LONG
//                ).show()
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to send SMS: ${exception.message}")
//            Toast.makeText(
//                context,
//                "Error al enviar SMS: ${exception.message}",
//                Toast.LENGTH_LONG
//            ).show()
        }
    }

    /**
     * Realiza una llamada al contacto especificado
     * Usa TelecomManager para Android 8+ (mejor soporte en background)
     * Fallback a Intent.ACTION_CALL para versiones anteriores
     * @param context Contexto de la aplicación
     * @param recipient Número de teléfono del destinatario
     */
    fun call(context: Context, recipient: String) {
        try {
            if (recipient.isNotBlank()) {
                Log.i(TAG, "Making emergency call to $recipient")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8+ usar TelecomManager (funciona mejor desde background)
                    callWithTelecomManager(context, recipient)
                } else {
                    // Fallback para Android 7 y anteriores
                    callWithIntent(context, recipient)
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

    /**
     * Realiza llamada usando TelecomManager (Android 8+)
     * Esta API es preferida para llamadas desde background/servicios
     */
    private fun callWithTelecomManager(context: Context, recipient: String) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val uri = Uri.fromParts("tel", recipient, null)

            Log.i(TAG, "Using TelecomManager.placeCall() for background call")
            telecomManager.placeCall(uri, Bundle.EMPTY)

            Log.i(TAG, "TelecomManager.placeCall() executed successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException with TelecomManager: ${e.message}, falling back to Intent")
            callWithIntent(context, recipient)
        } catch (e: Exception) {
            Log.e(TAG, "TelecomManager failed: ${e.message}, falling back to Intent")
            callWithIntent(context, recipient)
        }
    }

    /**
     * Realiza llamada usando Intent (método tradicional)
     * Fallback para cuando TelecomManager no funciona
     */
    private fun callWithIntent(context: Context, recipient: String) {
        Log.i(TAG, "Using Intent.ACTION_CALL for call")

        val callIntent = Intent(Intent.ACTION_CALL)
        callIntent.data = Uri.parse("tel:$recipient")
        callIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        if (callIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(callIntent)
            Log.i(TAG, "Intent.ACTION_CALL started successfully")
        } else {
            Log.e(TAG, "No activity found to handle call intent")
            Toast.makeText(
                context,
                "Error: No se puede realizar la llamada",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}