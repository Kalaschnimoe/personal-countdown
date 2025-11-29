package com.moeproductions.personalcountdown

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit

class SpecificDaysActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var addBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var emptyTv: TextView

    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val pretty = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM, Locale.getDefault())

    private val items = sortedSetOf<String>() // ISO Strings, sortiert

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        title = "Spezifische Tage"

        val appPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val saved = appPrefs.getStringSet("pref_specific_days_set", emptySet()) ?: emptySet()
        items.addAll(saved)

        refreshList()

        addBtn.setOnClickListener { showPicker() }
        saveBtn.setOnClickListener {
            // Persistieren
            appPrefs.edit { putStringSet("pref_specific_days_set", items) }
            // Widget refresh
            WidgetUpdateReceiver.triggerNow(this)
            WidgetUpdateReceiver.scheduleNextMinute(this)
            finish()
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val key = items.elementAt(position)
            items.remove(key)
            refreshList()
            true
        }
    }

    private fun showPicker() {
        val now = Calendar.getInstance()
        val appPrefs = getSharedPreferences("countdown_prefs", MODE_PRIVATE)
        val targetMillis = appPrefs.getLong("target_date", -1L)

        val maxCal = Calendar.getInstance()
        if (targetMillis > 0) maxCal.timeInMillis = targetMillis

        val dlg = DatePickerDialog(
            this,
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.time
                // Nur bis Zieltag zulassen (wenn gesetzt)
                if (targetMillis > 0 && picked.time > targetMillis) {
                    Toast.makeText(this, "Auswahl liegt nach dem Zieltermin.", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }
                items.add(iso.format(picked))
                refreshList()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH)
        )
        // min Date: heute
        dlg.datePicker.minDate = System.currentTimeMillis()
        // max Date: Ziel (falls gesetzt)
        if (targetMillis > 0) dlg.datePicker.maxDate = targetMillis
        dlg.show()
    }

    private fun refreshList() {
        emptyTv.isVisible = items.isEmpty()
        val prettyList = items.mapNotNull {
            try { pretty.format(iso.parse(it)!!) } catch (_: Exception) { it }
        }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, prettyList)
    }

    private fun buildContentView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        emptyTv = TextView(this).apply { text = "Keine Tage ausgewählt" }
        listView = ListView(this)
        addBtn = Button(this).apply { text = "Datum hinzufügen" }
        saveBtn = Button(this).apply { text = "Speichern" }

        root.addView(addBtn, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(emptyTv, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(listView, LinearLayout.LayoutParams.MATCH_PARENT, 0).apply { (this as LinearLayout.LayoutParams).weight = 1f }
        root.addView(saveBtn, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return root
    }
}
