package com.yg.moduread

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yg.moduread.data.ExternalOpen
import com.yg.moduread.ui.AppTheme
import com.yg.moduread.ui.ReaderRoot
import com.yg.moduread.ui.ReaderViewModel

class MainActivity : ComponentActivity() {

    private val vm: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 冷启动时来自「用墨读打开」的 uri，先交给 ViewModel，等界面首帧后再消费
        vm.pendingExternalUri = ExternalOpen.uriOf(intent)
        setContent {
            val prefs by vm.readerPrefs.collectAsStateWithLifecycle()
            AppTheme(mode = prefs.themeMode) {
                ReaderRoot(vm)
            }
        }
    }

    /** 已有实例时再次被打开：直接切文档，不重建 Activity。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ExternalOpen.uriOf(intent)?.let(vm::open)
    }

}
