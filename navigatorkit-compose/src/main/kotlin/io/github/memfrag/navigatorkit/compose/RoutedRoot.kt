package io.github.memfrag.navigatorkit.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.memfrag.navigatorkit.state.RootLayout
import io.github.memfrag.navigatorkit.state.SplitLayout
import io.github.memfrag.navigatorkit.state.TabsLayout

/** Renders a [RootLayout]: stack, tabs, or list-detail split. */
@Composable
fun RoutedRoot(layout: RootLayout, modifier: Modifier = Modifier) {
    when (layout) {
        is RootLayout.Stack -> RoutedStack(layout.context, modifier)
        is RootLayout.Tabs -> RoutedTabs(layout.tabs, modifier)
        is RootLayout.Split -> RoutedSplit(layout.split, modifier)
    }
}

/** A bottom navigation bar bound to a [TabsLayout]. */
@Composable
fun RoutedTabs(tabs: TabsLayout, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                for (descriptor in tabs.tabs) {
                    NavigationBarItem(
                        selected = tabs.selection == descriptor.id,
                        onClick = { navigator.selectTab(descriptor.id) },
                        icon = { Text(descriptor.icon ?: "•") },
                        label = { Text(descriptor.title) },
                    )
                }
            }
        },
    ) { padding ->
        RoutedRoot(tabs.selectedLayout, Modifier.fillMaxSize().padding(padding))
    }
}

/**
 * A responsive list-detail split bound to a [SplitLayout]. Wide windows show
 * both panes side by side; compact windows show the list until a selection is
 * made, then the detail stack (system back returns to the list). The
 * counterpart of the Swift package's `NavigationSplitView` root.
 */
@Composable
fun RoutedSplit(split: SplitLayout, modifier: Modifier = Modifier) {
    val registry = LocalDestinationRegistry.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(320.dp)) {
                    split.sidebarRoot?.let { registry.Screen(it) }
                }
                VerticalDivider()
                Box(Modifier.fillMaxSize()) {
                    RoutedStack(split.detailContext, overrideRoot = split.sidebarSelection)
                }
            }
        } else {
            val selection = split.sidebarSelection
            if (selection == null && split.detailContext.backStack.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    split.sidebarRoot?.let { registry.Screen(it) }
                }
            } else {
                RoutedStack(split.detailContext, overrideRoot = selection)
            }
        }
    }
}
