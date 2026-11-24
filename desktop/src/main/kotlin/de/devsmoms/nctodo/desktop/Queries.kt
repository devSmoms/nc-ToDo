package de.devsmoms.nctodo.desktop

import de.devsmoms.nctodo.data.ListIcon
import de.devsmoms.nctodo.data.TaskItem
import de.devsmoms.nctodo.data.TaskList
import de.devsmoms.nctodo.sync.LocalData
import java.time.LocalDate

/** Smart views, same as in the Android app. */
enum class SmartList(val title: String) {
    MY_DAY("Mein Tag"),
    IMPORTANT("Wichtig"),
    PLANNED("Geplant"),
    ALL("Aufgaben"),
}

sealed interface Selection {
    data class Smart(val kind: SmartList) : Selection
    data class OfList(val url: String) : Selection
}

/** List name without the leading emoji icon. */
fun TaskList.label(): String = ListIcon.split(name).second.ifBlank { name }

fun TaskList.isDeckList(): Boolean = label().trimStart().startsWith("Deck:", ignoreCase = true)

/** Deck board lists ("Deck: …") are hidden, like the Android default. */
fun LocalData.forDisplay(): LocalData {
    val hidden = lists.filter { it.isDeckList() }.map { it.url }.toSet()
    if (hidden.isEmpty()) return this
    return copy(lists = lists.filterNot { it.url in hidden }, tasks = tasks.filterKeys { it !in hidden })
}

/** Top-level tasks of a selection (steps appear inside their task), sorted like on Android. */
fun tasksFor(selection: Selection, data: LocalData, hidden: Set<String>, today: LocalDate = LocalDate.now()): List<TaskItem> {
    val tasks = data.tasks
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
            SmartList.MY_DAY -> topLevel.filter { t -> t.todo.due?.let { !it.isAfter(today) } == true && !t.todo.completed }
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

/** Full text search over title and notes of top-level tasks. */
fun searchTasks(query: String, data: LocalData, hidden: Set<String>): List<TaskItem> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return tasksFor(Selection.Smart(SmartList.ALL), data, hidden)
        .filter { it.todo.summary.contains(q, ignoreCase = true) || it.todo.description.contains(q, ignoreCase = true) }
}

/** Steps (sub tasks) of a task. */
fun stepsOf(item: TaskItem, data: LocalData): List<TaskItem> =
    data.tasks[item.listUrl].orEmpty().filter { it.todo.parentUid == item.todo.uid }.sortedBy { it.todo.created }

/** The list new tasks go to in smart views: "Aufgaben"/"Tasks", else the first own writable list. */
fun List<TaskList>.defaultTarget(): TaskList? {
    val writable = filter { it.canWrite }
    return writable.firstOrNull { it.label().let { n -> n.equals("Aufgaben", true) || n.equals("Tasks", true) } }
        ?: writable.firstOrNull { it.isOwner } ?: writable.firstOrNull()
}

/** Menu order and groups: ungrouped lists first, then groups where their first list is. */
fun groupedLists(lists: List<TaskList>): Pair<List<TaskList>, List<Pair<String, List<TaskList>>>> {
    val sorted = lists.sortedWith(compareBy<TaskList>({ it.order ?: Int.MAX_VALUE }, { it.label().lowercase() }))
    return sorted.filter { it.group == null } to sorted.filter { it.group != null }.groupBy { it.group!! }.toList()
}
