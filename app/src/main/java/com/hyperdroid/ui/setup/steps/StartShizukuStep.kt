package com.hyperdroid.ui.setup.steps

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hyperdroid.R
import com.hyperdroid.model.PermissionStatus
import com.hyperdroid.ui.setup.components.SetupStepLayout

@Composable
fun StartShizukuStep(
    permissionStatus: PermissionStatus,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val isRunning = permissionStatus != PermissionStatus.NEEDS_SHIZUKU_START &&
        permissionStatus != PermissionStatus.NEEDS_SHIZUKU

    SetupStepLayout(
        icon = Icons.Outlined.PlayArrow,
        title = stringResource(R.string.setup_start_shizuku_title),
        description = stringResource(R.string.setup_start_shizuku_description),
        actionLabel = if (isRunning) stringResource(R.string.setup_continue)
        else stringResource(R.string.setup_open_shizuku),
        isActionEnabled = true,
        onAction = {
            if (isRunning) {
                onNext()
            } else {
                val intent = context.packageManager
                    .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    )
}
