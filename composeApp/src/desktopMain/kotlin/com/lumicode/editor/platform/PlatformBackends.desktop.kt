package com.lumicode.editor.platform

import com.lumicode.editor.dsh.installDesktopDshBackend
import com.lumicode.editor.workspace.installDesktopPtyBackend
import com.lumicode.editor.workspace.installDesktopWorkspaceBackend

actual fun installPlatformBackends() {
    installDesktopWorkspaceBackend()
    installDesktopDshBackend()
    installDesktopPtyBackend()
}
