package de.devsmoms.nctodo.ui

import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import java.time.LocalDate

/**
 * Tasks shown for a selection (list or smart view), top-level only – steps appear inside
 * their task. Shared by the app, the home screen widget and the quick add window.
 */
fun tasksForSelection(
    selection: Selection,
    tasks: Map<String, List<TaskItem>>,
    hidden: Set<String> = emptySet(),
    today: LocalDate = LocalDate.now(),
): List<TaskItem> {
    val all = when (selection) {
        is Selection.OfList -> tasks[selection.url].orEmpty()
        is Selection.Smart -> tasks.values.flatten()
    }
    val topLevel = all.filter { it.url !in hidden }.filter { t ->
        val parent = t.todo.parentUid
        parent == null || tasks[t.listUrl].orEmpty().none { it.todo.uid == parent }
    }
    return when (selection) {
        is Selection.OfList -> topLevel
        is Selection.Smart -> when (selection.kind) {
            SmartList.MY_DAY -> topLevel.filter { it.todo.due != null && !it.todo.due.isAfter(today) && !it.todo.completed }
            SmartList.IMPORTANT -> topLevel.filter { it.todo.starred && !it.todo.completed }
            SmartList.PLANNED -> topLevel.filter { it.todo.due != null && !it.todo.completed }
            SmartList.ALL -> topLevel
        }
    }.sortedWith(
        compareByDescending<TaskItem> { it.todo.starred }
            .thenBy { it.todo.due ?: LocalDate.MAX }
            .thenByDescending { it.todo.created },
    )
}

/** The list new tasks go to when no list is chosen: "Aufgaben"/"Tasks", else the first own writable list. */
fun List<TaskList>.defaultTarget(): TaskList? {
    val writable = filter { it.canWrite }
    return writable.firstOrNull { ListIcons.label(it.name).let { n -> n.equals("Aufgaben", true) || n.equals("Tasks", true) } }
        ?: writable.firstOrNull { it.isOwner } ?: writable.firstOrNull()
}

/** Display title of a selection. */
fun selectionTitle(selection: Selection, lists: List<TaskList>): String = when (selection) {
    is Selection.Smart -> selection.kind.title
    is Selection.OfList -> lists.firstOrNull { it.url == selection.url }?.let { ListIcons.label(it.name) } ?: "Liste"
}
