package com.moeproductions.personalcountdown

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.*
import com.applandeo.materialcalendarview.CalendarView
import com.applandeo.materialcalendarview.builders.DatePickerBuilder
import com.applandeo.materialcalendarview.listeners.OnSelectDateListener
import java.util.Calendar


class SpecificDaysActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var addBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var emptyTv: TextView

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Set, das in den SharedPreferences gespeichert wird (keine Duplikate)
    private val selectedDays = mutableSetOf<String>()

    // Datenbasis für den Adapter
    private val items = mutableListOf<String>()

    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = buildContentView()
        setContentView(root)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Vorhandene Tage aus den Settings laden
        selectedDays.addAll(
            prefs.getStringSet("pref_specific_days_set", emptySet()) ?: emptySet()
        )

        items.clear()
        items.addAll(selectedDays.toList().sorted())

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            items
        )
        listView.adapter = adapter
        emptyTv.isVisible = items.isEmpty()

        // Optional: Tag per Long-Klick aus der Liste löschen
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val value = items[position]
            selectedDays.remove(value)
            updateList()
            true
        }

        addBtn.setOnClickListener {
            openCalendarDialog()
        }

        saveBtn.setOnClickListener {
            prefs.edit {
                putStringSet("pref_specific_days_set", selectedDays)
            }
            Toast.makeText(this, "Tage gespeichert", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Öffnet einen Dialog mit MaterialCalendarView für Mehrfachauswahl.
     */
    private fun openCalendarDialog() {
        // Bestehende Tage (yyyy-MM-dd) -> List<Calendar> für Pre-Selection
        val preselectedCalendars = selectedDays.mapNotNull { dateString ->
            try {
                val date = dateFormat.parse(dateString)
                if (date != null) {
                    Calendar.getInstance().apply { time = date }
                } else null
            } catch (e: Exception) {
                null
            }
        }

        val listener = object : OnSelectDateListener {
            override fun onSelect(calendar: List<Calendar>) {
                selectedDays.clear()
                calendar.forEach { cal ->
                    val str = dateFormat.format(cal.time)
                    selectedDays.add(str)
                }
                updateList()
            }
        }

        val builder = DatePickerBuilder(this, listener)
            .pickerType(CalendarView.MANY_DAYS_PICKER)
            .selectedDays(preselectedCalendars)

        val dialog = builder.build()
        dialog.show()
    }


    /**
     * ListView aktualisieren, wenn sich selectedDays geändert hat.
     */
    private fun updateList() {
        items.clear()
        items.addAll(selectedDays.toList().sorted())
        adapter.notifyDataSetChanged()
        emptyTv.isVisible = items.isEmpty()
    }

    /**
     * Einfaches Layout: Button oben, Text "Keine Tage...", Liste mit weight=1, Speichern-Button.
     */
    private fun buildContentView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        emptyTv = TextView(this).apply { text = "Keine Tage ausgewählt" }
        listView = ListView(this)
        addBtn = Button(this).apply { text = "Datum auswählen" }
        saveBtn = Button(this).apply { text = "Speichern" }

        root.addView(
            addBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            emptyTv,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val listParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        ).apply {
            weight = 1f
        }
        root.addView(listView, listParams)

        root.addView(
            saveBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        return root
    }
}
