package io.github.memfrag.navigatorkit

import io.github.memfrag.navigatorkit.intent.ActivationFallback
import io.github.memfrag.navigatorkit.intent.NavigationIntent
import io.github.memfrag.navigatorkit.intent.RoutePlacement
import io.github.memfrag.navigatorkit.intent.navigationIntent
import io.github.memfrag.navigatorkit.resolver.IntentResolver
import io.github.memfrag.navigatorkit.resolver.NavigationException
import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.PresentationStyle
import io.github.memfrag.navigatorkit.state.PresentedContext
import io.github.memfrag.navigatorkit.state.SceneNavigator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Port of the Swift package's executor end-state matrix — the shared
 * behavioral contract. Same scenarios, same expected trees.
 */
class IntentResolverTest {

    private fun SceneNavigator.resolve(intent: NavigationIntent) =
        IntentResolver.resolve(intent, this)

    @Test
    fun `canonical hard intent end state`() {
        val scene = makeTabbedScene()
        scene.tabs().selection = Search

        scene.resolve(
            navigationIntent {
                selectTab(Shop)
                setStack(ProductRoute.List, ProductRoute.Detail(42))
                present(ReviewRoute.Compose(42), PresentationStyle.BottomSheet())
                push(ProductRoute.Detail(1))
                alert("Arrived!", "One intent, five containers.")
            }
        )

        assertEquals(Shop, scene.tabs().selection)
        assertContentEquals(
            listOf(ProductRoute.List, ProductRoute.Detail(42)),
            scene.baseContext.backStack,
        )
        val sheet = assertNotNull(scene.baseContext.sheet)
        assertEquals(PresentationStyle.BottomSheet(), sheet.style)
        assertEquals(ReviewRoute.Compose(42), sheet.content.root)
        assertContentEquals(listOf<Route>(ProductRoute.Detail(1)), sheet.content.backStack)
        assertEquals("Arrived!", sheet.content.alert?.title)
    }

    @Test
    fun `nested presentation`() {
        val scene = makeTabbedScene()
        scene.resolve(
            navigationIntent {
                present(ReviewRoute.Compose(1))
                present(ProductRoute.Detail(2), PresentationStyle.FullScreen)
            }
        )
        val sheet = assertNotNull(scene.baseContext.sheet)
        val nested = assertNotNull(sheet.content.fullScreen)
        assertEquals(ProductRoute.Detail(2), nested.content.root)
        assertSame(nested.content, scene.activeContext)
    }

    @Test
    fun `tab switch dismisses visible sheet but keeps sibling stacks`() {
        val scene = makeTabbedScene()
        val shopBase = scene.baseContext
        shopBase.backStack.add(ProductRoute.Detail(7))
        shopBase.sheet = PresentedContext(PresentationStyle.BottomSheet(), NavigationContext())

        scene.resolve(navigationIntent { selectTab(Search) })

        assertEquals(Search, scene.tabs().selection)
        assertNull(shopBase.sheet, "visible presentation dismissed on tab switch (contract parity)")
        assertContentEquals(listOf<Route>(ProductRoute.Detail(7)), shopBase.backStack)
    }

    @Test
    fun `reselecting current tab keeps its sheet`() {
        val scene = makeTabbedScene()
        scene.baseContext.sheet = PresentedContext(PresentationStyle.BottomSheet(), NavigationContext())
        scene.resolve(navigationIntent { selectTab(Shop) })
        assertNotNull(scene.baseContext.sheet)
    }

    @Test
    fun `path mutation under a sheet dismisses it first`() {
        val scene = makeTabbedScene()
        scene.baseContext.sheet = PresentedContext(PresentationStyle.BottomSheet(), NavigationContext())

        scene.resolve(
            navigationIntent {
                selectTab(Shop)
                setStack(ProductRoute.Detail(9))
            }
        )
        assertNull(scene.baseContext.sheet)
        assertContentEquals(listOf<Route>(ProductRoute.Detail(9)), scene.baseContext.backStack)
    }

    @Test
    fun `pop operations`() {
        val scene = makeTabbedScene()
        scene.baseContext.backStack.addAll(
            listOf(ProductRoute.Detail(1), ProductRoute.Detail(2), ProductRoute.Detail(3))
        )

        scene.resolve(navigationIntent { pop() })
        assertEquals(2, scene.baseContext.backStack.size)

        scene.resolve(navigationIntent { popTo(ProductRoute.Detail(1)) })
        assertContentEquals(listOf<Route>(ProductRoute.Detail(1)), scene.baseContext.backStack)

        scene.resolve(navigationIntent { popToRoot() })
        assertTrue(scene.baseContext.backStack.isEmpty())

        // Pop on an empty stack is a no-op.
        scene.resolve(navigationIntent { pop() })
        assertTrue(scene.baseContext.backStack.isEmpty())
    }

    @Test
    fun `popTo missing route throws`() {
        val scene = makeTabbedScene()
        assertFailsWith<NavigationException.RouteNotInBackStack> {
            scene.resolve(navigationIntent { popTo(ProductRoute.Detail(5)) })
        }
    }

    @Test
    fun `selectTab on stack root throws`() {
        val scene = SceneNavigator(
            io.github.memfrag.navigatorkit.state.RootLayout.Stack(NavigationContext())
        )
        assertFailsWith<NavigationException.NoTabsLayout> {
            scene.resolve(navigationIntent { selectTab(Shop) })
        }
    }

    @Test
    fun `unknown tab throws`() {
        val scene = makeTabbedScene()
        assertFailsWith<NavigationException.UnknownTab> {
            scene.resolve(navigationIntent { selectTab("bogus") })
        }
    }

    @Test
    fun `dismiss after present throws`() {
        val scene = makeTabbedScene()
        assertFailsWith<NavigationException.InvalidOperation> {
            scene.resolve(
                navigationIntent {
                    present(ReviewRoute.Compose(1))
                    dismiss()
                }
            )
        }
    }

    @Test
    fun `dismiss removes deepest presentation`() {
        val scene = makeTabbedScene()
        val sheetContent = NavigationContext()
        val nestedContent = NavigationContext()
        scene.baseContext.sheet = PresentedContext(PresentationStyle.BottomSheet(), sheetContent)
        sheetContent.sheet = PresentedContext(PresentationStyle.BottomSheet(), nestedContent)

        scene.resolve(navigationIntent { dismiss() })
        assertNotNull(scene.baseContext.sheet)
        assertNull(sheetContent.sheet)

        scene.resolve(navigationIntent { dismiss() })
        assertNull(scene.baseContext.sheet)

        // Nothing presented: no-op, no error.
        scene.resolve(navigationIntent { dismiss() })
    }

    @Test
    fun `dismissAll unwinds root presentation and base chain`() {
        val scene = makeTabbedScene()
        scene.baseContext.sheet = PresentedContext(PresentationStyle.BottomSheet(), NavigationContext())
        scene.rootPresentation = PresentedContext(PresentationStyle.FullScreen, NavigationContext())

        scene.resolve(navigationIntent { dismissAll() })
        assertNull(scene.rootPresentation)
        assertNull(scene.baseContext.sheet)
    }

    @Test
    fun `sidebar selection targets detail and dismisses its sheet on change`() {
        val scene = makeTabbedScene()
        val split = scene.settingsSplit()
        split.sidebarSelection = SettingsRoute.General
        split.detailContext.sheet = PresentedContext(PresentationStyle.BottomSheet(), NavigationContext())

        scene.resolve(
            navigationIntent {
                selectTab(Settings)
                selectSidebar(SettingsRoute.Advanced)
                push(SettingsRoute.General)
            }
        )

        assertEquals(SettingsRoute.Advanced, split.sidebarSelection)
        assertNull(split.detailContext.sheet)
        assertContentEquals(listOf<Route>(SettingsRoute.General), split.detailContext.backStack)
    }

    @Test
    fun `selectSidebar without split throws`() {
        val scene = SceneNavigator(
            io.github.memfrag.navigatorkit.state.RootLayout.Stack(NavigationContext())
        )
        assertFailsWith<NavigationException.NoSplitLayout> {
            scene.resolve(navigationIntent { selectSidebar(SettingsRoute.General) })
        }
    }

    // ---- Activate semantics ----

    @Test
    fun `activate reveals route in other tab dismissing coverings`() {
        val scene = makeTabbedScene()
        val shopBase = scene.baseContext
        shopBase.backStack.addAll(listOf(ProductRoute.Detail(1), ProductRoute.Detail(2)))
        shopBase.sheet = PresentedContext(PresentationStyle.BottomSheet(), NavigationContext())
        scene.tabs().selection = Search

        scene.resolve(
            NavigationIntent.navigate(
                ProductRoute.Detail(1),
                RoutePlacement.ActivateExisting(ActivationFallback.Push),
            )
        )

        assertEquals(Shop, scene.tabs().selection)
        assertContentEquals(listOf<Route>(ProductRoute.Detail(1)), shopBase.backStack)
        assertNull(shopBase.sheet)
    }

    @Test
    fun `activate falls back when absent`() {
        val scene = makeTabbedScene()
        scene.resolve(
            NavigationIntent.navigate(
                ProductRoute.Detail(99),
                RoutePlacement.ActivateExisting(ActivationFallback.Push),
            )
        )
        assertContentEquals(listOf<Route>(ProductRoute.Detail(99)), scene.baseContext.backStack)
    }

    @Test
    fun `activate route at context root pops to root`() {
        val scene = makeTabbedScene()
        scene.baseContext.backStack.add(ProductRoute.Detail(1))
        scene.resolve(
            NavigationIntent.navigate(
                ProductRoute.List,
                RoutePlacement.ActivateExisting(ActivationFallback.Push),
            )
        )
        assertTrue(scene.baseContext.backStack.isEmpty())
    }

    @Test
    fun `activate route inside sheet keeps the sheet`() {
        val scene = makeTabbedScene()
        val sheetContent = NavigationContext(root = ReviewRoute.Compose(5))
        sheetContent.backStack.add(ProductRoute.Detail(8))
        scene.baseContext.sheet = PresentedContext(PresentationStyle.BottomSheet(), sheetContent)

        scene.resolve(
            NavigationIntent.navigate(
                ReviewRoute.Compose(5),
                RoutePlacement.ActivateExisting(ActivationFallback.Push),
            )
        )

        assertNotNull(scene.baseContext.sheet)
        assertTrue(sheetContent.backStack.isEmpty())
    }

    @Test
    fun `primaryRoute reports the terminal route`() {
        val intent = navigationIntent {
            selectTab(Shop)
            setStack(ProductRoute.List, ProductRoute.Detail(42))
            present(ReviewRoute.Compose(42))
        }
        assertEquals(ReviewRoute.Compose(42), intent.primaryRoute)
    }
}
