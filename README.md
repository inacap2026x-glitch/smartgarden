# SmartGarden

Aplicación Android para monitoreo y control de riego/ventilación de plantas (IoT).
Proyecto de la Unidad 2 — Aplicaciones Móviles Android.

## Tecnologías
- Kotlin + Jetpack Compose (Material 3), arquitectura MVVM, Navigation Compose
- Firebase Authentication y Cloud Firestore
- Servicio en primer plano (simulador de dispositivos ESP32) y notificaciones de alerta

## Funcionalidades
- Registro, login seguro (bloqueo tras 5 intentos), recuperación de contraseña y sesión persistente
- Gestión de plantas con sensores asignables y umbrales configurables por planta
- Control automático y manual de bomba de agua y ventilador
- Alerta de temperatura con notificación y emergencia automática
- Roles usuario / administrador (usuarios, plantas, sensores, registros)

## Cómo ejecutar
1. Abre el proyecto con Android Studio.
2. Crea un proyecto en Firebase, registra la app con el paquete `com.app.smartgarden` y habilita
   *Authentication* (correo/contraseña y acceso anónimo) y *Firestore*.
3. Descarga tu `google-services.json` y colócalo en `app/` (ver `google-services.json.example`).
4. Publica las reglas de seguridad de Firestore del proyecto.
5. Ejecuta la app en un emulador o dispositivo (Android 8+).

## Nota
`google-services.json` no se incluye en el repositorio por ser configuración privada del proyecto Firebase.
