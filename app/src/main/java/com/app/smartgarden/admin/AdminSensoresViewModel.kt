package com.app.smartgarden.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.app.smartgarden.registros.Registro
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class SensorAdmin(
    val id: String = "",
    val codigo: String = "",
    val asignado: Boolean = false,
    val plantaId: String? = null,
    val uidDuenoPlanta: String? = null,
    val fechaCreacion: Long = 0L
)

data class AdminSensoresUiState(
    val sensores: List<SensorAdmin> = emptyList(),
    val cargando: Boolean = true,
    val mensajeError: String? = null
)

class AdminSensoresViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    var uiState by mutableStateOf(AdminSensoresUiState())
        private set

    init {
        // Todos los sensores del sistema, de todos los usuarios
        listener = db.collection("sensores").addSnapshotListener { snap, error ->
            if (error != null) {
                uiState = uiState.copy(cargando = false, mensajeError = error.localizedMessage)
                return@addSnapshotListener
            }
            val lista = snap?.documents?.map { d ->
                SensorAdmin(
                    id = d.id,
                    codigo = d.getString("codigo") ?: "(sin código)",
                    asignado = d.getBoolean("asignado") ?: false,
                    plantaId = d.getString("plantaId"),
                    // Si el sensor es antiguo y no guarda el dueño de la planta, se usa el creador
                    uidDuenoPlanta = d.getString("uidDuenoPlanta") ?: d.getString("uidPropietario"),
                    fechaCreacion = d.getLong("fechaCreacion") ?: 0L
                )
            }?.sortedBy { it.codigo.lowercase() } ?: emptyList()
            uiState = uiState.copy(cargando = false, sensores = lista, mensajeError = null)
        }
    }

    // Registra un sensor nuevo (equivale a dar de alta un ESP32 de fábrica)
    fun crear(codigo: String) {
        if (codigo.isBlank()) return
        db.collection("sensores").add(
            hashMapOf(
                "codigo" to codigo.trim(),
                "asignado" to false,
                "uidPropietario" to null,
                "plantaId" to null,
                "uidDuenoPlanta" to null,
                "fechaCreacion" to System.currentTimeMillis()
            )
        )
            .addOnSuccessListener { Registro.log("SENSOR", "Sensor \"${codigo.trim()}\" registrado por un administrador") }
            .addOnFailureListener { e -> uiState = uiState.copy(mensajeError = e.localizedMessage) }
    }

    // Desvincula el sensor de su planta y lo deja disponible
    fun liberar(sensor: SensorAdmin) {
        desvincularPlanta(sensor)
        db.collection("sensores").document(sensor.id)
            .update(mapOf("asignado" to false, "plantaId" to null, "uidDuenoPlanta" to null))
            .addOnSuccessListener {
                Registro.log(
                    "SENSOR",
                    "Sensor \"${sensor.codigo}\" liberado por un administrador",
                    plantaId = sensor.plantaId,
                    uidAfectado = sensor.uidDuenoPlanta
                )
            }
            .addOnFailureListener { e -> uiState = uiState.copy(mensajeError = e.localizedMessage) }
    }

    // Elimina el sensor del sistema (si estaba asignado, también limpia la planta)
    fun eliminar(sensor: SensorAdmin) {
        desvincularPlanta(sensor)
        db.collection("sensores").document(sensor.id).delete()
            .addOnSuccessListener {
                Registro.log("SENSOR", "Sensor \"${sensor.codigo}\" eliminado por un administrador")
            }
            .addOnFailureListener { e -> uiState = uiState.copy(mensajeError = e.localizedMessage) }
    }

    private fun desvincularPlanta(sensor: SensorAdmin) {
        val plantaId = sensor.plantaId ?: return
        val dueno = sensor.uidDuenoPlanta ?: return
        db.collection("usuarios").document(dueno)
            .collection("plantas").document(plantaId)
            .update("sensorId", null)
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
