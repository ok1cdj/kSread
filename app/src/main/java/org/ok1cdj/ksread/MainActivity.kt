package org.ok1cdj.ksread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.ok1cdj.ksread.ui.KSreadTheme
import org.ok1cdj.ksread.ui.LibraryScreen
import org.ok1cdj.ksread.ui.MainViewModel
import org.ok1cdj.ksread.ui.ReaderScreen
import org.ok1cdj.ksread.ui.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KSreadTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val vm: MainViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    // Keep the e-ink panel awake while a book is auto-advancing.
    val view = LocalView.current
    view.keepScreenOn = state.isPlaying

    when (state.screen) {
        Screen.LIBRARY -> LibraryScreen(vm, state)
        Screen.READER -> ReaderScreen(vm, state)
    }
}
