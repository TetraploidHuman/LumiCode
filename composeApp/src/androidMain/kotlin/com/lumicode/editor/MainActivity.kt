package com.lumicode.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.lumicode.editor.model.SampleWorkspace
import com.lumicode.editor.state.IdeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state = remember { IdeState(SampleWorkspace.files) }
            App(state)
        }
    }
}
