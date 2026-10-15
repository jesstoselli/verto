package com.verto.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.verto.ui.navigation.VertoNavHost
import com.verto.ui.theme.VertoTheme

@Composable
fun App() {
    VertoTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            VertoNavHost()
        }
    }
}
