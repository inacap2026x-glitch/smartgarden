package com.app.smartgarden.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.smartgarden.admin.AdminPlantasScreen
import com.app.smartgarden.admin.AdminRegistrosScreen
import com.app.smartgarden.admin.AdminScreen
import com.app.smartgarden.admin.AdminSensoresScreen
import com.app.smartgarden.auth.LoginScreen
import com.app.smartgarden.auth.RegisterScreen
import com.app.smartgarden.home.HomeScreen
import com.app.smartgarden.home.PlantaDetalleScreen
import com.google.firebase.auth.FirebaseAuth

object Rutas {
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val HOME = "home"
    const val DETALLE_PLANTA = "detalle/{plantaId}?owner={owner}"
    const val ADMIN = "admin"
    const val ADMIN_PLANTAS = "admin/plantas/{uid}"
    const val ADMIN_REGISTROS = "admin/registros"
    const val ADMIN_SENSORES = "admin/sensores"

    // owner = uid del dueño; solo se usa cuando un administrador abre la planta de otro usuario
    fun detalleConId(plantaId: String, owner: String? = null) =
        if (owner == null) "detalle/$plantaId" else "detalle/$plantaId?owner=$owner"

    fun adminPlantas(uid: String) = "admin/plantas/$uid"
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    // Firebase guarda la sesión en el dispositivo, pero la restaura de forma asíncrona al abrir la app.
    // Por eso esperamos a que Firebase informe el estado de la sesión (AuthStateListener) antes de
    // decidir la pantalla inicial: si hay usuario → HOME, si no → LOGIN.
    var destinoInicial by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { fa ->
            if (destinoInicial == null) {
                destinoInicial = if (fa.currentUser != null) Rutas.HOME else Rutas.LOGIN
            }
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val inicio = destinoInicial ?: return

    NavHost(
        navController = navController,
        startDestination = inicio
    ) {
        composable(Rutas.LOGIN) {
            LoginScreen(
                onLoginExitoso = {
                    navController.navigate(Rutas.HOME) {
                        popUpTo(Rutas.LOGIN) { inclusive = true }
                    }
                },
                onIrARegistro = {
                    navController.navigate(Rutas.REGISTRO)
                }
            )
        }

        composable(Rutas.REGISTRO) {
            RegisterScreen(
                onRegistroExitoso = {
                    navController.navigate(Rutas.LOGIN) {
                        popUpTo(Rutas.REGISTRO) { inclusive = true }
                    }
                },
                onVolverALogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(Rutas.HOME) {
            HomeScreen(
                onPlantaSeleccionada = { plantaId ->
                    navController.navigate(Rutas.detalleConId(plantaId))
                },
                onAbrirAdmin = {
                    navController.navigate(Rutas.ADMIN)
                },
                onCerrarSesion = {
                    navController.navigate(Rutas.LOGIN) {
                        popUpTo(Rutas.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Rutas.ADMIN) {
            AdminScreen(
                onVerPlantas = { uid -> navController.navigate(Rutas.adminPlantas(uid)) },
                onVerRegistros = { navController.navigate(Rutas.ADMIN_REGISTROS) },
                onVerSensores = { navController.navigate(Rutas.ADMIN_SENSORES) },
                onVolver = { navController.popBackStack() }
            )
        }

        composable(Rutas.ADMIN_SENSORES) {
            AdminSensoresScreen(onVolver = { navController.popBackStack() })
        }

        composable(Rutas.ADMIN_REGISTROS) {
            AdminRegistrosScreen(onVolver = { navController.popBackStack() })
        }

        composable(Rutas.ADMIN_PLANTAS) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid") ?: ""
            AdminPlantasScreen(
                ownerUid = uid,
                onPlantaSeleccionada = { plantaId ->
                    navController.navigate(Rutas.detalleConId(plantaId, uid))
                },
                onVolver = { navController.popBackStack() }
            )
        }

        composable(
            route = Rutas.DETALLE_PLANTA,
            arguments = listOf(
                navArgument("owner") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val plantaId = backStackEntry.arguments?.getString("plantaId") ?: ""
            val owner = backStackEntry.arguments?.getString("owner")
            PlantaDetalleScreen(
                plantaId = plantaId,
                ownerUid = owner,
                onVolver = { navController.popBackStack() }
            )
        }
    }
}