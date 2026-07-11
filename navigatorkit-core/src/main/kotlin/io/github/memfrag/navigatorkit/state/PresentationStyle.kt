package io.github.memfrag.navigatorkit.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a child [NavigationContext] is presented over its parent. Modeled
 * independently of Compose so the state layer stays headless and
 * serializable; the UI module maps these onto `ModalBottomSheet` and
 * full-screen destinations.
 */
@Serializable
sealed interface PresentationStyle {

    /** A modal bottom sheet (the counterpart of an iOS sheet). */
    @Serializable
    @SerialName("bottomSheet")
    data class BottomSheet(
        val skipPartiallyExpanded: Boolean = false,
        val dismissible: Boolean = true,
    ) : PresentationStyle

    /** A full-screen modal (the counterpart of `fullScreenCover`). */
    @Serializable
    @SerialName("fullScreen")
    data object FullScreen : PresentationStyle
}

/** Serializable mirror of the list-detail split's pane emphasis. */
@Serializable
enum class SplitPaneFocus { List, Detail }
