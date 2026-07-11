package io.github.memfrag.navigatorkit

import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * A navigation destination value — the Android counterpart of NavigatorKit's
 * Swift `Route` protocol.
 *
 * Feature modules define `@Serializable` route types implementing this
 * interface. Routes are pure values: they carry the data needed to build a
 * destination screen but know nothing about UI.
 *
 * ```kotlin
 * @Serializable
 * sealed interface ProductRoute : Route {
 *     @Serializable @SerialName("product.list")   data object List : ProductRoute
 *     @Serializable @SerialName("product.detail") data class Detail(val id: Int) : ProductRoute
 * }
 * ```
 *
 * Where Swift needed `AnyRoute` + a decoding registry, Kotlin's polymorphic
 * serialization does both jobs: `@SerialName` is the stable type id
 * (Swift's `routeTypeID`), and the [SerializersModule] built with
 * [routeSerializersModule] is the decoding registry.
 */
interface Route

/**
 * Builds the serializers module that acts as the route "registry" for state
 * restoration — the equivalent of Swift's `RouteTypeRegistry`.
 *
 * ```kotlin
 * val routesModule = routeSerializersModule {
 *     subclass(ProductRoute.List::class, ProductRoute.List.serializer())
 *     subclass(ProductRoute.Detail::class, ProductRoute.Detail.serializer())
 * }
 * ```
 */
fun routeSerializersModule(
    build: PolymorphicModuleBuilder<Route>.() -> Unit
): SerializersModule = SerializersModule {
    polymorphic(Route::class) { build() }
}

/**
 * Identity of a tab in a [TabsLayout]. String-backed so tab selection is
 * trivially serializable for state restoration.
 */
@JvmInline
@kotlinx.serialization.Serializable
value class TabId(val value: String)
