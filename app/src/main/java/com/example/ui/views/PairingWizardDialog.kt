package com.example.ui.views

import androidx.compose.runtime.Composable
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.p2p.PairingWizardDialog as SubPairingWizardDialog

@Composable
fun PairingWizardDialog(
    viewModel: DocAnizerViewModel,
    serverIp: String,
    serverPort: Int,
    onDismiss: () -> Unit
) {
    SubPairingWizardDialog(
        viewModel = viewModel,
        serverIp = serverIp,
        serverPort = serverPort,
        onDismiss = onDismiss
    )
}
