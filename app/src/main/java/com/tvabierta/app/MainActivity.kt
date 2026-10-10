package com.tvabierta.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvabierta.app.ui.MainViewModel
import com.tvabierta.app.ui.browser.BrowserScreen
import com.tvabierta.app.ui.player.PlayerScreen
import com.tvabierta.app.ui.theme.Palette
import com.tvabierta.app.ui.theme.TvTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TVs have no system bars; on phones and tablets hide them for a full-screen picture.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (savedInstanceState == null) handleViewIntent(intent)
        setContent { TvTheme { TvAbiertaApp(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** Streams and playlists opened from other apps, as VLC accepts them. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        vm.openExternal(uri.toString(), intent.type)
    }
}

@Composable
private fun TvAbiertaApp(vm: MainViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    // Hoisted so the rail and the grid keep their scroll position while the player is open.
    val railState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        val current = session
        if (current == null) {
            BrowserScreen(vm, railState, gridState)
        } else {
            PlayerScreen(current, vm)
        }
    }
}
