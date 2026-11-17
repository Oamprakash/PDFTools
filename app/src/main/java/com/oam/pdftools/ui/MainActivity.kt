package com.oam.pdftools.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.*
import com.oam.pdftools.work.PdfWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                NavHost(nav, "home") {
                    composable("home") { HomeScreen { nav.navigate("tool/$it") } }
                    composable("tool/{op}") {
                        val op = it.arguments!!.getString("op")!!
                        if (op == PdfWorker.SPLIT) SplitScreen { nav.popBackStack() }
                        else if (op == PdfWorker.EXTRACT || op == PdfWorker.ROTATE) PageGridScreen(op) { nav.popBackStack() }
                        else ToolScreen(op) { nav.popBackStack() }
                    }
                }
            }
        }
    }
}
