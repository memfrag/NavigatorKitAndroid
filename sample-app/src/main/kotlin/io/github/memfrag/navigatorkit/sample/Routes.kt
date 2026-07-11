package io.github.memfrag.navigatorkit.sample

import io.github.memfrag.navigatorkit.Route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// In a real multi-module app each of these route families would live in its
// own feature module, depending only on :navigatorkit-core. They're grouped
// here for a single-module sample; the registration pattern (RoutableFeature)
// is identical either way.

@Serializable
sealed interface ProductRoute : Route {
    @Serializable @SerialName("product.list")
    data object List : ProductRoute

    @Serializable @SerialName("product.detail")
    data class Detail(val id: Int) : ProductRoute
}

@Serializable
sealed interface ReviewRoute : Route {
    @Serializable @SerialName("review.compose")
    data class Compose(val productId: Int) : ReviewRoute

    @Serializable @SerialName("review.photoPicker")
    data object PhotoPicker : ReviewRoute
}

@Serializable
sealed interface SettingsRoute : Route {
    @Serializable @SerialName("settings.menu")
    data object Menu : SettingsRoute

    @Serializable @SerialName("settings.general")
    data object General : SettingsRoute

    @Serializable @SerialName("settings.advanced")
    data object Advanced : SettingsRoute
}

@Serializable
sealed interface PlaygroundRoute : Route {
    @Serializable @SerialName("playground.home")
    data object Home : PlaygroundRoute
}

enum class AppTab { Shop, Settings, Playground }
