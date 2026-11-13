package com.app.smartgarden.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.smartgarden.ui.theme.AccentGreen
import com.app.smartgarden.ui.theme.BackgroundSecondary
import com.app.smartgarden.ui.theme.ErrorRed
import com.app.smartgarden.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminScreen(
    adminViewModel: AdminViewModel = viewModel(),
    onVerPlantas: (String) -> Unit,
    onVerRegistros: () -> Unit,
    onVerSensores: () -> Unit,
    onVolver: () -> Unit
) {
    val uiState = adminViewModel.uiState
    var usuarioParaCambiar by remember { mutableStateOf<UsuarioAdmin?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Administración",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.cargando) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AccentGreen
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onVerRegistros,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                Text("Registros")
                            }
                            Button(
                                onClick = onVerSensores,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                Text("Sensores")
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Usuarios (${uiState.usuarios.size})",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    items(uiState.usuarios, key = { it.uid }) { usuario ->
                        TarjetaUsuario(
                            usuario = usuario,
                            esYo = usuario.uid == adminViewModel.uidActual,
                            onCambiarRol = { usuarioParaCambiar = usuario },
                            onVerPlantas = { onVerPlantas(usuario.uid) },
                            onToggleActivo = { adminViewModel.cambiarActivo(usuario.uid, !usuario.activo) },
                            onRestablecer = { adminViewModel.enviarRestablecimiento(usuario) }
                        )
                    }
                }
            }

            uiState.mensajeInfo?.let { info ->
                Text(
                    text = info,
                    color = AccentGreen,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }

            uiState.mensajeError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }

    // Confirmación antes de cambiar el rol de alguien
    usuarioParaCambiar?.let { usuario ->
        val nuevoRol = if (usuario.rol == "admin") "usuario" else "admin"
        AlertDialog(
            onDismissRequest = { usuarioParaCambiar = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = if (nuevoRol == "admin") "Dar permisos de administrador" else "Quitar permisos de administrador",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = if (nuevoRol == "admin")
                        "${usuario.email} podrá gestionar todos los usuarios, plantas y sensores."
                    else
                        "${usuario.email} volverá a ser un usuario normal y solo verá sus propias plantas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.cambiarRol(usuario.uid, nuevoRol)
                        usuarioParaCambiar = null
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (nuevoRol == "admin") AccentGreen else ErrorRed
                    )
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { usuarioParaCambiar = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaUsuario(
    usuario: UsuarioAdmin,
    esYo: Boolean,
    onCambiarRol: () -> Unit,
    onVerPlantas: () -> Unit,
    onToggleActivo: () -> Unit,
    onRestablecer: () -> Unit
) {
    val esAdmin = usuario.rol == "admin"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundSecondary)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = usuario.email,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (esYo) {
                    Text(
                        text = "Tu cuenta",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (!usuario.activo) {
                    Text(
                        text = "Cuenta desactivada",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (esAdmin) AccentGreen.copy(alpha = 0.15f)
                        else TextSecondary.copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (esAdmin) "Administrador" else "Usuario",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (esAdmin) AccentGreen else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        FlowRow {
            TextButton(onClick = onVerPlantas) {
                Text("Ver plantas", color = AccentGreen)
            }
            TextButton(onClick = onRestablecer) {
                Text("Restablecer contraseña", color = AccentGreen)
            }
            // Un admin no puede cambiarse el rol ni desactivarse a sí mismo
            if (!esYo) {
                TextButton(onClick = onCambiarRol) {
                    Text(
                        text = if (esAdmin) "Quitar administrador" else "Hacer administrador",
                        color = if (esAdmin) ErrorRed else AccentGreen
                    )
                }
                TextButton(onClick = onToggleActivo) {
                    Text(
                        text = if (usuario.activo) "Desactivar cuenta" else "Activar cuenta",
                        color = if (usuario.activo) ErrorRed else AccentGreen
                    )
                }
            }
        }
    }
}
