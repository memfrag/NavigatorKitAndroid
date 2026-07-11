package io.github.memfrag.navigatorkit.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.memfrag.navigatorkit.compose.LocalNavigator
import io.github.memfrag.navigatorkit.intent.RoutePlacement
import io.github.memfrag.navigatorkit.intent.navigationIntent
import io.github.memfrag.navigatorkit.state.AlertButton

private data class Product(val id: Int, val name: String, val price: String)

private val products = listOf(
    Product(1, "Aeropress", "$39"),
    Product(2, "Chemex", "$49"),
    Product(3, "Hario V60", "$25"),
    Product(42, "La Marzocco Linea Mini", "$5,900"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen() {
    Scaffold(topBar = { TopAppBar(title = { Text("Shop") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(products) { product ->
                val navigator = LocalNavigator.current
                ListItem(
                    headlineContent = { Text(product.name) },
                    trailingContent = { Text(product.price) },
                    modifier = Modifier.clickable {
                        navigator.navigate(ProductRoute.Detail(product.id))
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(id: Int) {
    val navigator = LocalNavigator.current
    val product = products.firstOrNull { it.id == id } ?: Product(id, "Product #$id", "—")
    Scaffold(topBar = { TopAppBar(title = { Text(product.name) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(product.price, style = MaterialTheme.typography.headlineMedium)
            Text("Product #$id", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { navigator.navigate(ReviewRoute.Compose(id)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Write a Review") }
            OutlinedButton(
                onClick = { navigator.push(ProductRoute.Detail(id + 1)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Next Product") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeReviewScreen(productId: Int) {
    val navigator = LocalNavigator.current
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("New Review", style = MaterialTheme.typography.titleLarge)
        Text("Reviewing product #$productId", style = MaterialTheme.typography.bodyMedium)
        // Nested presentation: a sheet from within a sheet.
        OutlinedButton(
            onClick = { navigator.present(ReviewRoute.PhotoPicker) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add Photo…") }
        Button(
            onClick = {
                navigator.dismiss()
                navigator.alert("Thanks!", "Your review was submitted.")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Submit") }
        OutlinedButton(
            onClick = {
                navigator.perform(
                    navigationIntent {
                        dialog(
                            "Discard this review?",
                            buttons = listOf(
                                AlertButton.destructive("Discard") { navigator.dismiss() },
                                AlertButton.cancel(),
                            ),
                        )
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Cancel") }
    }
}

@Composable
fun PhotoPickerScreen() {
    val navigator = LocalNavigator.current
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Photo Picker", style = MaterialTheme.typography.titleLarge)
        Text("A nested sheet, two presentation levels deep.")
        Button(onClick = { navigator.dismiss() }) { Text("Done") }
    }
}

// ---- Settings (split) ----

@Composable
fun SettingsMenuScreen() {
    val navigator = LocalNavigator.current
    Column {
        for (route in listOf(SettingsRoute.General, SettingsRoute.Advanced)) {
            ListItem(
                headlineContent = { Text(if (route == SettingsRoute.General) "General" else "Advanced") },
                modifier = Modifier.clickable { navigator.selectSidebar(route) },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailScreen(title: String) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("Settings section: $title", overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---- Playground: fire the canonical hard intent ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaygroundScreen() {
    val navigator = LocalNavigator.current
    Scaffold(topBar = { TopAppBar(title = { Text("Playground") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("One intent, five containers", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    navigator.perform(
                        navigationIntent {
                            selectTab(AppTab.Shop)
                            setStack(ProductRoute.List, ProductRoute.Detail(42))
                            present(ReviewRoute.Compose(42))
                            push(ReviewRoute.PhotoPicker)
                            alert("Arrived!", "Tab → stack → sheet → push → alert.")
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Run canonical hard intent") }

            OutlinedButton(
                onClick = {
                    navigator.navigate(
                        ProductRoute.Detail(42),
                        RoutePlacement.ActivateExisting(
                            io.github.memfrag.navigatorkit.intent.ActivationFallback.Push
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Activate Product #42 (reveal existing)") }

            OutlinedButton(
                onClick = { navigator.dismissAll() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Dismiss everything") }
        }
    }
}
