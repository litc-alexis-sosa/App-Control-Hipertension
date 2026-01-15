package com.example.apphipertension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Teléfono reiniciado. Reprogramando alarma.")

            // ---------------------------------------------------------
            // 1. TU CÓDIGO ORIGINAL (INTACTO)
            // ---------------------------------------------------------
            try {
                AlarmUtils.scheduleDailyTipAlarm(context)
                AlarmUtils.scheduleAllReminders(context)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error en AlarmUtils: ${e.message}")
            }

            // ---------------------------------------------------------
            // 2. NUEVO CÓDIGO: Reprogramar Medicamentos de Firebase
            // ---------------------------------------------------------
            reprogramarMedicamentosFirebase(context)
        }
    }

    private fun reprogramarMedicamentosFirebase(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.d("BootReceiver", "No hay usuario logueado en Firebase. No se reprograman medicamentos.")
            return
        }

        // 'goAsync()' le dice a Android: "Espérame, voy a hacer algo tardado (conectar a internet)"
        // Si no usas esto, Android mata el BootReceiver antes de recibir los datos de Firebase.
        val pendingResult = goAsync()

        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .document(user.uid)
            .collection("medicamentos")
            .whereEqualTo("tieneRecordatorio", true) // Solo traemos los activos
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    try {
                        val datos = doc.data
                        // Recuperamos el ID numérico de la alarma
                        val alarmIdLong = datos["alarmId"] as? Long ?: datos["alarmId"] as? Int ?: 0

                        // Reconstruimos el objeto Medicine
                        val medicine = Medicine(
                            id = doc.id,
                            nombre = datos["nombre"] as? String ?: "",
                            dosis = datos["dosis"] as? String ?: "",
                            unidad = datos["unidad"] as? String ?: "",
                            hora = datos["hora"] as? String ?: "",
                            frecuencia = datos["frecuencia"] as? String ?: "",
                            fecha = datos["fecha"] as? String ?: "",
                            tieneRecordatorio = true,
                            alarmId = alarmIdLong.toInt()
                        )

                        // Llamamos a nuestro nuevo Scheduler compartido
                        MedicationAlarmScheduler.programarAlarma(context, medicine)
                        Log.d("BootReceiver", "Alarma reprogramada para: ${medicine.nombre} (${medicine.hora})")

                    } catch (e: Exception) {
                        Log.e("BootReceiver", "Error al procesar medicamento: ${e.message}")
                    }
                }
                // Avisamos al sistema que terminamos
                pendingResult.finish()
            }
            .addOnFailureListener { e ->
                Log.e("BootReceiver", "Error al conectar con Firestore: ${e.message}")
                pendingResult.finish()
            }
    }
}