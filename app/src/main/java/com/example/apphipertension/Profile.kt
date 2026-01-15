package com.example.apphipertension

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.*
import androidx.core.content.edit
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.Calendar
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import android.Manifest
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.time.ZoneId

class Profile : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var etPeso: EditText
    private lateinit var etAltura: EditText
    private lateinit var etIMC: EditText
    private lateinit var etFechaNacimiento: EditText
    private lateinit var tvEdad: TextView
    private lateinit var rgSexo: RadioGroup
    private lateinit var rbMujer: RadioButton
    private lateinit var rbHombre: RadioButton
    private lateinit var etFechaDiagnostico: EditText
    private lateinit var tvCorreo: TextView
    private lateinit var btnLogout: Button
    private lateinit var btnSave: Button
    private lateinit var profileName: TextView
    private lateinit var changeName: TextView
    private lateinit var changePhoto: TextView

    private var selectedBirthDate: String = ""
    private var selectedDiagnosisDate: String = ""
    private var userUid: String = ""
    private lateinit var tvEditarMetaCalorias: TextView
    private var metaCaloriasActual: Double = 2000.0
    private val CALENDAR_PERMISSION_CODE = 103

    // Variables para Alimentos
    private lateinit var cgAlimentosPredefinidos: ChipGroup
    private lateinit var cgAlimentosPersonalizados: ChipGroup
    private lateinit var etAlimentoPersonalizado: EditText
    private lateinit var btnAgregarAlimentoPer: Button

    // Lista predefinida de alimentos (ID a Nombre)
    private val alimentosPredefinidos = mapOf(
        "harinas" to "Harinas Refinadas",
        "grasas" to "Grasas",
        "azucares" to "Azúcares Añadidos",
        "embutidos" to "Embutidos",
        "sal" to "Exceso de Sal",
        "alcohol" to "Alcohol"
    )

    // Variables para Medicamentos
    private lateinit var cgMedicamentosPersonalizados: ChipGroup
    private lateinit var etMedicamentoPersonalizado: EditText
    private lateinit var btnAgregarMedicamentoPer: Button

    // Variables para Padecimientos
    private lateinit var cgPadecimientosPredefinidos: ChipGroup
    private lateinit var cgPadecimientosPersonalizados: ChipGroup
    private lateinit var etPadecimientoPersonalizado: EditText
    private lateinit var btnAgregarPadecimientoPer: Button

    // Lista predefinida de Padecimientos (ID a Nombre)
    private val padecimientosPredefinidos = mapOf(
        "diabetes" to "Diabetes",
        "colesterol" to "Colesterol Alto",
        "trigliceridos" to "Triglicéridos Altos",
        "renal" to "Enfermedad Renal",
        "cardiaco" to "Problema Cardíaco"
        // Puedes añadir más aquí
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        // Referencias UI
        profileImage = findViewById(R.id.profileImage)
        etPeso = findViewById(R.id.etPeso)
        etAltura = findViewById(R.id.etAltura)
        etIMC = findViewById(R.id.etIMC)
        etFechaNacimiento = findViewById(R.id.etFechaNacimiento)
        tvEdad = findViewById(R.id.tvEdad)
        rgSexo = findViewById(R.id.rgSexo)
        rbMujer = findViewById(R.id.rbMujer)
        rbHombre = findViewById(R.id.rbHombre)
        etFechaDiagnostico = findViewById(R.id.etFechaDiagnostico)
        tvCorreo = findViewById(R.id.tvCorreo)
        btnLogout = findViewById(R.id.btnLogout)
        profileName = findViewById(R.id.profileName)
        changeName = findViewById(R.id.changeName)
        changePhoto = findViewById(R.id.changePhoto)

        val btnCalcularIMC = findViewById<Button>(R.id.btnCalcularIMC)
        btnCalcularIMC.setOnClickListener { calculateIMC() }
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)
        btnSave.setOnClickListener { saveProfileData() }

        // Listener para cambiar nombre (puedes mostrar un AlertDialog para pedir nuevo nombre)
        changeName.setOnClickListener {
            val input = EditText(this)
            input.hint = "Nuevo nombre"
            val dialog = android.app.AlertDialog.Builder(this)
                .setTitle("Cambiar nombre")
                .setView(input)
                .setPositiveButton("Guardar") { _, _ ->
                    val newName = input.text.toString()
                    if (newName.isNotBlank()) {
                        profileName.text = newName
                        saveProfileData()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .create()
            dialog.show()
        }

        // Listener para cambiar foto
        changePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, 101)
        }

        tvEditarMetaCalorias = findViewById(R.id.tvEditarMetaCalorias)
        cgAlimentosPredefinidos = findViewById(R.id.cgAlimentosPredefinidos)
        cgAlimentosPersonalizados = findViewById(R.id.cgAlimentosPersonalizados)
        etAlimentoPersonalizado = findViewById(R.id.etAlimentoPersonalizado)
        btnAgregarAlimentoPer = findViewById(R.id.btnAgregarAlimentoPer)

        // Configurar los listeners
        tvEditarMetaCalorias.setOnClickListener {
            // La función 'mostrarDialogoMetaCalorias' la añadiremos después
            mostrarDialogoMetaCalorias(userUid, metaCaloriasActual)
        }

        // Llenar los chips predefinidos
        setupAlimentosPredefinidos()

        // Listener para añadir chip personalizado
        btnAgregarAlimentoPer.setOnClickListener {
            val nombreAlimento = etAlimentoPersonalizado.text.toString().trim()
            if (nombreAlimento.isNotEmpty()) {
                // Use the generic function and specify the correct ChipGroup
                addGenericCustomChip(nombreAlimento, cgAlimentosPersonalizados) // <-- NEW NAME + ChipGroup
                etAlimentoPersonalizado.text.clear()
            }
        }

        // --- INICIALIZAR VISTAS MEDICAMENTOS ---
        cgMedicamentosPersonalizados = findViewById(R.id.cgMedicamentosPersonalizados)
        etMedicamentoPersonalizado = findViewById(R.id.etMedicamentoPersonalizado)
        btnAgregarMedicamentoPer = findViewById(R.id.btnAgregarMedicamentoPer)

        btnAgregarMedicamentoPer.setOnClickListener {
            val nombreMedicamento = etMedicamentoPersonalizado.text.toString().trim()
            if (nombreMedicamento.isNotEmpty()) {
                // Reutilizamos la lógica del chip, pero con otro ChipGroup
                addGenericCustomChip(nombreMedicamento, cgMedicamentosPersonalizados)
                etMedicamentoPersonalizado.text.clear()
            }
        }

        // --- INICIALIZAR VISTAS PADECIMIENTOS ---
        cgPadecimientosPredefinidos = findViewById(R.id.cgPadecimientosPredefinidos)
        cgPadecimientosPersonalizados = findViewById(R.id.cgPadecimientosPersonalizados)
        etPadecimientoPersonalizado = findViewById(R.id.etPadecimientoPersonalizado)
        btnAgregarPadecimientoPer = findViewById(R.id.btnAgregarPadecimientoPer)

        // Llenar chips predefinidos de padecimientos
        setupPadecimientosPredefinidos()

        btnAgregarPadecimientoPer.setOnClickListener {
            val nombrePadecimiento = etPadecimientoPersonalizado.text.toString().trim()
            if (nombrePadecimiento.isNotEmpty()) {
                addGenericCustomChip(nombrePadecimiento, cgPadecimientosPersonalizados)
                etPadecimientoPersonalizado.text.clear()
            }
        }

        // DatePickers
        etFechaNacimiento.setOnClickListener { showDatePicker(true) }
        etFechaDiagnostico.setOnClickListener { showDatePicker(false) }

        // Logout
        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, Login::class.java))
            finish()
        }

        // Cargar datos de usuario
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            userUid = user.uid
            profileName.text = user.displayName ?: user.email ?: ""
            tvCorreo.text = user.email ?: ""
            loadProfileData()
            loadProfileImage()
        }



        //MenuInferior
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.setOnItemSelectedListener { item ->
            val intent: Intent? = when (item.itemId) {
                R.id.nav_home -> Intent(this, MainActivity::class.java)
                R.id.nav_meds -> Intent(this, Medicate::class.java)
                R.id.nav_calendar -> Intent(this, com.example.apphipertension.Calendar::class.java)
                R.id.nav_profile -> null
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
                item.itemId == R.id.nav_profile || item.itemId == R.id.nav_more // Return true if 'More' was clicked, false otherwise
            }
        }




        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }



    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CALENDAR_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido
                Toast.makeText(this, "Permiso concedido. Intenta guardar la fecha de nuevo.", Toast.LENGTH_LONG).show()

                // Opcional: Intentar crear el evento automáticamente
                // (Necesitamos parsear la fecha que ya estaba en el EditText)
                try {
                    val dateStr = selectedDiagnosisDate // Usamos la variable de clase
                    if (dateStr.isNotEmpty()) {
                        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        val date = LocalDate.parse(dateStr, formatter)
                        createCalendarEvent(date.year, date.monthValue - 1, date.dayOfMonth) // Mes es 0-indexado para Calendar
                    }
                } catch (e: Exception) {
                    Log.e("Profile", "Error al parsear fecha tras permiso: $e")
                }

            } else {
                // Permiso denegado
                Toast.makeText(this, "Permiso de calendario denegado. No se puede crear el recordatorio.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure the correct item is selected every time the activity becomes visible
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_profile // Set it again here
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

    // Manejo DatePickers para nacimiento/diagnóstico
    private fun showDatePicker(isBirth: Boolean) {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)

        val picker = DatePickerDialog(this, { _, y, m, d ->
            val dateStr = "%02d/%02d/%04d".format(d, m+1, y)
            if (isBirth) {
                etFechaNacimiento.setText(dateStr)
                calculateAge()
            } else {
                // --- LÓGICA DE RECORDATORIO AÑADIDA ---
                etFechaDiagnostico.setText(dateStr)
                selectedDiagnosisDate = dateStr // Guarda la fecha por si la necesitamos

                // Preguntar al usuario si quiere añadir un recordatorio
                askToCreateReminder(y, m, d)
                // ------------------------------------
            }
        }, year, month, day)
        picker.show()
    }

    private fun calculateIMC() {
        val peso = etPeso.text.toString().toFloatOrNull()
        val altura = etAltura.text.toString().toFloatOrNull()
        if (peso != null && altura != null && altura > 0) {
            val imc = peso / (altura * altura)
            etIMC.setText(String.format("%.2f", imc))
        } else {
            Toast.makeText(this, "Introduce peso y altura válidos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateAge() {
        val fecha = etFechaNacimiento.text.toString()
        try {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val birthDate = LocalDate.parse(fecha, formatter)
            val hoy = LocalDate.now()
            val edad = Period.between(birthDate, hoy).years
            tvEdad.text = "Edad: $edad"
        } catch (e: Exception) {
            tvEdad.text = "Edad: "
        }
    }

    // Guardar datos en Firestore
    private fun saveProfileData() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()

        val sexo = when (rgSexo.checkedRadioButtonId) {
            R.id.rbMujer -> "Mujer"
            R.id.rbHombre -> "Hombre"
            else -> ""
        }

        val alimentosPre = mutableListOf<String>()
        for (i in 0 until cgAlimentosPredefinidos.childCount) {
            val chip = cgAlimentosPredefinidos.getChildAt(i) as Chip
            if (chip.isChecked) {
                alimentosPre.add(chip.tag.toString()) // Guardamos el ID (del tag)
            }
        }

        val alimentosPer = mutableListOf<String>()
        for (i in 0 until cgAlimentosPersonalizados.childCount) {
            val chip = cgAlimentosPersonalizados.getChildAt(i) as Chip
            alimentosPer.add(chip.text.toString()) // Guardamos el nombre
        }

        val medicamentosPer = mutableListOf<String>()
        for (i in 0 until cgMedicamentosPersonalizados.childCount) {
            val chip = cgMedicamentosPersonalizados.getChildAt(i) as Chip
            medicamentosPer.add(chip.text.toString())
        }

        val padecimientosPre = mutableListOf<String>()
        for (i in 0 until cgPadecimientosPredefinidos.childCount) {
            val chip = cgPadecimientosPredefinidos.getChildAt(i) as Chip
            if (chip.isChecked) {
                padecimientosPre.add(chip.tag.toString())
            }
        }

        val padecimientosPer = mutableListOf<String>()
        for (i in 0 until cgPadecimientosPersonalizados.childCount) {
            val chip = cgPadecimientosPersonalizados.getChildAt(i) as Chip
            padecimientosPer.add(chip.text.toString())
        }

        val data = hashMapOf(
            "nombre" to profileName.text.toString(),
            "peso" to etPeso.text.toString(),
            "altura" to etAltura.text.toString(),
            "imc" to etIMC.text.toString(),
            "fecha_nacimiento" to etFechaNacimiento.text.toString(),
            "edad" to tvEdad.text.toString().removePrefix("Edad: ").trim(),
            "sexo" to sexo,
            "correo" to tvCorreo.text.toString(),
            "proxima_cita_medica" to etFechaDiagnostico.text.toString(),
            "alimentosEvitar_pre" to alimentosPre,
            "alimentosEvitar_per" to alimentosPer,
            "medicamentosEvitar" to medicamentosPer,
            "padecimientos_pre" to padecimientosPre,
            "padecimientos_per" to padecimientosPer
        )
        db.collection("users").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                // Mensaje de éxito:
                Toast.makeText(this, "Datos guardados correctamente", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar los datos", Toast.LENGTH_SHORT).show()
            }
    }

    // Cargar datos del perfil desde Firestore
    private fun loadProfileData() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    profileName.text = doc.getString("nombre") ?: (user.email ?: "")
                    etPeso.setText(doc.getString("peso") ?: "")
                    etAltura.setText(doc.getString("altura") ?: "")
                    etIMC.setText(doc.getString("imc") ?: "")
                    etFechaNacimiento.setText(doc.getString("fecha_nacimiento") ?: "")
                    tvEdad.text = "Edad: " + (doc.getString("edad") ?: "")
                    when (doc.getString("sexo")) {
                        "Mujer" -> rgSexo.check(R.id.rbMujer)
                        "Hombre" -> rgSexo.check(R.id.rbHombre)
                    }
                    etFechaDiagnostico.setText(doc.getString("proxima_cita_medica") ?: "")
                    metaCaloriasActual = doc.getDouble("meta_calorias_diarias") ?: 2000.0
                    tvEditarMetaCalorias.text = "${"%.0f".format(metaCaloriasActual)} cal"
                    tvCorreo.text = doc.getString("correo") ?: (user.email ?: "")

                    cgAlimentosPersonalizados.removeAllViews()
                    val alimentosPre = doc.get("alimentosEvitar_pre") as? List<String> ?: emptyList()
                    for (i in 0 until cgAlimentosPredefinidos.childCount) {
                        val chip = cgAlimentosPredefinidos.getChildAt(i) as Chip
                        if (alimentosPre.contains(chip.tag.toString())) {
                            chip.isChecked = true
                        }
                    }

                    val alimentosPer = doc.get("alimentosEvitar_per") as? List<String> ?: emptyList()
                    alimentosPer.forEach { nombre -> addGenericCustomChip(nombre, cgAlimentosPersonalizados) }

                    // --- NUEVO: CARGAR MEDICAMENTOS ---
                    cgMedicamentosPersonalizados.removeAllViews()
                    val medicamentosPer = doc.get("medicamentosEvitar") as? List<String> ?: emptyList()
                    medicamentosPer.forEach { nombre -> addGenericCustomChip(nombre, cgMedicamentosPersonalizados) }

                    // --- NUEVO: CARGAR PADECIMIENTOS ---
                    cgPadecimientosPersonalizados.removeAllViews()
                    val padecimientosPre = doc.get("padecimientos_pre") as? List<String> ?: emptyList()
                    for (i in 0 until cgPadecimientosPredefinidos.childCount) {
                        val chip = cgPadecimientosPredefinidos.getChildAt(i) as Chip
                        chip.isChecked = padecimientosPre.contains(chip.tag.toString())
                    }
                    val padecimientosPer = doc.get("padecimientos_per") as? List<String> ?: emptyList()
                    padecimientosPer.forEach { nombre -> addGenericCustomChip(nombre, cgPadecimientosPersonalizados) }
                }
            }
    }

    // --- Foto de perfil: guardar/cargar local ---
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val imageUri = data?.data
            if (imageUri != null && userUid.isNotBlank()) {
                // Mostrar la imagen seleccionada en el ImageView
                profileImage.setImageURI(imageUri)
                // Guardar la imagen en almacenamiento interno y obtener el path
                val inputStream = contentResolver.openInputStream(imageUri)
                val bmp = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                val path = saveImageToInternalStorage(bmp, userUid) // <-- Ahora regresa el path
                // Guardar el path en SharedPreferences
                val prefs = getSharedPreferences("user_profile", MODE_PRIVATE)
                prefs.edit().putString("profile_image_path", path).apply()
            }
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap, filename: String): String {
        val file = File(filesDir, "$filename-profile.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        return file.absolutePath // ← REGRESA el path
    }

    private fun loadProfileImage() {
        if (userUid.isBlank()) return
        val file = File(filesDir, "$userUid-profile.png")
        val prefs = getSharedPreferences("user_profile", MODE_PRIVATE)
        val path = prefs.getString("profile_image_path", null)
        if (path != null && File(path).exists()) {
            val bmp = BitmapFactory.decodeFile(path)
            profileImage.setImageBitmap(bmp)
        } else {
            profileImage.setImageResource(R.drawable.profile) // tu imagen anónima
        }
    }

    // (En Profile.kt, pégalas al final de la clase)

    // Llena el ChipGroup con nuestras opciones predefinidas
    private fun setupAlimentosPredefinidos() {
        cgAlimentosPredefinidos.removeAllViews()
        for ((id, nombre) in alimentosPredefinidos) {
            val chip = Chip(this)
            chip.text = nombre
            chip.tag = id // Usamos el tag para guardar el ID
            chip.isCheckable = true
            cgAlimentosPredefinidos.addView(chip)
        }
    }

    // Renombramos la función para hacerla genérica
    private fun addGenericCustomChip(nombre: String, chipGroup: ChipGroup) {
        val chip = Chip(this)
        chip.text = nombre
        chip.isCloseIconVisible = true // Permite borrar
        chip.setOnCloseIconClickListener {
            chipGroup.removeView(it)
        }
        chipGroup.addView(chip)
    }

    private fun setupPadecimientosPredefinidos() {
        cgPadecimientosPredefinidos.removeAllViews()
        for ((id, nombre) in padecimientosPredefinidos) {
            val chip = Chip(this)
            chip.text = nombre
            chip.tag = id // Guardamos ID
            chip.isCheckable = true // Checkable
            // chip.setStyle(...) // Ya no es necesaria
            cgPadecimientosPredefinidos.addView(chip)
        }
    }

    // La función para el diálogo de calorías (copiada de Dieta.kt)
    private fun mostrarDialogoMetaCalorias(uid: String, metaActual: Double) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Meta de Calorías Diarias")

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Ej. 2000"
        if (metaActual > 0) {
            input.setText(metaActual.toInt().toString())
        }
        builder.setView(input)

        builder.setPositiveButton("Guardar") { dialog, _ ->
            val metaStr = input.text.toString()
            if (metaStr.isNotEmpty()) {
                try {
                    val nuevaMeta = metaStr.toDouble()
                    val db = FirebaseFirestore.getInstance()

                    db.collection("users").document(uid)
                        .update("meta_calorias_diarias", nuevaMeta)
                        .addOnSuccessListener {
                            metaCaloriasActual = nuevaMeta
                            tvEditarMetaCalorias.text = "${"%.0f".format(nuevaMeta)} cal"
                            Toast.makeText(this, "Meta actualizada", Toast.LENGTH_SHORT).show()
                        }
                    dialog.dismiss()
                } catch (e: Exception) { /* ... */ }
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    /**
     * Muestra un diálogo preguntando al usuario si desea crear un recordatorio.
     */
    private fun askToCreateReminder(year: Int, month: Int, day: Int) {
        AlertDialog.Builder(this)
            .setTitle("Crear Recordatorio")
            .setMessage("¿Deseas añadir un recordatorio para tu próxima cita médica en el calendario?")
            .setPositiveButton("Sí, añadir") { _, _ ->
                // El usuario dijo sí, verificar permisos
                checkAndRequestCalendarPermission(year, month, day)
            }
            .setNegativeButton("No, gracias", null)
            .show()
    }
    /**
     * Verifica si tiene permisos. Si los tiene, crea el evento. Si no, los pide.
     */
    private fun checkAndRequestCalendarPermission(year: Int, month: Int, day: Int) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            // Permiso ya concedido, crear el evento
            createCalendarEvent(year, month, day)
        } else {
            // Permiso no concedido, solicitarlo
            // (La fecha se guardará en 'selectedDiagnosisDate' para usarla después)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR),
                CALENDAR_PERMISSION_CODE
            )
        }
    }
    private fun createCalendarEvent(year: Int, month: Int, day: Int) {
        try {
            // Asumimos una hora por defecto para la cita, ej. 10:00 AM
            val cal = Calendar.getInstance()
            cal.set(year, month, day, 10, 0) // 10:00 AM
            val startTime = cal.timeInMillis

            cal.add(Calendar.HOUR_OF_DAY, 1) // Duración de 1 hora
            val endTime = cal.timeInMillis

            // Crear el Intent
            val intent = Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                .putExtra(CalendarContract.Events.TITLE, "Cita Médica (AppHipertensión)")
                .putExtra(CalendarContract.Events.DESCRIPTION, "Recordatorio automático de cita médica generado por AppHipertensión.")
                .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)

            startActivity(intent)

        } catch (e: Exception) {
            Toast.makeText(this, "Error al crear evento: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}