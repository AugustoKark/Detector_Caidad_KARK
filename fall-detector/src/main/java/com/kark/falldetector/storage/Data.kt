package com.kark.falldetector.storage

import com.kark.falldetector.core.Guardian

class Data(private val guardian: Guardian) {

    internal fun dispatch(type: Int, timestamp: Long, values: FloatArray) {
        // No almacenamos datos de sensores para cumplir con políticas de privacidad
    }

    internal fun log(priority: Int, tag: String, entry: String) {
        // No almacenamos logs del sistema para cumplir con políticas de privacidad
    }
}