package io.github.memfrag.navigatorkit

import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.RootLayout
import io.github.memfrag.navigatorkit.state.SceneNavigator
import io.github.memfrag.navigatorkit.state.SplitLayout
import io.github.memfrag.navigatorkit.state.TabDescriptor
import io.github.memfrag.navigatorkit.state.TabsLayout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.subclass

// Mirrors the Swift test fixtures: same routes, same tabbed scene shape,
// so ported assertions stay comparable side by side.

@Serializable
sealed interface ProductRoute : Route {
    @Serializable @SerialName("product.list")
    data object List : ProductRoute

    @Serializable @SerialName("product.detail")
    data class Detail(val id: Int) : ProductRoute
}

@Serializable
sealed interface ReviewRoute : Route {
    @Serializable @SerialName("review.compose")
    data class Compose(val productId: Int) : ReviewRoute
}

@Serializable
sealed interface SettingsRoute : Route {
    @Serializable @SerialName("settings.menu")
    data object Menu : SettingsRoute

    @Serializable @SerialName("settings.general")
    data object General : SettingsRoute

    @Serializable @SerialName("settings.advanced")
    data object Advanced : SettingsRoute
}

enum class AppTab { Shop, Settings, Search }

val Shop = TabId("shop")
val Settings = TabId("settings")
val Search = TabId("search")

/** All fixture routes registered — the "full" registry. */
val fullJson: Json = Json {
    serializersModule = routeSerializersModule {
        subclass(ProductRoute.List::class)
        subclass(ProductRoute.Detail::class)
        subclass(ReviewRoute.Compose::class)
        subclass(SettingsRoute.Menu::class)
        subclass(SettingsRoute.General::class)
        subclass(SettingsRoute.Advanced::class)
    }
}

/** ProductRoute unregistered — simulates a renamed/removed feature. */
val partialJson: Json = Json {
    serializersModule = routeSerializersModule {
        subclass(ReviewRoute.Compose::class)
        subclass(SettingsRoute.Menu::class)
        subclass(SettingsRoute.General::class)
        subclass(SettingsRoute.Advanced::class)
    }
}

fun makeTabbedScene(): SceneNavigator {
    val shop = NavigationContext(root = ProductRoute.List)
    val settingsSplit = SplitLayout(
        sidebarRoot = SettingsRoute.Menu,
        detailContext = NavigationContext(),
    )
    val search = NavigationContext()
    return SceneNavigator(
        root = RootLayout.Tabs(
            TabsLayout(
                selection = Shop,
                tabs = listOf(
                    TabDescriptor(Shop, "Shop", content = RootLayout.Stack(shop)),
                    TabDescriptor(Settings, "Settings", content = RootLayout.Split(settingsSplit)),
                    TabDescriptor(Search, "Search", content = RootLayout.Stack(search)),
                ),
            )
        )
    )
}

fun SceneNavigator.tabs(): TabsLayout = (root as RootLayout.Tabs).tabs

fun SceneNavigator.settingsSplit(): SplitLayout =
    ((root as RootLayout.Tabs).tabs.layoutFor(Settings) as RootLayout.Split).split
