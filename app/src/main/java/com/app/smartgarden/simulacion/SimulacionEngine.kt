package com.app.smartgarden.simulacion

import com.app.smartgarden.home.Planta

/**
 * Lógica pura de la simulación del invernadero (sin Firebase ni Android).
 * Dado el estado actual de una planta, calcula el siguiente estado, incluida
 * la máquina de estados de la ALERTA DE TEMPERATURA:
 *
 *   ninguna ──(temp ≥ umbral de alerta)──► pendiente  (2 min para actuar)
 *   pendiente ──(usuario atiende)──────► atendida
 *   pendiente ──(pasan 2 min)──────────► emergencia   (ventilador de emergencia)
 *   atendida / emergencia ──(temp baja del umbral − margen)──► ninguna
 */
object SimulacionEngine {

    const val INTERVALO_MS = 4000L
    const val ESPERA_ALERTA_MS = 2 * 60 * 1000L // tiempo que tiene el usuario para actuar

    const val ALERTA_NINGUNA = "ninguna"
    const val ALERTA_PENDIENTE = "pendiente"
    const val ALERTA_ATENDIDA = "atendida"
    const val ALERTA_EMERGENCIA = "emergencia"

    private const val INCREMENTO_TEMP = 0.4    // sube sola si el ventilador está apagado
    private const val ENFRIAMIENTO_TEMP = 0.8  // baja cuando el ventilador está encendido
    private const val ENFRIAMIENTO_EMERGENCIA = 2.0 // ventiladores de emergencia: más del doble de potencia
    private const val DECREMENTO_HUM = 1.2     // baja sola si la bomba está apagada
    private const val HUMEDECIMIENTO_HUM = 2.5 // sube cuando la bomba está encendida

    private const val MARGEN_APAGADO_TEMP = 1.5 // cuánto debe bajar la temp para apagar el ventilador
    private const val MARGEN_APAGADO_HUM = 5.0  // cuánto debe subir la humedad para apagar la bomba
    private const val MARGEN_FIN_ALERTA = 2.0   // cuánto debe bajar bajo el umbral de alerta para darla por terminada

    private const val TEMP_MIN = 18.0
    private const val TEMP_MAX = 45.0
    private const val HUM_MIN = 0.0
    private const val HUM_MAX = 100.0

    data class Resultado(
        val temperatura: Double,
        val humedad: Double,
        val ventilador: Boolean,
        val bomba: Boolean,
        val alertaEstado: String,
        val alertaInicio: Long,
        val emergencia: Boolean
    )

    fun siguiente(planta: Planta, ahora: Long = System.currentTimeMillis()): Resultado {
        var temp = planta.temperatura
        var hum = planta.humedad
        var ventilador = planta.ventiladorEncendido
        var bomba = planta.bombaEncendida

        // Deriva natural: sube la temperatura / baja la humedad, salvo que el actuador esté encendido
        temp += when {
            planta.ventiladorEmergencia -> -ENFRIAMIENTO_EMERGENCIA
            ventilador -> -ENFRIAMIENTO_TEMP
            else -> INCREMENTO_TEMP
        }
        hum += if (bomba) HUMEDECIMIENTO_HUM else -DECREMENTO_HUM

        temp = temp.coerceIn(TEMP_MIN, TEMP_MAX)
        hum = hum.coerceIn(HUM_MIN, HUM_MAX)

        // Solo en modo automático el sistema decide prender/apagar los actuadores
        if (planta.modoAutomatico) {
            if (!ventilador && temp >= planta.temperaturaUmbral) {
                ventilador = true
            } else if (ventilador && temp <= planta.temperaturaUmbral - MARGEN_APAGADO_TEMP) {
                ventilador = false
            }

            if (!bomba && hum <= planta.humedadUmbral) {
                bomba = true
            } else if (bomba && hum >= planta.humedadUmbral + MARGEN_APAGADO_HUM) {
                bomba = false
            }
        }

        // ----- Máquina de estados de la alerta -----
        var estado = planta.alertaEstado
        var inicio = planta.alertaInicio
        var emergencia = planta.ventiladorEmergencia
        val sobreUmbral = temp >= planta.alertaUmbral
        val normalizada = temp <= planta.alertaUmbral - MARGEN_FIN_ALERTA

        when (estado) {
            ALERTA_PENDIENTE -> when {
                normalizada -> { estado = ALERTA_NINGUNA; inicio = 0L }
                ahora - inicio >= ESPERA_ALERTA_MS -> { estado = ALERTA_EMERGENCIA; emergencia = true }
            }
            ALERTA_ATENDIDA -> if (normalizada) { estado = ALERTA_NINGUNA; inicio = 0L }
            ALERTA_EMERGENCIA -> if (normalizada) { estado = ALERTA_NINGUNA; inicio = 0L; emergencia = false }
            else -> if (sobreUmbral) { estado = ALERTA_PENDIENTE; inicio = ahora }
        }

        // En emergencia el ventilador queda encendido a máxima potencia
        if (emergencia) ventilador = true

        return Resultado(
            temperatura = Math.round(temp * 10) / 10.0,   // un decimal, sin ruido de punto flotante
            humedad = Math.round(hum * 10) / 10.0,
            ventilador = ventilador,
            bomba = bomba,
            alertaEstado = estado,
            alertaInicio = inicio,
            emergencia = emergencia
        )
    }
}
