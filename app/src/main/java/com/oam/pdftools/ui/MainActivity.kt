package com.oam.pdftools.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.*
import com.oam.pdftools.Incoming
import com.oam.pdftools.importShared
import com.oam.pdftools.work.PdfWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        if (s == null) handle(intent)
        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                val route by pendingRoute
                LaunchedEffect(route) { route?.let { nav.navigate(it) { popUpTo("home") }; pendingRoute.value = null } }
                val back: () -> Unit = { nav.popBackStack() }
                NavHost(nav, "home") {
                    composable("home") { HomeScreen { Incoming.uris = emptyList(); nav.navigate("tool/$it") } }
                    composable("open") { OpenWithScreen({ nav.navigate("tool/$it") }, back) }
                    composable("tool/{op}") {
                        when (val op = it.arguments!!.getString("op")!!) {
                            PdfWorker.SPLIT -> SplitScreen(back)
                            PdfWorker.EXTRACT, PdfWorker.ROTATE, PdfWorker.TO_IMAGE -> PageGridScreen(op, back)
                            else -> ToolScreen(op, back)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    /** Share sheet (SEND / SEND_MULTIPLE) and "Open with" (VIEW). */
    private fun handle(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            else -> return
        }
        if (uris.isEmpty()) return
        val images = uris.all { (contentResolver.getType(it) ?: intent.type).orEmpty().startsWith("image/") }
        lifecycleScope.launch {
            val local = importShared(uris).ifEmpty { return@launch }
            Incoming.uris = local
            pendingRoute.value = when {
                images -> "tool/${PdfWorker.IMG}"
                local.size > 1 -> "tool/${PdfWorker.MERGE}"
                else -> "open"
            }
        }
    }
}
