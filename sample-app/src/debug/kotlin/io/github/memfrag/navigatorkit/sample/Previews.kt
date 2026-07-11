package io.github.memfrag.navigatorkit.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.memfrag.navigatorkit.compose.LocalDestinationRegistry
import io.github.memfrag.navigatorkit.compose.LocalNavigator
import io.github.memfrag.navigatorkit.compose.Navigator
import io.github.memfrag.navigatorkit.compose.RoutedRoot
import io.github.memfrag.navigatorkit.intent.navigationIntent
import io.github.memfrag.navigatorkit.resolver.IntentResolver
import io.github.memfrag.navigatorkit.state.SceneNavigator
import androidx.compose.runtime.CompositionLocalProvider

/**
 * `@Preview` composables for Android Studio's preview pane. Each provides a
 * Navigator over a scene mutated to a chosen state, then draws the tree via
 * [RoutedRoot] (not the full RoutedScene) so previews don't require a
 * back-press dispatcher.
 */
@Composable
private fun PreviewScene(configure: SceneNavigator.() -> Unit = {}) {
    val navigator = remember {
        val scene = ShopComposition.newScene().apply(configure)
        Navigator(scene, ShopComposition.registry)
    }
    MaterialTheme {
        Surface {
            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalDestinationRegistry provides navigator.registry,
            ) {
                Box(Modifier.fillMaxSize()) {
                    RoutedRoot(navigator.scene.root)
                }
            }
        }
    }
}

@Preview(name = "Shop — list", widthDp = 380, heightDp = 800, showBackground = true)
@Composable
fun ShopListPreview() = PreviewScene()

@Preview(name = "Shop — detail", widthDp = 380, heightDp = 800, showBackground = true)
@Composable
fun ShopDetailPreview() = PreviewScene {
    IntentResolver.resolve(
        navigationIntent {
            selectTab(AppTab.Shop)
            setStack(ProductRoute.List, ProductRoute.Detail(42))
        },
        this,
    )
}

@Preview(name = "Playground", widthDp = 380, heightDp = 800, showBackground = true)
@Composable
fun PlaygroundPreview() = PreviewScene {
    IntentResolver.resolve(navigationIntent { selectTab(AppTab.Playground) }, this)
}

@Preview(name = "Settings split (wide)", widthDp = 900, heightDp = 720, showBackground = true)
@Composable
fun SettingsSplitPreview() = PreviewScene {
    IntentResolver.resolve(navigationIntent { selectTab(AppTab.Settings) }, this)
}
