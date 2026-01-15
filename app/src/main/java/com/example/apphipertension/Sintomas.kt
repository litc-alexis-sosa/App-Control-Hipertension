package com.example.apphipertension

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.LocalTime
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class Sintomas : AppCompatActivity() {
    private lateinit var sintomasAdapter: SintomasAdapter
    private lateinit var btnGuardar: Button
    private lateinit var etNota: EditText
    private lateinit var btnSeleccionarFecha: Button
    private lateinit var btnSeleccionarHora: Button
    private lateinit var rvSintomas: RecyclerView
    private lateinit var tvError: TextView
    private var fechaSeleccionada: LocalDate = LocalDate.now()
    private var horaSeleccionada: LocalTime = LocalTime.now()
    //Almacena el ID del documento si estamos editando
    private var documentIdEnEdicion: String? = null

    // Lista de síntomas base (la que se actualiza en la UI)
    val sintomasBase = mutableListOf(
        Sintoma("bien", "Estoy bien", R.drawable.ic_bien),
        Sintoma("mareos", "Mareos", R.drawable.ic_mareos),
        Sintoma("dolor_cabeza", "Dolor de cabeza", R.drawable.ic_dolor_cabeza),
        Sintoma("vision_borrosa", "Visión borrosa", R.drawable.ic_vision_borrosa),
        Sintoma("zumbido", "Zumbido en oídos", R.drawable.ic_zumbido),
        Sintoma("dolor_pecho", "Dolor en el pecho", R.drawable.ic_dolor_pecho),
        Sintoma("fatiga", "Fatiga", R.drawable.ic_fatiga),
        Sintoma("sangrado", "Sangrado nasal", R.drawable.ic_sangrado),
        Sintoma("dificultad_respirar", "Dificultad para respirar", R.drawable.ic_dificultad_respirar),
        Sintoma("nauseas", "Náuseas", R.drawable.ic_nauseas),
        Sintoma("vomitos", "Vómitos", R.drawable.ic_vomitos),
        Sintoma("otro", "Otro...", R.drawable.ic_otro)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sintomas)

        // Configura Toolbar con flecha de regreso
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // -- Propiedades de clase:
        rvSintomas = findViewById(R.id.rvSintomas)
        btnSeleccionarFecha = findViewById(R.id.btnSeleccionarFecha)
        btnSeleccionarHora = findViewById(R.id.btnSeleccionarHora)
        btnGuardar = findViewById(R.id.btnGuardarSintomas)
        etNota = findViewById(R.id.etNotaSintoma)
        tvError = findViewById(R.id.tvError)

        // Inicializa la vista de fecha y hora inicial
        btnSeleccionarFecha.text = fechaSeleccionada.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        btnSeleccionarHora.text = horaSeleccionada.format(DateTimeFormatter.ofPattern("hh:mm a"))


        // Inicializa RecyclerView con GridLayoutManager
        sintomasAdapter = SintomasAdapter(sintomasBase) { sintoma, position ->
            // Lógica mejorada para "Otro..."
            if (sintoma.id == "otro") {
                if (sintoma.seleccionado) {
                    // Si ya está seleccionado, lo deselecciona y limpia nota
                    sintomasBase[position].seleccionado = false
                    sintomasBase[position].nota = null
                    sintomasAdapter.notifyItemChanged(position)
                } else {
                    // Si NO está seleccionado, muestra diálogo para agregar la nota
                    showDialogOtroSintoma { texto ->
                        sintomasBase[position].seleccionado = true
                        sintomasBase[position].nota = texto
                        sintomasAdapter.notifyItemChanged(position)
                    }
                }
            } else {
                // Para el resto de síntomas, solo alterna el estado
                sintoma.seleccionado = !sintoma.seleccionado
                sintomasBase[position].nota = null // Limpiar nota de otros síntomas (por si acaso)
                sintomasAdapter.notifyItemChanged(position)
            }
            tvError.visibility = View.GONE // Oculta el error al seleccionar
        }
        val spanCount = calculateNoOfColumns(120) // 120dp ancho mínimo por ítem
        rvSintomas.layoutManager = GridLayoutManager(this, spanCount)
        rvSintomas.adapter = sintomasAdapter

        // --- Lógica de Edición: Precarga de datos ---
        val extras = intent.extras
        if (extras != null && extras.containsKey(HistorySintomasActivity.EXTRA_DOCUMENT_ID)) {
            precargarSintomasParaEdicion(extras)
        }


        // DatePicker
        btnSeleccionarFecha.setOnClickListener {
            val hoy = fechaSeleccionada
            val picker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val fecha = LocalDate.of(year, month + 1, dayOfMonth)
                    fechaSeleccionada = fecha
                    btnSeleccionarFecha.text = fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                },
                hoy.year, hoy.monthValue - 1, hoy.dayOfMonth
            )
            picker.show()
        }

        // TimePicker
        btnSeleccionarHora.setOnClickListener {
            val ahora = horaSeleccionada
            val picker = android.app.TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val hora = LocalTime.of(hourOfDay, minute)
                    horaSeleccionada = hora
                    btnSeleccionarHora.text = hora.format(DateTimeFormatter.ofPattern("hh:mm a"))
                },
                ahora.hour, ahora.minute, false // FALSE para formato 12h
            )
            picker.show()
        }

        // Botón Guardar / Actualizar
        btnGuardar.setOnClickListener {
            val fecha = fechaSeleccionada
            val hora = horaSeleccionada
            val nota = etNota.text.toString()
            val sintomasSeleccionados = sintomasBase.filter { it.seleccionado }

            if (sintomasSeleccionados.isEmpty()) {
                tvError.visibility = View.VISIBLE
            } else {
                tvError.visibility = View.GONE

                // Usamos la función modificada que decide si actualizar o agregar
                guardarOActualizarSintomas(
                    fecha.format(DateTimeFormatter.ISO_DATE),
                    hora.format(DateTimeFormatter.ofPattern("HH:mm")),
                    sintomasSeleccionados,
                    nota,
                    onSuccess = {
                        val mensaje = if (documentIdEnEdicion != null) "Síntomas actualizados" else "Síntomas guardados"
                        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()

                        // Si estábamos editando, al terminar volvemos al historial
                        if (documentIdEnEdicion != null) {
                            finish()
                        } else {
                            // Si es un nuevo registro, limpiamos la UI
                            sintomasBase.forEach { it.seleccionado = false; it.nota = null }
                            sintomasAdapter.notifyDataSetChanged()
                            etNota.text.clear()
                        }
                    },
                    onError = { ex ->
                        Toast.makeText(this, "Error: ${ex.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        val btnVerHistorial = findViewById<Button>(R.id.btnVerHistorial)
        btnVerHistorial.setOnClickListener {
            // CORRECCIÓN APLICADA: Limpiar la pila de Activities al ir al Historial
            val intent = Intent(this, HistorySintomasActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // Destruye las Activities superiores (como esta, si es la de edición)
            }
            startActivity(intent)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // --- FUNCIÓN: Precarga la UI con datos de edición ---
    private fun precargarSintomasParaEdicion(extras: Bundle) {
        // 1. Obtener ID del documento y actualizar el título/botón
        documentIdEnEdicion = extras.getString(HistorySintomasActivity.EXTRA_DOCUMENT_ID)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.title = "Editar Síntomas"
        btnGuardar.text = "Actualizar Registro"

        // 2. Precargar Fecha y Hora (ISO 8601)
        val fechaIso = extras.getString(HistorySintomasActivity.EXTRA_FECHA) ?: return
        val hora24h = extras.getString(HistorySintomasActivity.EXTRA_HORA) ?: return

        try {
            fechaSeleccionada = LocalDate.parse(fechaIso, DateTimeFormatter.ISO_DATE)
            horaSeleccionada = LocalTime.parse(hora24h, DateTimeFormatter.ofPattern("HH:mm"))

            // Actualizar botones de fecha/hora (formato de visualización)
            btnSeleccionarFecha.text = fechaSeleccionada.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            btnSeleccionarHora.text = horaSeleccionada.format(DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "ES")))
        } catch (e: Exception) {
            Toast.makeText(this, "Error cargando fecha/hora: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // 3. Precargar Nota General
        val notaGeneral = extras.getString(HistorySintomasActivity.EXTRA_NOTA)
        etNota.setText(notaGeneral)

        // 4. Precargar Síntomas seleccionados
        val idsSeleccionados = extras.getStringArrayList(HistorySintomasActivity.EXTRA_SINTOMA_IDS) ?: return
        val notaOtroSintoma = extras.getString("otro_sintoma_nota")

        sintomasBase.forEach { sintoma ->
            if (idsSeleccionados.contains(sintoma.id)) {
                sintoma.seleccionado = true

                // Si es "otro", precargar la nota personalizada guardada
                if (sintoma.id == "otro") {
                    sintoma.nota = notaOtroSintoma
                }
            } else {
                sintoma.seleccionado = false
                sintoma.nota = null
            }
        }
        sintomasAdapter.notifyDataSetChanged()
    }


    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun calculateNoOfColumns(minItemWidthDp: Int): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val noOfColumns = (screenWidthDp / minItemWidthDp).toInt()
        return noOfColumns.coerceAtLeast(2) // aseguramos mínimo 2 columnas
    }

    fun getIconResIdForSintoma(id: String): Int {
        return when (id) {
            "bien" -> R.drawable.ic_bien
            "mareos" -> R.drawable.ic_mareos
            "dolor_cabeza" -> R.drawable.ic_dolor_cabeza
            "vision_borrosa" -> R.drawable.ic_vision_borrosa
            "zumbido" -> R.drawable.ic_zumbido
            "dolor_pecho" -> R.drawable.ic_dolor_pecho
            "fatiga" -> R.drawable.ic_fatiga
            "sangrado" -> R.drawable.ic_sangrado
            "dificultad_respirar" -> R.drawable.ic_dificultad_respirar
            "nauseas" -> R.drawable.ic_nauseas
            "vomitos" -> R.drawable.ic_vomitos
            "otro" -> R.drawable.ic_otro
            else -> R.drawable.ic_otro
        }
    }

    // --- FUNCIÓN MODIFICADA: Guarda o Actualiza según el estado de Edición ---
    fun guardarOActualizarSintomas(
        fecha: String,
        hora: String,
        sintomasSeleccionados: List<Sintoma>,
        nota: String = "",
        onSuccess: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        // 1. Mapeo de Síntomas (igual que antes)
        val sintomasMapeados = sintomasSeleccionados.map { sintoma ->
            val nombreFinal = if (sintoma.id == "otro" && !sintoma.nota.isNullOrBlank()) {
                sintoma.nota!!
            } else {
                sintoma.nombre
            }
            mapOf("id" to sintoma.id, "nombre" to nombreFinal)
        }

        val registro = hashMapOf(
            "fecha" to fecha,
            "hora" to hora,
            "sintomas" to sintomasMapeados,
            "nota" to nota
        )

        val ref = db.collection("users").document(user.uid).collection("sintomas")

        if (documentIdEnEdicion != null) {
            // MODO EDICIÓN: Actualizar el documento existente
            ref.document(documentIdEnEdicion!!)
                .set(registro as Map<String, Any>)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { ex -> onError(ex) }
        } else {
            // MODO CREACIÓN: Agregar nuevo documento
            ref.add(registro)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { ex -> onError(ex) }
        }
    }


    // La función cargarSintomas se mantiene sin cambios, pero ahora usa el documentId
    // ... (cargarSintomas function remains)
    fun cargarSintomas(
        fecha: String,
        onResult: (List<SintomaGuardado>) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val fechaStr = fecha.toString() // "yyyy-MM-dd"

        db.collection("users")
            .document(user.uid)
            .collection("sintomas")
            .whereEqualTo("fecha", fechaStr)
            .get()
            .addOnSuccessListener { result ->
                val lista = mutableListOf<SintomaGuardado>()
                for (doc in result) {
                    val sintomasRaw = doc.get("sintomas") as? List<HashMap<String, Any>> ?: emptyList()
                    val sintomas = sintomasRaw.map {
                        Sintoma(
                            id = it["id"] as? String ?: "",
                            nombre = it["nombre"] as? String ?: "",
                            iconResId = getIconResIdForSintoma(it["id"] as? String ?: ""),
                            seleccionado = true
                        )
                    }
                    val nota = doc.getString("nota") ?: ""
                    val hora = doc.getString("hora") ?: ""

                    lista.add(SintomaGuardado(
                        documentId = doc.id,
                        fecha = fechaStr,
                        hora = hora,
                        sintomas = sintomas,
                        nota = nota
                    ))
                }
                onResult(lista)
            }
    }

    fun showDialogOtroSintoma(onResult: (String) -> Unit) {
        val input = EditText(this)
        input.hint = "Describe el síntoma"
        AlertDialog.Builder(this)
            .setTitle("Agregar síntoma personalizado")
            .setView(input)
            .setPositiveButton("Aceptar") { d, _ ->
                val texto = input.text.toString().trim()
                if (texto.isNotEmpty()) onResult(texto)
                d.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    class SintomasAdapter(
        private val sintomas: List<Sintoma>,
        val onClick: (Sintoma, Int) -> Unit
    ) : RecyclerView.Adapter<SintomasAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icono: ImageView = view.findViewById(R.id.imgSintoma)
            val nombre: TextView = view.findViewById(R.id.tvSintomaNombre)
            init {
                view.setOnClickListener {
                    onClick(sintomas[adapterPosition], adapterPosition)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_sintoma, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sintoma = sintomas[position]
            holder.icono.setImageResource(sintoma.iconResId)

            // Lógica mejorada para mostrar el nombre del síntoma "Otro..."
            val nombreMostrar = if (sintoma.id == "otro" && sintoma.seleccionado && !sintoma.nota.isNullOrBlank()) {
                // Muestra una versión corta si es el síntoma "Otro" y está seleccionado
                "Otro: ${sintoma.nota!!.take(15)}..."
            } else {
                sintoma.nombre
            }
            holder.nombre.text = nombreMostrar

            // Usa los drawables definidos
            holder.itemView.setBackgroundResource(
                if (sintoma.seleccionado) R.drawable.bg_sintoma_selected else R.drawable.bg_sintoma_unselected
            )

            // Se mantiene la transparencia como indicador visual secundario
            holder.itemView.alpha = if (sintoma.seleccionado) 1.0f else 0.7f // Aumento la opacidad del no seleccionado para mejor contraste
        }

        override fun getItemCount() = sintomas.size
    }

}