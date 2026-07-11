package io.github.memfrag.navigatorkit.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.memfrag.navigatorkit.state.SceneNavigator

/**
 * The entry composable for a scene: provides the [Navigator] and
 * [DestinationRegistry] to the tree, renders the root layout plus the
 * scene-level presentation, and wires system back to the navigation tree.
 *
 * ```kotlin
 * setContent {
 *     val navigator = remember { Navigator(blueprintScene(), registry) }
 *     MaterialTheme { RoutedScene(navigator) }
 * }
 * ```
 */
@Composable
fun RoutedScene(navigator: Navigator, modifier: Modifier = Modifier) {
    // Predictive/system back resolves against the tree; snapshot reads keep
    // `canGoBack` current across recompositions.
    BackHandler(enabled = navigator.canGoBack()) { navigator.goBack() }

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalDestinationRegistry provides navigator.registry,
    ) {
        Box(modifier.fillMaxSize()) {
            RoutedRoot(navigator.scene.root)
            RootPresentationHost(navigator.scene)
        }
    }
}

/** Binds `SceneNavigator.rootPresentation` — a presentation over the whole root. */
@Composable
private fun RootPresentationHost(scene: SceneNavigator) {
    val presentation = scene.rootPresentation ?: return
    // A root presentation always covers the entire window; render as a
    // full-window dialog regardless of style, then recurse into its stack.
    Dialog(
        onDismissRequest = { scene.rootPresentation = null },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
            RoutedStack(presentation.content)
        }
    }
}
