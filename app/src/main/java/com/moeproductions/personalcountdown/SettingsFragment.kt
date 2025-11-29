package com.moeproductions.personalcountdown

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_countdown, rootKey)

        val listener = Preference.OnPreferenceChangeListener { _, _ ->
            // Widget sofort refreshen
            WidgetUpdateReceiver.triggerNow(requireContext())
            WidgetUpdateReceiver.scheduleNextMinute(requireContext())
            updateSpecificDaysSummary()
            true
        }

        findPreference<Preference>("pref_display_format")?.onPreferenceChangeListener = listener
        findPreference<Preference>("pref_count_mode")?.onPreferenceChangeListener = listener
        findPreference<SeekBarPreference>("pref_working_days_per_week")?.onPreferenceChangeListener =
            listener

        findPreference<Preference>("pref_specific_days")?.setOnPreferenceClickListener {
            startActivity(android.content.Intent(requireContext(), SpecificDaysActivity::class.java))
            true
        }

        updateSpecificDaysSummary()
    }

    private fun updateSpecificDaysSummary() {
        val ctx = requireContext()
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        val set = prefs.getStringSet("pref_specific_days_set", emptySet()) ?: emptySet()
        val cnt = set.size
        val p = findPreference<Preference>("pref_specific_days")
        p?.summary = if (cnt == 0) "Keine Tage ausgewählt" else "$cnt Tag(e) ausgewählt"
    }
}
