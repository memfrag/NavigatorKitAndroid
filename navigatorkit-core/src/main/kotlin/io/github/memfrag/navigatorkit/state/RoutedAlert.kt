package io.github.memfrag.navigatorkit.state

/**
 * A route-driven alert dialog, rendered by the UI layer on whichever
 * [NavigationContext] it is set on. Alerts are transient: never persisted
 * by state restoration.
 */
class RoutedAlert(
    val title: String,
    val message: String? = null,
    val buttons: List<AlertButton> = emptyList(),
)

/** A route-driven modal choice dialog (confirmation-dialog counterpart). */
class RoutedDialog(
    val title: String,
    val message: String? = null,
    val buttons: List<AlertButton> = emptyList(),
)

class AlertButton(
    val label: String,
    val role: Role? = null,
    val onClick: (() -> Unit)? = null,
) {
    enum class Role { Cancel, Destructive }

    companion object {
        fun cancel(label: String = "Cancel", onClick: (() -> Unit)? = null) =
            AlertButton(label, Role.Cancel, onClick)

        fun destructive(label: String, onClick: (() -> Unit)? = null) =
            AlertButton(label, Role.Destructive, onClick)
    }
}
