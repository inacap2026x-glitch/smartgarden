package com.app.smartgarden.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.smartgarden.sensores.Sensor
import com.app.smartgarden.sensores.SensorViewModel
import com.app.smartgarden.simulacion.SimulacionEngine
import kotlinx.coroutines.delay
import com.app.smartgarden.ui.theme.AccentGreen
import com.app.smartgarden.ui.theme.BackgroundSecondary
import com.app.smartgarden.ui.theme.ErrorRed
import com.app.smartgarden.ui.theme.TextSecondary
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantaDetalleScreen(
    plantaId: String,
    ownerUid: String? = null,
    detalleViewModel: PlantaDetalleViewModel = viewModel(),
    sensorViewModel: SensorViewModel = viewModel(),
    onVolver: () -> Unit
) {
    val uiState = detalleViewModel.uiState
    var mostrarDialogoSensor by remember { mutableStateOf(false) }
    var mostrarDialogoUmbrales by remember { mutableStateOf(false) }
    var codigoSensorAsignado by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(plantaId, ownerUid) {
        detalleViewModel.ownerUid = ownerUid
        sensorViewModel.ownerUid = ownerUid
        detalleViewModel.observarPlanta(plantaId)
    }

    LaunchedEffect(uiState.planta?.sensorId) {
        val sensorId = uiState.planta?.sensorId
        if (sensorId != null) {
            FirebaseFirestore.getInstance().collection("sensores").document(sensorId).get()
                .addOnSuccessListener { doc -> codigoSensorAsignado = doc.getString("codigo") }
        } else {
            codigoSensorAsignado = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.planta?.nombre ?: "Planta", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarDialogoUmbrales = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "Configurar umbrales", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.cargando -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = AccentGreen)
                }

                uiState.planta == null -> {
                    Text(
                        text = uiState.mensajeError ?: "No se encontró la planta",
                        color = ErrorRed,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                }

                else -> {
                    val planta = uiState.planta

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (planta.descripcion.isNotBlank()) {
                            Text(planta.descripcion, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // ---------- ALERTA DE TEMPERATURA ----------
                        if (planta.alertaEstado == "pendiente" || planta.alertaEstado == "emergencia") {
                            BannerAlerta(
                                planta = planta,
                                onAtender = { detalleViewModel.atenderAlerta(plantaId) }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // ---------- SENSOR ----------
                        FilaInfo(
                            titulo = "Sensor",
                            subtitulo = if (planta.sensorId != null) codigoSensorAsignado ?: "Cargando..." else "Sin sensor asignado",
                            accion = {
                                if (planta.sensorId != null) {
                                    TextButton(onClick = { sensorViewModel.liberarSensor(planta.sensorId, plantaId) }) {
                                        Text("Desvincular", color = ErrorRed)
                                    }
                                } else {
                                    TextButton(onClick = {
                                        sensorViewModel.observarSensoresDisponibles()
                                        mostrarDialogoSensor = true
                                    }) {
                                        Text("Asignar", color = AccentGreen)
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ---------- LECTURAS ----------
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TarjetaLectura(Modifier.weight(1f), "🌡️", "Temperatura", "${"%.1f".format(planta.temperatura)}°C", "Umbral: ${"%.0f".format(planta.temperaturaUmbral)}°C")
                            TarjetaLectura(Modifier.weight(1f), "💧", "Humedad", "${"%.1f".format(planta.humedad)}%", "Umbral: ${"%.0f".format(planta.humedadUmbral)}%")
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ---------- MODO AUTOMÁTICO ----------
                        FilaInfo(
                            titulo = "Modo automático",
                            subtitulo = if (planta.modoAutomatico) "Riega y ventila según umbrales" else "Control manual",
                            accion = {
                                Switch(
                                    checked = planta.modoAutomatico,
                                    onCheckedChange = { detalleViewModel.cambiarModoAutomatico(plantaId, it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // ---------- ESTADO BOMBA ----------
                        EstadoActuador(
                            activo = planta.bombaEncendida,
                            textoActivo = "Regando ahora",
                            textoInactivo = "Bomba apagada"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // ---------- ESTADO VENTILADOR ----------
                        EstadoActuador(
                            activo = planta.ventiladorEncendido,
                            textoActivo = "Ventilando ahora",
                            textoInactivo = "Ventilador apagado"
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // ---------- BOTONES MANUALES ----------
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    if (planta.bombaEncendida) detalleViewModel.detenerRiego(plantaId)
                                    else detalleViewModel.regarAhora(plantaId)
                                },
                                enabled = !planta.modoAutomatico,
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (planta.bombaEncendida) ErrorRed else AccentGreen,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.weight(1f).height(54.dp)
                            ) {
                                Text(if (planta.bombaEncendida) "Detener riego" else "Regar ahora", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    if (planta.ventiladorEncendido) detalleViewModel.apagarVentilador(plantaId)
                                    else detalleViewModel.encenderVentilador(plantaId)
                                },
                                enabled = !planta.modoAutomatico,
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (planta.ventiladorEncendido) ErrorRed else AccentGreen,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.weight(1f).height(54.dp)
                            ) {
                                Text(if (planta.ventiladorEncendido) "Apagar vent." else "Encender vent.", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (planta.modoAutomatico) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Desactiva el modo automático para controlar manualmente",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (mostrarDialogoSensor) {
        DialogAsignarSensor(
            sensorViewModel = sensorViewModel,
            onAsignar = { sensorId ->
                sensorViewModel.asignarSensorAPlanta(sensorId, plantaId)
                mostrarDialogoSensor = false
            },
            onDismiss = { mostrarDialogoSensor = false }
        )
    }

    if (mostrarDialogoUmbrales && uiState.planta != null) {
        DialogConfigurarUmbrales(
            humedadActual = uiState.planta.humedadUmbral,
            temperaturaActual = uiState.planta.temperaturaUmbral,
            alertaActual = uiState.planta.alertaUmbral,
            onConfirmar = { hum, temp, alerta ->
                detalleViewModel.actualizarUmbrales(plantaId, hum, temp, alerta)
                mostrarDialogoUmbrales = false
            },
            onDismiss = { mostrarDialogoUmbrales = false }
        )
    }
}

// Tarjeta roja con cuenta regresiva de 2 minutos mientras la alerta está pendiente
@Composable
private fun BannerAlerta(planta: Planta, onAtender: () -> Unit) {
    val emergencia = planta.alertaEstado == "emergencia"
    var restanteMs by remember { mutableStateOf(0L) }

    LaunchedEffect(planta.alertaInicio, planta.alertaEstado) {
        while (true) {
            restanteMs = (planta.alertaInicio + SimulacionEngine.ESPERA_ALERTA_MS - System.currentTimeMillis())
                .coerceAtLeast(0L)
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ErrorRed.copy(alpha = 0.12f))
            .padding(16.dp)
    ) {
        Text(
            text = if (emergencia) "🚨 EMERGENCIA ACTIVA" else "⚠️ ALERTA DE TEMPERATURA",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = ErrorRed
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (emergencia)
                "No se atendió a tiempo. Los ventiladores de emergencia están a máxima potencia hasta que la temperatura se normalice."
            else
                "La temperatura superó el umbral de alerta (%.0f°C). Tiempo restante para actuar: %02d:%02d".format(
                    planta.alertaUmbral, restanteMs / 60000, (restanteMs / 1000) % 60
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onAtender,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
        ) {
            Text("Atender alerta (encender ventilador)")
        }
    }
}

@Composable
private fun FilaInfo(titulo: String, subtitulo: String, accion: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundSecondary)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        accion()
    }
}

@Composable
private fun EstadoActuador(activo: Boolean, textoActivo: String, textoInactivo: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (activo) AccentGreen.copy(alpha = 0.12f) else BackgroundSecondary)
            .padding(16.dp)
    ) {
        Text(
            text = if (activo) textoActivo else textoInactivo,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (activo) AccentGreen else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun TarjetaLectura(modifier: Modifier = Modifier, emoji: String, label: String, valor: String, umbral: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundSecondary)
            .padding(16.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(valor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(umbral, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun DialogConfigurarUmbrales(
    humedadActual: Double,
    temperaturaActual: Double,
    alertaActual: Double,
    onConfirmar: (Double, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var humedadTexto by remember { mutableStateOf(humedadActual.toString()) }
    var temperaturaTexto by remember { mutableStateOf(temperaturaActual.toString()) }
    var alertaTexto by remember { mutableStateOf(alertaActual.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Configurar umbrales", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Bajo este % de humedad se activa la bomba. Sobre esta temperatura se activa el ventilador.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = humedadTexto,
                    onValueChange = { humedadTexto = it },
                    label = { Text("Umbral de humedad (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = temperaturaTexto,
                    onValueChange = { temperaturaTexto = it },
                    label = { Text("Umbral del ventilador (°C)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = alertaTexto,
                    onValueChange = { alertaTexto = it },
                    label = { Text("Umbral de ALERTA (°C)") },
                    supportingText = { Text("Al superarlo suena una alerta; si no la atiendes en 2 min se activa la emergencia") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hum = humedadTexto.toDoubleOrNull() ?: humedadActual
                    val temp = temperaturaTexto.toDoubleOrNull() ?: temperaturaActual
                    val alerta = alertaTexto.toDoubleOrNull() ?: alertaActual
                    onConfirmar(hum, temp, alerta)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) }
        }
    )
}

@Composable
private fun DialogAsignarSensor(
    sensorViewModel: SensorViewModel,
    onAsignar: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val uiState = sensorViewModel.uiState
    var mostrarCrear by remember { mutableStateOf(false) }
    var nuevoCodigo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Asignar sensor", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                if (!mostrarCrear) {
                    if (uiState.cargando) {
                        CircularProgressIndicator(color = AccentGreen)
                    } else if (uiState.sensoresDisponibles.isEmpty()) {
                        Text("No hay sensores libres. Registra uno nuevo.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.sensoresDisponibles.forEach { sensor: Sensor ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BackgroundSecondary).padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(sensor.codigo, color = MaterialTheme.colorScheme.onBackground)
                                    TextButton(onClick = { onAsignar(sensor.id) }) { Text("Elegir", color = AccentGreen) }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { mostrarCrear = true }) { Text("+ Registrar sensor nuevo", color = AccentGreen) }
                } else {
                    OutlinedTextField(
                        value = nuevoCodigo,
                        onValueChange = { nuevoCodigo = it },
                        label = { Text("Código del sensor (ej. ESP32-A1B2)") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            sensorViewModel.crearSensor(nuevoCodigo)
                            mostrarCrear = false
                            nuevoCodigo = ""
                        },
                        enabled = nuevoCodigo.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Registrar") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = TextSecondary) }
        }
    )
}