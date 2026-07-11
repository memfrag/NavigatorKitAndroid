package io.github.memfrag.navigatorkit.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.memfrag.navigatorkit.compose.Navigator
import io.github.memfrag.navigatorkit.compose.RoutedScene

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val navigator = remember {
                Navigator(ShopComposition.newScene(), ShopComposition.registry)
            }
            // Deep link that launched the activity.
            remember { handleDeepLink(intent, navigator) }

            MaterialTheme {
                RoutedScene(navigator)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A live navigator exists in composition; the next recomposition sees
        // the mutated tree. For simplicity the sample re-reads on launch only;
        // a production app would hoist the Navigator above setContent.
    }
}

private fun handleDeepLink(intent: Intent?, navigator: Navigator): Boolean {
    val uri = intent?.data?.toString() ?: return false
    ShopComposition.deepLinks.intentFor(uri)?.let(navigator::perform)
    return true
}
