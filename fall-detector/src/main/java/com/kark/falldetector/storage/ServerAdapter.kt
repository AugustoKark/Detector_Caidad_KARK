package com.kark.falldetector.storage

import android.content.Context
import com.kark.falldetector.utils.Log



object ServerAdapter {
    private val TAG = ServerAdapter::class.java.name

    fun reportFallEvent(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        batteryLevel: Int
    ) {
        // No enviamos datos a servidor externo para cumplir con políticas de privacidad
        Log.i(TAG, "Fall event detected - no external data transmission")
    }

    fun reportFallAlertCancelled(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        batteryLevel: Int
    ) {
        // No enviamos datos a servidor externo para cumplir con políticas de privacidad
        Log.i(TAG, "Fall alert cancelled - no external data transmission")
    }

    fun uploadFile(context: Context, filePath: String) {
        // No subimos archivos a servidor externo para cumplir con políticas de privacidad
        Log.i(TAG, "File upload blocked - no external data transmission")
    }
}