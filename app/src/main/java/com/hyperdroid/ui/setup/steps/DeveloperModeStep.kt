package com.hyperdroid.ui.setup.steps

import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hyperdroid.R
import com.hyperdroid.model.PermissionStatus
import com.hyperdroid.ui.setup.components.SetupStepLayout

@Composable
fun DeveloperModeStep(
    permissionStatus: PermissionStatus,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val isDone = permissionStatus != PermissionStatus.NEEDS_DEVELOPER_MODE

    SetupStepLayout(
        icon = Icons.Outlined.DeveloperMode,
        title = stringResource(R.string.setup_developer_mode_title),
        description = stringResource(R.string.setup_developer_mode_description),
        actionLabel = if (isDone) stringResource(R.string.setup_continue)
        else stringResource(R.string.setup_open_settings),
        isActionEnabled = true,
        onAction = {
            if (isDone) {
                onNext()
            } else {
                val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    )
}
