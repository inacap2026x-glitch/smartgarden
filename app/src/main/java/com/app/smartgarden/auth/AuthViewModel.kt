package com.app.smartgarden.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.app.smartgarden.registros.Registro
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var uiState by mutableStateOf(AuthUiState())
        private set

    companion object {
        const val MAX_INTENTOS = 5
    }

    // ---------- VALIDACIÓN DE CONTRASEÑA SEGURA ----------
    fun validarPasswordSegura(password: String): String? {
        if (password.length < 8) return "Debe tener al menos 8 caracteres"
        if (!password.any { it.isUpperCase() }) return "Debe incluir al menos una mayúscula"
        if (!password.any { it.isLowerCase() }) return "Debe incluir al menos una minúscula"
        if (!password.any { it.isDigit() }) return "Debe incluir al menos un número"
        if (!password.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }) {
            return "Debe incluir al menos un símbolo (ej: !@#$%)"
        }
        return null
    }

    // ---------- REGISTRO ----------
    fun registrar(email: String, password: String, confirmPassword: String) {
        val errorPassword = validarPasswordSegura(password)
        if (errorPassword != null) {
            uiState = uiState.copy(mensajeError = errorPassword)
            return
        }
        if (password != confirmPassword) {
            uiState = uiState.copy(mensajeError = "Las contraseñas no coinciden")
            return
        }

        uiState = uiState.copy(cargando = true, mensajeError = null)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                val datosUsuario = hashMapOf(
                    "email" to email,
                    "fechaRegistro" to System.currentTimeMillis(),
                    "rol" to "usuario"
                )
                db.collection("usuarios").document(uid).set(datosUsuario)
                    .addOnSuccessListener {
                        // Registramos el evento ANTES de cerrar sesión (aún hay usuario autenticado)
                        Registro.log(
                            tipo = "CUENTA_CREADA",
                            descripcion = "Cuenta creada: $email",
                            uid = uid,
                            email = email,
                            onListo = {
                                // Cerramos la sesión automática que crea Firebase al registrar,
                                // para que el usuario inicie sesión explícitamente después.
                                auth.signOut()
                                uiState = uiState.copy(cargando = false, registroExitoso = true)
                            }
                        )
                    }
                    .addOnFailureListener { e ->
                        uiState = uiState.copy(cargando = false, mensajeError = e.localizedMessage)
                    }
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(cargando = false, mensajeError = e.localizedMessage)
            }
    }

    // ---------- LOGIN CON CONTROL DE INTENTOS ----------
    fun login(email: String, password: String) {
        uiState = uiState.copy(cargando = true, mensajeError = null)
        val emailKey = email.trim().lowercase()

        db.collection("intentosLogin").document(emailKey).get()
            .addOnSuccessListener { doc ->
                val bloqueadoHasta = doc.getLong("bloqueadoHasta") ?: 0L
                val ahora = System.currentTimeMillis()

                if (bloqueadoHasta > ahora) {
                    val minutosRestantes = ((bloqueadoHasta - ahora) / 60000) + 1
                    uiState = uiState.copy(
                        cargando = false,
                        mensajeError = "Cuenta bloqueada. Intenta en $minutosRestantes min o recupera tu contraseña.",
                        mostrarRecuperar = true
                    )
                    return@addOnSuccessListener
                }

                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        db.collection("intentosLogin").document(emailKey)
                            .set(mapOf("intentosFallidos" to 0L, "bloqueadoHasta" to 0L))

                        // Una cuenta desactivada por un administrador no puede entrar
                        val uid = result.user?.uid ?: ""
                        db.collection("usuarios").document(uid).get()
                            .addOnSuccessListener { perfil ->
                                if (perfil.getBoolean("activo") == false) {
                                    Registro.log(
                                        "LOGIN",
                                        "Intento de acceso con cuenta desactivada",
                                        onListo = { auth.signOut() }
                                    )
                                    uiState = uiState.copy(
                                        cargando = false,
                                        mensajeError = "Tu cuenta fue desactivada por un administrador."
                                    )
                                } else {
                                    Registro.log("LOGIN", "Inicio de sesión")
                                    uiState = uiState.copy(cargando = false, loginExitoso = true)
                                }
                            }
                            .addOnFailureListener { e ->
                                auth.signOut()
                                uiState = uiState.copy(cargando = false, mensajeError = e.localizedMessage)
                            }
                    }
                    .addOnFailureListener {
                        manejarIntentoFallido(emailKey, doc)
                    }
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(cargando = false, mensajeError = e.localizedMessage)
            }
    }

    private fun manejarIntentoFallido(emailKey: String, doc: DocumentSnapshot) {
        val intentosActuales = (doc.getLong("intentosFallidos") ?: 0L).toInt() + 1

        if (intentosActuales >= MAX_INTENTOS) {
            val bloqueoHasta = System.currentTimeMillis() + (15 * 60 * 1000)
            db.collection("intentosLogin").document(emailKey)
                .set(mapOf("intentosFallidos" to intentosActuales.toLong(), "bloqueadoHasta" to bloqueoHasta))
            uiState = uiState.copy(
                cargando = false,
                mensajeError = "Superaste el máximo de intentos. Cuenta bloqueada 15 min. Recupera tu contraseña si lo necesitas.",
                mostrarRecuperar = true
            )
        } else {
            db.collection("intentosLogin").document(emailKey)
                .set(mapOf("intentosFallidos" to intentosActuales.toLong(), "bloqueadoHasta" to 0L))
            val restantes = MAX_INTENTOS - intentosActuales
            uiState = uiState.copy(
                cargando = false,
                mensajeError = "Credenciales incorrectas. Te quedan $restantes intento(s).",
                mostrarRecuperar = intentosActuales >= 3
            )
        }
    }

    // ---------- RECUPERAR CONTRASEÑA ----------
    fun recuperarPassword(email: String) {
        if (email.isBlank()) {
            uiState = uiState.copy(mensajeError = "Ingresa tu correo para recuperar la contraseña")
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                uiState = uiState.copy(mensajeError = "Se envió un correo de recuperación a $email")
            }
            .addOnFailureListener { e ->
                uiState = uiState.copy(mensajeError = e.localizedMessage)
            }
    }

    fun limpiarMensaje() {
        uiState = uiState.copy(mensajeError = null)
    }
}

data class AuthUiState(
    val cargando: Boolean = false,
    val mensajeError: String? = null,
    val loginExitoso: Boolean = false,
    val registroExitoso: Boolean = false,
    val mostrarRecuperar: Boolean = false
)