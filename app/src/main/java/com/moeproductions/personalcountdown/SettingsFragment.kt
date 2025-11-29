package com.moeproductions.personalcountdown

import android.os.Bundle
import android.content.Intent
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_countdown, rootKey)

        // Globaler Listener: immer wenn sich etwas ändert → Widget aktualisieren
        val globalListener = Preference.OnPreferenceChangeListener { _, _ ->
            WidgetUpdateReceiver.triggerNow(requireContext())
            WidgetUpdateReceiver.scheduleNextMinute(requireContext())
            // falls sich spezifische Tage über Modus-Änderung ändern
            updateSpecificDaysSummary()
            true
        }

        // Anzeigeformat, Berechnungsmodus, Arbeitstage-UI einrichten
        setupDisplayFormatPreference(globalListener)
        setupCalcModePreference(globalListener)
        setupWorkdaysPreference(globalListener)

        // Klick auf "Spezifische Tage auswählen" → Activity öffnen
        findPreference<Preference>("pref_specific_days")?.setOnPreferenceClickListener {
            val ctx = requireContext()
            val intent = Intent(ctx, SpecificDaysActivity::class.java)
            startActivity(intent)
            true
        }

        updateSpecificDaysSummary()
    }

    override fun onResume() {
        super.onResume()
        updateSpecificDaysSummary()

        // sicherheitshalber Berechnungsmodus anwenden (Visibility)
        val modePref = findPreference<ListPreference>("pref_count_mode")
        modePref?.value?.let { value ->
            updateModeDependentPreferences(value)
        }
    }

    // --- spezifische Tage Summary ---

    private fun updateSpecificDaysSummary() {
        val ctx = requireContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val set = prefs.getStringSet("pref_specific_days_set", emptySet()) ?: emptySet()
        val cnt = set.size
        val p = findPreference<Preference>("pref_specific_days")
        p?.summary = if (cnt == 0) "Keine Tage ausgewählt" else "$cnt Tag(e) ausgewählt"
    }

    // --- Anzeigeformat ---

    private fun setupDisplayFormatPreference(globalListener: Preference.OnPreferenceChangeListener) {
        val displayPref = findPreference<ListPreference>("pref_display_format") ?: return

        // Initial: aktuellen Eintrag als Summary setzen
        displayPref.value?.let { value ->
            val idx = displayPref.findIndexOfValue(value)
            if (idx >= 0) {
                displayPref.summary = displayPref.entries[idx]
            }
        }

        displayPref.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { pref, newValue ->
                val listPref = pref as ListPreference
                val value = newValue as String
                val idx = listPref.findIndexOfValue(value)
                if (idx >= 0) {
                    pref.summary = listPref.entries[idx]
                }
                globalListener.onPreferenceChange(pref, newValue)
                true
            }
    }

    // --- Berechnungsmodus ---

    private fun setupCalcModePreference(globalListener: Preference.OnPreferenceChangeListener) {
        val modePref = findPreference<ListPreference>("pref_count_mode") ?: return

        // Initial: Summary + Sichtbarkeit setzen
        modePref.value?.let { value ->
            applyCalcModeSummary(modePref, value)
            updateModeDependentPreferences(value)
        }

        modePref.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { pref, newValue ->
                val listPref = pref as ListPreference
                val value = newValue as String

                applyCalcModeSummary(listPref, value)
                updateModeDependentPreferences(value)

                globalListener.onPreferenceChange(pref, newValue)
                true
            }
    }

    private fun applyCalcModeSummary(pref: ListPreference, value: String) {
        val idx = pref.findIndexOfValue(value)
        if (idx >= 0) {
            pref.summary = pref.entries[idx]
        } else {
            pref.summary = null
        }
    }

    /**
     * Blendet "Arbeitstage pro Woche" und "Spezifische Tage auswählen"
     * je nach Berechnungsmodus ein/aus.
     *
     * ACHTUNG: modeValue muss zu den entryValues von pref_count_mode passen!
     * Beispiel (arrays.xml):
     *   <item>workdays</item>
     *   <item>specific_days</item>
     *   <item>calendar_days</item>
     */
    private fun updateModeDependentPreferences(modeValue: String) {
        // WICHTIG: Key wie im prefs_countdown.xml
        val workdaysPref = findPreference<SeekBarPreference>("pref_working_days_per_week")
        val specificDaysPref = findPreference<Preference>("pref_specific_days")

        when (modeValue) {
            "WORK" -> {          // Arbeitswochen/-tage
                workdaysPref?.isVisible = true
                specificDaysPref?.isVisible = false
            }
            "SPECIFIC" -> {      // Spezifische Tage
                workdaysPref?.isVisible = false
                specificDaysPref?.isVisible = true
            }
            "TOTAL" -> {         // Gesamttage
                workdaysPref?.isVisible = false
                specificDaysPref?.isVisible = false
            }
            else -> {
                workdaysPref?.isVisible = false
                specificDaysPref?.isVisible = false
            }
        }
    }

    // --- Arbeitstage pro Woche (Slider) ---

    private fun setupWorkdaysPreference(globalListener: Preference.OnPreferenceChangeListener) {
        val workPref = findPreference<SeekBarPreference>("pref_working_days_per_week") ?: return

        fun applyTitle(value: Int) {
            workPref.title = "Arbeitstage pro Woche: $value"
        }

        // Initialer Titel
        applyTitle(workPref.value)

        workPref.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { pref, newValue ->
                val v = newValue as Int
                applyTitle(v)
                globalListener.onPreferenceChange(pref, newValue)
                true
            }
    }
}
