package com.hyperdroid.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperdroid.ui.setup.components.StepIndicator
import com.hyperdroid.ui.setup.steps.DeveloperModeStep
import com.hyperdroid.ui.setup.steps.GrantPermissionStep
import com.hyperdroid.ui.setup.steps.InstallShizukuStep
import com.hyperdroid.ui.setup.steps.SetupCompleteStep
import com.hyperdroid.ui.setup.steps.StartShizukuStep
import com.hyperdroid.ui.setup.steps.WelcomeStep
import com.hyperdroid.ui.setup.steps.WirelessDebuggingStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSetupComplete) {
        if (uiState.isSetupComplete) onSetupComplete()
    }

    LaunchedEffect(Unit) { viewModel.startPolling() }
    DisposableEffect(Unit) { onDispose { viewModel.stopPolling() } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Setup") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StepIndicator(
                currentStep = uiState.currentStep,
                totalSteps = uiState.totalSteps
            )

            when (uiState.currentStep) {
                0 -> WelcomeStep(
                    avfStatus = uiState.avfStatus,
                    onNext = { viewModel.nextStep() }
                )
                1 -> DeveloperModeStep(
                    permissionStatus = uiState.permissionStatus,
                    onNext = { viewModel.nextStep() }
                )
                2 -> WirelessDebuggingStep(
                    permissionStatus = uiState.permissionStatus,
                    onNext = { viewModel.nextStep() }
                )
                3 -> InstallShizukuStep(
                    permissionStatus = uiState.permissionStatus,
                    onNext = { viewModel.nextStep() }
                )
                4 -> StartShizukuStep(
                    permissionStatus = uiState.permissionStatus,
                    onNext = { viewModel.nextStep() }
                )
                5 -> GrantPermissionStep(
                    permissionStatus = uiState.permissionStatus,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    onGrantPermission = { viewModel.grantVMPermission() },
                    onNext = { viewModel.nextStep() }
                )
                6 -> SetupCompleteStep(
                    onFinish = { viewModel.completeSetup() }
                )
            }
        }
    }
}
