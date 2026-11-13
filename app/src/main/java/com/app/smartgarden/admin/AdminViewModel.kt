package com.app.smartgarden.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.app.smartgarden.registros.Registro
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class UsuarioAdmin(
    val uid: String = "",
    val email: String = "",
    val rol: String = "usuario",
    val fechaRegistro: Long = 0L,
    val activo: Boolean = true
)

data class AdminUiState(
    val usuarios: List<UsuarioAdmin> = emptyList(),
    val cargando: Boolean = true,
    val mensajeError: String? = null,
    val mensajeInfo: String? = null
)

class AdminViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    var uiState by mutableStateOf(AdminUiState())
        private set

    // UID del admin que está usando la app (para no permitir que se quite el rol a sí mismo)
    val uidActual: String? get() = auth.currentUser?.uid

    init {
        observarUsuarios()
    }

    // Escucha en tiempo real a TODOS los usuarios registrados
    private fun observarUsuarios() {
        listener?.remove()
        listener = db.collection("usuarios")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    uiState = uiState.copy(cargando = false, mensajeError = error.localizedMessage)
                    return@addSnapshotListener
                }

                val lista = snapshot?.documents?.map { doc ->
                    UsuarioAdmin(
                        uid = doc.id,
                        email = doc.getString("email") ?: "(sin correo)",
                        rol = doc.getString("rol") ?: "usuario",
                        fechaRegistro = doc.getLong("fechaRegistro") ?: 0L,
                        activo = doc.getBoolean("activo") != false
                    )
                }?.sortedBy { it.email.lowercase() } ?: emptyList()

                uiState = uiState.copy(cargando = false, usuarios = lista, mensajeError = null)
            }
    }

    // Cambia el rol de un usuario entre "usuario" y "admin"
    fun cambiarRol(uid: String, nuevoRol: String) {
        // Seguridad: un admin no puede cambiarse el rol a sí mismo (evita quedarse sin admins)
        if (uid == uidActual) return
        if (nuevoRol != "usuario" && nuevoRol != "admin") return

        db.collection("usuarios").document(uid)
            .update("rol", nuevoRol)
            .addOnSuccessListener {
                val emailObjetivo = uiState.usuarios.firstOrNull { it.uid == uid }?.email
                Registro.log(
                    "ROL",
                    "Rol cambiado a \"$nuevoRol\"",
                    uidAfectado = uid,
                    emailAfectado = emailObjetivo
                )
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(mensajeError = e.localizedMessage)
            }
    }

    // Activa o desactiva una cuenta. Una cuenta desactivada no puede iniciar sesión
    // y, si estaba conectada, se cierra su sesión en tiempo real.
    fun cambiarActivo(uid: String, activo: Boolean) {
        if (uid == uidActual) return // el admin no puede desactivarse a sí mismo
        uiState = uiState.copy(mensajeError = null, mensajeInfo = null)

        db.collection("usuarios").document(uid)
            .update("activo", activo)
            .addOnSuccessListener {
                val emailObjetivo = uiState.usuarios.firstOrNull { it.uid == uid }?.email
                Registro.log(
                    "CREDENCIALES",
                    if (activo) "Cuenta activada" else "Cuenta desactivada",
                    uidAfectado = uid,
                    emailAfectado = emailObjetivo
                )
                uiState = uiState.copy(
                    mensajeInfo = if (activo) "Cuenta activada" else "Cuenta desactivada"
                )
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(mensajeError = e.localizedMessage)
            }
    }

    // Envía al usuario un correo para que ELLA/ÉL elija una contraseña nueva.
    // (El admin nunca conoce ni define la contraseña de otra persona.)
    fun enviarRestablecimiento(usuario: UsuarioAdmin) {
        uiState = uiState.copy(mensajeError = null, mensajeInfo = null)
        auth.sendPasswordResetEmail(usuario.email)
            .addOnSuccessListener {
                Registro.log(
                    "CREDENCIALES",
                    "Correo de restablecimiento de contraseña enviado",
                    uidAfectado = usuario.uid,
                    emailAfectado = usuario.email
                )
                uiState = uiState.copy(mensajeInfo = "Correo de restablecimiento enviado a ${usuario.email}")
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
