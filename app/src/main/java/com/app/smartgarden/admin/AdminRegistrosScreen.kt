package com.app.smartgarden.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FILTROS = listOf(
    null to "Todos",
    "CUENTA_CREADA" to "Cuentas",
    "LOGIN" to "Inicios de sesión",
    "PLANTA_CREADA" to "Plantas +",
    "PLANTA_ELIMINADA" to "Plantas −",
    "ACCION_MANUAL" to "Acciones manuales",
    "ACTUADOR_AUTO" to "Automáticos",
    "UMBRALES" to "Umbrales",
    "MODO" to "Modo",
    "SENSOR" to "Sensores",
    "ROL" to "Roles",
    "CREDENCIALES" to "Credenciales",
    "ALERTA" to "Alertas"
)

private fun etiquetaTipo(tipo: String) =
    FILTROS.firstOrNull { it.first == tipo }?.second ?: tipo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminRegistrosScreen(
    viewModel: AdminRegistrosViewModel = viewModel(),
    onVolver: () -> Unit
) {
    val uiState = viewModel.uiState
    val formato = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    val visibles = uiState.registros.filter { uiState.filtro == null || it.tipo == uiState.filtro }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Registros y monitoreo", style = MaterialTheme.typography.titleLarge) },
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
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Resumen(Modifier.weight(1f), "Cuentas", uiState.totalCuentas?.toString() ?: "—")
                        Resumen(Modifier.weight(1f), "Admins", uiState.totalAdmins?.toString() ?: "—")
                        Resumen(Modifier.weight(1f), "Plantas", uiState.totalPlantas?.toString() ?: "—")
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FILTROS) { (tipo, nombre) ->
                            FilterChip(
                                selected = uiState.filtro == tipo,
                                onClick = { viewModel.filtrar(tipo) },
                                label = { Text(nombre) }
                            )
                        }
                    }
                }

                if (uiState.cargando) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentGreen)
                        }
                    }
                } else if (visibles.isEmpty()) {
                    item {
                        Text(
                            "Sin eventos para mostrar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                } else {
                    items(visibles, key = { it.id }) { ev ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(BackgroundSecondary)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    etiquetaTipo(ev.tipo),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentGreen
                                )
                                Text(
                                    formato.format(Date(ev.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                ev.descripcion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Por: ${ev.emailUsuario}" +
                                    (ev.emailAfectado?.let { " · Sobre: $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
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
}

@Composable
private fun Resumen(modifier: Modifier, titulo: String, valor: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BackgroundSecondary)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(titulo, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
