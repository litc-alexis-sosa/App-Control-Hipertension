package com.example.apphipertension

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MeasurementAdapter
    private val measurementList = mutableListOf<MeasurementWithId>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_history)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val tvEmpty = findViewById<TextView>(R.id.tvEmptyHistorial)
        recyclerView = findViewById(R.id.recyclerViewHistory)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MeasurementAdapter(
            measurementList,
            onEdit = { measurement ->
                showEditMeasurementDialog(measurement) {
                    recargarHistorial()
                }
            },
            onDelete = { measurement ->
                eliminarMedicion(measurement) {
                    recargarHistorial()
                }
            }
        )
        recyclerView.adapter = adapter

        recargarHistorial() // ¡Carga los datos al iniciar!

        //MenuInferior
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            val intent: Intent? = when (item.itemId) {
                R.id.nav_home -> Intent(this, MainActivity::class.java)
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
                // --- USE THESE FLAGS ---
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                // -----------------------
                startActivity(intent)
                // No need for finish() or overridePendingTransition here
                true // Indicate navigation occurred
            } else {
                item.itemId == R.id.nav_more // Return true if 'More' was clicked, false otherwise
            }
        }


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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
        sheet.show()
    }

    private fun recargarHistorial() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val tvEmpty = findViewById<TextView>(R.id.tvEmptyHistorial)

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("mediciones")
            .orderBy("date", Query.Direction.DESCENDING)
            .orderBy("time", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                measurementList.clear()
                for (doc in result) {
                    val medicion = doc.toObject(Measurement::class.java)
                    measurementList.add(MeasurementWithId(doc.id, medicion))
                }
                adapter.notifyDataSetChanged()
                tvEmpty.visibility = if (measurementList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar historial", Toast.LENGTH_SHORT).show()
            }
    }

    private fun eliminarMedicion(medicion: MeasurementWithId, onSuccess: () -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("mediciones")
            .document(medicion.id)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener {
                Toast.makeText(this, "Error al eliminar medición", Toast.LENGTH_SHORT).show()
            }
    }

        private fun showEditMeasurementDialog(medicion: MeasurementWithId, onSaved: () -> Unit) {
        val measurement = medicion.measurement
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_pressure, null)
        val editFecha = dialogView.findViewById<EditText>(R.id.editFecha)
        val editHora = dialogView.findViewById<EditText>(R.id.editHora)
        val editSistolica = dialogView.findViewById<EditText>(R.id.editSistolica)
        val editDiastolica = dialogView.findViewById<EditText>(R.id.editDiastolica)
        val editPulso = dialogView.findViewById<EditText>(R.id.editPulso)
        val editNota = dialogView.findViewById<EditText>(R.id.editNota)

        // Parsear fecha y hora actuales
        var date = try { LocalDate.parse(measurement.date) } catch (_: Exception) { LocalDate.now() }
        var time = try {
            // Puede venir "14:32" o "02:32 PM"
            if (measurement.time.contains("AM") || measurement.time.contains("PM")) {
                LocalTime.parse(measurement.time, java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
            } else {
                LocalTime.parse(measurement.time)
            }
        } catch (_: Exception) { LocalTime.now() }

        // Set valores actuales
        editFecha.setText(date.toString())
        editHora.setText(time.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))) // Muestra en 12h AM/PM
        editSistolica.setText(measurement.sistolica.toString())
        editDiastolica.setText(measurement.diastolica.toString())
        editPulso.setText(measurement.pulso.toString())
        editNota.setText(measurement.nota ?: "")

        // DatePicker
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

        // TimePicker
        editHora.setOnClickListener {
            val tp = TimePickerDialog(
                this,
                { _, h, min ->
                    time = LocalTime.of(h, min)
                    editHora.setText(time.format(DateTimeFormatter.ofPattern("hh:mm a")))
                },
                time.hour, time.minute, false // 12h AM/PM
            )
            tp.show()
        }

        AlertDialog.Builder(this)
            .setTitle("Editar medición")
            .setView(dialogView)
            .setPositiveButton("Guardar") { dialog, _ ->
                val s = editSistolica.text.toString().toIntOrNull()
                val d = editDiastolica.text.toString().toIntOrNull()
                val p = editPulso.text.toString().toIntOrNull()
                val nota = editNota.text.toString()
                if (s != null && d != null && p != null) {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val db = FirebaseFirestore.getInstance()
                        db.collection("users").document(user.uid)
                            .collection("mediciones").document(medicion.id)
                            .set(measurement.copy(
                                date = date.toString(),
                                time = time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), // Guarda siempre en 24h
                                sistolica = s,
                                diastolica = d,
                                pulso = p,
                                nota = nota
                            ))
                            .addOnSuccessListener {
                                Toast.makeText(this, "Medición actualizada", Toast.LENGTH_SHORT).show()
                                onSaved()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show()
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

}

class MeasurementAdapter(
    private val items: List<MeasurementWithId>,
    private val onEdit: (MeasurementWithId) -> Unit,
    private val onDelete: (MeasurementWithId) -> Unit
) : RecyclerView.Adapter<MeasurementAdapter.MeasurementViewHolder>() {

    class MeasurementViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateTime = view.findViewById<TextView>(R.id.tvFechaHora)
        val pressure = view.findViewById<TextView>(R.id.tvPresion)
        val pulse = view.findViewById<TextView>(R.id.tvPulso)
        val note = view.findViewById<TextView>(R.id.tvNota)
        val btnEditar = view.findViewById<Button>(R.id.btnEditar)
        val btnQuitar = view.findViewById<Button>(R.id.btnQuitar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_measurement, parent, false)
        return MeasurementViewHolder(view)
    }

    override fun onBindViewHolder(holder: MeasurementViewHolder, position: Int) {
        val m = items[position].measurement

        val hora12 = try {
            java.time.LocalTime.parse(m.time)
                .format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (e: Exception) {
            m.time
        }

        holder.dateTime.text = "${m.date} $hora12"
        holder.pressure.text = "Sistólica/Diastólica: ${m.sistolica}/${m.diastolica} mmHg"
        holder.pulse.text = "Pulso: ${m.pulso} LPM"
        if (m.nota.isNullOrBlank()) {
            holder.note.visibility = View.GONE // Hide if note is empty
        } else {
            holder.note.visibility = View.VISIBLE // Show if note exists
            holder.note.text = "Nota: ${m.nota}" // Set the text
        }
        holder.btnEditar.setOnClickListener { onEdit(items[position]) }
        holder.btnQuitar.setOnClickListener { onDelete(items[position]) }
    }

    override fun getItemCount() = items.size
}