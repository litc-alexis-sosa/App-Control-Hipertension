package com.example.apphipertension

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nombreMed = intent.getStringExtra("NOMBRE_MED") ?: "Medicamento"
        val dosisMed = intent.getStringExtra("DOSIS_MED") ?: ""

        // 1. Crear el canal de Alta Prioridad (Sonido + Vibración + Pop-up)
        crearCanalNotificacion(context)

        // 2. Intent para abrir la app al tocar la notificación
        val tapIntent = Intent(context, Medicate::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Sonido de Alarma (No de notificación normal)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        // 4. Construir la notificación estilo "Alarma"
        val builder = NotificationCompat.Builder(context, "canal_med_alta_prioridad")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Cambia esto por tu icono (R.drawable.ic_tu_icono)
            .setContentTitle("¡Hora de tu medicina!")
            .setContentText("Te toca tomar: $nombreMed ($dosisMed)")
            .setPriority(NotificationCompat.PRIORITY_MAX) // ¡IMPORTANTE! MAX para que salga arriba
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Categoría Alarma
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible en pantalla bloqueada
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 1000, 500, 1000)) // Patrón de vibración fuerte
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true) // <--- ESTO PRENDE LA PANTALLA COMO UNA LLAMADA

            // Botón de "Ya la tomé" (Cierra la notificación)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Ya la tomé",
                pendingIntent
            )

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            // Usamos un ID único basado en el tiempo para que no se reemplacen si se juntan varias
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun crearCanalNotificacion(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alarmas de Medicación"
            val descriptionText = "Notificaciones de alta prioridad para tomar medicina"
            val importance = NotificationManager.IMPORTANCE_HIGH // IMPORTANCIA ALTA

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel("canal_med_alta_prioridad", name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setSound(alarmSound, audioAttributes)
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}