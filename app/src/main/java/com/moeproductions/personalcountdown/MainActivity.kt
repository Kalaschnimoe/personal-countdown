package com.moeproductions.personalcountdown

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var selectedDateText: TextView
    private lateinit var countdownText: TextView
    private lateinit var titleInput: EditText

    private val uiHandler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updatePreview()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    // Keys
    private val PREFS_NAME = "countdown_prefs"
    private val KEY_TARGET = "target_date"
    private val KEY_TITLE = "event_title"

    // Temporäre Auswahl (bis gespeichert)
    private var selectedMillis: Long? = null
    private var selectedTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        titleInput = findViewById(R.id.titleInput)
        selectedDateText = findViewById(R.id.selectedDateText)
        countdownText = findViewById(R.id.countdownText)

        val pickDateButton: Button = findViewById(R.id.pickDateButton)
        val pickTimeButton: Button = findViewById(R.id.pickTimeButton)
        val presetToday: Button = findViewById(R.id.presetToday)
        val preset7: Button = findViewById(R.id.preset7)
        val preset30: Button = findViewById(R.id.preset30)
        val shareButton: Button = findViewById(R.id.shareButton)
        val resetButton: Button = findViewById(R.id.resetButton)
        val openSettingsButton: Button = findViewById(R.id.openSettingsButton)
        openSettingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }


        val saveCloseButton: Button = findViewById(R.id.saveCloseButton)
        saveCloseButton.setOnClickListener {
            // 1. Titel und Zieldatum holen
            val title = titleInput.text.toString().trim()
            val targetMillis = selectedMillis ?: prefs.getLong(KEY_TARGET, -1L).takeIf { it > 0 }

            // 2. Validieren: Wurde ein Zieldatum gewählt?
            if (targetMillis == null) {
                Toast.makeText(
                    this, "Bitte zuerst ein Ziel-Datum/-Zeit wählen.", Toast.LENGTH_SHORT
                ).show()
                // Wichtig: Beendet die Ausführung des setOnClickListeners hier vollständig
                return@setOnClickListener
            }

            // --- Nur wenn die Validierung erfolgreich war, geht es hier weiter ---

            // 3. Daten in SharedPreferences speichern
            prefs.edit(commit = true) {
                putLong(KEY_TARGET, targetMillis)
                putString(KEY_TITLE, title)
            }

            // 4. Widget sofort aktualisieren
            updateWidgets()

            // 5. Minütliche Updates für die Zukunft sicherstellen
            // Diese beiden Befehle sorgen dafür, dass das Widget sofort und dann minütlich aktualisiert wird.
            WidgetUpdateReceiver.triggerNow(this)
            WidgetUpdateReceiver.scheduleNextMinute(this)

            // 6. Activity schließen
            finish()
        }

        // Vorbelegen aus Prefs
        val savedMillis = prefs.getLong(KEY_TARGET, -1L)
        val savedTitle = prefs.getString(KEY_TITLE, "") ?: ""
        if (savedMillis > 0) {
            selectedMillis = savedMillis
            selectedDateText.text = "Ziel: ${formatFull(Date(savedMillis))}"
        }
        if (savedTitle.isNotBlank()) {
            selectedTitle = savedTitle
            titleInput.setText(savedTitle)
        }

        pickDateButton.setOnClickListener { showDatePicker() }
        pickTimeButton.setOnClickListener { showTimePicker() }
        presetToday.setOnClickListener { setPresetDays(0) }
        preset7.setOnClickListener { setPresetDays(7) }
        preset30.setOnClickListener { setPresetDays(30) }

        shareButton.setOnClickListener { shareCountdown() }
        resetButton.setOnClickListener { resetAll() }

        // Titeländerung live speichern (und Widget nudgen)
        titleInput.setOnEditorActionListener { view, _, _ ->
            // 1. Neuen Titel holen und speichern
            val newTitle = view.text.toString().trim()
            selectedTitle = newTitle // Lokale Variable aktualisieren
            prefs.edit(commit = true) {
                putString(KEY_TITLE, newTitle)
            }

            // 2. WIDGET SOFORT AKTUALISIEREN
            updateWidgets()

            // 3. Zukünftige Alarme sicherstellen (optional, aber gut für Konsistenz)
            WidgetUpdateReceiver.cancelAlarm(this) // Alten Alarm stoppen
            WidgetUpdateReceiver.scheduleNextMinute(this) // Neuen Alarm planen

            true // Signalisiert, dass das Event verarbeitet wurde
        }
    }

    override fun onStart() {
        super.onStart()
        uiHandler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        uiHandler.removeCallbacks(ticker)
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        selectedMillis?.let { cal.timeInMillis = it }

        DatePickerDialog(
            this, { _, y, m, d ->
                val tmp = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, d)
                    // falls keine Uhrzeit gewählt: 23:59:59.999
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                selectedMillis = tmp.timeInMillis
                persistAndRefresh()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        val initial = selectedMillis ?: cal.timeInMillis
        cal.timeInMillis = initial

        val is24 = DateFormat.is24HourFormat(this)
        TimePickerDialog(
            this, { _, hour, minute ->
                val tmp = Calendar.getInstance().apply {
                    timeInMillis = selectedMillis ?: System.currentTimeMillis()
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedMillis = tmp.timeInMillis
                persistAndRefresh()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), is24
        ).show()
    }

    private fun setPresetDays(days: Int) {
        val now = System.currentTimeMillis()
        val tmp = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, days)
            // auf 23:59:59.999 des Zieltags setzen
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        selectedMillis = tmp.timeInMillis
        persistAndRefresh()
    }

    private fun persistAndRefresh() {
        val millis = selectedMillis
        if (millis == null) {
            selectedDateText.text = "Kein Ziel gesetzt"
            countdownText.text = "—"
            return
        }
        if (millis <= System.currentTimeMillis()) {
            Toast.makeText(this, "Ziel liegt in der Vergangenheit.", Toast.LENGTH_SHORT).show()
        }
        // Speichern
        prefs.edit(commit = true) {
            putLong(KEY_TARGET, millis)
            putString(KEY_TITLE, titleInput.text.toString().trim())
        }

        selectedDateText.text = "Ziel: ${formatFull(Date(millis))}"

        // Widget updaten
        lifecycleScope.launch {
            CountdownWidget().updateAll(this@MainActivity)
        }

        WidgetUpdateReceiver.scheduleNextMinute(this)
        WidgetUpdateReceiver.triggerNow(this)

        updatePreview()
    }

    private fun updatePreview() {
        val millis = selectedMillis ?: prefs.getLong(KEY_TARGET, -1L).takeIf { it > 0 }
        if (millis == null) {
            countdownText.text = "—"
            return
        }

        val now = System.currentTimeMillis()
        if (millis <= now) {
            countdownText.text = "✨ Datum überschritten"
            return
        }

        val diff = millis - now
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        val afterDays = diff - TimeUnit.DAYS.toMillis(days)
        val hours = TimeUnit.MILLISECONDS.toHours(afterDays)
        val afterHours = afterDays - TimeUnit.HOURS.toMillis(hours)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(afterHours)
        val afterMinutes = afterHours - TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(afterMinutes)

        val title = (titleInput.text?.toString()?.takeIf { it.isNotBlank() } ?: prefs.getString(
            KEY_TITLE,
            ""
        )).orEmpty()

        val line1 = if (title.isBlank()) "Countdown" else title
        val dayLabel = if (days == 1L) "Tag" else "Tage"
        val line2 = String.format(
            Locale.getDefault(), "%d %s %02d:%02d:%02d", days, dayLabel, hours, minutes, seconds
        )
        countdownText.text = "$line1\n$line2"
    }

    private fun shareCountdown() {
        val millis = selectedMillis ?: prefs.getLong(KEY_TARGET, -1L)
        if (millis <= 0) {
            Toast.makeText(this, "Kein Ziel gesetzt.", Toast.LENGTH_SHORT).show()
            return
        }
        val title = titleInput.text.toString().ifBlank { "Countdown" }
        val formatted = formatFull(Date(millis))
        val text = "$title – Ziel am $formatted\nNoch: ${
            countdownText.text.toString().substringAfter('\n', missingDelimiterValue = "")
        }"

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startActivity(android.content.Intent.createChooser(intent, "Teilen"))
    }

    private fun resetAll() {
        // 1. Lokale und UI-Werte zurücksetzen    selectedMillis = null
        selectedTitle = ""
        titleInput.setText("")
        selectedDateText.text = "Kein Ziel gesetzt"
        countdownText.text = "—"

        // 2. Gespeicherte Daten löschen
        prefs.edit(commit = true) {
            clear() // Löscht alle Einträge in diesen SharedPreferences
        }

        // 3. Alle zukünftigen minütlichen Alarme stoppen
        WidgetUpdateReceiver.cancelAlarm(this)

        // 4. Widget sofort aktualisieren, um den leeren Zustand anzuzeigen
        updateWidgets()

        // 5. Nutzer feedback geben
        Toast.makeText(this, "Zurückgesetzt.", Toast.LENGTH_SHORT).show()
    }

    private fun formatFull(date: Date): String {
        // Nutzt Systemsprache/Region
        val sdf = SimpleDateFormat.getDateTimeInstance(
            SimpleDateFormat.MEDIUM, SimpleDateFormat.SHORT, Locale.getDefault()
        )
        return sdf.format(date)
    }

    private fun updateWidgets() {
        // Widgets müssen im Hintergrund aktualisiert werden, daher eine Coroutine.
        MainScope().launch {
            // Holt alle Instanzen deines CountdownWidgets
            GlanceAppWidgetManager(this@MainActivity).getGlanceIds(CountdownWidget::class.java)
                .forEach { glanceId ->
                    // Ruft die provideGlance-Methode für jedes Widget auf
                    CountdownWidget().update(this@MainActivity, glanceId)
                }
        }
    }
}