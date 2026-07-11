package io.github.memfrag.navigatorkit.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.memfrag.navigatorkit.Route
import io.github.memfrag.navigatorkit.intent.RoutePlacement
import kotlin.reflect.KClass

/**
 * Maps route types to composable screens, letting feature modules register
 * their destinations without knowing about each other or the app shell — the
 * counterpart of the Swift package's `DestinationRegistry`.
 *
 * On Android the idiomatic wiring is DI multibindings (e.g. Hilt `@IntoSet`
 * of [RoutableFeature]); the builder below is the manual equivalent.
 *
 * ```kotlin
 * val registry = destinationRegistry {
 *     feature(ProductsFeature)
 *     feature(ReviewsFeature)
 * }
 * ```
 */
class DestinationRegistry internal constructor(
    private val screens: Map<KClass<out Route>, @Composable (Route) -> Unit>,
    private val placements: Map<KClass<out Route>, RoutePlacement>,
) {
    fun hasDestination(route: Route): Boolean = screens.containsKey(route::class)

    /** The default placement declared for the route's type, if any. */
    fun defaultPlacement(route: Route): RoutePlacement? = placements[route::class]

    /** Renders the screen for a route; unknown types render a diagnostic. */
    @Composable
    fun Screen(route: Route) {
        val content = screens[route::class]
        if (content != null) content(route) else Text("Unregistered route: ${route::class.simpleName}")
    }
}

/** Conformed to by feature modules to declare their route → screen mappings. */
interface RoutableFeature {
    fun destinations(builder: DestinationRegistryBuilder)
}

fun destinationRegistry(build: DestinationRegistryBuilder.() -> Unit): DestinationRegistry =
    DestinationRegistryBuilder().apply(build).build()

class DestinationRegistryBuilder internal constructor() {
    @PublishedApi
    internal val screens = mutableMapOf<KClass<out Route>, @Composable (Route) -> Unit>()

    @PublishedApi
    internal val placements = mutableMapOf<KClass<out Route>, RoutePlacement>()

    /** Registers the screen (and optional default placement) for one route type. */
    inline fun <reified T : Route> destination(
        placement: RoutePlacement? = null,
        noinline content: @Composable (T) -> Unit,
    ) {
        @Suppress("UNCHECKED_CAST")
        screens[T::class] = { route -> content(route as T) }
        if (placement != null) placements[T::class] = placement
    }

    /** Adds a feature's destinations. */
    fun feature(feature: RoutableFeature) = feature.destinations(this)

    internal fun build() = DestinationRegistry(screens.toMap(), placements.toMap())
}

/** The destination registry for the enclosing [RoutedScene]. */
val LocalDestinationRegistry = staticCompositionLocalOf<DestinationRegistry> {
    error("No DestinationRegistry in scope. Wrap content in RoutedScene { }.")
}
