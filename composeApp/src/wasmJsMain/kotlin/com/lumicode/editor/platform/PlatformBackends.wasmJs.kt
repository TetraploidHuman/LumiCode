package com.lumicode.editor.platform

import com.lumicode.editor.dsh.installWasmDshBackend
import com.lumicode.editor.workspace.installWasmPtyBackend
import com.lumicode.editor.workspace.installWasmWorkspaceBackend

actual fun installPlatformBackends() {
    installWasmDshBackend()
    installWasmWorkspaceBackend()
    installWasmPtyBackend()
}
