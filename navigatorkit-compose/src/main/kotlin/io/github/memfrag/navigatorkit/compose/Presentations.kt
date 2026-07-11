@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.memfrag.navigatorkit.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.memfrag.navigatorkit.state.AlertButton
import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.PresentationStyle

/**
 * Installs bottom-sheet / full-screen / alert / dialog surfaces for a
 * [NavigationContext], bound to its snapshot state. Applied by [RoutedStack]
 * at every level, which is what makes nested presentation chains work — each
 * presented context installs its own host.
 */
@Composable
fun PresentationHost(context: NavigationContext) {
    // Bottom sheet
    val sheet = context.sheet
    if (sheet != null && sheet.style is PresentationStyle.BottomSheet) {
        val style = sheet.style as PresentationStyle.BottomSheet
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = style.skipPartiallyExpanded,
        )
        ModalBottomSheet(
            onDismissRequest = { if (style.dismissible) context.sheet = null },
            sheetState = sheetState,
        ) {
            RoutedStack(sheet.content, Modifier.fillMaxWidth())
        }
    }

    // Full-screen modal (fullScreenCover counterpart)
    val cover = context.fullScreen
    if (cover != null) {
        Dialog(
            onDismissRequest = { context.fullScreen = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                RoutedStack(cover.content)
            }
        }
    }

    // Alert
    context.alert?.let { alert ->
        AlertDialog(
            onDismissRequest = { context.alert = null },
            title = { Text(alert.title) },
            text = alert.message?.let { { Text(it) } },
            confirmButton = { AlertButtons(alert.buttons) { context.alert = null } },
        )
    }

    // Confirmation dialog (no native action sheet on Android; a dialog is idiomatic)
    context.dialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { context.dialog = null },
            title = { Text(dialog.title) },
            text = dialog.message?.let { { Text(it) } },
            confirmButton = { AlertButtons(dialog.buttons) { context.dialog = null } },
        )
    }
}

@Composable
private fun AlertButtons(buttons: List<AlertButton>, dismiss: () -> Unit) {
    Row {
        val ordered = buttons.sortedBy { it.role == AlertButton.Role.Cancel }
        for (button in ordered) {
            TextButton(onClick = { button.onClick?.invoke(); dismiss() }) {
                Text(button.label)
            }
        }
        if (buttons.isEmpty()) {
            TextButton(onClick = dismiss) { Text("OK") }
        }
    }
}
