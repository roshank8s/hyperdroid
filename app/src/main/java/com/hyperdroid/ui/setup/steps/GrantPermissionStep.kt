package com.hyperdroid.ui.setup.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperdroid.R
import com.hyperdroid.model.PermissionStatus
import com.hyperdroid.ui.setup.components.SetupStepLayout

@Composable
fun GrantPermissionStep(
    permissionStatus: PermissionStatus,
    isLoading: Boolean,
    errorMessage: String?,
    onGrantPermission: () -> Unit,
    onNext: () -> Unit
) {
    val isGranted = permissionStatus == PermissionStatus.GRANTED

    SetupStepLayout(
        icon = Icons.Outlined.AdminPanelSettings,
        title = stringResource(R.string.setup_grant_permission_title),
        description = stringResource(R.string.setup_grant_permission_description),
        actionLabel = when {
            isGranted -> stringResource(R.string.setup_continue)
            isLoading -> "Granting..."
            errorMessage != null -> stringResource(R.string.setup_retry)
            else -> stringResource(R.string.setup_grant)
        },
        isActionEnabled = !isLoading,
        onAction = {
            if (isGranted) {
                onNext()
            } else {
                onGrantPermission()
            }
        }
    ) {
        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
