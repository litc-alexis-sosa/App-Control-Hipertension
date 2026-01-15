package com.example.apphipertension

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

object AlarmUtils {

    private const val ALARM_REQUEST_CODE = 1001

    fun scheduleDailyTipAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyTipReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Configurar la hora de la alarma (ej. 9:00 AM)
        val calendar: Calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        // Si la hora de la alarma ya pasó hoy, programarla para mañana
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Programar la alarma repetitiva
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, // Despertar el teléfono
            calendar.timeInMillis,   // Hora de inicio (ej. mañana a las 9 AM)
            AlarmManager.INTERVAL_DAY, // Repetir cada día
            pendingIntent
        )

        Log.d("AlarmUtils", "Alarma programada para las 9:00 AM, comenzando en ${calendar.time}")
    }

    fun scheduleAllReminders(context: Context) {
        // Programar recordatorios de MEDICIÓN (8:00 y 19:00)
        scheduleRepeatingAlarm(context, "REMIND_MEDICION", 2001, 8, 0)
        scheduleRepeatingAlarm(context, "REMIND_MEDICION", 2002, 19, 0)

        // Programar recordatorios de COMIDA (9:00, 14:00, 20:30)
        scheduleRepeatingAlarm(context, "REMIND_COMIDA", 2003, 9, 0)
        scheduleRepeatingAlarm(context, "REMIND_COMIDA", 2004, 14, 0)
        scheduleRepeatingAlarm(context, "REMIND_COMIDA", 2005, 20, 30)

        // Programar recordatorio de ACTIVIDAD (17:00)
        scheduleRepeatingAlarm(context, "REMIND_ACTIVIDAD", 2006, 17, 0)

        // Programar recordatorio de SÍNTOMAS (20:00)
        scheduleRepeatingAlarm(context, "REMIND_SINTOMAS", 2007, 20, 0)

        Log.d("AlarmUtils", "Todos los recordatorios de registro han sido programados.")
    }

    // --- NUEVA FUNCIÓN HELPER ---
    private fun scheduleRepeatingAlarm(
        context: Context,
        reminderType: String,
        requestCode: Int,
        hour: Int,
        minute: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_TYPE", reminderType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode, // ¡Request Code ÚNICO por alarma!
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar: Calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

}
