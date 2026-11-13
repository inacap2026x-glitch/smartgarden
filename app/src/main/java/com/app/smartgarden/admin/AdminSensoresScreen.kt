package com.app.smartgarden.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSensoresScreen(
    viewModel: AdminSensoresViewModel = viewModel(),
    onVolver: () -> Unit
) {
    val uiState = viewModel.uiState
    var mostrarCrear by remember { mutableStateOf(false) }
    var sensorParaEliminar by remember { mutableStateOf<SensorAdmin?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensores (${uiState.sensores.size})", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { mostrarCrear = true },
                containerColor = AccentGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Registrar sensor")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.cargando -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AccentGreen
                )

                uiState.sensores.isEmpty() -> Text(
                    "No hay sensores registrados. Toca + para registrar uno.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.sensores, key = { it.id }) { sensor ->
                        TarjetaSensor(
                            sensor = sensor,
                            onLiberar = { viewModel.liberar(sensor) },
                            onEliminar = { sensorParaEliminar = sensor }
                        )
                    }
                }
            }

            uiState.mensajeError?.let { error ->
                Text(
                    text = error,
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }
    }

    if (mostrarCrear) {
        var codigo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { mostrarCrear = false },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Registrar sensor", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it },
                    label = { Text("Código del sensor (ej: ESP32-001)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.crear(codigo)
                        mostrarCrear = false
                    },
                    enabled = codigo.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) { Text("Registrar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarCrear = false }) { Text("Cancelar", color = TextSecondary) }
            }
        )
    }

    sensorParaEliminar?.let { sensor ->
        AlertDialog(
            onDismissRequest = { sensorParaEliminar = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Eliminar sensor", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Se eliminará \"${sensor.codigo}\"" +
                        (if (sensor.asignado) " y se desvinculará de su planta" else "") +
                        ". Esta acción no se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.eliminar(sensor)
                        sensorParaEliminar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { sensorParaEliminar = null }) { Text("Cancelar", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun TarjetaSensor(
    sensor: SensorAdmin,
    onLiberar: () -> Unit,
    onEliminar: () -> Unit
) {
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
            Text(
                text = sensor.codigo,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (sensor.asignado) AccentGreen.copy(alpha = 0.15f)
                        else TextSecondary.copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (sensor.asignado) "Asignado" else "Disponible",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sensor.asignado) AccentGreen else TextSecondary
                )
            }
        }
        if (sensor.asignado) {
            Text(
                text = "Planta: ${sensor.plantaId ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Row {
            if (sensor.asignado) {
                TextButton(onClick = onLiberar) { Text("Liberar", color = AccentGreen) }
            }
            TextButton(onClick = onEliminar) { Text("Eliminar", color = ErrorRed) }
        }
    }
}
