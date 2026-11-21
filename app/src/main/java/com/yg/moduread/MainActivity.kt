package com.yg.moduread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.yg.moduread.ui.AppTheme
import com.yg.moduread.ui.ReaderRoot
import com.yg.moduread.ui.ReaderViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val vm: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by vm.readerPrefs.collectAsStateWithLifecycle()
            AppTheme(mode = prefs.themeMode) {
                ReaderRoot(vm)
            }
        }
    }
}
