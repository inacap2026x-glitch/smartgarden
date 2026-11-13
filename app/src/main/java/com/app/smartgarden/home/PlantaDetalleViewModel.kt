package com.app.smartgarden.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.app.smartgarden.registros.Registro
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class PlantaDetalleUiState(
    val planta: Planta? = null,
    val cargando: Boolean = true,
    val mensajeError: String? = null
)

class PlantaDetalleViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    var uiState by mutableStateOf(PlantaDetalleUiState())
        private set

    // Si un administrador abre la planta de otro usuario, aquí va el uid del dueño.
    // Si es null, se usa el usuario que inició sesión.
    var ownerUid: String? = null
    private fun uidDueno(): String? = ownerUid ?: auth.currentUser?.uid

    // Anota en la bitácora una acción sobre la planta abierta
    private fun registrar(tipo: String, descripcion: String, plantaId: String) {
        val nombre = uiState.planta?.nombre ?: "Planta"
        Registro.log(tipo, "$nombre: $descripcion", plantaId = plantaId, uidAfectado = ownerUid)
    }

    fun observarPlanta(plantaId: String) {
        val uid = uidDueno() ?: return

        listener?.remove()
        uiState = uiState.copy(cargando = true)

        listener = db.collection("usuarios").document(uid)
            .collection("plantas").document(plantaId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    uiState = uiState.copy(cargando = false, mensajeError = error.localizedMessage)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val planta = Planta(
                        id = snapshot.id,
                        nombre = snapshot.getString("nombre") ?: "",
                        descripcion = snapshot.getString("descripcion") ?: "",
                        temperatura = snapshot.getDouble("temperatura") ?: 0.0,
                        humedad = snapshot.getDouble("humedad") ?: 0.0,
                        bombaEncendida = snapshot.getBoolean("bombaEncendida") ?: false,
                        ventiladorEncendido = snapshot.getBoolean("ventiladorEncendido") ?: false,
                        modoAutomatico = snapshot.getBoolean("modoAutomatico") ?: true,
                        humedadUmbral = snapshot.getDouble("humedadUmbral") ?: 40.0,
                        temperaturaUmbral = snapshot.getDouble("temperaturaUmbral") ?: 28.0,
                        sensorId = snapshot.getString("sensorId"),
                        ultimaActualizacion = snapshot.getLong("ultimaActualizacion") ?: 0L,
                        alertaUmbral = snapshot.getDouble("alertaUmbral") ?: 35.0,
                        alertaEstado = snapshot.getString("alertaEstado") ?: "ninguna",
                        alertaInicio = snapshot.getLong("alertaInicio") ?: 0L,
                        ventiladorEmergencia = snapshot.getBoolean("ventiladorEmergencia") ?: false
                    )
                    uiState = uiState.copy(cargando = false, planta = planta, mensajeError = null)
                }
            }
    }

    // ---------- CONTROL MANUAL ----------
    fun cambiarModoAutomatico(plantaId: String, activo: Boolean) {
        val uid = uidDueno() ?: return
        db.collection("usuarios").document(uid)
            .collection("plantas").document(plantaId)
            .update("modoAutomatico", activo)
            .addOnSuccessListener { registrar("MODO", "Modo ${if (activo) "automático activado" else "manual activado"}", plantaId) }
    }

    fun regarAhora(plantaId: String) {
        val uid = uidDueno() ?: return
        db.collection("usuarios").document(uid)
            .collection("plantas").document(plantaId)
            .update(mapOf("bombaEncendida" to true, "ultimaActualizacion" to System.currentTimeMillis()))
            .addOnSuccessListener { registrar("ACCION_MANUAL", "Riego manual iniciado", plantaId) }
    }

    fun detenerRiego(plantaId: String) {
        val uid = uidDueno() ?: return
        db.collection("usuarios").document(uid)
            .collection("plantas").document(plantaId)
            .update("bombaEncendida", false)
            .addOnSuccessListener { registrar("ACCION_MANUAL", "Riego detenido manualmente", plantaId) }
    }

    fun encenderVentilador(plantaId: String) {
        val uid = uidDueno() ?: return
        db.collection("usuarios").document(uid)
            .collection("plantas").document(plantaId)
            .update(mapOf("ventiladorEncendido" to true, "ultimaActualizacion" to System.currentTimeMillis()))
            .addOnSuccessListener { registrar("ACCION_MANUAL", "Ventilador encendido manualmente", plantaId) }
    }

    fun apagarVentilador(plantaId: String) {
        val uid = uidDueno() ?: return
        db.collection("usuarios").document(uid)
            .collection("plantas").document(plantaId)
            .update("ventiladorEncendido", false)
            .addOnSuccessListener { registrar("ACCION_MANUAL", "Ventilador apagado manualmente", plantaId) }
    }

    fun actualizarUmbrales(plantaId: String, humedadUmbral: Double, temperaturaUmbral: Double, alertaUmbral: Double) {
        val uid = uidDueno() ?: return
        db.collection("usuarios").document(uid)
            .collection("plantas").document(plantaId)
            .update(
                mapOf(
                    "humedadUmbral" to humedadUmbral,
                    "temperaturaUmbral" to temperaturaUmbral,
                    "alertaUmbral" to alertaUmbral
                )
            )
            .addOnSuccessListener {
                registrar("UMBRALES", "Umbrales: humedad %.0f%%, ventilador %.0f°C, alerta %.0f°C".format(humedadUmbral, temperaturaUmbral, alertaUmbral), plantaId)
            }
    }

    // El usuario atiende la alerta de temperatura: enciende el ventilador y evita la emergencia
    fun atenderAlerta(plantaId: String) {
        val uid = uidDueno() ?: return
        db.collection("usuarios").document(uid)
            .collection("plantas").document(plantaId)
            .update(
                mapOf(
                    "alertaEstado" to "atendida",
                    "ventiladorEmergencia" to false,
                    "ventiladorEncendido" to true,
                    "ultimaActualizacion" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                registrar("ALERTA", "Alerta de temperatura atendida por el usuario (ventilador encendido)", plantaId)
            }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
    }
}