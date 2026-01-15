package com.example.apphipertension

import android.os.Bundle
import android.widget.TextView
import android.widget.ImageView
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.*
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.cardview.widget.CardView
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private var sistolica: Int = 0
    private var diastolica: Int = 0
    private var pulso: Int = 0
    // private lateinit var shimmerLayout: ShimmerFrameLayout // Comentado si no lo estás usando
    private lateinit var headerCard: CardView
    private val REQUEST_CODE_POST_NOTIFICATIONS = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // TIRAMISU es API 33
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_DENIED) {
                // Si el permiso no está concedido, lo solicitamos
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
                )
            }
        }
        val today = LocalDate.now()
        val headerGreeting = findViewById<TextView>(R.id.headerGreeting)
        val headerName = findViewById<TextView>(R.id.headerName)
        val streakText = findViewById<TextView>(R.id.streakText)
        val profileImage = findViewById<ImageView>(R.id.profileImage)
        // val streakIcon = findViewById<ImageView>(R.id.streakIcon) // Comentado si no está en el layout

        // ---- OBTENER CONSEJO DEL DÍA ----
        val tvConsejoTitulo = findViewById<TextView>(R.id.tvConsejoTitulo)
        val tvConsejoDia = findViewById<TextView>(R.id.tvConsejoDia)
        tvConsejoTitulo.text = "Consejo del día:"
        tvConsejoDia.text = ConsejosDiarios.obtenerConsejoDelDia()
        // ----------------------------------

        // Obtener usuario Firebase
        val user = FirebaseAuth.getInstance().currentUser
        headerGreeting.text = "¡Hola!"
        val db = FirebaseFirestore.getInstance()
        val uid = user?.uid

        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    val nombre = document.getString("nombre")
                    headerName.text = if (!nombre.isNullOrBlank()) nombre else user.email ?: "correo@ejemplo.com"
                }
                .addOnFailureListener {
                    headerName.text = user.email ?: "correo@ejemplo.com"
                }
        } else {
            headerName.text = user?.email ?: "correo@ejemplo.com"
        }
        loadProfileImage()
        // Cambiamos la llamada para capturar el Bitmap.
        val profileBitmap = loadProfileImage()
        profileImage.setOnClickListener {
            // 1. Si cargamos un Bitmap de archivo, lo mostramos.
            if (profileBitmap != null) {
                showZoomedProfileImage(profileBitmap)
            }
            // 2. Si quieres que se amplíe la imagen por defecto (R.drawable.profile) también:
            else if (profileImage.drawable != null) {
                // Tienes que convertir el Drawable a Bitmap si quieres pasarlo a la función
                val defaultDrawable = profileImage.drawable
                if (defaultDrawable is BitmapDrawable) {
                    showZoomedProfileImage(defaultDrawable.bitmap)
                } else {
                    // Caso para Drawables vectoriales/otros (más complejo, pero es un fallback)
                    // Puedes ignorar este else si solo quieres el zoom para imágenes personalizadas.
                }
            }
        }


        // ---- INICIO lógica de racha ----
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val prefs = getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
        val lastVisitString = prefs.getString("last_visit_date", null)
        val streakCount = prefs.getInt("streak_count", 0)

        var newStreak = 1
        if (lastVisitString != null) {
            val lastVisit = LocalDate.parse(lastVisitString, formatter)
            val daysDiff = java.time.Period.between(lastVisit, today).days

            newStreak = when {
                daysDiff == 0 -> streakCount
                daysDiff == 1 -> streakCount + 1
                else -> 1
            }
        }

        prefs.edit()
            .putString("last_visit_date", today.format(formatter))
            .putInt("streak_count", newStreak)
            .apply()

        streakText.text = "Racha de $newStreak días"
        // ---- FIN lógica de racha ---

        val btnVerHistorial = findViewById<Button>(R.id.btnVerHistorial)
        btnVerHistorial.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // MenuInferior
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            val intent: Intent? = when (item.itemId) {
                R.id.nav_home -> null
                R.id.nav_meds -> Intent(this, Medicate::class.java)
                R.id.nav_calendar -> Intent(this, Calendar::class.java)
                R.id.nav_profile -> Intent(this, Profile::class.java)
                R.id.nav_more -> {
                    showMoreMenuBottomSheet()
                    null
                }
                else -> null
            }

            if (intent != null) {
                // --- USE THESE FLAGS for other activities ---
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                true // Indicate navigation occurred
            } else {
                // Return true if Home or More was clicked, false otherwise
                item.itemId == R.id.nav_home || item.itemId == R.id.nav_more
            }
        }

        // ---- Sección presión arterial ----

        val valueSistolica = findViewById<TextView>(R.id.valueSistolica)
        val valueDiastolica = findViewById<TextView>(R.id.valueDiastolica)
        val valuePulso = findViewById<TextView>(R.id.valuePulso)
        val dateText = findViewById<TextView>(R.id.dateText)
        val timeText = findViewById<TextView>(R.id.timeText)
        val btnAddValues = findViewById<Button>(R.id.btnAddValues)

        // Mostrar fecha y hora actual al iniciar (Valores por defecto antes de cargar de Firebase)
        val hour = LocalTime.now()
        val formatterDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        dateText.text = today.format(formatterDate)
        timeText.text = to12HourFormat(hour.format(DateTimeFormatter.ofPattern("HH:mm")))

        valueSistolica.text = "---" // Usar guiones para indicar que no hay datos
        valueDiastolica.text = "---"
        valuePulso.text = "---"

        btnAddValues.setOnClickListener {
            showAddPressureDialog(
                onSave = { date, time, sist, diast, pulso, nota ->
                    // Actualiza la UI de la CardView con los nuevos valores guardados
                    valueSistolica.text = sist.toString()
                    valueDiastolica.text = diast.toString()
                    valuePulso.text = pulso.toString()
                    dateText.text = date.toString()
                    timeText.text = to12HourFormat(time.format(DateTimeFormatter.ofPattern("HH:mm")))
                    findViewById<TextView>(R.id.displayNote).text = if (nota.isBlank()) "Sin nota" else nota
                }
            )
        }

        cargarUltimaMedicion()
        cargarProximaToma()

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
        bottomNav.selectedItemId = R.id.nav_home // Set it again here
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido
                Toast.makeText(this, "Permiso de notificaciones concedido.", Toast.LENGTH_SHORT).show()
            } else {
                // Permiso denegado
                Toast.makeText(this, "Permiso de notificaciones denegado. No recibirás alertas.", Toast.LENGTH_LONG).show()
                // Podrías mostrar un diálogo explicando por qué necesitas el permiso
            }
        }
    }

    private fun to12HourFormat(hora24: String): String {
        return try {
            val time = java.time.LocalTime.parse(hora24)
            time.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "ES")))
        } catch (e: Exception) {
            hora24
        }
    }

    private fun cargarUltimaMedicion() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .document(user.uid)
            .collection("mediciones")
            .orderBy("date", Query.Direction.DESCENDING)
            .orderBy("time", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                val cardSistolica = findViewById<TextView>(R.id.valueSistolica)
                val cardDiastolica = findViewById<TextView>(R.id.valueDiastolica)
                val cardPulso = findViewById<TextView>(R.id.valuePulso)
                val cardFecha = findViewById<TextView>(R.id.dateText)
                val cardHora = findViewById<TextView>(R.id.timeText)
                val cardNota = findViewById<TextView>(R.id.displayNote)

                if (!result.isEmpty) {
                    val doc = result.documents.first()
                    val sistolica = doc.getLong("sistolica")?.toString() ?: "---"
                    val diastolica = doc.getLong("diastolica")?.toString() ?: "---"
                    val pulso = doc.getLong("pulso")?.toString() ?: "---"
                    val fecha = doc.getString("date") ?: ""
                    val hora = doc.getString("time") ?: ""
                    val nota = doc.getString("nota") ?: "Sin nota"

                    cardSistolica.text = sistolica
                    cardDiastolica.text = diastolica
                    cardPulso.text = pulso
                    cardFecha.text = fecha
                    cardHora.text = to12HourFormat(hora)
                    cardNota.text = if (nota.isBlank()) "Sin nota" else nota

                    this.sistolica = sistolica.toIntOrNull() ?: 0
                    this.diastolica = diastolica.toIntOrNull() ?: 0
                    this.pulso = pulso.toIntOrNull() ?: 0
                }
            }
    }

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

    private fun showAddPressureDialog(
        initialDate: LocalDate = LocalDate.now(),
        initialTime: LocalTime = LocalTime.now(),
        onSave: (date: LocalDate, time: LocalTime, sist: Int, diast: Int, pulso: Int, nota: String) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_pressure, null)
        val editFecha = dialogView.findViewById<EditText>(R.id.editFecha)
        val editHora = dialogView.findViewById<EditText>(R.id.editHora)
        val editSistolica = dialogView.findViewById<EditText>(R.id.editSistolica)
        val editDiastolica = dialogView.findViewById<EditText>(R.id.editDiastolica)
        val editPulso = dialogView.findViewById<EditText>(R.id.editPulso)
        val editNota = dialogView.findViewById<EditText>(R.id.editNota)

        var date = initialDate
        var time = initialTime

        editFecha.setText(date.toString())
        editHora.setText(time.toString().substring(0,5))

        editFecha.setOnClickListener {
            val dp = DatePickerDialog(
                this,
                { _, y, m, d ->
                    date = LocalDate.of(y, m + 1, d)
                    editFecha.setText(date.toString())
                },
                date.year, date.monthValue - 1, date.dayOfMonth
            )
            dp.show()
        }

        editHora.setOnClickListener {
            val tp = TimePickerDialog(
                this,
                { _, h, min ->
                    time = LocalTime.of(h, min)
                    // Muestra la hora en formato 12h en el campo, pero guarda el objeto LocalTime
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "ES"))
                    editHora.setText(time.format(formatter))
                },
                time.hour, time.minute, false
            )
            tp.show()
        }

        AlertDialog.Builder(this)
            .setTitle("Añadir presión arterial")
            .setView(dialogView)
            .setPositiveButton("Guardar") { dialog, _ ->
                val s = editSistolica.text.toString().toIntOrNull()
                val d = editDiastolica.text.toString().toIntOrNull()
                val p = editPulso.text.toString().toIntOrNull()
                val nota = editNota.text.toString()
                if (s != null && d != null && p != null) {
                    onSave(date, time, s, d, p, nota)

                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val uid = user.uid
                        val measurement = Measurement(
                            sistolica = s,
                            diastolica = d,
                            pulso = p,
                            date = date.toString(),
                            time = "%02d:%02d".format(time.hour, time.minute),
                            nota = nota
                        )
                        val db = FirebaseFirestore.getInstance()
                        db.collection("users").document(uid)
                            .collection("mediciones")
                            .add(measurement)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Medición guardada en la nube", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error al guardar en la nube", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Valores no válidos", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun loadProfileImage(): Bitmap? { // <--- Cambiamos el tipo de retorno
        val userUid = FirebaseAuth.getInstance().currentUser?.uid
        val profileImage = findViewById<ImageView>(R.id.profileImage)

        // El Bitmap que cargaremos
        var loadedBitmap: Bitmap? = null

        if (userUid.isNullOrBlank()) {
            profileImage.setImageResource(R.drawable.profile)
            // No hay Bitmap real, solo el recurso por defecto
        } else {
            val file = File(filesDir, "$userUid-profile.png")
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                profileImage.setImageBitmap(bmp)
                loadedBitmap = bmp // <--- Guardamos el Bitmap cargado
            } else {
                profileImage.setImageResource(R.drawable.profile)
                // Aquí podrías cargar el recurso por defecto como Bitmap si lo quieres zoomable
            }
        }
        return loadedBitmap // <--- Devolvemos el Bitmap
    }

    private fun showZoomedProfileImage(bitmap: Bitmap) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE) // Ocultar título si el tema lo muestra
        dialog.setContentView(R.layout.dialog_zoom_image) // Usaremos un layout simple

        val imageView = dialog.findViewById<ImageView>(R.id.zoomedImageView)
        imageView.setImageBitmap(bitmap)

        // Opcional: Permitir cerrar el diálogo al tocar fuera de él o al tocar la imagen
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK)) // Fondo negro
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Cierra el diálogo al hacer clic en la imagen
        imageView.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun cargarProximaToma() {
        val cardMedicina = findViewById<CardView>(R.id.cardNextDose) // ID que usaremos en el layout
        val tvProximaDosis = findViewById<TextView>(R.id.tvProximaDosis)
        val tvNombreMedicina = findViewById<TextView>(R.id.tvNombreMedicina)
        val tvDosisDetalle = findViewById<TextView>(R.id.tvDosisDetalle)

        // Ocultar la tarjeta por defecto
        cardMedicina.visibility = View.GONE

        MedicineUtils.loadNextDose { medicine, dateTime ->
            if (medicine != null && dateTime != null) {
                // Calcular cuánto tiempo falta
                val minutesUntil = ChronoUnit.MINUTES.between(LocalDateTime.now(), dateTime)
                val hours = minutesUntil / 60
                val minutes = minutesUntil % 60

                // Mostrar la CardView
                cardMedicina.visibility = View.VISIBLE

                // Formatear el texto
                val timeText = if (hours > 0) {
                    // Mostrar "Hoy a las 10:00 AM" o "Mañana a las 09:00 AM"
                    "${MedicineUtils.formatDate(dateTime)} a las ${MedicineUtils.formatTime(dateTime)}"
                } else {
                    // Mostrar "En 15 minutos"
                    "En $minutes minutos"
                }

                tvProximaDosis.text = timeText
                tvNombreMedicina.text = medicine.nombre
                tvDosisDetalle.text = "${medicine.dosis} ${medicine.unidad}"

            } else {
                cardMedicina.visibility = View.GONE
            }
        }
    }
}
