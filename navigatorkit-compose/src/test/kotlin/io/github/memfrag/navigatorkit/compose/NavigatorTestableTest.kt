package io.github.memfrag.navigatorkit.compose

import io.github.memfrag.navigatorkit.Route
import io.github.memfrag.navigatorkit.TabId
import io.github.memfrag.navigatorkit.intent.navigationIntent
import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.RootLayout
import io.github.memfrag.navigatorkit.state.TabDescriptor
import io.github.memfrag.navigatorkit.state.TabsLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compose facade is testable on the plain JVM — no Robolectric, no
 * emulator — because a headless [Navigator] and the single-pass resolver are
 * pure Kotlin. Constructing the navigator and driving it mutates snapshot
 * state directly, which we assert on.
 */
class NavigatorTestableTest {

    private sealed interface R : Route {
        data object List : R
        data class Detail(val id: Int) : R
    }

    @Test
    fun `testableStack builds a rooted scene`() {
        val navigator = Navigator.testableStack(root = R.List)
        assertEquals(R.List, navigator.scene.baseContext.root)
        assertTrue(navigator.scene.baseContext.backStack.isEmpty())
    }

    @Test
    fun `navigate applies synchronously`() {
        val navigator = Navigator.testableStack(root = R.List)
        navigator.navigate(R.Detail(42))  // empty registry → default Push
        assertEquals(listOf<Route>(R.Detail(42)), navigator.scene.baseContext.backStack)
    }

    @Test
    fun `perform drives a compound intent across containers`() {
        val tabs = TabsLayout(
            selection = TabId("a"),
            tabs = listOf(
                TabDescriptor(TabId("a"), "A", content = RootLayout.Stack(NavigationContext(root = R.List))),
                TabDescriptor(TabId("b"), "B", content = RootLayout.Stack(NavigationContext())),
            ),
        )
        val navigator = Navigator.testable(RootLayout.Tabs(tabs))

        navigator.perform(
            navigationIntent {
                selectTab(TabId("b"))
                push(R.Detail(1))
                present(R.Detail(2))
            }
        )

        assertEquals(TabId("b"), tabs.selection)
        val base = navigator.scene.baseContext
        assertEquals(listOf<Route>(R.Detail(1)), base.backStack)
        assertEquals(R.Detail(2), base.sheet?.content?.root)
    }

    @Test
    fun `dismiss closes the topmost presentation`() {
        val navigator = Navigator.testableStack(root = R.List)
        navigator.present(R.Detail(9))
        assertEquals(R.Detail(9), navigator.scene.baseContext.sheet?.content?.root)

        navigator.dismiss()
        assertNull(navigator.scene.baseContext.sheet)
    }
}
