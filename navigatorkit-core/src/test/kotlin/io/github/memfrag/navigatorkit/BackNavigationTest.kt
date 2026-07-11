package io.github.memfrag.navigatorkit

import io.github.memfrag.navigatorkit.back.canGoBack
import io.github.memfrag.navigatorkit.back.goBack
import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.PresentationStyle
import io.github.memfrag.navigatorkit.state.PresentedContext
import io.github.memfrag.navigatorkit.state.RoutedAlert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackNavigationTest {

    @Test
    fun `back unwinds overlay, sheet stack, sheet, stack, tab, then yields to system`() {
        val scene = makeTabbedScene()
        scene.tabs().selection = Settings
        scene.tabs().selection = Shop
        scene.baseContext.backStack.add(ProductRoute.Detail(1))
        val sheetContent = NavigationContext(root = ReviewRoute.Compose(1))
        sheetContent.backStack.add(ProductRoute.Detail(2))
        scene.baseContext.sheet = PresentedContext(PresentationStyle.BottomSheet(), sheetContent)
        sheetContent.alert = RoutedAlert("Oops")

        assertTrue(scene.goBack()) // 1. alert
        assertNull(sheetContent.alert)

        assertTrue(scene.goBack()) // 2. pop inside the sheet
        assertTrue(sheetContent.backStack.isEmpty())

        assertTrue(scene.goBack()) // 3. dismiss the sheet
        assertNull(scene.baseContext.sheet)

        assertTrue(scene.goBack()) // 4. pop the base stack
        assertTrue(scene.baseContext.backStack.isEmpty())

        // 5. base stack empty, first tab selected → system takes over.
        assertFalse(scene.canGoBack())
        assertFalse(scene.goBack())
    }

    @Test
    fun `back returns to first tab before exiting`() {
        val scene = makeTabbedScene()
        scene.tabs().selection = Search

        assertTrue(scene.canGoBack())
        assertTrue(scene.goBack())
        assertEquals(Shop, scene.tabs().selection)
        assertFalse(scene.goBack())
    }

    @Test
    fun `back-to-first-tab can be disabled`() {
        val scene = makeTabbedScene()
        scene.tabs().selection = Search
        assertFalse(scene.canGoBack(backToFirstTab = false))
        assertFalse(scene.goBack(backToFirstTab = false))
        assertEquals(Search, scene.tabs().selection)
    }

    @Test
    fun `back dismisses an empty root presentation`() {
        val scene = makeTabbedScene()
        scene.rootPresentation = PresentedContext(PresentationStyle.FullScreen, NavigationContext())
        assertTrue(scene.goBack())
        assertNull(scene.rootPresentation)
    }
}
