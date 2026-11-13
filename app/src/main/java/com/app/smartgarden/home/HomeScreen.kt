package com.app.smartgarden.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.widget.Toast
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.smartgarden.ui.theme.AccentGreen
import com.app.smartgarden.ui.theme.BackgroundSecondary
import com.app.smartgarden.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    onPlantaSeleccionada: (String) -> Unit,
    onAbrirAdmin: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    val uiState = homeViewModel.uiState
    var mostrarDialogo by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Si un administrador desactiva esta cuenta, se cierra la sesión automáticamente
    LaunchedEffect(uiState.cuentaDesactivada) {
        if (uiState.cuentaDesactivada) {
            Toast.makeText(context, "Tu cuenta fue desactivada por un administrador", Toast.LENGTH_LONG).show()
            homeViewModel.cerrarSesion()
            onCerrarSesion()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mis plantas",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    // Solo los administradores ven el acceso al panel de administración
                    if (uiState.esAdmin) {
                        IconButton(onClick = onAbrirAdmin) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = "Panel de administración",
                                tint = AccentGreen
                            )
                        }
                    }
                    IconButton(onClick = {
                        homeViewModel.cerrarSesion()
                        onCerrarSesion()
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Logout,
                            contentDescription = "Cerrar sesión",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarDialogo = true },
                containerColor = AccentGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Agregar planta"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.cargando -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentGreen
                    )
                }

                uiState.plantas.isEmpty() -> {
                    // Estado vacío: invitación a agregar la primera planta
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🌱",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Aún no tienes plantas",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Toca el botón + para agregar tu primera planta",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 24.dp,
                            vertical = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.plantas) { planta ->
                            TarjetaPlanta(
                                planta = planta,
                                onClick = { onPlantaSeleccionada(planta.id) }
                            )
                        }
                    }
                }
            }

            uiState.mensajeError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }

    if (mostrarDialogo) {
        DialogAgregarPlanta(
            onConfirmar = { nombre, descripcion ->
                homeViewModel.agregarPlanta(nombre, descripcion)
                mostrarDialogo = false
            },
            onDismiss = { mostrarDialogo = false }
        )
    }
}

@Composable
private fun TarjetaPlanta(
    planta: Planta,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundSecondary)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = planta.nombre,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                // Indicador de estado de la bomba
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (planta.bombaEncendida) AccentGreen.copy(alpha = 0.15f)
                            else TextSecondary.copy(alpha = 0.1f)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (planta.bombaEncendida) "Regando" else "En espera",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (planta.bombaEncendida) AccentGreen else TextSecondary
                    )
                }
            }

            if (planta.descripcion.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = planta.descripcion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            if (planta.alertaEstado == "pendiente" || planta.alertaEstado == "emergencia") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (planta.alertaEstado == "emergencia") "🚨 Emergencia: ventiladores a máxima potencia"
                    else "⚠️ Alerta de temperatura: requiere atención",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DatoSensor(label = "Temperatura", valor = "${"%.1f".format(planta.temperatura)}°C")
                DatoSensor(label = "Humedad", valor = "${"%.1f".format(planta.humedad)}%")
            }
        }
    }
}

@Composable
private fun DatoSensor(label: String, valor: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = valor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun DialogAgregarPlanta(
    onConfirmar: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Nueva planta",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre de la planta") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción (opcional)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmar(nombre, descripcion) },
                enabled = nombre.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen
                )
            ) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}