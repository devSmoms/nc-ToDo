package de.devsmoms.nctodo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.devsmoms.nctodo.ical.Todo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A due date with optional time (null = all day). */
data class DueValue(val date: LocalDate, val time: LocalTime? = null)

val Todo.dueValue: DueValue? get() = due?.let { DueValue(it, dueTime) }

fun formatDue(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Heute"
        today.plusDays(1) -> "Morgen"
        today.minusDays(1) -> "Gestern"
        else -> DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, d. MMM" else "d. MMM yyyy", Locale.GERMAN).format(date)
    }
}

fun formatDue(date: LocalDate, time: LocalTime?): String =
    formatDue(date) + (time?.let { ", " + DateTimeFormatter.ofPattern("HH:mm").format(it) } ?: "")

fun formatDue(due: DueValue): String = formatDue(due.date, due.time)

/** Overdue: an earlier day, or today with a time that has passed. */
fun isOverdue(date: LocalDate?, time: LocalTime?, completed: Boolean = false): Boolean {
    if (date == null || completed) return false
    val now = LocalDateTime.now()
    return if (time == null) date.isBefore(now.toLocalDate()) else date.atTime(time).isBefore(now)
}

/**
 * Picks a due date; with "Ganztägig" switched off a time picker follows.
 * The switch starts according to the "all day by default" setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDialog(initial: DueValue?, allDayDefault: Boolean, onDismiss: () -> Unit, onConfirm: (DueValue) -> Unit) {
    var allDay by remember { mutableStateOf(initial?.let { it.time == null } ?: allDayDefault) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val pickedDay = pickedDate

    if (pickedDay == null) {
        val start = (initial?.date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = start)
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val date = pickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?: return@TextButton
                    if (allDay) onConfirm(DueValue(date)) else pickedDate = date
                }) { Text(if (allDay) "Übernehmen" else "Weiter") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        ) {
            Column {
                DatePicker(pickerState)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                ) {
                    Text("Ganztägig", modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
            }
        }
    } else {
        TimeDialog(initial?.time ?: LocalTime.of(9, 0), onDismiss = onDismiss) { onConfirm(DueValue(pickedDay, it)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uhrzeit") },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
