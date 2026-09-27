package com.lumicode.editor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lumicode.editor.resources.Res
import com.lumicode.editor.resources.jbmono_bold
import com.lumicode.editor.resources.jbmono_medium
import com.lumicode.editor.resources.jbmono_regular
import com.lumicode.editor.resources.noto_sans_bold
import com.lumicode.editor.resources.noto_sans_regular
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font

@OptIn(ExperimentalResourceApi::class)
@Composable
actual fun InstallArchiveFonts() {
    val sans = FontFamily(
        Font(Res.font.noto_sans_regular, FontWeight.Normal),
        Font(Res.font.noto_sans_bold, FontWeight.Bold),
    )
    val mono = FontFamily(
        Font(Res.font.jbmono_regular, FontWeight.Normal),
        Font(Res.font.jbmono_medium, FontWeight.Medium),
        Font(Res.font.jbmono_bold, FontWeight.Bold),
    )
    SideEffect {
        RlFonts.sans = sans
        RlFonts.mono = mono
    }
}
