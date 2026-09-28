package com.lumicode.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.lumicode.editor.model.SampleWorkspace
import com.lumicode.editor.platform.AndroidPrefsContext
import com.lumicode.editor.state.IdeState
import com.lumicode.editor.ui.theme.RlSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPrefsContext.app = applicationContext
        RlSettings.load()
        setContent {
            val state = remember {
                IdeState(SampleWorkspace.files).also { it.loadPanelPrefs() }
            }
            App(state)
        }
    }
}
