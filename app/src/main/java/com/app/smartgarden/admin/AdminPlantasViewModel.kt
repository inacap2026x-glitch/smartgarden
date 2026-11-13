package com.app.smartgarden.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.app.smartgarden.home.Planta
import com.app.smartgarden.registros.Registro
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class AdminPlantasUiState(
    val emailUsuario: String = "",
    val plantas: List<Planta> = emptyList(),
    val cargando: Boolean = true,
    val mensajeError: String? = null
)

class AdminPlantasViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null
    private var ownerUid: String = ""

    var uiState by mutableStateOf(AdminPlantasUiState())
        private set

    // Escucha en tiempo real las plantas de OTRO usuario (solo permitido a admins por las reglas)
    fun cargar(uid: String) {
        ownerUid = uid
        listener?.remove()
        uiState = AdminPlantasUiState(cargando = true)

        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { doc ->
                uiState = uiState.copy(emailUsuario = doc.getString("email") ?: "")
            }

        listener = db.collection("usuarios").document(uid)
            .collection("plantas")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    uiState = uiState.copy(cargando = false, mensajeError = error.localizedMessage)
                    return@addSnapshotListener
                }
                val lista = snapshot?.documents?.map { doc ->
                    Planta(
                        id = doc.id,
                        nombre = doc.getString("nombre") ?: "Planta sin nombre",
                        descripcion = doc.getString("descripcion") ?: "",
                        temperatura = doc.getDouble("temperatura") ?: 0.0,
                        humedad = doc.getDouble("humedad") ?: 0.0,
                        bombaEncendida = doc.getBoolean("bombaEncendida") ?: false,
                        ventiladorEncendido = doc.getBoolean("ventiladorEncendido") ?: false,
                        modoAutomatico = doc.getBoolean("modoAutomatico") ?: true,
                        humedadUmbral = doc.getDouble("humedadUmbral") ?: 40.0,
                        temperaturaUmbral = doc.getDouble("temperaturaUmbral") ?: 28.0,
                        sensorId = doc.getString("sensorId"),
                        ultimaActualizacion = doc.getLong("ultimaActualizacion") ?: 0L,
                        alertaUmbral = doc.getDouble("alertaUmbral") ?: 35.0,
                        alertaEstado = doc.getString("alertaEstado") ?: "ninguna",
                        alertaInicio = doc.getLong("alertaInicio") ?: 0L,
                        ventiladorEmergencia = doc.getBoolean("ventiladorEmergencia") ?: false
                    )
                } ?: emptyList()
                uiState = uiState.copy(cargando = false, plantas = lista, mensajeError = null)
            }
    }

    // Elimina la planta y, si tenía un sensor asignado, lo deja libre para reasignarlo
    fun eliminarPlanta(planta: Planta) {
        planta.sensorId?.let { sensorId ->
            db.collection("sensores").document(sensorId)
                .update(mapOf("asignado" to false, "plantaId" to null))
        }
        db.collection("usuarios").document(ownerUid)
            .collection("plantas").document(planta.id)
            .delete()
            .addOnSuccessListener {
                Registro.log(
                    "PLANTA_ELIMINADA",
                    "Planta \"${planta.nombre}\" eliminada por un administrador",
                    plantaId = planta.id,
                    uidAfectado = ownerUid,
                    emailAfectado = uiState.emailUsuario
                )
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(mensajeError = e.localizedMessage)
            }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}
