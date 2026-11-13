package com.app.smartgarden.sensores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.app.smartgarden.registros.Registro
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class Sensor(
    val id: String = "",
    val codigo: String = "",
    val asignado: Boolean = false,
    val uidPropietario: String? = null,
    val plantaId: String? = null,
    val fechaCreacion: Long = 0L
)

data class SensorUiState(
    val sensoresDisponibles: List<Sensor> = emptyList(),
    val cargando: Boolean = false,
    val mensajeError: String? = null
)

class SensorViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    var uiState by mutableStateOf(SensorUiState())
        private set

    // Simula el "escaneo": lista los sensores que existen pero no están
    // asignados a ninguna planta todavía.
    fun observarSensoresDisponibles() {
        listener?.remove()
        uiState = uiState.copy(cargando = true)

        listener = db.collection("sensores")
            .whereEqualTo("asignado", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    uiState = uiState.copy(cargando = false, mensajeError = error.localizedMessage)
                    return@addSnapshotListener
                }

                val lista = snapshot?.documents?.map { doc ->
                    Sensor(
                        id = doc.id,
                        codigo = doc.getString("codigo") ?: "",
                        asignado = doc.getBoolean("asignado") ?: false,
                        uidPropietario = doc.getString("uidPropietario"),
                        plantaId = doc.getString("plantaId"),
                        fechaCreacion = doc.getLong("fechaCreacion") ?: 0L
                    )
                } ?: emptyList()

                uiState = uiState.copy(cargando = false, sensoresDisponibles = lista)
            }
    }

    // Simula "registrar" un sensor físico nuevo (tú defines el código,
    // como si fuera el ID de fábrica del ESP32)
    fun crearSensor(codigo: String) {
        val uid = auth.currentUser?.uid ?: return
        if (codigo.isBlank()) return

        val nuevo = hashMapOf(
            "codigo" to codigo,
            "asignado" to false,
            "uidPropietario" to uid,
            "plantaId" to null,
            "fechaCreacion" to System.currentTimeMillis()
        )

        db.collection("sensores").add(nuevo)
            .addOnSuccessListener { Registro.log("SENSOR", "Sensor \"$codigo\" registrado") }
            .addOnFailureListener { e ->
                uiState = uiState.copy(mensajeError = e.localizedMessage)
            }
    }

    // Si un administrador gestiona la planta de otro usuario, aquí va el uid del dueño
    var ownerUid: String? = null

    // Vincula un sensor a una planta: actualiza AMBOS documentos
    fun asignarSensorAPlanta(sensorId: String, plantaId: String) {
        val uid = ownerUid ?: auth.currentUser?.uid ?: return

        db.collection("sensores").document(sensorId)
            .update(mapOf("asignado" to true, "plantaId" to plantaId, "uidDuenoPlanta" to uid))

        db.collection("usuarios").document(uid)
            .collection("plantas").document(plantaId)
            .update("sensorId", sensorId)
            .addOnSuccessListener {
                Registro.log("SENSOR", "Sensor $sensorId asignado a una planta", plantaId = plantaId, uidAfectado = ownerUid)
            }
    }

    // Desvincula el sensor de una planta (lo libera para poder asignarlo a otra)
    fun liberarSensor(sensorId: String, plantaId: String) {
        val uid = ownerUid ?: auth.currentUser?.uid ?: return

        db.collection("sensores").document(sensorId)
            .update(mapOf("asignado" to false, "plantaId" to null, "uidDuenoPlanta" to null))

        db.collection("usuarios").document(uid)
            .collection("plantas").document(plantaId)
            .update("sensorId", null)
            .addOnSuccessListener {
                Registro.log("SENSOR", "Sensor $sensorId liberado de una planta", plantaId = plantaId, uidAfectado = ownerUid)
            }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}