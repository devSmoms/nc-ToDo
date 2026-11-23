package de.devsmoms.nctodo.ui

import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.sync.LocalData
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

/** Lists Nextcloud Deck creates for its boards are named "Deck: <board>". */
fun TaskList.isDeckList(): Boolean = ListIcons.label(name).trimStart().startsWith("Deck:", ignoreCase = true)

/**
 * The data as shown to the user: with [hideDeck], Deck board lists and their tasks are
 * left out everywhere (menu, smart lists, widget, quick add, reminders). Sync is unaffected.
 */
fun LocalData.forDisplay(hideDeck: Boolean): LocalData {
    if (!hideDeck) return this
    val hidden = lists.filter { it.isDeckList() }.map { it.url }.toSet()
    if (hidden.isEmpty()) return this
    return copy(lists = lists.filterNot { it.url in hidden }, tasks = tasks.filterKeys { it !in hidden })
}
