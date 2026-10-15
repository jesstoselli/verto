package com.verto.ui.plan

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun PlanScreen(onOpenTask: (String?) -> Unit, onOpenRoutine: (String?) -> Unit, onOpenAnchor: (String?) -> Unit) {
    Text("Planejar")
}
