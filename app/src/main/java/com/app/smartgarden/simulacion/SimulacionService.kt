package com.app.smartgarden.simulacion

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.app.smartgarden.MainActivity
import com.app.smartgarden.home.Planta
import com.app.smartgarden.registros.Registro
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * SIMULADOR DE DISPOSITIVOS (reemplaza al ESP32 en esta evaluación).
 *
 * Es un servicio en primer plano que NO depende de la sesión de ningún usuario:
 * se conecta a Firebase con una identidad propia de "dispositivo" (una instancia
 * secundaria de Firebase con autenticación anónima, separada de la sesión del usuario)
 * y simula TODAS las plantas de TODOS los usuarios cada 4 segundos: deriva de
 * temperatura/humedad y encendido/apagado automático de bomba y ventilador.
 *
 * Si varios teléfonos tienen la app abierta, solo uno simula a la vez (liderazgo con
 * "lease" en Firestore: documento sistema/simulador), para no duplicar la velocidad.
 */
class SimulacionService : Service() {

    companion object {
        private const val TAG = "SimulacionService"
        private const val CANAL_ID = "simulacion"
        private const val NOTIF_ID = 1001
        private const val APP_DISPOSITIVO = "dispositivo"
        private const val LEASE_MS = 15_000L
        private const val CANAL_ALERTAS = "alertas"
        private const val ACCION_ATENDER = "com.app.smartgarden.ATENDER_ALERTA"
        private const val EXTRA_RUTA = "ruta"

        fun iniciar(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, SimulacionService::class.java)
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val instanciaId = UUID.randomUUID().toString()
    private val plantas = ConcurrentHashMap<String, Pair<DocumentReference, Planta>>()

    private var dDb: FirebaseFirestore? = null
    private var listenerPlantas: ListenerRegistration? = null
    private var jobSimulacion: Job? = null

    @Volatile
    private var soyLider = false

    // ---- Alertas en este teléfono ----
    @Volatile
    private var esAdminLocal = false
    private var listenerPerfil: ListenerRegistration? = null
    private val ultimoNotificado = ConcurrentHashMap<String, String>()

    // Cuando cambia el usuario con sesión, se vuelve a leer su rol (para alertar a admins de TODO)
    private val authListener = FirebaseAuth.AuthStateListener { fa ->
        listenerPerfil?.remove()
        esAdminLocal = false
        fa.currentUser?.uid?.let { uid ->
            listenerPerfil = FirebaseFirestore.getInstance().collection("usuarios").document(uid)
                .addSnapshotListener { d, _ -> esAdminLocal = d?.getString("rol") == "admin" }
        }
    }

    override fun onCreate() {
        super.onCreate()
        crearCanal()
        startForeground(NOTIF_ID, notificacionPermanente())
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Botón "Atender" de la notificación de alerta
        if (intent?.action == ACCION_ATENDER) {
            intent.getStringExtra(EXTRA_RUTA)?.let { atenderDesdeNotificacion(it) }
        }
        if (jobSimulacion?.isActive != true) conectarDispositivo()
        return START_STICKY
    }

    // Identidad propia del dispositivo: otra instancia de Firebase, con auth anónima
    private fun conectarDispositivo() {
        val app = try {
            FirebaseApp.getInstance(APP_DISPOSITIVO)
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(this, FirebaseApp.getInstance().options, APP_DISPOSITIVO)
        }
        val dAuth = FirebaseAuth.getInstance(app)
        dDb = FirebaseFirestore.getInstance(app)

        if (dAuth.currentUser != null) {
            empezar()
        } else {
            dAuth.signInAnonymously()
                .addOnSuccessListener { empezar() }
                .addOnFailureListener { e ->
                    Log.e(TAG, "No se pudo autenticar el dispositivo (¿Anonymous activado?): ${e.message}")
                    // Reintenta en 20 s
                    scope.launch {
                        delay(20_000)
                        if (jobSimulacion?.isActive != true) conectarDispositivo()
                    }
                }
        }
    }

    private fun empezar() {
        val db = dDb ?: return
        listenerPlantas?.remove()
        plantas.clear()

        // Todas las plantas de todos los usuarios (consulta de grupo de colecciones)
        listenerPlantas = db.collectionGroup("plantas").addSnapshotListener { snap, error ->
            if (error != null) {
                Log.e(TAG, "Error leyendo plantas: ${error.message}")
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            plantas.clear()
            snap.documents.forEach { d ->
                plantas[d.reference.path] = d.reference to Planta(
                    id = d.id,
                    nombre = d.getString("nombre") ?: "Planta",
                    temperatura = d.getDouble("temperatura") ?: 25.0,
                    humedad = d.getDouble("humedad") ?: 60.0,
                    bombaEncendida = d.getBoolean("bombaEncendida") ?: false,
                    ventiladorEncendido = d.getBoolean("ventiladorEncendido") ?: false,
                    modoAutomatico = d.getBoolean("modoAutomatico") ?: true,
                    humedadUmbral = d.getDouble("humedadUmbral") ?: 40.0,
                    temperaturaUmbral = d.getDouble("temperaturaUmbral") ?: 28.0,
                    sensorId = d.getString("sensorId"),
                    alertaUmbral = d.getDouble("alertaUmbral") ?: 35.0,
                    alertaEstado = d.getString("alertaEstado") ?: SimulacionEngine.ALERTA_NINGUNA,
                    alertaInicio = d.getLong("alertaInicio") ?: 0L,
                    ventiladorEmergencia = d.getBoolean("ventiladorEmergencia") ?: false
                )
                revisarNotificacion(d.reference, plantas[d.reference.path]!!.second)
            }
        }

        jobSimulacion = scope.launch {
            while (true) {
                delay(SimulacionEngine.INTERVALO_MS)
                renovarLiderazgo(db)
                if (soyLider) {
                    plantas.values.toList().forEach { (ref, planta) -> avanzar(db, ref, planta) }
                }
            }
        }
    }

    // Solo una instancia (teléfono) simula a la vez
    private fun renovarLiderazgo(db: FirebaseFirestore) {
        val ref = db.collection("sistema").document("simulador")
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            val ahora = System.currentTimeMillis()
            val dueno = snap.getString("propietario")
            val expira = snap.getLong("expira") ?: 0L
            if (dueno == instanciaId || expira < ahora) {
                tx.set(ref, mapOf("propietario" to instanciaId, "expira" to ahora + LEASE_MS))
                true
            } else {
                false
            }
        }
            .addOnSuccessListener { soyLider = it }
            .addOnFailureListener { soyLider = false }
    }

    private fun avanzar(db: FirebaseFirestore, ref: DocumentReference, planta: Planta) {
        val r = SimulacionEngine.siguiente(planta)
        val uidDueno = ref.parent.parent?.id

        if (r.bomba != planta.bombaEncendida) {
            Registro.logSistema(
                db,
                "${planta.nombre}: Bomba ${if (r.bomba) "encendida" else "apagada"} automáticamente (humedad %.1f%%)".format(r.humedad),
                plantaId = planta.id,
                uidAfectado = uidDueno
            )
        }
        if (r.ventilador != planta.ventiladorEncendido) {
            Registro.logSistema(
                db,
                "${planta.nombre}: Ventilador ${if (r.ventilador) "encendido" else "apagado"} automáticamente (temperatura %.1f°C)".format(r.temperatura),
                plantaId = planta.id,
                uidAfectado = uidDueno
            )
        }

        if (r.alertaEstado != planta.alertaEstado) {
            val texto = when (r.alertaEstado) {
                SimulacionEngine.ALERTA_PENDIENTE ->
                    "${planta.nombre}: ALERTA de temperatura (%.1f°C ≥ %.0f°C). El usuario tiene 2 min para actuar".format(r.temperatura, planta.alertaUmbral)
                SimulacionEngine.ALERTA_EMERGENCIA ->
                    "${planta.nombre}: EMERGENCIA activada por falta de respuesta. Ventiladores de emergencia ON (%.1f°C)".format(r.temperatura)
                else ->
                    "${planta.nombre}: alerta de temperatura finalizada (%.1f°C)".format(r.temperatura)
            }
            Registro.logSistema(db, texto, plantaId = planta.id, uidAfectado = uidDueno, tipo = "ALERTA")
        }

        ref.update(
            mapOf(
                "temperatura" to r.temperatura,
                "humedad" to r.humedad,
                "ventiladorEncendido" to r.ventilador,
                "bombaEncendida" to r.bomba,
                "alertaEstado" to r.alertaEstado,
                "alertaInicio" to r.alertaInicio,
                "ventiladorEmergencia" to r.emergencia,
                "ultimaActualizacion" to System.currentTimeMillis()
            )
        )
    }

    // ---------- ALERTAS: notificación con sonido en el teléfono del dueño (y de los admins) ----------
    private fun revisarNotificacion(ref: DocumentReference, planta: Planta) {
        val ruta = ref.path
        val idNotif = ruta.hashCode()
        val estado = planta.alertaEstado

        if (estado != SimulacionEngine.ALERTA_PENDIENTE && estado != SimulacionEngine.ALERTA_EMERGENCIA) {
            ultimoNotificado.remove(ruta)
            getSystemService(NotificationManager::class.java).cancel(idNotif)
            return
        }

        val miUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val uidDueno = ref.parent.parent?.id
        if (uidDueno != miUid && !esAdminLocal) return

        val clave = "$estado:${planta.alertaInicio}"
        if (ultimoNotificado[ruta] == clave) return
        ultimoNotificado[ruta] = clave

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val emergencia = estado == SimulacionEngine.ALERTA_EMERGENCIA
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val atender = PendingIntent.getService(
            this, idNotif,
            Intent(this, SimulacionService::class.java).setAction(ACCION_ATENDER).putExtra(EXTRA_RUTA, ruta),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val titulo = if (emergencia) "🚨 EMERGENCIA: ${planta.nombre}" else "⚠️ Alerta de temperatura: ${planta.nombre}"
        val texto = if (emergencia)
            "No se atendió a tiempo. Ventiladores de emergencia activados (%.1f°C).".format(planta.temperatura)
        else
            "%.1f°C supera el umbral de %.0f°C. Tienes 2 minutos para actuar.".format(planta.temperatura, planta.alertaUmbral)

        val notif = NotificationCompat.Builder(this, CANAL_ALERTAS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) // Android < 8
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .apply { if (!emergencia) addAction(0, "Atender (ventilador ON)", atender) }
            .build()

        getSystemService(NotificationManager::class.java).notify(idNotif, notif)
    }

    // El usuario toca "Atender" en la notificación: se enciende el ventilador y se evita la emergencia
    private fun atenderDesdeNotificacion(ruta: String) {
        val db = dDb ?: return
        db.document(ruta).update(
            mapOf(
                "alertaEstado" to SimulacionEngine.ALERTA_ATENDIDA,
                "ventiladorEmergencia" to false,
                "ventiladorEncendido" to true,
                "ultimaActualizacion" to System.currentTimeMillis()
            )
        )
        Registro.logSistema(
            db,
            "Alerta de temperatura atendida desde la notificación (ventilador encendido)",
            plantaId = ruta.substringAfterLast('/'),
            uidAfectado = ruta.split('/').getOrNull(1),
            tipo = "ALERTA"
        )
        getSystemService(NotificationManager::class.java).cancel(ruta.hashCode())
    }

    private fun crearCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ID,
                "Simulador de dispositivos",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Mantiene activa la simulación de sensores y actuadores" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(canal)

            // Canal de alertas: importancia máxima, con sonido de alarma y vibración
            val alertas = NotificationChannel(
                CANAL_ALERTAS,
                "Alertas de temperatura",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisa cuando la temperatura de una planta supera el umbral de alerta"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
            }
            nm.createNotificationChannel(alertas)
        }
    }

    private fun notificacionPermanente(): Notification {
        val abrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("SmartGarden activo")
            .setContentText("Simulador de dispositivos funcionando")
            .setContentIntent(abrir)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        FirebaseAuth.getInstance().removeAuthStateListener(authListener)
        listenerPerfil?.remove()
        listenerPlantas?.remove()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
