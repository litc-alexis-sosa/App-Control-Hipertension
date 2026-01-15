package com.example.apphipertension


import android.util.Log // Asegúrate de tener esta importación
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DataFetcher {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getUid(): String? = auth.currentUser?.uid

    suspend fun fetchAllReportData(): AnalysisReport? {
        val uid = getUid() ?: return null

        try {
            // 1. Perfil (Actualizado)
            val profileDoc = db.collection("users").document(uid).get().await()
            val profileData = profileDoc.let { doc ->
                ProfileData(
                    nombre = doc.getString("nombre") ?: auth.currentUser?.email ?: "Usuario",
                    correo = auth.currentUser?.email ?: "",
                    peso = doc.getString("peso") ?: "N/A",
                    altura = doc.getString("altura") ?: "N/A",
                    imc = doc.getString("imc") ?: "N/A",
                    fechaNacimiento = doc.getString("fecha_nacimiento") ?: "N/A",
                    edad = doc.getString("edad") ?: "N/A",
                    sexo = doc.getString("sexo") ?: "N/A",
                    proxima_cita_medica = doc.getString("proxima_cita_medica") ?: "N/A",
                    meta_calorias_diarias = doc.getDouble("meta_calorias_diarias") ?: 0.0,
                    alimentosEvitar_pre = doc.get("alimentosEvitar_pre") as? List<String> ?: emptyList(),
                    alimentosEvitar_per = doc.get("alimentosEvitar_per") as? List<String> ?: emptyList(),
                    medicamentosEvitar = doc.get("medicamentosEvitar") as? List<String> ?: emptyList(),
                    padecimientos_pre = doc.get("padecimientos_pre") as? List<String> ?: emptyList(),
                    padecimientos_per = doc.get("padecimientos_per") as? List<String> ?: emptyList()
                )
            }

            // 2. Mediciones (Sin limit)
            val medicionesSnapshot = db.collection("users").document(uid).collection("mediciones")
                .orderBy("date", Query.Direction.DESCENDING)
                .orderBy("time", Query.Direction.DESCENDING)
                // .limit(20) <-- ELIMINADO
                .get().await()
            val mediciones = medicionesSnapshot.toObjects(Measurement::class.java)

            // 3. Medicamentos (Sin limit)
            val medsSnapshot = db.collection("users").document(uid).collection("medicamentos")
                .orderBy("fecha", Query.Direction.DESCENDING) // Añadido orden
                .get().await()
            val medicamentos = medsSnapshot.toObjects(Medicine::class.java)

            // 4. Síntomas (Sin limit)
            val sintomasSnapshot = db.collection("users").document(uid).collection("sintomas")
                .orderBy("fecha", Query.Direction.DESCENDING)
                .orderBy("hora", Query.Direction.DESCENDING)
                // .limit(10) <-- ELIMINADO
                .get().await()

            val sintomas = sintomasSnapshot.documents.mapNotNull { doc ->
                val sintomasRaw = doc.get("sintomas") as? List<HashMap<String, Any>> ?: emptyList()
                val sintomaList = sintomasRaw.map { item ->
                    Sintoma(
                        id = item["id"] as? String ?: "",
                        nombre = item["nombre"] as? String ?: "Sintoma desconocido",
                        iconResId = 0,
                        seleccionado = true,
                        nota = if (item["id"] == "otro") item["nombre"] as? String else null
                    )
                }
                SintomaGuardado(
                    documentId = doc.id,
                    fecha = doc.getString("fecha") ?: "N/A",
                    hora = doc.getString("hora") ?: "N/A",
                    sintomas = sintomaList,
                    nota = doc.getString("nota") ?: ""
                )
            }

            // 5. Cargar Registros de Dieta (Sin limit)
            val dietaSnapshot = db.collection("users").document(uid).collection("registros_dieta")
                .orderBy(com.google.firebase.firestore.FieldPath.documentId(), Query.Direction.DESCENDING)
                // .limit(7) <-- ELIMINADO
                .get().await()
            val registrosDieta = dietaSnapshot.mapNotNull { it.toObject<RegistroDieta>() }

            // 6. Cargar Registros de Actividad Física (Sin limit)
            val actividadSnapshot = db.collection("users").document(uid).collection("actividades_fisicas")
                .orderBy("fecha", Query.Direction.DESCENDING)
                .orderBy("hora", Query.Direction.DESCENDING)
                // .limit(7) <-- ELIMINADO
                .get().await()
            val registrosActividad = actividadSnapshot.documents.mapNotNull { doc ->
                val actividadesRaw = doc.get("actividades") as? List<HashMap<String, Any>> ?: emptyList()

                // --- CORRECCIÓN AQUÍ ---
                // Mapeo manual de HashMap a ActividadRegistrada
                val actividades = actividadesRaw.map { map ->
                    ActividadRegistrada(
                        id = map["id"] as? String ?: "",
                        nombre = map["nombre"] as? String ?: "Desconocida",
                        duracionEnMinutos = (map["duracionEnMinutos"] as? Long)?.toInt() ?: 0 // Firestore guarda números como Long
                    )
                }
                // --- FIN DE LA CORRECCIÓN ---

                ActividadGuardada(
                    documentId = doc.id,
                    fecha = doc.getString("fecha") ?: "",
                    hora = doc.getString("hora") ?: "",
                    actividades = actividades,
                    nota = doc.getString("nota")
                )
            }

            return AnalysisReport(profileData, mediciones, medicamentos, sintomas, registrosDieta, registrosActividad)

        } catch (e: Exception) {
            Log.e("DataFetcher", "Error fetching report data", e)
            e.printStackTrace()
            return null
        }
    }
}