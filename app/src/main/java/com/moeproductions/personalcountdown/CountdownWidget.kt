package com.moeproductions.personalcountdown

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.ui.unit.sp
import androidx.glance.text.FontStyle
import androidx.compose.ui.unit.DpSize
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.text.TextAlign
import java.util.Calendar

private data class CountdownParts(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val isExpired: Boolean
)

class CountdownWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 40.dp),  // 2x1
            DpSize(250.dp, 60.dp),  // 4x1
            DpSize(250.dp, 110.dp)  // 4x2 (oder größer)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }
}

@Composable
private fun WidgetContent() {
    val ctx = LocalContext.current

    // 1) Prefs zuerst lesen
    val prefs = ctx.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
    val appPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)

    val target = prefs.getLong("target_date", -1L)
    val title = prefs.getString("event_title", "")?.trim().orEmpty()

    val displayFormat = appPrefs.getString("pref_display_format", "DAYS_HH_MM") ?: "DAYS_HH_MM"
    val countMode = appPrefs.getString("pref_count_mode", "TOTAL") ?: "TOTAL"

    // SeekBarPreference speichert i. d. R. als INT – robust lesen:
    val workingDaysPerWeek: Int = try {
        appPrefs.getInt("pref_working_days_per_week", 5)
    } catch (e: ClassCastException) {
        // Fallback falls früher mal als String gespeichert
        val s = appPrefs.getString("pref_working_days_per_week", "5") ?: "5"
        s.toIntOrNull() ?: 5
    }.coerceIn(1, 7)
    val now = System.currentTimeMillis()

    val specificSet: Set<String> =
        appPrefs.getStringSet("pref_specific_days_set", emptySet())
            ?.toSet()               // Kopie, damit wir die Prefs nicht versehentlich verändern
            ?: emptySet()

// Helper: heute 00:00 und Ziel 23:59:59.999 bestimmen
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val targetEnd = Calendar.getInstance().apply {
        timeInMillis = if (target > 0) target else todayStart
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    fun parseIsoDay(s: String): Long? = try {
        val cal = Calendar.getInstance()
        val parts = s.split("-")
        if (parts.size == 3) {
            cal.set(Calendar.YEAR, parts[0].toInt())
            cal.set(Calendar.MONTH, parts[1].toInt() - 1)
            cal.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } else null
    } catch (_: Exception) { null }

    // ISO "yyyy-MM-dd" -> Tagesbeginn in ms (du hast parseIsoDay bereits definiert)
    val specificDaysCount: Int = if (target > 0) {
        specificSet.mapNotNull { parseIsoDay(it) }
            .count { it in todayStart..targetEnd }
    } else 0

    // Nächster ausgewählter Tag (>= heute)
    val nextSpecificMillis: Long? = if (target > 0 && specificSet.isNotEmpty()) {
        val todayStartCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        specificSet.mapNotNull { parseIsoDay(it) }
            .filter { it in todayStart..targetEnd }.minOrNull()
    } else null

    // 2) Grundteile berechnen (einmal)
    val parts: CountdownParts = if (target > 0) {
        val diff = target - now
        if (diff <= 0L) CountdownParts(0L, 0L, 0L, true)
        else {
            val d = TimeUnit.MILLISECONDS.toDays(diff)
            val afterDays = diff - TimeUnit.DAYS.toMillis(d)
            val h = TimeUnit.MILLISECONDS.toHours(afterDays)
            val afterHours = afterDays - TimeUnit.HOURS.toMillis(h)
            val m = TimeUnit.MILLISECONDS.toMinutes(afterHours)
            CountdownParts(d, h, m, false)
        }
    } else CountdownParts(0L, 0L, 0L, false)
    val (days, hours, minutes, isExpired) = parts

    // 3) Für Format/Work-Modus einmalig aufbereiten
    val diff = (target - now).coerceAtLeast(0L)
    val totalDays = TimeUnit.MILLISECONDS.toDays(diff)
    val hh = TimeUnit.MILLISECONDS.toHours(diff - TimeUnit.DAYS.toMillis(totalDays))
    val mm = TimeUnit.MILLISECONDS.toMinutes(
        diff - TimeUnit.DAYS.toMillis(totalDays) - TimeUnit.HOURS.toMillis(hh)
    )

    val workdays =
        if (countMode == "WORK") approximateWorkdays(diff, workingDaysPerWeek) else 0L
    val work = if (countMode == "WORK") splitWorkWeeksDays(
        workdays,
        workingDaysPerWeek
    ) else WorkSplit(0, 0)

    fun formatByPreference(d: Long, h: Long, m: Long): String = when (displayFormat) {
        "DAYS_HH_MM" -> {
            val dl = if (d == 1L) "Tag" else "Tage"
            if (d > 0) "$d $dl  ${h}h ${String.format("%02d", m)}"
            else "${h}h ${String.format("%02d", m)}"
        }

        "DAYS_HHh_MMmin" -> {
            val dl = if (d == 1L) "Tag" else "Tage"
            if (d > 0) "$d $dl  ${h}h ${String.format("%02d", m)}min"
            else "${h}h ${String.format("%02d", m)}min"
        }

        else -> { // ONLY_DAYS
            val dl = if (d == 1L) "Tag" else "Tage"
            "$d $dl"
        }
    }

    fun formatWorkByPreference(w: WorkSplit, h: Long, m: Long): String = when (displayFormat) {
        "ONLY_DAYS" -> {
            val label = if (workdays == 1L) "Arbeitstag" else "Arbeitstage"
            "$workdays $label"
        }

        else -> {
            val base = buildString {
                if (w.weeks > 0) append("${w.weeks} AW ")
                append("${w.days} AT")
            }
            if (displayFormat == "DAYS_HHh_MMmin")
                "$base  ${h}h ${String.format("%02d", m)}min"
            else
                "$base  ${h}h ${String.format("%02d", m)}"
        }
    }

    // Responsive-Modus
    val size = LocalSize.current
    val mode = when {
        size.width < 130.dp -> 2
        size.height <= 70.dp -> 1
        else -> 0
    }

    // Farben & Padding (wie bei dir)
    val (bgColor, textColor) = when {
        isExpired -> GlanceTheme.colors.surface to GlanceTheme.colors.onSurface
        days >= 7 -> GlanceTheme.colors.primaryContainer to GlanceTheme.colors.onPrimaryContainer
        days in 1..6 -> GlanceTheme.colors.tertiaryContainer to GlanceTheme.colors.onTertiaryContainer
        else -> GlanceTheme.colors.errorContainer to GlanceTheme.colors.onErrorContainer
    }
    val pad = when (mode) {
        2 -> 2.dp; 1 -> 4.dp; else -> 6.dp
    }


    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .padding(pad)
            .clickable(actionStartActivity(MainActivity::class.java)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Titel (optional)
        if (title.isNotBlank() && mode == 0) {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )
            Spacer(GlanceModifier.size(6.dp))
        }

        if (target <= 0L) {
            Text(
                text = "Kein Ziel gesetzt",
                style = TextStyle(color = textColor)
            )
            return@Column
        }

        if (isExpired) {
            Text(
                text = "✨ Datum überschritten",
                style = TextStyle(color = textColor, fontWeight = FontWeight.Bold)
            )
            return@Column
        }

        // Hauptzeile
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val mainLine = when (countMode) {
                "WORK" -> formatWorkByPreference(work, hh, mm)
                "SPECIFIC" -> {
                    when (displayFormat) {
                        "ONLY_DAYS" -> "$specificDaysCount spezifische Tag(e)"
                        "DAYS_HHh_MMmin" -> "$specificDaysCount Tag(e)  ${hh}h ${
                            String.format(
                                "%02d",
                                mm
                            )
                        }min"

                        else -> "$specificDaysCount Tag(e)  ${hh}h ${String.format("%02d", mm)}"
                    }
                }
                else -> formatByPreference(totalDays, hh, mm)
            }
            Text(
                text = mainLine,
                style = TextStyle(
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = when (mode) {
                        2 -> 14.sp; 1 -> 16.sp; else -> 20.sp
                    },
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
        }

        // 2×1: „Noch …“ + „bis …“
        if (mode == 2) {
            val lead = when (countMode) {
                "WORK" -> {
                    val base = buildString {
                        if (work.weeks > 0) append("${work.weeks}AW ")
                        append("${work.days}AT")
                    }
                    "Noch $base  ${hh}h ${String.format("%02d", mm)}"
                }
                "SPECIFIC" -> {
                    val label = if (specificDaysCount == 1) "Tag" else "Tage"
                    "Noch $specificDaysCount $label  ${hh}h ${String.format("%02d", mm)}"
                }
                else -> {
                    buildString {
                        append("Noch ")
                        if (totalDays > 0) append("${totalDays}T ")
                        append("${hh}h ${String.format("%02d", mm)}min")
                    }
                }
            }
            Text(
                text = lead,
                style = TextStyle(
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
            val subtitle = "bis " + if (title.isNotBlank()) title else formatShortDate(target)
            Text(
                text = subtitle,
                style = TextStyle(
                    color = textColor,
                    fontStyle = FontStyle.Italic,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }

        // ≥4×2: Fußzeile
        if (mode == 0) {
            if (countMode == "SPECIFIC" && nextSpecificMillis != null) {
                Text(
                    text = "nächster ausgewählter Tag: ${formatShortDate(nextSpecificMillis)}",
                    style = TextStyle(
                        color = textColor,
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.size(2.dp))
            }
            Spacer(GlanceModifier.size(4.dp))
            Text(
                text = "bis ${formatShort(target)}",
                style = TextStyle(
                    color = textColor,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic
                ),
                maxLines = 1
            )
        }
    }

}


// Datumsformat unten rechts
private fun formatShort(targetMillis: Long): String {
    val sdf = SimpleDateFormat.getDateTimeInstance(
        SimpleDateFormat.MEDIUM,
        SimpleDateFormat.SHORT,
        Locale.getDefault()
    )
    return sdf.format(targetMillis)
}

private fun formatShortDate(targetMillis: Long): String {
    val sdf = SimpleDateFormat.getDateInstance(
        SimpleDateFormat.MEDIUM,
        Locale.getDefault()
    )
    return sdf.format(targetMillis)
}

private const val DAY_MS = 24L * 60L * 60L * 1000L

// Approx. ArbeitsTAGE anhand "N Arbeitstage/Woche" (ohne Wochentagsauswahl)
private fun approximateWorkdays(totalMillis: Long, workingDaysPerWeek: Int): Long {
    if (totalMillis <= 0 || workingDaysPerWeek <= 0) return 0
    val totalDays = totalMillis / DAY_MS.toDouble()
    val fraction = (workingDaysPerWeek.coerceIn(1, 7)) / 7.0
    return kotlin.math.round(totalDays * fraction).toLong()
}

private data class WorkSplit(val weeks: Long, val days: Long)

private fun splitWorkWeeksDays(workdays: Long, workingDaysPerWeek: Int): WorkSplit {
    val perWeek = workingDaysPerWeek.coerceIn(1, 7)
    val weeks = workdays / perWeek
    val days = workdays % perWeek
    return WorkSplit(weeks, days)
}

