package com.app.smartgarden.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.app.smartgarden.home.Planta
import com.app.smartgarden.ui.theme.AccentGreen
import com.app.smartgarden.ui.theme.BackgroundSecondary
import com.app.smartgarden.ui.theme.ErrorRed
import com.app.smartgarden.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPlantasScreen(
    ownerUid: String,
    viewModel: AdminPlantasViewModel = viewModel(),
    onPlantaSeleccionada: (String) -> Unit,
    onVolver: () -> Unit
) {
    val uiState = viewModel.uiState
    var plantaParaEliminar by remember { mutableStateOf<Planta?>(null) }

    LaunchedEffect(ownerUid) { viewModel.cargar(ownerUid) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Plantas", style = MaterialTheme.typography.titleLarge)
                        if (uiState.emailUsuario.isNotBlank()) {
                            Text(
                                uiState.emailUsuario,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.cargando -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AccentGreen
                )

                uiState.plantas.isEmpty() -> Text(
                    text = "Este usuario aún no tiene plantas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.plantas, key = { it.id }) { planta ->
                        TarjetaPlantaAdmin(
                            planta = planta,
                            onClick = { onPlantaSeleccionada(planta.id) },
                            onEliminar = { plantaParaEliminar = planta }
                        )
                    }
                }
            }

            uiState.mensajeError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
        }
    }

    plantaParaEliminar?.let { planta ->
        AlertDialog(
            onDismissRequest = { plantaParaEliminar = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Eliminar planta", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "Se eliminará \"${planta.nombre}\" y su sensor quedará libre. Esta acción no se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.eliminarPlanta(planta)
                        plantaParaEliminar = null
                    },
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { plantaParaEliminar = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun TarjetaPlantaAdmin(
    planta: Planta,
    onClick: () -> Unit,
    onEliminar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundSecondary)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(
            text = planta.nombre,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "%.1f °C · %.1f %% · Bomba: %s · Ventilador: %s".format(
                planta.temperatura,
                planta.humedad,
                if (planta.bombaEncendida) "ON" else "OFF",
                if (planta.ventiladorEncendido) "ON" else "OFF"
            ),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = "Umbrales: %.0f °C · %.0f %%  |  Sensor: %s".format(
                planta.temperaturaUmbral,
                planta.humedadUmbral,
                planta.sensorId ?: "sin asignar"
            ),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        TextButton(onClick = onEliminar) {
            Text("Eliminar planta", color = ErrorRed)
        }
    }
}
