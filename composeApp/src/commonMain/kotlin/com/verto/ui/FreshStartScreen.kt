package com.verto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.verto.ui.common.Hint

/** Shown before any list after days away: coming back must not start with a wall of leftovers. */
@Composable
fun FreshStartScreen(onReset: () -> Unit, onKeep: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Que bom te ver.", style = MaterialTheme.typography.headlineMedium)
        Text("Quer começar com um dia limpo?", style = MaterialTheme.typography.titleLarge)
        Hint("Dia limpo tira tudo de Pra hoje e guarda os despejos soltos. Nada é apagado.")
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset") }
        OutlinedButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) { Text("Ver como estava") }
    }
}
