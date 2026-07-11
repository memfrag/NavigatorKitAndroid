package io.github.memfrag.navigatorkit

import io.github.memfrag.navigatorkit.restore.NavigationSnapshotCoder
import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.PresentationStyle
import io.github.memfrag.navigatorkit.state.PresentedContext
import io.github.memfrag.navigatorkit.state.RootLayout
import io.github.memfrag.navigatorkit.state.RoutedAlert
import io.github.memfrag.navigatorkit.state.SceneNavigator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnapshotRestorationTest {

    private fun makePopulatedScene(): SceneNavigator {
        val scene = makeTabbedScene()
        scene.baseContext.backStack.add(ProductRoute.Detail(42))

        val sheetContent = NavigationContext(root = ReviewRoute.Compose(42))
        sheetContent.backStack.add(ProductRoute.Detail(1))
        scene.baseContext.sheet = PresentedContext(
            PresentationStyle.BottomSheet(skipPartiallyExpanded = true),
            sheetContent,
        )
        sheetContent.fullScreen = PresentedContext(
            PresentationStyle.FullScreen,
            NavigationContext(root = ProductRoute.Detail(2)),
        )

        scene.settingsSplit().sidebarSelection = SettingsRoute.General
        scene.settingsSplit().detailContext.backStack.add(SettingsRoute.Advanced)

        scene.baseContext.alert = RoutedAlert("Transient")
        return scene
    }

    @Test
    fun `round trip restores full tree and skips transient state`() {
        val data = NavigationSnapshotCoder.encode(makePopulatedScene(), fullJson)

        val restored = makeTabbedScene()
        val report = assertNotNull(NavigationSnapshotCoder.decodeAndRestore(restored, data, fullJson))
        assertTrue(report.isClean)

        assertEquals(Shop, restored.tabs().selection)
        assertContentEquals(listOf<Route>(ProductRoute.Detail(42)), restored.baseContext.backStack)

        val sheet = assertNotNull(restored.baseContext.sheet)
        assertEquals(PresentationStyle.BottomSheet(skipPartiallyExpanded = true), sheet.style)
        assertEquals(ReviewRoute.Compose(42), sheet.content.root)
        assertContentEquals(listOf<Route>(ProductRoute.Detail(1)), sheet.content.backStack)

        val cover = assertNotNull(sheet.content.fullScreen)
        assertEquals(ProductRoute.Detail(2), cover.content.root)

        assertEquals(SettingsRoute.General, restored.settingsSplit().sidebarSelection)
        assertContentEquals(
            listOf<Route>(SettingsRoute.Advanced),
            restored.settingsSplit().detailContext.backStack,
        )

        assertNull(restored.baseContext.alert, "alerts are transient")
    }

    @Test
    fun `unknown route type truncates path and drops presentations above`() {
        val data = NavigationSnapshotCoder.encode(makePopulatedScene(), fullJson)

        // ProductRoute is unregistered in partialJson (renamed/removed type).
        val restored = makeTabbedScene()
        val report = assertNotNull(NavigationSnapshotCoder.decodeAndRestore(restored, data, partialJson))
        assertFalse(report.isClean)

        // Shop path element undecodable → truncated to root, sheet dropped.
        assertTrue(restored.baseContext.backStack.isEmpty())
        assertNull(restored.baseContext.sheet)

        // Settings survives (SettingsRoute still registered).
        assertContentEquals(
            listOf<Route>(SettingsRoute.Advanced),
            restored.settingsSplit().detailContext.backStack,
        )
    }

    @Test
    fun `undecodable layer root drops the layer and everything above`() {
        val scene = makeTabbedScene()
        val sheetContent = NavigationContext(root = ProductRoute.Detail(3))
        scene.baseContext.sheet = PresentedContext(PresentationStyle.BottomSheet(), sheetContent)
        sheetContent.sheet = PresentedContext(
            PresentationStyle.BottomSheet(),
            NavigationContext(root = ReviewRoute.Compose(3)),
        )
        val data = NavigationSnapshotCoder.encode(scene, fullJson)

        val restored = makeTabbedScene()
        val report = assertNotNull(NavigationSnapshotCoder.decodeAndRestore(restored, data, partialJson))
        assertFalse(report.isClean)
        // First layer's root undecodable → whole chain gone even though the
        // nested ReviewRoute was decodable.
        assertNull(restored.baseContext.sheet)
    }

    @Test
    fun `version mismatch and corrupt data are discarded`() {
        val scene = makeTabbedScene()
        val current = NavigationSnapshotCoder.encode(scene, fullJson)
        val futureVersion = current.replaceFirst("\"version\":1", "\"version\":999")
            .let { if (it == current) current.replaceFirst("\"version\": 1", "\"version\": 999") else it }

        assertNull(NavigationSnapshotCoder.decodeAndRestore(makeTabbedScene(), futureVersion, fullJson))
        assertNull(NavigationSnapshotCoder.decodeAndRestore(makeTabbedScene(), "junk", fullJson))
    }

    @Test
    fun `blueprint shape mismatch skips subtree without crashing`() {
        val data = NavigationSnapshotCoder.encode(makePopulatedScene(), fullJson)
        val stackScene = SceneNavigator(RootLayout.Stack(NavigationContext()))
        assertNotNull(NavigationSnapshotCoder.decodeAndRestore(stackScene, data, fullJson))
        assertTrue(stackScene.baseContext.backStack.isEmpty())
    }

    @Test
    fun `root presentation chain round trips`() {
        val scene = makeTabbedScene()
        scene.rootPresentation = PresentedContext(
            PresentationStyle.FullScreen,
            NavigationContext(root = SettingsRoute.Menu),
        )
        val data = NavigationSnapshotCoder.encode(scene, fullJson)

        val restored = makeTabbedScene()
        assertNotNull(NavigationSnapshotCoder.decodeAndRestore(restored, data, fullJson))
        val rootPresentation = assertNotNull(restored.rootPresentation)
        assertEquals(PresentationStyle.FullScreen, rootPresentation.style)
        assertEquals(SettingsRoute.Menu, rootPresentation.content.root)
    }
}
