package com.hyperdroid.ui.setup.steps

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperdroid.R
import com.hyperdroid.permission.AVFChecker
import com.hyperdroid.ui.setup.components.SetupStepLayout
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.ui.Alignment

@Composable
fun WelcomeStep(
    avfStatus: AVFChecker.AVFStatus?,
    onNext: () -> Unit
) {
    SetupStepLayout(
        icon = Icons.Outlined.Cloud,
        title = stringResource(R.string.setup_welcome_title),
        description = stringResource(R.string.setup_welcome_description),
        actionLabel = stringResource(R.string.setup_get_started),
        isActionEnabled = avfStatus?.isSupported == true,
        onAction = onNext
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        if (avfStatus != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (avfStatus.isSupported)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (avfStatus.isSupported)
                            Icons.Default.CheckCircle
                        else
                            Icons.Default.Error,
                        contentDescription = null,
                        tint = if (avfStatus.isSupported)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (avfStatus.isSupported)
                            stringResource(R.string.setup_avf_supported)
                        else
                            avfStatus.failureReason
                                ?: stringResource(R.string.setup_avf_not_supported),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
