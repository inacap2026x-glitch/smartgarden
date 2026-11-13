package com.app.smartgarden.registros

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Bitácora central de la app (colección "registros" en Firestore).
 * Cada evento guarda: quién lo hizo (actor), a quién afecta (si es otro usuario),
 * qué tipo de evento es, una descripción legible y la hora.
 *
 * Tipos usados: CUENTA_CREADA, LOGIN, PLANTA_CREADA, PLANTA_ELIMINADA,
 * ACCION_MANUAL, UMBRALES, MODO, SENSOR, ROL, ACTUADOR_AUTO
 */
object Registro {

    // Evento generado por el simulador de dispositivos (sin usuario: usa la instancia del dispositivo)
    fun logSistema(
        db: FirebaseFirestore,
        descripcion: String,
        plantaId: String? = null,
        uidAfectado: String? = null,
        tipo: String = "ACTUADOR_AUTO"
    ) {
        db.collection("registros").add(
            hashMapOf<String, Any?>(
                "tipo" to tipo,
                "descripcion" to descripcion,
                "uidUsuario" to "sistema",
                "emailUsuario" to "Sistema (simulador)",
                "uidAfectado" to uidAfectado,
                "emailAfectado" to null,
                "plantaId" to plantaId,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    fun log(
        tipo: String,
        descripcion: String,
        plantaId: String? = null,
        uidAfectado: String? = null,
        emailAfectado: String? = null,
        uid: String? = null,
        email: String? = null,
        onListo: (() -> Unit)? = null
    ) {
        val auth = FirebaseAuth.getInstance()
        val evento = hashMapOf<String, Any?>(
            "tipo" to tipo,
            "descripcion" to descripcion,
            "uidUsuario" to (uid ?: auth.currentUser?.uid),
            "emailUsuario" to (email ?: auth.currentUser?.email),
            "uidAfectado" to uidAfectado,
            "emailAfectado" to emailAfectado,
            "plantaId" to plantaId,
            "timestamp" to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance().collection("registros").add(evento)
            .addOnCompleteListener { onListo?.invoke() }
    }
}
