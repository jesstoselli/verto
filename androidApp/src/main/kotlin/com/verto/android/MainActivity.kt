package com.verto.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.verto.data.sync.SyncScheduler
import com.verto.ui.App
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val scheduler: SyncScheduler by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }

    // Opening the app (or coming back to it) is when fresh data matters most.
    override fun onStart() {
        super.onStart()
        scheduler.syncNow()
    }
}
