package com.hyperdroid.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperdroid.data.VMRepository
import com.hyperdroid.model.PermissionStatus
import com.hyperdroid.model.VMConfig
import com.hyperdroid.model.VMStatus
import com.hyperdroid.permission.AVFChecker
import com.hyperdroid.permission.PermissionManager
import com.hyperdroid.service.VMServiceManager
import com.hyperdroid.vm.VMEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vmRepository: VMRepository,
    private val permissionManager: PermissionManager,
    private val avfChecker: AVFChecker,
    private val vmEngine: VMEngine,
    private val vmServiceManager: VMServiceManager
) : ViewModel() {

    data class HomeUiState(
        val vms: List<VMConfig> = emptyList(),
        val permissionStatus: PermissionStatus = PermissionStatus.GRANTED,
        val avfStatus: AVFChecker.AVFStatus? = null,
        val vmErrors: Map<String, String> = emptyMap(),
        val isLoading: Boolean = true
    )

    // One-shot error events for Snackbar
    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    init {
        permissionManager.refreshStatus()
        // Reset stale STARTING/RUNNING statuses from previous app sessions
        viewModelScope.launch {
            vmRepository.getAllVMsOnce().forEach { vm ->
                if (vm.status == VMStatus.STARTING || vm.status == VMStatus.RUNNING) {
                    vmRepository.updateVM(vm.copy(status = VMStatus.STOPPED))
                }
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        vmRepository.getAllVMs(),
        permissionManager.status,
        vmEngine.lastErrors
    ) { vms, permStatus, errors ->
        HomeUiState(
            vms = vms,
            permissionStatus = permStatus,
            avfStatus = avfChecker.checkAVFSupport(),
            vmErrors = errors,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun startVM(id: String) {
        viewModelScope.launch {
            vmRepository.getVMById(id)?.let { vm ->
                vmRepository.updateVM(vm.copy(
                    status = VMStatus.STARTING,
                    lastStartedAt = System.currentTimeMillis()
                ))
                vmServiceManager.notifyVMStarted(vm.id, vm.name)
                val result = vmEngine.createAndStartVM(vm)
                if (result.isFailure) {
                    vmServiceManager.notifyVMStopped(vm.name)
                    val errorMsg = vmEngine.getLastError(vm.id)
                        ?: result.exceptionOrNull()?.message
                        ?: "Unknown error"
                    _errorEvents.emit("${vm.name}: $errorMsg")
                }
            }
        }
    }

    fun stopVM(id: String) {
        viewModelScope.launch {
            val vm = vmRepository.getVMById(id)
            vmEngine.stopVM(id)
            vm?.let { vmServiceManager.notifyVMStopped(it.name) }
        }
    }

    fun deleteVM(vm: VMConfig) {
        viewModelScope.launch {
            vmEngine.deleteVM(vm.id)
            // Delete copied disk image from app storage
            vm.imagePath?.let { path ->
                try {
                    val file = java.io.File(path)
                    if (file.exists() && file.absolutePath.contains("vm_images")) {
                        file.delete()
                        Log.i("HomeViewModel", "Deleted disk image: $path")
                    }
                } catch (e: Exception) {
                    Log.w("HomeViewModel", "Failed to delete disk image: $path", e)
                }
            }
            vmRepository.deleteVM(vm)
        }
    }

    fun clearError(vmId: String) {
        vmEngine.clearError(vmId)
    }
}
