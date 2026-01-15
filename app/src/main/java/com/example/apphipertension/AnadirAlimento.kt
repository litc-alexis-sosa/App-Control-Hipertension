package com.example.apphipertension

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.apphipertension.databinding.ActivityAnadirAlimentoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Locale
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.util.Log

class AnadirAlimento : AppCompatActivity() {

    // View Binding
    private lateinit var binding: ActivityAnadirAlimentoBinding

    // Firebase
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // RecyclerView
    private lateinit var adapter: AlimentoAdapter

    // Lista para guardar TODOS los alimentos de la base de datos
    private val listaCompletaAlimentos = mutableListOf<Alimento>()

    // Variable para guardar la fecha que recibimos de DietaActivity
    private var fechaSeleccionada: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnadirAlimentoBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // Inicializar Firebase
        db = Firebase.firestore
        auth = FirebaseAuth.getInstance()

        val toolbar = binding.toolbar // Use ViewBinding
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            // Simply close this activity when the back arrow is pressed
            finish()
        }



        // 1. Obtener la fecha de la actividad anterior
        fechaSeleccionada = intent.getStringExtra("FECHA_SELECCIONADA")
        if (fechaSeleccionada == null) {
            Toast.makeText(this, "Error: No se seleccionó fecha", Toast.LENGTH_LONG).show()
            finish() // Cierra la actividad si no hay fecha
            return
        }

        // 2. Configurar el RecyclerView
        setupRecyclerView()

        // 3. Configurar la barra de búsqueda
        setupSearchView()

        // 4. Configurar el botón de comida personalizada
        binding.btnComidaPersonalizada.setOnClickListener {
            // TODO: Implementar lógica de comida personalizada
            mostrarDialogoComidaPersonalizada()
        }

        // 5. Cargar la lista de alimentos desde Firestore
        cargarAlimentosDesdeFirestore()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply padding to the main layout (the ConstraintLayout with ID 'main')
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        // Inicializamos el adapter con una lista vacía y le pasamos la
        // función que se ejecutará al hacer clic (::onAlimentoSeleccionado)
        adapter = AlimentoAdapter(emptyList(), ::onAlimentoSeleccionado)

        binding.recyclerViewAlimentos.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAlimentos.adapter = adapter
    }

    private fun cargarAlimentosDesdeFirestore() {
        // Asumimos que la colección se llama "alimentos_mexico"
        db.collection("alimentos_mexico")
            .get()
            .addOnSuccessListener { result ->
                listaCompletaAlimentos.clear()
                // Convertimos cada documento en un objeto Alimento
                for (document in result) {
                    val alimento = document.toObject(Alimento::class.java)
                    listaCompletaAlimentos.add(alimento)
                }
                // Actualizamos el adapter con la lista completa
                adapter.updateList(listaCompletaAlimentos)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar alimentos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupSearchView() {
        binding.searchViewAlimentos.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            // No hacemos nada cuando el usuario presiona "Enter"
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            // Esto se llama CADA VEZ que el usuario escribe o borra una letra
            override fun onQueryTextChange(newText: String?): Boolean {
                filtrarLista(newText)
                return true
            }
        })
    }

    private fun filtrarLista(query: String?) {
        if (query.isNullOrEmpty()) {
            // Si la búsqueda está vacía, mostramos la lista completa
            adapter.updateList(listaCompletaAlimentos)
        } else {
            // Filtramos la lista completa
            val listaFiltrada = listaCompletaAlimentos.filter { alimento ->
                // Comparamos en minúsculas para que no sea sensible
                alimento.nombre.lowercase().contains(query.lowercase())
            }
            adapter.updateList(listaFiltrada)
        }
    }

    /**
     * Esto se llama cuando el usuario HACE CLIC en un alimento de la lista.
     */
    private fun onAlimentoSeleccionado(alimento: Alimento) {
        mostrarDialogoGuardarAlimento(alimento)
    }

    private fun mostrarDialogoGuardarAlimento(alimento: Alimento) {
        val builder = AlertDialog.Builder(this)
        // Inflamos el layout personalizado
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_guardar_alimento, null)

        // Obtenemos las vistas del layout inflado
        val tvTituloDialogo = dialogView.findViewById<TextView>(R.id.tvTituloDialogo)
        val tvInfoCaloriasBase = dialogView.findViewById<TextView>(R.id.tvInfoCaloriasBase)
        val etCantidad = dialogView.findViewById<EditText>(R.id.etCantidad)
        val spinnerCategoria = dialogView.findViewById<Spinner>(R.id.spinnerCategoria)
        val tvTotalCaloriasCalculadas = dialogView.findViewById<TextView>(R.id.tvTotalCaloriasCalculadas)

        // 1. Configuramos los textos iniciales
        tvTituloDialogo.text = "Añadir ${alimento.nombre}"
        tvInfoCaloriasBase.text = "Base: ${alimento.calorias_base} cal / ${alimento.unidad_base}"
        tvTotalCaloriasCalculadas.text = "Total: 0 cal"

        // 2. Configuramos el Spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.categorias_comida, // El array que creamos en strings.xml
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategoria.adapter = adapter
        }

        // 3. Configuramos el cálculo en tiempo real
        etCantidad.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    val cantidad = s.toString().toDouble()
                    val totalCalculado = cantidad * alimento.calorias_base
                    // Formateamos a 2 decimales
                    tvTotalCaloriasCalculadas.text = "Total: ${"%.2f".format(totalCalculado)} cal"
                } catch (e: NumberFormatException) {
                    tvTotalCaloriasCalculadas.text = "Total: 0 cal"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. Construimos el diálogo
        builder.setView(dialogView)
            .setPositiveButton("Guardar", null) // Ponemos null para anular el cierre automático
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }

        val dialog = builder.create()
        dialog.show()

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE) // GUARDAR
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE) // CANCELAR

        val blueColor = Color.parseColor("#466B95")
        positiveButton?.setTextColor(blueColor)
        negativeButton?.setTextColor(blueColor)

        // 5. Anulamos el botón "Guardar" para validarlo primero
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val cantidadStr = etCantidad.text.toString()
            if (cantidadStr.isEmpty() || cantidadStr == ".") {
                etCantidad.error = "Ingresa una cantidad válida"
                return@setOnClickListener
            }

            val cantidad = cantidadStr.toDouble()
            if (cantidad <= 0) {
                etCantidad.error = "La cantidad debe ser mayor a 0"
                return@setOnClickListener
            }

            var categoriaSeleccionada = spinnerCategoria.selectedItem.toString().lowercase()
            if (categoriaSeleccionada == "colación") {
                categoriaSeleccionada = "colacion" // Forzamos el nombre del campo sin acento
            }
            val caloriasTotalesItem = cantidad * alimento.calorias_base

            val alimentoRegistrado = AlimentoRegistrado(
                nombre = alimento.nombre,
                cantidad = cantidad,
                unidad = alimento.unidad_base,
                calorias = caloriasTotalesItem
            )

            guardarAlimentoEnFirestore(alimentoRegistrado, categoriaSeleccionada)
            dialog.dismiss()
        }
    }

    /**
     * Convierte una fecha de "dd/MM/yyyy" a "yyyy-MM-dd"
     */
    private fun formatearFechaParaFirestore(fecha: String): String {
        // Ojo: Si tu fechaSeleccionada ya está en otro formato, ajusta esto
        val formatoEntrada = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val formatoSalida = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = formatoEntrada.parse(fecha)
        return formatoSalida.format(date)
    }

    private fun guardarAlimentoEnFirestore(alimento: AlimentoRegistrado, categoria: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        // Usamos la fecha que recibimos de la DietaActivity
        val fechaId = formatearFechaParaFirestore(fechaSeleccionada!!)

        // Referencia al documento del día
        val docRef = db.collection("users").document(userId)
            .collection("registros_dieta").document(fechaId)

        // Usamos FieldValue.arrayUnion para añadir a la lista de forma segura
        // y FieldValue.increment para sumar las calorías
        docRef.set(
            mapOf(
                categoria to FieldValue.arrayUnion(alimento),
                "calorias_totales_dia" to FieldValue.increment(alimento.calorias),
                "fecha" to com.google.firebase.Timestamp.now() // Opcional, para marcar la última actualización
            ),
            com.google.firebase.firestore.SetOptions.merge() // Merge() es CRUCIAL
        )
            .addOnSuccessListener {
                Toast.makeText(this, "${alimento.nombre} guardado", Toast.LENGTH_SHORT).show()
                checkAndNotifyCalorieGoal(userId, docRef)
                finish() // Cierra AnadirAlimentoActivity y vuelve a DietaActivity
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkAndNotifyCalorieGoal(uid: String, dailyRecordRef: com.google.firebase.firestore.DocumentReference) {
        Log.d("NotificationCheck", "Starting check...") // <-- ADD LOG

        // 1. Get User's Calorie Goal
        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val goal = userDoc.getDouble("meta_calorias_diarias")
            Log.d("NotificationCheck", "Goal fetched: $goal") // <-- ADD LOG
            if (goal == null || goal <= 0) {
                Log.d("NotificationCheck", "Goal not set or invalid. Exiting.") // <-- ADD LOG
                return@addOnSuccessListener
            }

            // 2. Get Updated Daily Calories
            dailyRecordRef.get().addOnSuccessListener { dailyDoc ->
                val currentCalories = dailyDoc.getDouble("calorias_totales_dia") ?: 0.0
                Log.d("NotificationCheck", "Current Calories fetched: $currentCalories") // <-- ADD LOG

                // 3. Check if Goal Exceeded
                if (currentCalories > goal) {
                    Log.d("NotificationCheck", "Goal EXCEEDED ($currentCalories > $goal)") // <-- ADD LOG

                    // 4. Check if notification already sent today
                    val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val todayDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    val lastNotifDate = prefs.getString("last_calorie_notif_date", "")
                    Log.d("NotificationCheck", "Today: $todayDate, Last Notif Date: $lastNotifDate") // <-- ADD LOG

                    if (todayDate != lastNotifDate) {
                        Log.d("NotificationCheck", "Sending notification...") // <-- ADD LOG
                        // Send notification!
                        sendCalorieGoalNotification(this, currentCalories, goal)

                        // Save today's date so we don't notify again
                        prefs.edit().putString("last_calorie_notif_date", todayDate).apply()
                        Log.d("NotificationCheck", "Saved today's date to preferences.") // <-- ADD LOG
                    } else {
                        Log.d("NotificationCheck", "Notification already sent today. Skipping.") // <-- ADD LOG
                    }
                } else {
                    Log.d("NotificationCheck", "Goal NOT exceeded ($currentCalories <= $goal)") // <-- ADD LOG
                }
            }.addOnFailureListener { e -> // <-- ADD Fail Listener
                Log.e("NotificationCheck", "Failed to get daily calories", e)
            }
        }.addOnFailureListener { e -> // <-- ADD Fail Listener
            Log.e("NotificationCheck", "Failed to get user goal", e)
        }
    }

    private fun sendCalorieGoalNotification(context: Context, currentCals: Double, goalCals: Double) {

        // --- Optional: Permission Check ---
        // You should ideally request this permission elsewhere before calling this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Handle missing permission - request it or log an error
                Log.e("Notification", "POST_NOTIFICATIONS permission missing")
                return // Don't proceed if permission is denied
            }
        }
        // ---------------------------------

        val builder = NotificationCompat.Builder(context, NotificationUtils.CALORIE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app icon
            .setContentTitle("¡Meta de Calorías Superada!")
            .setContentText("Llevas ${"%.0f".format(currentCals)} de ${"%.0f".format(goalCals)} cal. ¡Intenta balancear con actividad física! 👍")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Dismiss notification when tapped

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            val notificationId = 1 // Use a unique ID for this type of notification
            notify(notificationId, builder.build())
        }
    }

    // TODO: Implementar esto
    private fun mostrarDialogoComidaPersonalizada() {
        val builder = AlertDialog.Builder(this)
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_comida_personalizada, null)

        // Obtenemos las vistas
        val etNombre = dialogView.findViewById<EditText>(R.id.etNombrePersonalizado)
        val etCalorias = dialogView.findViewById<EditText>(R.id.etCaloriasPersonalizadas)
        val spinnerCategoria = dialogView.findViewById<Spinner>(R.id.spinnerCategoriaPersonalizada)

        // Configuramos el Spinner (reutilizamos el mismo array de strings)
        ArrayAdapter.createFromResource(
            this,
            R.array.categorias_comida,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategoria.adapter = adapter
        }

        // Construimos el diálogo
        builder.setView(dialogView)
            .setPositiveButton("Guardar", null) // Anulamos para validación
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }

        val dialog = builder.create()
        dialog.show()

        // Lógica de validación del botón "Guardar"
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val nombre = etNombre.text.toString()
            val caloriasStr = etCalorias.text.toString()

            // Validación 1: Nombre no vacío
            if (nombre.isEmpty()) {
                etNombre.error = "Ingresa un nombre"
                return@setOnClickListener
            }

            // Validación 2: Calorías no vacías y válidas
            if (caloriasStr.isEmpty() || caloriasStr == ".") {
                etCalorias.error = "Ingresa calorías válidas"
                return@setOnClickListener
            }

            val calorias = caloriasStr.toDouble()
            if (calorias <= 0) {
                etCalorias.error = "Las calorías deben ser mayores a 0"
                return@setOnClickListener
            }

            // Si todo es válido:
            var categoria = spinnerCategoria.selectedItem.toString().lowercase()
            if (categoria == "colación") {
                categoria = "colacion" // Forzamos el nombre del campo sin acento
            }

            val alimentoRegistrado = AlimentoRegistrado(
                nombre = nombre,
                cantidad = 1.0, // Para comida personalizada, la cantidad es 1
                unidad = "porción", // O "personalizado"
                calorias = calorias // El total de calorías
            )

            // Reutilizamos la MISMA función de guardado
            guardarAlimentoEnFirestore(alimentoRegistrado, categoria)
            dialog.dismiss()
        }
    }


}