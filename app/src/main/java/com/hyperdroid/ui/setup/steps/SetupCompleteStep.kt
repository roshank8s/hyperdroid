package com.hyperdroid.ui.setup.steps

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hyperdroid.R
import com.hyperdroid.ui.setup.components.SetupStepLayout

@Composable
fun SetupCompleteStep(
    onFinish: () -> Unit
) {
    SetupStepLayout(
        icon = Icons.Outlined.CheckCircle,
        title = stringResource(R.string.setup_complete_title),
        description = stringResource(R.string.setup_complete_description),
        actionLabel = stringResource(R.string.setup_finish),
        onAction = onFinish
    )
}
