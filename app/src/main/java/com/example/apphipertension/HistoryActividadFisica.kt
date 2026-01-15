package com.example.apphipertension

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ¡CAMBIO! Nombre de la clase
class HistoryActividadFisica : AppCompatActivity() {

    // ¡CAMBIO! Nuevas claves para el Intent
    companion object {
        const val EXTRA_ACTIVIDAD_DOC_ID = "document_id_edicion"
        const val EXTRA_FECHA = "fecha_edicion"
        const val EXTRA_HORA = "hora_edicion"
        const val EXTRA_NOTA = "nota_edicion"
        const val EXTRA_ACTIVIDADES_LIST = "actividades_list_edicion"
    }

    private lateinit var rvHistorial: RecyclerView
    private lateinit var tvNoHistorial: TextView
    private lateinit var historialAdapter: HistorialActividadAdapter // ¡CAMBIO!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ¡CAMBIO! Nuevo layout
        setContentView(R.layout.activity_history_actividad_fisica)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarHistorial)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // ¡CAMBIO! IDs
        rvHistorial = findViewById(R.id.rvHistorialActividad)
        tvNoHistorial = findViewById(R.id.tvNoHistorial)

        rvHistorial.layoutManager = LinearLayoutManager(this)

        // ¡CAMBIO! Nuevo adaptador
        historialAdapter = HistorialActividadAdapter(
            emptyList(),
            onEditClick = { registro -> iniciarEdicion(registro) },
            onDeleteClick = { registro -> confirmarYEliminar(registro) }
        )
        rvHistorial.adapter = historialAdapter

        setupRealtimeListener()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // --- Funciones de formato (sin cambios) ---
    private fun formatHour12h(hour24: String): String {
        return try {
            val time = LocalTime.parse(hour24, DateTimeFormatter.ofPattern("HH:mm"))
            time.format(DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "ES")))
        } catch (e: Exception) { hour24 }
    }

    private fun formatDate(dateISO: String): String {
        return try {
            val date = LocalDate.parse(dateISO, DateTimeFormatter.ISO_DATE)
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) { dateISO }
    }

    // --- ¡CAMBIO! Lógica del listener ---
    private fun setupRealtimeListener() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            tvNoHistorial.text = "Error: Usuario no autenticado."
            tvNoHistorial.visibility = View.VISIBLE
            return
        }

        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .document(user.uid)
            // ¡CAMBIO! Nueva colección
            .collection("actividades_fisicas")
            .orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .orderBy("hora", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    tvNoHistorial.text = "Error al cargar historial: ${e.message}"
                    tvNoHistorial.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                val listaHistorial = mutableListOf<ActividadGuardada>() // ¡CAMBIO!
                if (snapshots != null) {
                    for (doc in snapshots.documents) {
                        val fecha = doc.getString("fecha") ?: ""
                        val hora = doc.getString("hora") ?: ""
                        val nota = doc.getString("nota") ?: ""

                        // ¡CAMBIO! Mapeo a ActividadRegistrada
                        // Firestore devuelve una lista de HashMaps, la convertimos a nuestra Data Class
                        val actividadesRaw = doc.get("actividades") as? List<HashMap<String, Any>> ?: emptyList()
                        val actividadesRegistradas = actividadesRaw.map { map ->
                            ActividadRegistrada(
                                id = map["id"] as? String ?: "",
                                nombre = map["nombre"] as? String ?: "Actividad desconocida",
                                duracionEnMinutos = (map["duracionEnMinutos"] as? Long)?.toInt() ?: 0
                            )
                        }

                        // ¡CAMBIO! Nueva clase ActividadGuardada
                        listaHistorial.add(ActividadGuardada(
                            documentId = doc.id,
                            fecha = fecha,
                            hora = hora,
                            actividades = actividadesRegistradas,
                            nota = nota
                        ))
                    }
                }

                if (listaHistorial.isEmpty()) {
                    tvNoHistorial.visibility = View.VISIBLE
                    rvHistorial.visibility = View.GONE
                } else {
                    tvNoHistorial.visibility = View.GONE
                    rvHistorial.visibility = View.VISIBLE
                    historialAdapter.updateData(listaHistorial)
                }
            }
    }

    // --- ¡CAMBIO! Lógica de Edición ---
    private fun iniciarEdicion(registro: ActividadGuardada) {
        // 1. Intent a ActividadFisica
        val intent = Intent(this, ActividadFisica::class.java).apply {
            // 2. Adjuntar datos
            putExtra(EXTRA_ACTIVIDAD_DOC_ID, registro.documentId)
            putExtra(EXTRA_FECHA, registro.fecha)
            putExtra(EXTRA_HORA, registro.hora)
            putExtra(EXTRA_NOTA, registro.nota)

            // 3. ¡CAMBIO! Enviar la lista de actividades
            // Como ActividadRegistrada es Parcelable, podemos enviarla directamente
            val actividadesList = ArrayList(registro.actividades)
            putParcelableArrayListExtra(EXTRA_ACTIVIDADES_LIST, actividadesList)
        }
        startActivity(intent)
    }

    // --- ¡CAMBIO! Textos de confirmación ---
    private fun confirmarYEliminar(registro: ActividadGuardada) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar este registro de actividad de la fecha ${formatDate(registro.fecha)} a las ${formatHour12h(registro.hora)}?")
            .setPositiveButton("Eliminar") { dialog, _ ->
                eliminarRegistro(registro.documentId)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- ¡CAMBIO! Colección de Firestore ---
    private fun eliminarRegistro(documentId: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Error: No se pudo autenticar.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .document(user.uid)
            .collection("actividades_fisicas") // ¡CAMBIO!
            .document(documentId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Registro eliminado.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al eliminar: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    // --- ¡CAMBIO! Adaptador ---
    inner class HistorialActividadAdapter(
        private var historial: List<ActividadGuardada>, // ¡CAMBIO!
        private val onEditClick: (ActividadGuardada) -> Unit, // ¡CAMBIO!
        private val onDeleteClick: (ActividadGuardada) -> Unit // ¡CAMBIO!
    ) : RecyclerView.Adapter<HistorialActividadAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            // ¡CAMBIO! IDs
            val tvDateTime: TextView = view.findViewById(R.id.tvHistorialDateTime)
            val tvActividades: TextView = view.findViewById(R.id.tvHistorialActividades)
            val tvNota: TextView = view.findViewById(R.id.tvHistorialNota)
            val btnEditar: Button = view.findViewById(R.id.btnEditarActividad)
            val btnEliminar: Button = view.findViewById(R.id.btnEliminarActividad)

            init {
                btnEditar.setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onEditClick(historial[adapterPosition])
                    }
                }
                btnEliminar.setOnClickListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onDeleteClick(historial[adapterPosition])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // ¡CAMBIO! Layout
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_historial_actividad, parent, false)
            return ViewHolder(view)
        }

        // --- ¡CAMBIO! Lógica de Bind ---
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val registro = historial[position]

            val fechaFormateada = formatDate(registro.fecha)
            val horaFormateada = formatHour12h(registro.hora)
            holder.tvDateTime.text = "Fecha: $fechaFormateada | Hora: $horaFormateada"

            // Formateamos la lista de actividades
            val actividadesText = registro.actividades.joinToString(separator = ", ") {
                "${it.nombre} (${it.duracionEnMinutos} min)"
            }
            holder.tvActividades.text = "Actividades: $actividadesText"

            if (registro.nota.isNullOrEmpty()) {
                holder.tvNota.visibility = View.GONE
            } else {
                holder.tvNota.visibility = View.VISIBLE
                holder.tvNota.text = "Nota General: ${registro.nota}"
            }
        }

        override fun getItemCount() = historial.size

        fun updateData(newHistorial: List<ActividadGuardada>) { // ¡CAMBIO!
            historial = newHistorial
            notifyDataSetChanged()
        }
    }
}