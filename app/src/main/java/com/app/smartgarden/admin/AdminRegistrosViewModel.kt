package com.app.smartgarden.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

data class EventoRegistro(
    val id: String = "",
    val tipo: String = "",
    val descripcion: String = "",
    val emailUsuario: String = "",
    val emailAfectado: String? = null,
    val timestamp: Long = 0L
)

data class AdminRegistrosUiState(
    val registros: List<EventoRegistro> = emptyList(),
    val totalCuentas: Int? = null,
    val totalAdmins: Int? = null,
    val totalPlantas: Int? = null,      // null = no disponible (aún sin permiso o cargando)
    val cargando: Boolean = true,
    val filtro: String? = null,         // null = todos los tipos
    val mensajeError: String? = null
)

class AdminRegistrosViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()

    var uiState by mutableStateOf(AdminRegistrosUiState())
        private set

    init {
        // Últimos 300 eventos, del más reciente al más antiguo
        listeners += db.collection("registros")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(300)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    uiState = uiState.copy(cargando = false, mensajeError = error.localizedMessage)
                    return@addSnapshotListener
                }
                val lista = snap?.documents?.map { d ->
                    EventoRegistro(
                        id = d.id,
                        tipo = d.getString("tipo") ?: "",
                        descripcion = d.getString("descripcion") ?: "",
                        emailUsuario = d.getString("emailUsuario") ?: "(desconocido)",
                        emailAfectado = d.getString("emailAfectado"),
                        timestamp = d.getLong("timestamp") ?: 0L
                    )
                } ?: emptyList()
                uiState = uiState.copy(cargando = false, registros = lista, mensajeError = null)
            }

        // Total de cuentas y administradores
        listeners += db.collection("usuarios").addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            uiState = uiState.copy(
                totalCuentas = snap.size(),
                totalAdmins = snap.documents.count { it.getString("rol") == "admin" }
            )
        }

        // Total de plantas de TODOS los usuarios (consulta de grupo de colecciones).
        // Requiere una regla de Firestore específica; si aún no existe, queda en "—".
        listeners += db.collectionGroup("plantas").addSnapshotListener { snap, error ->
            if (error != null || snap == null) return@addSnapshotListener
            uiState = uiState.copy(totalPlantas = snap.size())
        }
    }

    fun filtrar(tipo: String?) {
        uiState = uiState.copy(filtro = tipo)
    }

    override fun onCleared() {
        super.onCleared()
        listeners.forEach { it.remove() }
    }
}
