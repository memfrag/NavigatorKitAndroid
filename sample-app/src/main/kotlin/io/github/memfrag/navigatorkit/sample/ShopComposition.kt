package io.github.memfrag.navigatorkit.sample

import io.github.memfrag.navigatorkit.TabId
import io.github.memfrag.navigatorkit.compose.DestinationRegistry
import io.github.memfrag.navigatorkit.compose.DestinationRegistryBuilder
import io.github.memfrag.navigatorkit.compose.RoutableFeature
import io.github.memfrag.navigatorkit.compose.destinationRegistry
import io.github.memfrag.navigatorkit.deeplink.DeepLinkMap
import io.github.memfrag.navigatorkit.deeplink.deepLinkMap
import io.github.memfrag.navigatorkit.intent.RoutePlacement
import io.github.memfrag.navigatorkit.intent.navigationIntent
import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.PresentationStyle
import io.github.memfrag.navigatorkit.state.RootLayout
import io.github.memfrag.navigatorkit.state.SceneNavigator
import io.github.memfrag.navigatorkit.state.SplitLayout
import io.github.memfrag.navigatorkit.state.TabDescriptor
import io.github.memfrag.navigatorkit.state.TabsLayout

// Each feature registers its own destinations, knowing nothing about the
// others or the app shell — exactly the decoupling the Swift package's
// RoutableFeature provides.

object ProductsFeature : RoutableFeature {
    override fun destinations(builder: DestinationRegistryBuilder) = with(builder) {
        destination<ProductRoute.List> { ProductListScreen() }
        destination<ProductRoute.Detail> { ProductDetailScreen(it.id) }
    }
}

object ReviewsFeature : RoutableFeature {
    override fun destinations(builder: DestinationRegistryBuilder) = with(builder) {
        // Reviews present as a bottom sheet by default — declared here, so
        // callers don't need to know.
        destination<ReviewRoute.Compose>(
            placement = RoutePlacement.bottomSheet(),
        ) { ComposeReviewScreen(it.productId) }
        destination<ReviewRoute.PhotoPicker> { PhotoPickerScreen() }
    }
}

object SettingsFeature : RoutableFeature {
    override fun destinations(builder: DestinationRegistryBuilder) = with(builder) {
        destination<SettingsRoute.Menu> { SettingsMenuScreen() }
        destination<SettingsRoute.General> { SettingsDetailScreen("General") }
        destination<SettingsRoute.Advanced> { SettingsDetailScreen("Advanced") }
    }
}

object PlaygroundFeature : RoutableFeature {
    override fun destinations(builder: DestinationRegistryBuilder) = with(builder) {
        destination<PlaygroundRoute.Home> { PlaygroundScreen() }
    }
}

object ShopComposition {

    val registry: DestinationRegistry = destinationRegistry {
        feature(ProductsFeature)
        feature(ReviewsFeature)
        feature(SettingsFeature)
        feature(PlaygroundFeature)
    }

    /** A fresh per-window navigation tree (blueprint equivalent). */
    fun newScene(): SceneNavigator = SceneNavigator(
        root = RootLayout.Tabs(
            TabsLayout(
                selection = TabId(AppTab.Shop.name.lowercase()),
                tabs = listOf(
                    TabDescriptor(
                        id = TabId(AppTab.Shop.name.lowercase()),
                        title = "Shop",
                        icon = "🛍",
                        content = RootLayout.Stack(NavigationContext(root = ProductRoute.List)),
                    ),
                    TabDescriptor(
                        id = TabId(AppTab.Settings.name.lowercase()),
                        title = "Settings",
                        icon = "⚙",
                        content = RootLayout.Split(
                            SplitLayout(
                                sidebarRoot = SettingsRoute.Menu,
                                sidebarSelection = SettingsRoute.General,
                                detailContext = NavigationContext(),
                            )
                        ),
                    ),
                    TabDescriptor(
                        id = TabId(AppTab.Playground.name.lowercase()),
                        title = "Playground",
                        icon = "✨",
                        content = RootLayout.Stack(NavigationContext(root = PlaygroundRoute.Home)),
                    ),
                ),
            )
        )
    )

    /**
     * Deep links. `shopapp://products/42` and the same path as an App Link
     * share one pattern; a malformed id falls through.
     */
    val deepLinks: DeepLinkMap = deepLinkMap {
        pattern("/products/:id/review") { params ->
            val id = params.int("id")
            navigationIntent {
                selectTab(AppTab.Shop)
                setStack(ProductRoute.List, ProductRoute.Detail(id))
                present(ReviewRoute.Compose(id), PresentationStyle.BottomSheet())
            }
        }
        pattern("/products/:id") { params ->
            navigationIntent {
                selectTab(AppTab.Shop)
                setStack(ProductRoute.List, ProductRoute.Detail(params.int("id")))
            }
        }
        pattern("/settings/**") {
            navigationIntent { selectTab(AppTab.Settings) }
        }
    }
}
