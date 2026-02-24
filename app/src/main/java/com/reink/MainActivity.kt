package com.reink

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reink.ui.navigation.ReInkNavGraph
import com.reink.ui.theme.ReInkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val _volumeKeyEvents = MutableSharedFlow<VolumeKey>(extraBufferCapacity = 1)
    val volumeKeyEvents = _volumeKeyEvents.asSharedFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReInkTheme {
                ReInkNavGraph(volumeKeyEvents = volumeKeyEvents)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                _volumeKeyEvents.tryEmit(VolumeKey.DOWN)
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                _volumeKeyEvents.tryEmit(VolumeKey.UP)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

enum class VolumeKey { UP, DOWN }
