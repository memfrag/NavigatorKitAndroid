package io.github.memfrag.navigatorkit.restore

import io.github.memfrag.navigatorkit.Route
import io.github.memfrag.navigatorkit.TabId
import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.PresentationStyle
import io.github.memfrag.navigatorkit.state.PresentedContext
import io.github.memfrag.navigatorkit.state.RootLayout
import io.github.memfrag.navigatorkit.state.SceneNavigator
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * A serializable value mirror of a scene's navigation tree — the state
 * restoration format shared in spirit (and in field names) with the Swift
 * package. Alerts and dialogs are transient and never persisted.
 *
 * Routes are stored as raw JSON elements and decoded individually, which is
 * what makes restoration *lossy instead of brittle*: a route type that was
 * renamed or unregistered truncates the path before it and drops the
 * presentations stacked above, rather than failing the whole restore.
 *
 * Container shape is never restored from the snapshot — it always comes from
 * the app's blueprint; only selections, stacks, and presentation chains are
 * applied. Mismatched subtrees are skipped.
 */
@Serializable
data class NavigationSnapshot(
    val version: Int = CURRENT_VERSION,
    val root: RootSnapshot,
    val rootPresentedChain: List<PresentedLayerSnapshot> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
sealed interface RootSnapshot {
    @Serializable @SerialName("stack")
    data class Stack(val context: ContextSnapshot) : RootSnapshot

    @Serializable @SerialName("tabs")
    data class Tabs(val selection: TabId, val tabs: List<Entry>) : RootSnapshot {
        @Serializable
        data class Entry(val id: TabId, val content: RootSnapshot)
    }

    @Serializable @SerialName("split")
    data class Split(val sidebarSelection: JsonElement?, val detail: ContextSnapshot) : RootSnapshot
}

@Serializable
data class ContextSnapshot(
    val path: List<JsonElement> = emptyList(),
    val presentedChain: List<PresentedLayerSnapshot> = emptyList(),
)

/** One presented level: how it is shown plus its content stack (linear chain). */
@Serializable
data class PresentedLayerSnapshot(
    val style: PresentationStyle,
    val root: JsonElement?,
    val path: List<JsonElement> = emptyList(),
)

/** What lenient decoding had to drop while restoring. */
data class RestorationReport(val droppedRoutes: List<String>) {
    val isClean: Boolean get() = droppedRoutes.isEmpty()
}

object NavigationSnapshotCoder {

    private val routeSerializer = PolymorphicSerializer(Route::class)

    /** Captures the scene's current state (no registry needed to encode). */
    fun capture(scene: SceneNavigator, json: Json): NavigationSnapshot =
        NavigationSnapshot(
            root = capture(scene.root, json),
            rootPresentedChain = captureChain(scene.rootPresentation, json),
        )

    fun encode(scene: SceneNavigator, json: Json): String {
        // Force defaults on: the version field must always be present in
        // persisted data, or future format bumps can't be detected.
        val encoder = Json(from = json) { encodeDefaults = true }
        return encoder.encodeToString(NavigationSnapshot.serializer(), capture(scene, json))
    }

    /**
     * Decodes and applies a snapshot to a blueprint-instantiated scene.
     * Returns null (leaving the scene untouched) when the data is unreadable
     * or from another format version; otherwise a report of dropped routes.
     */
    fun decodeAndRestore(scene: SceneNavigator, data: String, json: Json): RestorationReport? {
        val snapshot = try {
            json.decodeFromString(NavigationSnapshot.serializer(), data)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (snapshot.version != NavigationSnapshot.CURRENT_VERSION) return null

        val restore = Restore(json)
        restore.apply(snapshot.root, scene.root)
        scene.rootPresentation = restore.buildChain(snapshot.rootPresentedChain)
        return RestorationReport(restore.dropped)
    }

    // ---- Capture ----

    private fun capture(layout: RootLayout, json: Json): RootSnapshot = when (layout) {
        is RootLayout.Stack -> RootSnapshot.Stack(capture(layout.context, json))
        is RootLayout.Tabs -> RootSnapshot.Tabs(
            selection = layout.tabs.selection,
            tabs = layout.tabs.tabs.map { RootSnapshot.Tabs.Entry(it.id, capture(it.content, json)) },
        )
        is RootLayout.Split -> RootSnapshot.Split(
            sidebarSelection = layout.split.sidebarSelection?.let {
                json.encodeToJsonElement(routeSerializer, it)
            },
            detail = capture(layout.split.detailContext, json),
        )
    }

    private fun capture(context: NavigationContext, json: Json): ContextSnapshot =
        ContextSnapshot(
            path = context.backStack.map { json.encodeToJsonElement(routeSerializer, it) },
            presentedChain = captureChain(context.presented, json),
        )

    private fun captureChain(first: PresentedContext?, json: Json): List<PresentedLayerSnapshot> =
        buildList {
            var current = first
            while (current != null) {
                add(
                    PresentedLayerSnapshot(
                        style = current.style,
                        root = current.content.root?.let {
                            json.encodeToJsonElement(routeSerializer, it)
                        },
                        path = current.content.backStack.map {
                            json.encodeToJsonElement(routeSerializer, it)
                        },
                    )
                )
                current = current.content.presented
            }
        }

    // ---- Restore ----

    private class Restore(private val json: Json) {
        val dropped = mutableListOf<String>()

        fun apply(snapshot: RootSnapshot, layout: RootLayout) {
            when {
                snapshot is RootSnapshot.Stack && layout is RootLayout.Stack ->
                    apply(snapshot.context, layout.context)

                snapshot is RootSnapshot.Tabs && layout is RootLayout.Tabs -> {
                    if (layout.tabs.tabs.any { it.id == snapshot.selection }) {
                        layout.tabs.selection = snapshot.selection
                    }
                    snapshot.tabs.forEach { entry ->
                        layout.tabs.layoutFor(entry.id)?.let { apply(entry.content, it) }
                    }
                }

                snapshot is RootSnapshot.Split && layout is RootLayout.Split -> {
                    layout.split.sidebarSelection = snapshot.sidebarSelection?.let(::decodeRoute)
                    apply(snapshot.detail, layout.split.detailContext)
                }

                // Blueprint shape changed since the snapshot: skip subtree.
                else -> {}
            }
        }

        fun apply(snapshot: ContextSnapshot, context: NavigationContext) {
            val (path, truncated) = decodePath(snapshot.path)
            context.backStack.clear()
            context.backStack.addAll(path)
            context.alert = null
            context.dialog = null
            // Presentations were stacked on a view that no longer restores.
            val chain = if (truncated) emptyList() else snapshot.presentedChain
            attach(buildChain(chain), context)
        }

        fun buildChain(layers: List<PresentedLayerSnapshot>): PresentedContext? {
            val first = layers.firstOrNull() ?: return null

            // A layer whose root is gone drops itself and everything above.
            val root = first.root?.let { decodeRoute(it) ?: return null }
            val (path, truncated) = decodePath(first.path)

            val content = NavigationContext(root = root, initialBackStack = path)
            if (!truncated) {
                attach(buildChain(layers.drop(1)), content)
            }
            return PresentedContext(first.style, content)
        }

        private fun attach(chain: PresentedContext?, context: NavigationContext) {
            if (chain?.style is PresentationStyle.FullScreen) {
                context.sheet = null
                context.fullScreen = chain
            } else {
                context.sheet = chain
                context.fullScreen = null
            }
        }

        /** Decodes until the first failure; truncates there. */
        private fun decodePath(elements: List<JsonElement>): Pair<List<Route>, Boolean> {
            val routes = mutableListOf<Route>()
            for (element in elements) {
                routes += decodeRoute(element) ?: return routes to true
            }
            return routes to false
        }

        private fun decodeRoute(element: JsonElement): Route? = try {
            json.decodeFromJsonElement(PolymorphicSerializer(Route::class), element)
        } catch (e: SerializationException) {
            dropped += element.toString()
            null
        } catch (e: IllegalArgumentException) {
            dropped += element.toString()
            null
        }
    }
}
