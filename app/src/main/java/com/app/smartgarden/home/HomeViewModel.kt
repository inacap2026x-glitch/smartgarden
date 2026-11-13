package com.app.smartgarden.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.smartgarden.registros.Registro
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

data class Planta(
    val id: String = "",
    val nombre: String = "",
    val descripcion: String = "",
    val temperatura: Double = 0.0,
    val humedad: Double = 0.0,
    val bombaEncendida: Boolean = false,
    val ventiladorEncendido: Boolean = false,
    val modoAutomatico: Boolean = true,
    val humedadUmbral: Double = 40.0,
    val temperaturaUmbral: Double = 28.0,
    val sensorId: String? = null,
    val ultimaActualizacion: Long = 0L,
    // Alerta de temperatura: umbral configurable, estado y momento en que empezó
    val alertaUmbral: Double = 35.0,
    val alertaEstado: String = "ninguna",   // ninguna | pendiente | atendida | emergencia
    val alertaInicio: Long = 0L,
    val ventiladorEmergencia: Boolean = false
)

data class HomeUiState(
    val plantas: List<Planta> = emptyList(),
    val cargando: Boolean = false,
    val mensajeError: String? = null,
    val esAdmin: Boolean = false,
    val cuentaDesactivada: Boolean = false
)

class HomeViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var uiState by mutableStateOf(HomeUiState())
        private set

    init {
        cargarPlantas()
        cargarRol()
    }

    private var listenerPerfil: ListenerRegistration? = null

    // Escucha el perfil del usuario en tiempo real:
    // - "rol": para mostrar el acceso al panel de administración
    // - "activo": si un admin desactiva la cuenta, la sesión se cierra al instante
    private fun cargarRol() {
        val uid = auth.currentUser?.uid ?: return
        listenerPerfil = db.collection("usuarios").document(uid)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) return@addSnapshotListener
                uiState = uiState.copy(
                    esAdmin = doc.getString("rol") == "admin",
                    cuentaDesactivada = doc.getBoolean("activo") == false
                )
            }
    }

    override fun onCleared() {
        super.onCleared()
        listenerPerfil?.remove()
    }

    fun cargarPlantas() {
        val uid = auth.currentUser?.uid ?: return

        uiState = uiState.copy(cargando = true)

        // Listener en tiempo real: cada vez que Firestore cambia, la UI se actualiza
        db.collection("usuarios")
            .document(uid)
            .collection("plantas")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    uiState = uiState.copy(
                        cargando = false,
                        mensajeError = error.localizedMessage
                    )
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

                uiState = uiState.copy(cargando = false, plantas = lista)
            }
    }

    fun agregarPlanta(nombre: String, descripcion: String) {
        val uid = auth.currentUser?.uid ?: return
        if (nombre.isBlank()) return

        viewModelScope.launch {
            val nuevaPlanta = hashMapOf(
                "nombre" to nombre,
                "descripcion" to descripcion,
                "temperatura" to 25.0,              // valor inicial simulado razonable
                "humedad" to 60.0,                   // valor inicial simulado razonable
                "bombaEncendida" to false,
                "ventiladorEncendido" to false,
                "modoAutomatico" to true,
                "humedadUmbral" to 40.0,
                "temperaturaUmbral" to 28.0,
                "alertaUmbral" to 35.0,
                "alertaEstado" to "ninguna",
                "alertaInicio" to 0L,
                "ventiladorEmergencia" to false,
                "sensorId" to null,
                "ultimaActualizacion" to System.currentTimeMillis()
            )

            db.collection("usuarios")
                .document(uid)
                .collection("plantas")
                .add(nuevaPlanta)
                .addOnSuccessListener { ref ->
                    Registro.log("PLANTA_CREADA", "Planta \"$nombre\" creada", plantaId = ref.id)
                }
                .addOnFailureListener { e ->
                    uiState = uiState.copy(mensajeError = e.localizedMessage)
                }
        }
    }

    fun cerrarSesion() {
        auth.signOut()
    }
}