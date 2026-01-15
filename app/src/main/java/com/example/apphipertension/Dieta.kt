package com.example.apphipertension

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// Asegúrate de importar tu clase de binding.
// El nombre se genera automáticamente: tu XML (activity_dieta.xml)
// se convierte en ActivityDietaBinding
import com.example.apphipertension.databinding.ActivityDietaBinding

import androidx.appcompat.app.AppCompatActivity
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.widget.Toast
import java.util.Calendar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Locale
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import android.widget.ImageButton
import com.google.firebase.firestore.FieldValue

class Dieta : AppCompatActivity() {

    // 1. Declarar la variable de binding
    private lateinit var binding: ActivityDietaBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var userId: String? = null
    private var metaCaloriasActual: Double = 2000.0

    private var fechaSeleccionadaFormatoBtn: String = "" // "dd/MM/yyyy"
    private var fechaSeleccionadaFormatoDoc: String = "" // "yyyy-MM-dd"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDietaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Obtener referencia a la Toolbar usando el binding
        val toolbar = binding.toolbar
        // 2. Establecerla como la ActionBar de la actividad
        setSupportActionBar(toolbar)
        // 3. Mostrar el botón de regreso (la flecha)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // 4. Definir qué hace la flecha (cerrar la actividad)
        toolbar.setNavigationOnClickListener { finish() }

        auth = FirebaseAuth.getInstance()
        db = Firebase.firestore
        // 3. Personalizar las tarjetas (includes)
        configurarTarjetas()

        // 4. Configurar el DatePickerDialog
        configurarSelectorFecha()

        // 5. Configurar el OnClickListener del FAB
        configurarFab()
        // Cargar la meta de calorías del usuario
        cargarMetaCalorias()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply padding to the main layout (the ConstraintLayout with ID 'main')
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * Añadimos onResume()
     * Este método se llama CADA VEZ que la actividad vuelve a estar visible
     * (por ej., al volver de AnadirAlimentoActivity).
     */
    override fun onResume() {
        super.onResume()
        // Si ya tenemos una fecha seleccionada, recargamos los datos
        if (fechaSeleccionadaFormatoDoc.isNotEmpty()) {
            cargarDatosDieta(fechaSeleccionadaFormatoDoc)
        }
    }

    private fun configurarTarjetas() {
        // --- Desayuno ---
        binding.cardDesayuno.tvTituloComida.text = "Desayuno"
        binding.cardDesayuno.ivIconoComida.setImageResource(R.drawable.desayuno)
        // Listeners para los botones de Desayuno
        binding.cardDesayuno.btnLimpiarComida.setOnClickListener { confirmarYLiberarComida("desayuno") }

        // --- Comida ---
        binding.cardComida.tvTituloComida.text = "Comida"
        binding.cardComida.ivIconoComida.setImageResource(R.drawable.comida)
        // Listeners para los botones de Comida
        binding.cardComida.btnLimpiarComida.setOnClickListener { confirmarYLiberarComida("comida") }

        // --- Cena ---
        binding.cardCena.tvTituloComida.text = "Cena"
        binding.cardCena.ivIconoComida.setImageResource(R.drawable.cena)
        // Listeners para los botones de Cena
        binding.cardCena.btnLimpiarComida.setOnClickListener { confirmarYLiberarComida("cena") }

        // --- Colación ---
        binding.cardColacion.tvTituloComida.text = "Colación"
        binding.cardColacion.ivIconoComida.setImageResource(R.drawable.colacion)
        // Listeners para los botones de Colación
        binding.cardColacion.btnLimpiarComida.setOnClickListener { confirmarYLiberarComida("colacion") }

        // Limpiamos la UI al inicio (esto está bien)
        limpiarUIComidas()
    }

    private fun limpiarUIComidas() {
        // Esta función resetea la UI a su estado inicial
        val textoDefault = "Añade alimentos"
        binding.cardDesayuno.tvDescripcionComida.text = textoDefault
        binding.cardDesayuno.tvCaloriasComida.text = "0 cal"

        binding.cardComida.tvDescripcionComida.text = textoDefault
        binding.cardComida.tvCaloriasComida.text = "0 cal"

        binding.cardCena.tvDescripcionComida.text = textoDefault
        binding.cardCena.tvCaloriasComida.text = "0 cal"

        binding.cardColacion.tvDescripcionComida.text = textoDefault
        binding.cardColacion.tvCaloriasComida.text = "0 cal"

        binding.tvCaloriasTotales.text = "Total calorías: 0 cal"
    }

    private fun configurarSelectorFecha() {
        val calendario = Calendar.getInstance()
        val anioActual = calendario.get(Calendar.YEAR)
        val mesActual = calendario.get(Calendar.MONTH)
        val diaActual = calendario.get(Calendar.DAY_OF_MONTH)

        // Establecemos la fecha inicial (hoy)
        actualizarFechaSeleccionada(diaActual, mesActual + 1, anioActual)

        binding.btnSeleccionarFechaDieta.setOnClickListener {
            val datePickerDialog = DatePickerDialog(this,
                { _, anioSeleccionado, mesSeleccionado, diaSeleccionado ->
                    // El mes está basado en 0, por eso sumamos 1
                    actualizarFechaSeleccionada(diaSeleccionado, mesSeleccionado + 1, anioSeleccionado)
                },
                anioActual, mesActual, diaActual
            )
            datePickerDialog.show()
        }
    }

    /**
     * Nueva función para centralizar la actualización de fecha
     */
    private fun actualizarFechaSeleccionada(dia: Int, mes: Int, anio: Int) {
        // Formato para el botón (ej. "23/10/2025")
        fechaSeleccionadaFormatoBtn = "$dia/$mes/$anio"
        binding.btnSeleccionarFechaDieta.text = fechaSeleccionadaFormatoBtn

        // Formato para el ID de Firestore (ej. "2025-10-23")
        // Usamos Calendar para asegurar el formato correcto (ej. 05 vs 5)
        val cal = Calendar.getInstance()
        cal.set(anio, mes - 1, dia) // Mes en Calendar es 0-indexado
        val formatoDoc = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        fechaSeleccionadaFormatoDoc = formatoDoc.format(cal.time)

        // Cargar los datos para la fecha seleccionada
        cargarDatosDieta(fechaSeleccionadaFormatoDoc)
    }

    private fun configurarFab() {
        binding.fabAgregarComida.setOnClickListener {
            val intent = Intent(this, AnadirAlimento::class.java)
            // Enviamos la fecha en formato "dd/MM/yyyy"
            intent.putExtra("FECHA_SELECCIONADA", fechaSeleccionadaFormatoBtn)
            startActivity(intent)
        }
    }

    /**
     * Esta es la función que dejamos pendiente, ahora la implementamos.
     */
    private fun cargarDatosDieta(fechaDocumentoId: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Error de autenticación", Toast.LENGTH_SHORT).show()
            finish() // Cerramos la app o mandamos a Login
            return
        }

        // 1. Reseteamos la UI antes de cargar nuevos datos
        limpiarUIComidas()

        // 2. Referencia al documento del día
        val docRef = db.collection("users").document(uid)
            .collection("registros_dieta").document(fechaDocumentoId)

        docRef.get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    // Si el documento existe, lo convertimos a nuestro Data Class
                    val registro = documentSnapshot.toObject<RegistroDieta>()
                    if (registro != null) {
                        actualizarUI(registro)
                    }
                } else {
                    // No hay datos para este día, la UI ya está limpia (gracias a limpiarUIComidas())
                    // Log.d("DietaActivity", "No existe documento para: $fechaDocumentoId")
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al cargar datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Nueva función para rellenar la UI con datos de Firestore
     */
    private fun actualizarUI(registro: RegistroDieta) {
        // --- CALORIE GOAL CHECK ---
        val totalCalorias = registro.calorias_totales_dia
        val meta = metaCaloriasActual // Use the class variable

        binding.tvCaloriasTotales.text = "Total calorías: ${"%.0f".format(totalCalorias)} cal"

        if (totalCalorias > meta && meta > 0) {
            // Goal exceeded! Set text color to red
            binding.tvCaloriasTotales.setTextColor(Color.RED)
            // Optional: Add an icon or more text
            // binding.tvCaloriasTotales.text = "Total calorías: ${"%.0f".format(totalCalorias)} cal ⚠️"
        } else {
            // Goal not exceeded or not set, use default color (e.g., black)
            binding.tvCaloriasTotales.setTextColor(Color.BLACK) // Or your default text color
        }

        // Desayuno
        binding.cardDesayuno.tvDescripcionComida.text = formatarListaAlimentos(registro.desayuno)
        binding.cardDesayuno.tvCaloriasComida.text = "${"%.0f".format(calcularCaloriasLista(registro.desayuno))} cal"

        // Comida
        binding.cardComida.tvDescripcionComida.text = formatarListaAlimentos(registro.comida)
        binding.cardComida.tvCaloriasComida.text = "${"%.0f".format(calcularCaloriasLista(registro.comida))} cal"

        // Cena
        binding.cardCena.tvDescripcionComida.text = formatarListaAlimentos(registro.cena)
        binding.cardCena.tvCaloriasComida.text = "${"%.0f".format(calcularCaloriasLista(registro.cena))} cal"

        // Colación
        binding.cardColacion.tvDescripcionComida.text = formatarListaAlimentos(registro.colacion)
        binding.cardColacion.tvCaloriasComida.text = "${"%.0f".format(calcularCaloriasLista(registro.colacion))} cal"
    }

    /**
     * Nueva función Helper para convertir la lista en un String
     */
    private fun formatarListaAlimentos(lista: List<AlimentoRegistrado>): String {
        if (lista.isEmpty()) {
            return "Añade alimentos"
        }
        // Une cada item con ", "
        // ej. "1.0 Café", "2.0 Pan" -> "1.0 Café, 2.0 Pan"
        return lista.joinToString(separator = ", ") {
            "${it.cantidad} ${it.nombre}"
        }
    }

    /**
     * Nueva función Helper para sumar calorías de una lista
     */
    private fun calcularCaloriasLista(lista: List<AlimentoRegistrado>): Double {
        return lista.sumOf { it.calorias }
    }

    /**
     * Nueva función para cargar la meta de calorías
     */
    // (In Dieta.kt)
    private fun cargarMetaCalorias() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // Keep the default meta or handle error
            metaCaloriasActual = 2000.0 // Default fallback
            binding.tvMetaCalorias.text = "Error al cargar meta (Editar)" // Show error
            binding.tvMetaCalorias.setOnClickListener { /* Keep listener */ }
            return
        }

        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                val meta = documentSnapshot.getDouble("meta_calorias_diarias")
                // Store the loaded meta (or default if null/invalid)
                metaCaloriasActual = if (meta != null && meta > 0) meta else 2000.0

                if (meta != null && meta > 0) {
                    binding.tvMetaCalorias.text = "Meta: ${"%.0f".format(meta)} cal (Editar)"
                } else {
                    binding.tvMetaCalorias.text = "Fija tu meta (clic aquí)"
                }
                binding.tvMetaCalorias.setOnClickListener {
                    mostrarDialogoMetaCalorias(uid, metaCaloriasActual) // Use the stored value
                }
            }
            .addOnFailureListener {
                // Keep the default meta on failure
                metaCaloriasActual = 2000.0
                binding.tvMetaCalorias.text = "Error al cargar meta (Editar)"
                binding.tvMetaCalorias.setOnClickListener { /* Keep listener */ }
            }
    }

    /**
     * --- ¡NUEVA FUNCIÓN AÑADIDA! ---
     * Muestra un diálogo para que el usuario ingrese su meta de calorías.
     */
    private fun mostrarDialogoMetaCalorias(uid: String, metaActual: Double) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Meta de Calorías Diarias")

        // Crear un EditText para que el usuario escriba
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER // Teclado numérico
        input.hint = "Ej. 2000"
        if (metaActual > 0) {
            input.setText(metaActual.toInt().toString()) // Pone el valor actual
        }
        builder.setView(input)

        // Configurar botones
        builder.setPositiveButton("Guardar") { dialog, _ ->
            val metaStr = input.text.toString()
            if (metaStr.isNotEmpty()) {
                try {
                    val nuevaMeta = metaStr.toDouble()
                    // Guardamos la meta en Firestore
                    db.collection("users").document(uid)
                        .update("meta_calorias_diarias", nuevaMeta)
                        .addOnSuccessListener {
                            // Actualizamos la UI inmediatamente
                            binding.tvMetaCalorias.text = "Meta: ${"%.0f".format(nuevaMeta)} cal"
                            metaCaloriasActual = nuevaMeta
                            Toast.makeText(this, "Meta actualizada", Toast.LENGTH_SHORT).show()
                            cargarDatosDieta(fechaSeleccionadaFormatoDoc)
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error al guardar meta", Toast.LENGTH_SHORT).show()
                        }
                    dialog.dismiss()
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Ingresa un número válido", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "El campo no puede estar vacío", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    // --- Función para el botón LIMPIAR ---
    private fun confirmarYLiberarComida(categoria: String) {
        val uid = auth.currentUser?.uid ?: return

        AlertDialog.Builder(this)
            .setTitle("Confirmar Limpieza")
            .setMessage("¿Estás seguro de que quieres eliminar todos los alimentos registrados para '$categoria' en esta fecha?")
            .setPositiveButton("Limpiar") { dialog, _ ->
                val docRef = db.collection("users").document(uid)
                    .collection("registros_dieta").document(fechaSeleccionadaFormatoDoc)

                // Obtenemos el documento actual para saber cuánto restar
                docRef.get().addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val registroActual = documentSnapshot.toObject<RegistroDieta>()
                        if (registroActual != null) {
                            val caloriasARestar = when (categoria) {
                                "desayuno" -> calcularCaloriasLista(registroActual.desayuno)
                                "comida" -> calcularCaloriasLista(registroActual.comida)
                                "cena" -> calcularCaloriasLista(registroActual.cena)
                                "colacion" -> calcularCaloriasLista(registroActual.colacion)
                                else -> 0.0
                            }

                            // Actualizamos Firestore: ponemos la lista vacía y restamos calorías
                            docRef.update(mapOf(
                                categoria to emptyList<AlimentoRegistrado>(),
                                "calorias_totales_dia" to FieldValue.increment(-caloriasARestar) // Restamos
                            )).addOnSuccessListener {
                                Toast.makeText(this, "$categoria limpiado", Toast.LENGTH_SHORT).show()
                                cargarDatosDieta(fechaSeleccionadaFormatoDoc) // Recargamos la UI
                            }.addOnFailureListener {
                                Toast.makeText(this, "Error al limpiar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

}