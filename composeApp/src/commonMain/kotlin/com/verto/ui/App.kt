package com.verto.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.verto.ui.navigation.VertoNavHost
import com.verto.ui.theme.VertoTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(appViewModel: AppViewModel = koinViewModel()) {
    val offerFreshStart by appViewModel.offerFreshStart.collectAsStateWithLifecycle()
    LifecycleResumeEffect(appViewModel) {
        appViewModel.onForeground()
        onPauseOrDispose { }
    }
    VertoTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (offerFreshStart) {
                FreshStartScreen(onReset = appViewModel::startClean, onKeep = appViewModel::keepAsItWas)
            } else {
                VertoNavHost()
            }
        }
    }
}
