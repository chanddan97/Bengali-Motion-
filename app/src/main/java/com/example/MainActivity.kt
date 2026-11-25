package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.projects.StudioViewModel
import com.example.navigation.BanglaMotionApp
import com.example.ui.theme.BanglaMotionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = (application as BanglaMotionApplication).appContainer
        setContent {
            BanglaMotionTheme {
                val studioViewModel: StudioViewModel = viewModel(
                    factory = StudioViewModel.factory(container),
                )
                BanglaMotionApp(viewModel = studioViewModel)
            }
        }
    }
}
