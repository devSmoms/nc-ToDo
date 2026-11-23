package de.devsmoms.nctodo.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Small option chips for a new task: "Mein Tag", due date (optionally with time) and "Wichtig".
 * Used by the add bar of the app and by the quick add window.
 *
 * With [allDayDefault] off, the date presets ask for a time as well.
 * [onPopupChange] reports whether a menu or dialog is open, so callers can keep the chips
 * visible while the text field has lost focus to the popup.
 */
@Composable
fun TaskQuickOptions(
    due: DueValue?,
    starred: Boolean,
    onDue: (DueValue?) -> Unit,
    onStarred: (Boolean) -> Unit,
    allDayDefault: Boolean,
    modifier: Modifier = Modifier,
    onPopupChange: (Boolean) -> Unit = {},
) {
    val today = LocalDate.now()
    var dueMenu by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var timeFor by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(dueMenu, pickDate, timeFor) { onPopupChange(dueMenu || pickDate || timeFor != null) }

    fun choose(date: LocalDate) {
        if (allDayDefault) onDue(DueValue(date)) else timeFor = date
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        val inMyDay = due != null && due.date == today && due.time == null
        FilterChip(
            selected = inMyDay,
            onClick = { onDue(if (inMyDay) null else DueValue(today)) },
            label = { Text("Mein Tag") },
            leadingIcon = { Icon(Icons.Outlined.WbSunny, null, Modifier.size(18.dp)) },
        )
        Box {
            val other = due?.takeIf { !inMyDay }
            FilterChip(
                selected = other != null,
                onClick = { dueMenu = true },
                label = { Text(other?.let { formatDue(it) } ?: "Fällig") },
                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = dueMenu, onDismissRequest = { dueMenu = false }) {
                val nextWeek = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                listOf(
                    "Heute" to today,
                    "Morgen" to today.plusDays(1),
                    "Nächste Woche (${formatDue(nextWeek)})" to nextWeek,
                ).forEach { (label, date) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { dueMenu = false; choose(date) })
                }
                DropdownMenuItem(text = { Text("Datum auswählen …") }, onClick = { dueMenu = false; pickDate = true })
                if (due != null) {
                    DropdownMenuItem(text = { Text("Kein Datum") }, onClick = { onDue(null); dueMenu = false })
                }
            }
        }
        FilterChip(
            selected = starred,
            onClick = { onStarred(!starred) },
            label = { Text("Wichtig") },
            leadingIcon = { Icon(if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder, null, Modifier.size(18.dp)) },
        )
    }

    if (pickDate) {
        DueDialog(due, allDayDefault, onDismiss = { pickDate = false }) { onDue(it); pickDate = false }
    }
    timeFor?.let { date ->
        TimeDialog(due?.time ?: java.time.LocalTime.of(9, 0), onDismiss = { timeFor = null }) {
            onDue(DueValue(date, it))
            timeFor = null
        }
    }
}
