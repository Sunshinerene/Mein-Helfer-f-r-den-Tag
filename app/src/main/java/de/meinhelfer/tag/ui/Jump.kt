package de.meinhelfer.tag.ui

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** Ein Bereich der Seite, zu dem man ueber die Leiste unten springen kann. */
class JumpTarget(val label: String, val jump: suspend () -> Unit)

/** Bereiche der gerade sichtbaren Seite (die zuletzt angemeldete Seite gewinnt). */
class JumpRegistry {
    private val providers = mutableStateListOf<Pair<Any, () -> List<JumpTarget>>>()

    fun current(): List<JumpTarget> = providers.lastOrNull()?.second?.invoke().orEmpty()

    internal fun add(owner: Any, provider: () -> List<JumpTarget>) {
        providers.add(owner to provider)
    }

    internal fun remove(owner: Any) {
        providers.removeAll { it.first === owner }
    }
}

val LocalJumps = staticCompositionLocalOf { JumpRegistry() }

/** Seite meldet ihre Bereiche an, solange sie angezeigt wird. */
@Composable
fun RegisterJumps(provider: () -> List<JumpTarget>) {
    val registry = LocalJumps.current
    val latest by rememberUpdatedState(provider)
    DisposableEffect(registry) {
        val owner = Any()
        registry.add(owner) { latest() }
        onDispose { registry.remove(owner) }
    }
}

/**
 * Merkt sich beim Aufbau einer LazyColumn, an welcher Stelle welcher Bereich steht. Alle Eintraege der Liste
 * muessen ueber [jumpItem] bzw. [jumpItems] laufen, damit die Stellen stimmen. Am Anfang [reset] aufrufen.
 */
class JumpList {
    internal val marks = ArrayList<Pair<String, Int>>()
    internal var count = 0

    fun reset() {
        marks.clear()
        count = 0
    }

    fun targets(state: LazyListState): List<JumpTarget> =
        marks.toList().map { (label, index) -> JumpTarget(label) { state.animateScrollToItem(index) } }
}

fun LazyListScope.jumpItem(j: JumpList, label: String?, key: Any? = null, content: @Composable LazyItemScope.() -> Unit) {
    if (label != null) j.marks += label to j.count
    j.count++
    item(key = key) { content() }
}

fun <T> LazyListScope.jumpItems(
    j: JumpList,
    list: List<T>,
    label: (T) -> String?,
    key: ((T) -> Any)? = null,
    content: @Composable LazyItemScope.(T) -> Unit,
) {
    list.forEach { t ->
        label(t)?.let { j.marks += it to j.count }
        j.count++
    }
    items(list, key = key) { content(it) }
}

/** LazyColumn-Seite anmelden: liefert die JumpList zum Befuellen. */
@Composable
fun rememberJumpList(state: LazyListState): JumpList {
    val j = remember { JumpList() }
    RegisterJumps { j.targets(state) }
    return j
}
