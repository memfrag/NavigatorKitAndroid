package io.github.memfrag.navigatorkit

import io.github.memfrag.navigatorkit.deeplink.DeepLinkException
import io.github.memfrag.navigatorkit.deeplink.deepLinkMap
import io.github.memfrag.navigatorkit.intent.NavigationOperation
import io.github.memfrag.navigatorkit.intent.navigationIntent
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Port of the Swift URL pattern matching table. */
class UrlPatternTest {

    private val map = deepLinkMap {
        pattern("/products/**") {
            navigationIntent { selectTab(Shop) }
        }
        pattern("/products/:id") { params ->
            navigationIntent {
                selectTab(Shop)
                setStack(ProductRoute.List, ProductRoute.Detail(params.int("id")))
            }
        }
        pattern("/settings/**") {
            navigationIntent { selectTab(Settings) }
        }
        pattern("shopapp://only-scheme/:x") { params ->
            navigationIntent { push(ProductRoute.Detail(params.int("x"))) }
        }
        pattern("https://shop.example.com/web/:id") { params ->
            navigationIntent { push(ProductRoute.Detail(params.int("id"))) }
        }
    }

    @Test
    fun `most specific pattern wins regardless of declaration order`() {
        val intent = assertNotNull(map.intentFor("shopapp://products/42"))
        // ":id" beats the earlier-declared "**".
        assertEquals(2, intent.operations.size)
        val setStack = intent.operations[1] as NavigationOperation.SetStack
        assertContentEquals(listOf(ProductRoute.List, ProductRoute.Detail(42)), setStack.routes)
    }

    @Test
    fun `throwing handler falls through to next match`() {
        // "abc" fails Int extraction in ":id" → the "**" catch-all handles it.
        val intent = assertNotNull(map.intentFor("shopapp://products/abc"))
        assertEquals(1, intent.operations.size)
    }

    @Test
    fun `custom scheme and universal link share one pattern`() {
        val fromScheme = assertNotNull(map.intentFor("shopapp://products/7"))
        val fromWeb = assertNotNull(map.intentFor("https://anything.example/products/7"))
        assertEquals(fromScheme.operations.size, fromWeb.operations.size)
    }

    @Test
    fun `scheme constraint filters`() {
        assertNotNull(map.intentFor("shopapp://only-scheme/1"))
        assertNull(map.intentFor("otherapp://only-scheme/1"))
    }

    @Test
    fun `web host constraint filters`() {
        assertNotNull(map.intentFor("https://shop.example.com/web/1"))
        assertNull(map.intentFor("https://other.example.com/web/1"))
    }

    @Test
    fun `no match returns null`() {
        assertNull(map.intentFor("shopapp://unknown/path"))
    }

    @Test
    fun `catch all matches zero or more and captures the rest`() {
        assertNotNull(map.intentFor("shopapp://settings"))
        assertNotNull(map.intentFor("shopapp://settings/a/b/c"))
    }

    @Test
    fun `typed extraction and query parameters`() {
        val probe = deepLinkMap {
            pattern("/things/:id") { params ->
                assertEquals(9, params.int("id"))
                assertEquals("email", params.query("ref"))
                assertEquals(true, params.query("promo") == "1")
                assertNull(params.query("missing"))
                assertFailsWithDeepLink { params.int("bogus") }
                navigationIntent { push(ProductRoute.Detail(params.int("id"))) }
            }
        }
        assertNotNull(probe.intentFor("https://x.com/things/9?ref=email&promo=1"))
    }

    @Test
    fun `percent encoded components are decoded`() {
        val probe = deepLinkMap {
            pattern("/tags/:name") { params ->
                assertEquals("hello world", params.string("name"))
                navigationIntent { selectTab(Shop) }
            }
        }
        assertNotNull(probe.intentFor("https://x.com/tags/hello%20world"))
    }

    private inline fun assertFailsWithDeepLink(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected DeepLinkException")
        } catch (_: DeepLinkException) {
            // expected
        }
    }
}
