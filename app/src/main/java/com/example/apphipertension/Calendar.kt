package com.example.apphipertension

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CalendarView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.firebase.firestore.ktx.toObject
import com.example.apphipertension.ActividadRegistrada
import com.example.apphipertension.ActividadGuardada
import com.google.firebase.firestore.Query
import com.example.apphipertension.AlimentoRegistrado

class Calendar : AppCompatActivity() {
    private lateinit var calendarView: CalendarView
    private lateinit var cardsContainer: LinearLayout

    private var selectedDate: Long? = null // Fecha seleccionada en milisegundos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_calendar)

        calendarView = findViewById(R.id.calendarView)
        cardsContainer = findViewById(R.id.cardsContainer)

        // Selecciona la fecha de hoy por default
        selectedDate = calendarView.date
        updateCardsForSelectedDate(selectedDate!!)

        // Listener al cambiar día
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month, dayOfMonth, 0, 0, 0)
            selectedDate = cal.timeInMillis
            updateCardsForSelectedDate(cal.timeInMillis)
        }

        //MenuInferior
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_calendar
        bottomNav.setOnItemSelectedListener { item ->
            val intent: Intent? = when (item.itemId) {
                R.id.nav_home -> Intent(this, MainActivity::class.java)
                R.id.nav_meds -> Intent(this, Medicate::class.java)
                R.id.nav_calendar -> null
                R.id.nav_profile -> Intent(this, Profile::class.java)
                R.id.nav_more -> {
                    showMoreMenuBottomSheet()
                    null
                }
                else -> null
            }

            if (intent != null) {
                // --- USE THESE FLAGS ---
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                // -----------------------
                startActivity(intent)
                // No need for finish() or overridePendingTransition here
                true // Indicate navigation occurred
            } else {
                item.itemId == R.id.nav_calendar || item.itemId == R.id.nav_more
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


    }

    override fun onResume() {
        super.onResume()
        // Ensure the correct item is selected every time the activity becomes visible
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_calendar // Set it again here
    }

    // Muestra un menú con "Análisis", "Síntomas"...
    private fun showMoreMenuBottomSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_more, null)
        sheet.setContentView(view)

        view.findViewById<LinearLayout>(R.id.llAnalisis).setOnClickListener {
            startActivity(Intent(this, Analysis::class.java))
            sheet.dismiss()
        }
        view.findViewById<LinearLayout>(R.id.llSintomas).setOnClickListener {
            startActivity(Intent(this, Sintomas::class.java))
            sheet.dismiss()
        }
        view.findViewById<LinearLayout>(R.id.llDieta).setOnClickListener {
            startActivity(Intent(this, Dieta::class.java))
            sheet.dismiss()
        }
        view.findViewById<LinearLayout>(R.id.llActividadFisica).setOnClickListener {
            startActivity(Intent(this, ActividadFisica::class.java))
            sheet.dismiss()
        }
        sheet.show()
    }

    // Aquí traes los datos de Firestore por fecha (ejemplo: filtra las mediciones de ese día)
    private fun updateCardsForSelectedDate(dateMillis: Long) {
        val cardsContainer = findViewById<LinearLayout>(R.id.cardsContainer)
        cardsContainer.removeAllViews() // Clear previous views
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val selectedDateStr = sdf.format(java.util.Date(dateMillis))
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        // --- SECTION LAYOUT CREATION ---
        val layoutMediciones = createSectionLayout("Mediciones")
        val emptyMed = createEmptyMessage("Sin mediciones este día")
        layoutMediciones.addView(emptyMed)

        val layoutSintomas = createSectionLayout("Síntomas")
        val emptySintomas = createEmptyMessage("Sin síntomas registrados este día")
        layoutSintomas.addView(emptySintomas)

        // --- NEW: DIET SECTION ---
        val layoutDieta = createSectionLayout("Dieta")
        val emptyDieta = createEmptyMessage("Sin registro de dieta este día")
        layoutDieta.addView(emptyDieta)
        // -------------------------

        // --- NEW: ACTIVITY SECTION ---
        val layoutActividad = createSectionLayout("Actividad Física")
        val emptyActividad = createEmptyMessage("Sin actividad física registrada este día")
        layoutActividad.addView(emptyActividad)
        // -------------------------

        val layoutMedicamentos = createSectionLayout("Medicamentos")
        val emptyMeds = createEmptyMessage("Sin medicamentos este día")
        layoutMedicamentos.addView(emptyMeds)

        // Add layouts to the main container IN ORDER
        cardsContainer.addView(layoutMediciones)
        cardsContainer.addView(layoutSintomas)
        cardsContainer.addView(layoutDieta)      // <-- Added Diet
        cardsContainer.addView(layoutActividad) // <-- Added Activity
        cardsContainer.addView(layoutMedicamentos)

        // ----- 1. MEDICIONES -----
        db.collection("users")
            .document(user.uid)
            .collection("mediciones")
            .whereEqualTo("date", selectedDateStr)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    layoutMediciones.removeView(emptyMed)
                    for (doc in result.documents) {
                        // --- CAMBIA EL LAYOUT AQUÍ ---
                        val view = layoutInflater.inflate(R.layout.item_calendar_measurement, layoutMediciones, false)
                        // -----------------------------

                        val date = doc.getString("date") ?: selectedDateStr
                        val time = doc.getString("time") ?: ""
                        val hora12 = try {
                            LocalTime.parse(time).format(DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "ES"))) // Usar Locale
                        } catch (e: Exception) {
                            time
                        }
                        // Usar los IDs del nuevo layout (son los mismos en este caso)
                        view.findViewById<TextView>(R.id.tvFechaHora).text =
                            if (time.isNotBlank()) "$date $hora12" else date

                        val sistolica = doc.getLong("sistolica")?.toString() ?: "-"
                        val diastolica = doc.getLong("diastolica")?.toString() ?: "-"
                        view.findViewById<TextView>(R.id.tvPresion).text =
                            "Sistólica/Diastólica: $sistolica/$diastolica mmHg"

                        val pulso = doc.getLong("pulso")?.toString() ?: "-"
                        view.findViewById<TextView>(R.id.tvPulso).text =
                            "Pulso: $pulso LPM"

                        // Lógica para la nota (¡Asegúrate de que funcione!)
                        val tvNota = view.findViewById<TextView>(R.id.tvNota)
                        val nota = doc.getString("nota")
                        if (nota.isNullOrEmpty()) {
                            tvNota.visibility = View.GONE
                        } else {
                            tvNota.visibility = View.VISIBLE
                            tvNota.text = "Nota: $nota"
                        }

                        // --- ELIMINA ESTAS LÍNEAS ---
                        // view.findViewById<Button>(R.id.btnEditar)?.visibility = View.GONE
                        // view.findViewById<Button>(R.id.btnQuitar)?.visibility = View.GONE
                        // ---------------------------

                        layoutMediciones.addView(view)
                    }
                } else {
                    if (layoutMediciones.childCount <= 1) { layoutMediciones.addView(emptyMed) } // Re-add if empty
                }
            }
            .addOnFailureListener { // Add listener if missing
                Log.e("CalendarMeds", "Error loading measurements", it)
                layoutMediciones.removeView(emptyMed)
                layoutMediciones.addView(createEmptyMessage("Error al cargar mediciones"))
            }

        // ----- 2. SÍNTOMAS -----
        db.collection("users")
            .document(user.uid)
            .collection("sintomas")
            .whereEqualTo("fecha", selectedDateStr)
            .orderBy("hora", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    layoutSintomas.removeView(emptySintomas)
                    for (doc in result.documents) {
                        val view = layoutInflater.inflate(R.layout.item_calendar_sintoma, layoutSintomas, false)

                        // --- THIS IS THE MISSING/COMMENTED CODE ---
                        // Find the TextViews using their IDs from item_calendar_sintoma.xml
                        val tvHora = view.findViewById<TextView>(R.id.tvSintomaHora)
                        val tvLista = view.findViewById<TextView>(R.id.tvSintomaLista)
                        val tvNota = view.findViewById<TextView>(R.id.tvSintomaNota)

                        // Format the hour
                        val hora = doc.getString("hora") ?: ""
                        val hora12 = try {
                            LocalTime.parse(hora).format(DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "ES")))
                        } catch (e: Exception) { hora }
                        tvHora.text = "Hora del registro: $hora12"

                        // Format the symptom list
                        val sintomasRaw = doc.get("sintomas") as? List<HashMap<String, Any>> ?: emptyList()
                        val sintomasText = sintomasRaw.joinToString(separator = ", ") { it["nombre"] as? String ?: "Desconocido" }
                        tvLista.text = "Síntomas: $sintomasText"

                        // Handle the optional note
                        val nota = doc.getString("nota")
                        if (!nota.isNullOrEmpty()) {
                            tvNota.text = "Nota General: $nota"
                            tvNota.visibility = View.VISIBLE // Make sure it's visible
                        } else {
                            tvNota.visibility = View.GONE // Hide if empty
                        }
                        // ------------------------------------------

                        layoutSintomas.addView(view)
                    }
                } else {
                    // Ensure empty message is shown if result becomes empty later
                    if (layoutSintomas.childCount <= 1) { // Only title is present
                        layoutSintomas.addView(emptySintomas)
                    }
                }
            }
            .addOnFailureListener { e -> // Good to have this
                Log.e("CalendarSintomas", "Error loading symptoms", e)
                layoutSintomas.removeView(emptySintomas)
                layoutSintomas.addView(createEmptyMessage("Error al cargar síntomas"))
            }

        // ----- 3. DIETA (NEW) -----
        db.collection("users").document(user.uid).collection("registros_dieta")
            .document(selectedDateStr)
            .get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    layoutDieta.removeView(emptyDieta)
                    val registroDieta = doc.toObject<RegistroDieta>()
                    if (registroDieta != null) {
                        val view = layoutInflater.inflate(R.layout.item_calendar_dieta, layoutDieta, false)
                        val tvTotal = view.findViewById<TextView>(R.id.tvDietaTotalCalorias)
                        val tvResumen = view.findViewById<TextView>(R.id.tvDietaResumen)

                        tvTotal.text = "Total Calorías: ${"%.0f".format(registroDieta.calorias_totales_dia)} cal"

                        // --- CAMBIO AQUÍ: Construir el texto detallado ---
                        val resumenBuilder = StringBuilder() // Usamos StringBuilder para eficiencia

                        val desayunoStr = formatarListaAlimentos(registroDieta.desayuno)
                        if (desayunoStr.isNotEmpty()) {
                            resumenBuilder.append("Desayuno: ").append(desayunoStr).append("\n") // Añade salto de línea
                        }

                        val comidaStr = formatarListaAlimentos(registroDieta.comida)
                        if (comidaStr.isNotEmpty()) {
                            resumenBuilder.append("Comida: ").append(comidaStr).append("\n")
                        }

                        val cenaStr = formatarListaAlimentos(registroDieta.cena)
                        if (cenaStr.isNotEmpty()) {
                            resumenBuilder.append("Cena: ").append(cenaStr).append("\n")
                        }

                        val colacionStr = formatarListaAlimentos(registroDieta.colacion)
                        if (colacionStr.isNotEmpty()) {
                            resumenBuilder.append("Colación: ").append(colacionStr).append("\n")
                        }

                        // Asigna el texto final o un mensaje si todo está vacío
                        tvResumen.text = if (resumenBuilder.isEmpty()) {
                            "Sin alimentos registrados"
                        } else {
                            resumenBuilder.toString().trim() // trim() quita el último salto de línea
                        }
                        // --------------------------------------------------

                        layoutDieta.addView(view)
                    }
                } else {
                    // Asegura mostrar mensaje vacío si el documento no existe
                    if(layoutDieta.childCount <= 1){ // Solo el título
                        layoutDieta.removeView(emptyDieta) // Limpia por si acaso
                        layoutDieta.addView(emptyDieta)
                    }
                }
            }
            .addOnFailureListener { // Es bueno tener esto
                Log.e("CalendarDieta", "Error al cargar dieta", it)
                layoutDieta.removeView(emptyDieta)
                layoutDieta.addView(createEmptyMessage("Error al cargar dieta"))
            }

        // ----- 4. ACTIVIDAD FÍSICA (NEW) -----
        db.collection("users").document(user.uid).collection("actividades_fisicas")
            .whereEqualTo("fecha", selectedDateStr)
            .orderBy("hora", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .get().addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    layoutActividad.removeView(emptyActividad)
                    for (doc in result.documents) {
                        // We map directly to ActividadGuardada for consistency, even if there's only one doc per day usually
                        val actividadGuardada = ActividadGuardada(
                            documentId = doc.id,
                            fecha = doc.getString("fecha") ?: "",
                            hora = doc.getString("hora") ?: "",
                            actividades = (doc.get("actividades") as? List<HashMap<String, Any>> ?: emptyList()).map { map ->
                                ActividadRegistrada(
                                    id = map["id"] as? String ?: "",
                                    nombre = map["nombre"] as? String ?: "",
                                    duracionEnMinutos = (map["duracionEnMinutos"] as? Long)?.toInt() ?: 0
                                )
                            },
                            nota = doc.getString("nota")
                        )


                        val view = layoutInflater.inflate(R.layout.item_calendar_actividad, layoutActividad, false)

                        // Find Views (using example IDs)
                        val tvHora = view.findViewById<TextView>(R.id.tvActividadHora)
                        val tvLista = view.findViewById<TextView>(R.id.tvActividadLista)
                        val tvNota = view.findViewById<TextView>(R.id.tvActividadNota)

                        // Set Text
                        val hora12 = try {
                            LocalTime.parse(actividadGuardada.hora).format(DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "ES")))
                        } catch (e: Exception) { actividadGuardada.hora }
                        tvHora.text = "Hora: $hora12"

                        val actividadesText = actividadGuardada.actividades.joinToString(", ") {
                            "${it.nombre} (${it.duracionEnMinutos} min)"
                        }
                        tvLista.text = "Actividades: $actividadesText"

                        if (!actividadGuardada.nota.isNullOrEmpty()) {
                            tvNota.visibility = View.VISIBLE
                            tvNota.text = "Nota: ${actividadGuardada.nota}"
                        } else {
                            tvNota.visibility = View.GONE
                        }

                        layoutActividad.addView(view)
                    }
                }
            }

        // ----- 5. MEDICAMENTOS -----
        db.collection("users")
            .document(user.uid)
            .collection("medicamentos")
            // You might need to adjust the query if the 'fecha' field
            // doesn't directly match the selectedDateStr format or purpose.
            // For example, if 'fecha' is the *start* date of the medication.
            // If you only want to show meds scheduled for *that specific day*,
            // the query might be more complex or you might filter in the code.
            // For now, let's assume 'fecha' means "taken on this date" for simplicity.
            .whereEqualTo("fecha", selectedDateStr)
            .orderBy("hora", Query.Direction.ASCENDING) // Optional: order by time
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    layoutMedicamentos.removeView(emptyMeds)
                    for (doc in result.documents) {
                        // --- INFLATE THE NEW LAYOUT ---
                        val view = layoutInflater.inflate(R.layout.item_calendar_medicine, layoutMedicamentos, false)

                        // --- FIND VIEWS BY NEW IDS ---
                        val tvHora = view.findViewById<TextView>(R.id.tvMedicineHora)
                        val tvNombre = view.findViewById<TextView>(R.id.tvMedicineNombre)
                        val tvDosisFreq = view.findViewById<TextView>(R.id.tvMedicineDosisFreq)

                        // --- SET TEXT ---
                        val hora = doc.getString("hora") ?: ""
                        val hora12 = try {
                            LocalTime.parse(hora).format(DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "ES")))
                        } catch (e: Exception) { hora }
                        tvHora.text = "Hora de toma: $hora12"

                        tvNombre.text = doc.getString("nombre") ?: "Medicamento desconocido"

                        val dosis = doc.getString("dosis") ?: ""
                        val unidad = doc.getString("unidad") ?: ""
                        val frecuencia = doc.getString("frecuencia") ?: ""
                        tvDosisFreq.text = "Dosis: $dosis $unidad | Frecuencia: $frecuencia".trim()

                        // No need to hide buttons as they don't exist in the new layout

                        layoutMedicamentos.addView(view)
                    }
                }
            }
    } // End of updateCardsForSelectedDate


    // --- Helper Functions para crear títulos y mensajes de vacío ---
    private fun createSectionLayout(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)

            // Título de la sección
            val titleView = TextView(this@Calendar).apply {
                text = title
                setTextColor(Color.parseColor("#466B95"))
                textSize = 17f
                setPadding(0, 16, 0, 8)
                setTypeface(typeface, Typeface.BOLD)
            }
            addView(titleView)
        }
    }

    private fun createEmptyMessage(message: String): TextView {
        return TextView(this).apply {
            text = message
            setTextColor(Color.GRAY)
            textSize = 15f
        }
    }

    private fun formatarListaAlimentos(lista: List<AlimentoRegistrado>): String {
        if (lista.isEmpty()) {
            return "" // Devolvemos vacío si no hay nada
        }
        // Une cada item con ", "
        return lista.joinToString(separator = ", ") {
            // Formatear cantidad (opcional: quitar ".0" si es entero)
            val cantidadStr = if (it.cantidad == it.cantidad.toInt().toDouble()) {
                it.cantidad.toInt().toString()
            } else {
                it.cantidad.toString()
            }
            "$cantidadStr ${it.nombre}"
        }
    }
}