package com.hyperdroid.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperdroid.data.PreferencesManager
import com.hyperdroid.model.PermissionStatus
import com.hyperdroid.permission.AVFChecker
import com.hyperdroid.permission.PermissionManager
import com.hyperdroid.permission.ShizukuHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val permissionManager: PermissionManager,
    private val avfChecker: AVFChecker,
    private val shizukuHelper: ShizukuHelper,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    data class SetupUiState(
        val currentStep: Int = 0,
        val totalSteps: Int = 7,
        val permissionStatus: PermissionStatus = PermissionStatus.AVF_NOT_SUPPORTED,
        val avfStatus: AVFChecker.AVFStatus? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isSetupComplete: Boolean = false
    )

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        shizukuHelper.initialize()
        checkCurrentStatus()
    }

    fun checkCurrentStatus() {
        val status = permissionManager.refreshStatus()
        val avfStatus = permissionManager.getAVFStatus()
        _uiState.update {
            it.copy(
                permissionStatus = status,
                avfStatus = avfStatus
            )
        }
    }

    fun goToStep(step: Int) {
        if (step in 0 until _uiState.value.totalSteps) {
            _uiState.update { it.copy(currentStep = step, errorMessage = null) }
            viewModelScope.launch {
                preferencesManager.setSetupStep(step)
            }
        }
    }

    fun nextStep() {
        val current = _uiState.value.currentStep
        if (current < _uiState.value.totalSteps - 1) {
            goToStep(current + 1)
        }
    }

    fun previousStep() {
        val current = _uiState.value.currentStep
        if (current > 0) {
            goToStep(current - 1)
        }
    }

    fun requestShizukuPermission() {
        shizukuHelper.requestShizukuPermission()
    }

    fun grantVMPermission() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = shizukuHelper.grantVMPermission()
            result.fold(
                onSuccess = {
                    checkCurrentStatus()
                    _uiState.update { it.copy(isLoading = false) }
                    if (permissionManager.refreshStatus() == PermissionStatus.GRANTED) {
                        nextStep()
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to grant permission"
                        )
                    }
                }
            )
        }
    }

    fun completeSetup() {
        viewModelScope.launch {
            preferencesManager.setSetupCompleted(true)
            _uiState.update { it.copy(isSetupComplete = true) }
        }
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                delay(2000)
                val previousStatus = _uiState.value.permissionStatus
                checkCurrentStatus()
                val newStatus = _uiState.value.permissionStatus

                // Auto-advance if the current step's requirement was just met
                if (newStatus != previousStatus) {
                    val targetStep = statusToMinStep(newStatus)
                    val currentStep = _uiState.value.currentStep
                    if (targetStep > currentStep) {
                        goToStep(targetStep)
                    }
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun statusToMinStep(status: PermissionStatus): Int {
        return when (status) {
            PermissionStatus.AVF_NOT_SUPPORTED -> 0
            PermissionStatus.NEEDS_DEVELOPER_MODE -> 1
            PermissionStatus.NEEDS_WIRELESS_DEBUG -> 2
            PermissionStatus.NEEDS_SHIZUKU -> 3
            PermissionStatus.NEEDS_SHIZUKU_START -> 4
            PermissionStatus.NEEDS_VM_PERMISSION -> 5
            PermissionStatus.GRANTED -> 6
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        shizukuHelper.cleanup()
    }
}
