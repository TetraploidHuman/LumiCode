package com.lumicode.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lumicode.editor.ui.theme.RlMotion

private class PresenceEntry<T>(
    var item: T,
    val visible: MutableTransitionState<Boolean>,
)

/** 跟踪列表条目进出：移除时先播退场，再摘除。 */
class PresenceTracker<T> {
    private val held: SnapshotStateMap<String, PresenceEntry<T>> = mutableStateMapOf()
    private val order: SnapshotStateList<String> = mutableStateListOf()

    val hasPresence: Boolean
        get() = order.isNotEmpty()

    fun sync(items: List<T>, keyOf: (T) -> String) {
        val live = items.map { keyOf(it) to it }
        val liveKeys = live.map { it.first }.toSet()
        live.forEach { (k, item) ->
            val cur = held[k]
            if (cur == null) {
                held[k] = PresenceEntry(
                    item,
                    MutableTransitionState(false).apply { targetState = true },
                )
                if (k !in order) order.add(k)
            } else {
                cur.item = item
                if (!cur.visible.targetState) cur.visible.targetState = true
            }
        }
        held.forEach { (k, e) ->
            if (k !in liveKeys && e.visible.targetState) {
                e.visible.targetState = false
            }
        }
        val next = live.map { it.first } + order.filter { it !in liveKeys }
        if (order.toList() != next) {
            order.clear()
            order.addAll(next.distinct())
        }
    }

    fun pruneIdle() {
        val gone = held.entries
            .filter { (_, e) -> e.visible.isIdle && !e.visible.currentState && !e.visible.targetState }
            .map { it.key }
        gone.forEach { k ->
            held.remove(k)
            order.remove(k)
        }
    }

    fun displayKeys(): List<String> = order.toList()

    fun entry(key: String): Pair<T, MutableTransitionState<Boolean>>? {
        val e = held[key] ?: return null
        return e.item to e.visible
    }
}

@Composable
fun <T> rememberPresenceTracker(): PresenceTracker<T> = remember { PresenceTracker() }

@Composable
fun <T> rememberSyncedPresence(items: List<T>, keyOf: (T) -> String): PresenceTracker<T> {
    val tracker = rememberPresenceTracker<T>()
    SideEffect { tracker.sync(items, keyOf) }
    return tracker
}

val CollabEnter: EnterTransition
    get() = fadeIn(RlMotion.enter(200)) + expandVertically(
        animationSpec = RlMotion.enter(200),
        expandFrom = Alignment.Top,
    )

val CollabExit: ExitTransition
    get() = fadeOut(RlMotion.exit()) + shrinkVertically(
        animationSpec = RlMotion.exit(),
        shrinkTowards = Alignment.Top,
    )

@Composable
fun <T> PresenceItems(
    tracker: PresenceTracker<T>,
    enter: EnterTransition = CollabEnter,
    exit: ExitTransition = CollabExit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    tracker.displayKeys().forEach { k ->
        val pair = tracker.entry(k) ?: return@forEach
        val (item, visible) = pair
        key(k) {
            AnimatedVisibility(
                visibleState = visible,
                enter = enter,
                exit = exit,
            ) {
                Column { content(item) }
            }
            LaunchedEffect(visible.isIdle, visible.currentState, visible.targetState) {
                if (visible.isIdle && !visible.currentState && !visible.targetState) {
                    tracker.pruneIdle()
                }
            }
        }
    }
}

@Composable
fun <T> AnimatedPresenceColumn(
    items: List<T>,
    keyOf: (T) -> String,
    modifier: Modifier = Modifier,
    enter: EnterTransition = CollabEnter,
    exit: ExitTransition = CollabExit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    val tracker = rememberSyncedPresence(items, keyOf)
    Column(modifier) {
        PresenceItems(tracker, enter, exit, content)
    }
}

/** 区块整体出现/消失（如「待你拍板」整块）。 */
@Composable
fun AnimatedSection(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = CollabEnter,
    exit: ExitTransition = CollabExit,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
    ) {
        content()
    }
}
