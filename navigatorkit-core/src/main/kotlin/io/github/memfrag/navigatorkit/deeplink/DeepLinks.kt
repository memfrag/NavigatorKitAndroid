package io.github.memfrag.navigatorkit.deeplink

import io.github.memfrag.navigatorkit.intent.NavigationIntent
import java.net.URI
import java.net.URLDecoder

/**
 * The app's deep-link table: ordered [UrlPattern]s mapping URIs (App Links,
 * custom schemes, notification payloads) to [NavigationIntent]s. Same
 * grammar and matching rules as the Swift package, so one link table spec
 * serves both platforms.
 *
 * ```kotlin
 * val deepLinks = deepLinkMap {
 *     pattern("/products/:id") { params ->
 *         navigationIntent {
 *             selectTab(AppTab.Shop)
 *             setStack(ProductRoute.List, ProductRoute.Detail(params.int("id")))
 *         }
 *     }
 *     pattern("shopapp://orders/:id") { p -> navigationIntent { push(OrderRoute.Detail(p.int("id"))) } }
 * }
 * ```
 *
 * Most-specific match wins (literal > parameter > wildcard > catch-all),
 * declaration order breaks ties; a handler that throws [DeepLinkException]
 * fails that pattern and matching falls through to the next.
 */
class DeepLinkMap(private val patterns: List<UrlPattern>) {

    fun intentFor(uri: URI): NavigationIntent? {
        val ranked = patterns.withIndex().sortedWith(
            compareByDescending<IndexedValue<UrlPattern>> { it.value.specificity }
                .thenBy { it.index }
        )
        for ((_, pattern) in ranked) {
            val params = pattern.match(uri) ?: continue
            try {
                return pattern.transform(params)
            } catch (_: DeepLinkException) {
                // Malformed parameter: fall through to the next pattern.
            }
        }
        return null
    }

    fun intentFor(uri: String): NavigationIntent? = intentFor(URI(uri))
}

fun deepLinkMap(build: DeepLinkMapBuilder.() -> Unit): DeepLinkMap =
    DeepLinkMap(DeepLinkMapBuilder().apply(build).patterns)

class DeepLinkMapBuilder internal constructor() {
    internal val patterns = mutableListOf<UrlPattern>()

    fun pattern(pattern: String, intent: (UrlParameters) -> NavigationIntent) {
        patterns += UrlPattern(pattern, intent)
    }
}

/**
 * One deep-link route. Pattern syntax:
 * - `/products/:id` — path components; `:name` captures a parameter.
 * - `*` matches exactly one component; trailing `**` matches the rest.
 * - `shopapp://products/:id` — constrains the scheme.
 * - `https://shop.example.com/products/:id` — constrains scheme and host.
 *
 * Custom-scheme URIs are normalized so the host counts as the first path
 * component (`shopapp://products/42` → effective path `products/42`),
 * letting one path-only pattern serve URL schemes and App Links alike.
 */
class UrlPattern(
    pattern: String,
    internal val transform: (UrlParameters) -> NavigationIntent,
) {
    private sealed interface Component {
        val specificity: Int

        data class Literal(val value: String) : Component { override val specificity get() = 3 }
        data class Parameter(val name: String) : Component { override val specificity get() = 2 }
        data object Wildcard : Component { override val specificity get() = 1 }
        data object CatchAll : Component { override val specificity get() = 0 }
    }

    private val scheme: String?
    private val host: String?
    private val components: List<Component>

    internal val specificity: Int
        get() = components.sumOf { it.specificity } + if (scheme != null) 4 else 0

    init {
        var scheme: String? = null
        var host: String? = null
        var remainder = pattern

        val schemeSplit = remainder.indexOf("://")
        if (schemeSplit >= 0) {
            scheme = remainder.substring(0, schemeSplit).lowercase()
            remainder = remainder.substring(schemeSplit + 3)
            if (scheme == "http" || scheme == "https") {
                val slash = remainder.indexOf('/')
                if (slash >= 0) {
                    host = remainder.substring(0, slash).lowercase()
                    remainder = remainder.substring(slash + 1)
                } else {
                    host = remainder.lowercase()
                    remainder = ""
                }
            }
            // Custom schemes: the "host" is just the first effective path
            // component; leave it in the remainder.
        }

        this.scheme = scheme
        this.host = host
        this.components = remainder.split('/').filter { it.isNotEmpty() }.map { segment ->
            when {
                segment == "**" -> Component.CatchAll
                segment == "*" -> Component.Wildcard
                segment.startsWith(":") -> Component.Parameter(segment.drop(1))
                else -> Component.Literal(segment)
            }
        }
    }

    internal fun match(uri: URI): UrlParameters? {
        if (scheme != null && uri.scheme?.lowercase() != scheme) return null
        if (host != null && uri.host?.lowercase() != host) return null

        val effective = effectiveComponents(uri)
        val values = mutableMapOf<String, String>()
        var catchAll = emptyList<String>()

        var index = 0
        for (component in components) {
            when (component) {
                is Component.CatchAll -> {
                    catchAll = effective.drop(index)
                    index = effective.size
                }
                is Component.Literal -> {
                    if (index >= effective.size || effective[index] != component.value) return null
                    index++
                }
                is Component.Parameter -> {
                    if (index >= effective.size) return null
                    values[component.name] = effective[index]
                    index++
                }
                Component.Wildcard -> {
                    if (index >= effective.size) return null
                    index++
                }
            }
        }
        if (index != effective.size) return null

        val query = buildMap {
            uri.rawQuery?.split('&')?.forEach { pair ->
                if (pair.isEmpty()) return@forEach
                val eq = pair.indexOf('=')
                if (eq >= 0) {
                    put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)))
                } else {
                    put(decode(pair), "")
                }
            }
        }
        return UrlParameters(values, query, catchAll)
    }

    private companion object {
        fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)

        /** Leading-slash-free path, with a custom scheme's host folded in. */
        fun effectiveComponents(uri: URI): List<String> {
            val path = (uri.path ?: "").split('/').filter { it.isNotEmpty() }
            val isWebScheme = uri.scheme == "http" || uri.scheme == "https"
            val host = uri.host
            return if (!isWebScheme && !host.isNullOrEmpty()) listOf(host) + path else path
        }
    }
}

/** Values captured while matching: named path parameters, query, catch-all. */
class UrlParameters internal constructor(
    private val values: Map<String, String>,
    private val query: Map<String, String>,
    val catchAll: List<String>,
) {
    operator fun get(name: String): String? = values[name]

    fun string(name: String): String =
        values[name] ?: throw DeepLinkException.MissingParameter(name)

    fun int(name: String): Int =
        string(name).toIntOrNull()
            ?: throw DeepLinkException.InvalidParameter(name, values[name]!!, "Int")

    fun long(name: String): Long =
        string(name).toLongOrNull()
            ?: throw DeepLinkException.InvalidParameter(name, values[name]!!, "Long")

    fun boolean(name: String): Boolean = when (string(name).lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> throw DeepLinkException.InvalidParameter(name, values[name]!!, "Boolean")
    }

    fun query(name: String): String? = query[name]

    fun queryInt(name: String): Int =
        query[name]?.toIntOrNull()
            ?: throw DeepLinkException.InvalidParameter(name, query[name] ?: "<missing>", "Int")
}

sealed class DeepLinkException(message: String) : Exception(message) {
    class MissingParameter(val name: String) : DeepLinkException("Missing parameter: $name")
    class InvalidParameter(val name: String, val value: String, val type: String) :
        DeepLinkException("Parameter $name=\"$value\" is not a valid $type")
}
