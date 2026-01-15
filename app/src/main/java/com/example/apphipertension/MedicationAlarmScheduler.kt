package com.example.apphipertension


import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object MedicationAlarmScheduler {

    fun programarAlarma(context: Context, medicine: Medicine) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // --- ID de Alarma ---
            // Aseguramos que el ID sea único y constante para este medicamento
            val pendingIntentId = medicine.alarmId

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("NOMBRE_MED", medicine.nombre)
                putExtra("DOSIS_MED", "${medicine.dosis} ${medicine.unidad}")
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                pendingIntentId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 1. Limpieza y parseo de hora
            val horaLimpia = medicine.hora
                .replace(".", "")
                .replace(" ", " ")
                .uppercase()
                .trim()

            val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
            val horaLocal = java.time.LocalTime.parse(horaLimpia, formatter)

            // 2. Configurar calendario base con la hora elegida
            val calendario = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, horaLocal.hour)
                set(Calendar.MINUTE, horaLocal.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 3. Obtener intervalo
            val intervaloMillis = obtenerIntervaloMillis(medicine.frecuencia)

            // 4. LÓGICA CORREGIDA: "La Trampa del Mañana"
            // Si la hora ya pasó...
            if (calendario.timeInMillis <= System.currentTimeMillis()) {

                // CASO A: Si es medicina DIARIA (o cada 24h) -> Pasamos a mañana
                if (intervaloMillis >= AlarmManager.INTERVAL_DAY) {
                    calendario.add(Calendar.DAY_OF_YEAR, 1)
                }
                // CASO B: Si es por HORAS (cada 2, 4, 8h...) -> Sumamos horas hasta llegar al futuro
                else {
                    while (calendario.timeInMillis <= System.currentTimeMillis()) {
                        calendario.timeInMillis += intervaloMillis
                    }
                }
            }

            // 5. Programar
            // Nota: setRepeating es "inexacto" en Android moderno para ahorrar batería.
            // Puede variar unos minutos, pero sonará.
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendario.timeInMillis,
                intervaloMillis,
                pendingIntent
            )

            // Log para verificar
            val fechaHoraLog = java.text.SimpleDateFormat("dd/MM HH:mm").format(calendario.time)
            android.util.Log.d("SCHEDULER", "¡ÉXITO! Alarma programada para: ${medicine.nombre} a las $fechaHoraLog (Repite cada ${intervaloMillis/3600000}h)")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelarAlarma(context: Context, medicine: Medicine) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                medicine.alarmId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            android.util.Log.d("SCHEDULER", "Alarma cancelada para: ${medicine.nombre}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun obtenerIntervaloMillis(frecuenciaTexto: String): Long {
        val regex = Regex("\\d+")
        val resultado = regex.find(frecuenciaTexto)
        val horas = resultado?.value?.toLongOrNull()

        return if (horas != null) {
            horas * 3600 * 1000L
        } else {
            AlarmManager.INTERVAL_DAY
        }
    }
}