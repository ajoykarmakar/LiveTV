package com.clean.livetv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.pm.ActivityInfo
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.clean.livetv.ui.LiveTvApp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Force app back to normal orientation on launch just in case
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        setContent {
            LiveTvApp()
        }
    }
}