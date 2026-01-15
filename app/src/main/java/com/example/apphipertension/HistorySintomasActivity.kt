package com.example.apphipertension

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistorySintomasActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DOCUMENT_ID = "document_id_edicion"
        const val EXTRA_FECHA = "fecha_edicion"
        const val EXTRA_HORA = "hora_edicion"
        const val EXTRA_NOTA = "nota_edicion"
        // Clave para enviar la lista de IDs de síntomas seleccionados
        const val EXTRA_SINTOMA_IDS = "sintoma_ids_edicion"
    }

    private lateinit var rvHistorial: RecyclerView
    private lateinit var tvNoHistorial: TextView
    private lateinit var historialAdapter: HistorialSintomasAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_history_sintomas)

        // Configurar Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbarHistorial)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        rvHistorial = findViewById(R.id.rvHistorialSintomas)
        tvNoHistorial = findViewById(R.id.tvNoHistorial)

        rvHistorial.layoutManager = LinearLayoutManager(this)
        historialAdapter = HistorialSintomasAdapter(
            emptyList(),
            onEditClick = { registro -> iniciarEdicion(registro) },
            onDeleteClick = { registro -> confirmarYEliminar(registro) }
        )
        rvHistorial.adapter = historialAdapter

        // Usamos addSnapshotListener para que la lista se actualice en tiempo real
        setupRealtimeListener()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // ... (formatHour12h y formatDate se mantienen)
    private fun formatHour12h(hour24: String): String {
        return try {
            val time = LocalTime.parse(hour24, DateTimeFormatter.ofPattern("HH:mm"))
            time.format(DateTimeFormatter.ofPattern("hh:mm a", Locale("es", "ES")))
        } catch (e: Exception) {
            hour24
        }
    }

    private fun formatDate(dateISO: String): String {
        return try {
            val date = LocalDate.parse(dateISO, DateTimeFormatter.ISO_DATE)
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) {
            dateISO
        }
    }

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
            .collection("sintomas")
            .orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .orderBy("hora", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    tvNoHistorial.text = "Error al cargar historial en tiempo real: ${e.message}"
                    tvNoHistorial.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                val listaHistorial = mutableListOf<SintomaGuardado>()
                if (snapshots != null) {
                    for (doc in snapshots.documents) {
                        val fecha = doc.getString("fecha") ?: ""
                        val hora = doc.getString("hora") ?: ""
                        val nota = doc.getString("nota") ?: ""

                        val sintomasRaw = doc.get("sintomas") as? List<HashMap<String, Any>> ?: emptyList()

                        val sintomasRegistrados = sintomasRaw.map {
                            Sintoma(
                                id = it["id"] as? String ?: "",
                                nombre = it["nombre"] as? String ?: "Síntoma desconocido",
                                iconResId = 0,
                                seleccionado = true,
                                // Si el ID es 'otro', el nombre es la nota personalizada
                                nota = if (it["id"] == "otro") it["nombre"] as? String else null
                            )
                        }

                        listaHistorial.add(SintomaGuardado(
                            documentId = doc.id,
                            fecha = fecha,
                            hora = hora,
                            sintomas = sintomasRegistrados,
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

    private fun iniciarEdicion(registro: SintomaGuardado) {
        // 1. Crear el Intent para volver a Sintomas Activity
        val intent = Intent(this, Sintomas::class.java).apply {

            // 2. Adjuntar los datos del registro a editar
            putExtra(EXTRA_DOCUMENT_ID, registro.documentId)
            putExtra(EXTRA_FECHA, registro.fecha)
            putExtra(EXTRA_HORA, registro.hora)
            putExtra(EXTRA_NOTA, registro.nota)

            // 3. Crear una lista de IDs de síntomas para enviarlos de forma simple
            val sintomaIds = ArrayList(registro.sintomas.map { it.id })
            putExtra(EXTRA_SINTOMA_IDS, sintomaIds)

            // 4. Si hay una nota para el síntoma "otro", enviarla también
            val sintomaOtro = registro.sintomas.find { it.id == "otro" }
            if (sintomaOtro != null && !sintomaOtro.nombre.isNullOrBlank()) {
                // Usamos el nombre guardado en Firestore (que es la nota) y lo mandamos
                putExtra("otro_sintoma_nota", sintomaOtro.nombre)
            }
        }

        // 5. Iniciar la actividad
        startActivity(intent)
    }

    private fun confirmarYEliminar(registro: SintomaGuardado) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar este registro de síntomas de la fecha ${formatDate(registro.fecha)} a las ${formatHour12h(registro.hora)}?")
            .setPositiveButton("Eliminar") { dialog, _ ->
                eliminarRegistro(registro.documentId)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarRegistro(documentId: String) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Error: No se pudo autenticar al usuario para eliminar.", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .document(user.uid)
            .collection("sintomas")
            .document(documentId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Registro eliminado correctamente.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al eliminar el registro: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    // --- Adaptador para el RecyclerView ---
    inner class HistorialSintomasAdapter(
        private var historial: List<SintomaGuardado>,
        private val onEditClick: (SintomaGuardado) -> Unit,
        private val onDeleteClick: (SintomaGuardado) -> Unit
    ) : RecyclerView.Adapter<HistorialSintomasAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDateTime: TextView = view.findViewById(R.id.tvHistorialDateTime)
            val tvSintomas: TextView = view.findViewById(R.id.tvHistorialSintomas)
            val tvNota: TextView = view.findViewById(R.id.tvHistorialNota)

            val btnEditar: Button = view.findViewById(R.id.btnEditarSintoma)
            val btnEliminar: Button = view.findViewById(R.id.btnEliminarSintoma)

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
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_historial_sintoma, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val registro = historial[position]

            val fechaFormateada = formatDate(registro.fecha)
            val horaFormateada = formatHour12h(registro.hora)
            holder.tvDateTime.text = "Fecha: $fechaFormateada | Hora: $horaFormateada"

            val sintomasText = registro.sintomas.joinToString(separator = ", ") { it.nombre }
            holder.tvSintomas.text = "Síntomas: $sintomasText"

            if (registro.nota.isNullOrEmpty()) {
                holder.tvNota.visibility = View.GONE
            } else {
                holder.tvNota.visibility = View.VISIBLE
                holder.tvNota.text = "Nota General: ${registro.nota}"
            }
        }

        override fun getItemCount() = historial.size

        fun updateData(newHistorial: List<SintomaGuardado>) {
            historial = newHistorial
            notifyDataSetChanged()
        }
    }
}