package com.hyperdroid.ui.setup.steps

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hyperdroid.R
import com.hyperdroid.model.PermissionStatus
import com.hyperdroid.ui.setup.components.SetupStepLayout

@Composable
fun InstallShizukuStep(
    permissionStatus: PermissionStatus,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val isInstalled = permissionStatus != PermissionStatus.NEEDS_SHIZUKU

    SetupStepLayout(
        icon = Icons.Outlined.Security,
        title = stringResource(R.string.setup_install_shizuku_title),
        description = stringResource(R.string.setup_install_shizuku_description),
        actionLabel = if (isInstalled) stringResource(R.string.setup_continue)
        else stringResource(R.string.setup_install),
        isActionEnabled = true,
        onAction = {
            if (isInstalled) {
                onNext()
            } else {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=moe.shizuku.privileged.api")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Play Store not available, open browser
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                    )
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(webIntent)
                }
            }
        }
    )
}
