package com.hyperdroid.ui.createvm

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperdroid.data.VMRepository
import com.hyperdroid.model.OSType
import com.hyperdroid.model.VMConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CreateVMViewModel @Inject constructor(
    private val vmRepository: VMRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "CreateVMViewModel"
    }

    data class CreateVMUiState(
        val name: String = "",
        val osType: OSType = OSType.DEBIAN,
        val cpuCores: Int = 2,
        val memoryMB: Int = 2048,
        val diskSizeGB: Int = 16,
        val enableNetworking: Boolean = true,
        val imageUri: String? = null,
        val imageFileName: String? = null,
        val nameError: String? = null,
        val imageError: String? = null,
        val isSaving: Boolean = false,
        val isCreated: Boolean = false
    )

    private val _uiState = MutableStateFlow(CreateVMUiState())
    val uiState: StateFlow<CreateVMUiState> = _uiState.asStateFlow()

    fun updateName(name: String) {
        _uiState.update {
            it.copy(
                name = name.take(30),
                nameError = null
            )
        }
    }

    fun updateOsType(osType: OSType) {
        _uiState.update { it.copy(osType = osType) }
    }

    fun updateCpuCores(cores: Int) {
        _uiState.update { it.copy(cpuCores = cores.coerceIn(1, 8)) }
    }

    fun updateMemoryMB(mb: Int) {
        _uiState.update { it.copy(memoryMB = mb.coerceIn(512, 8192)) }
    }

    fun updateDiskSizeGB(gb: Int) {
        _uiState.update { it.copy(diskSizeGB = gb.coerceIn(4, 128)) }
    }

    fun updateNetworking(enabled: Boolean) {
        _uiState.update { it.copy(enableNetworking = enabled) }
    }

    fun updateImageUri(uri: String?, fileName: String?) {
        _uiState.update { it.copy(imageUri = uri, imageFileName = fileName, imageError = null) }
    }

    fun clearImage() {
        _uiState.update { it.copy(imageUri = null, imageFileName = null, imageError = null) }
    }

    fun createVM() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = "VM name cannot be empty") }
            return
        }
        if (state.osType == OSType.CUSTOM && state.imageUri == null) {
            _uiState.update { it.copy(imageError = "An OS image is required for Custom type") }
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            // If user selected an image, copy it to app storage for AVF access
            val realImagePath = state.imageUri?.let { uriString ->
                copyImageToAppStorage(uriString, state.imageFileName ?: "disk.img")
            }

            if (state.imageUri != null && realImagePath == null) {
                _uiState.update {
                    it.copy(isSaving = false, imageError = "Failed to copy image file")
                }
                return@launch
            }

            val config = VMConfig(
                name = state.name.trim(),
                osType = state.osType,
                cpuCores = state.cpuCores,
                memoryMB = state.memoryMB,
                diskSizeGB = state.diskSizeGB,
                enableNetworking = state.enableNetworking,
                imagePath = realImagePath
            )
            vmRepository.insertVM(config)
            _uiState.update { it.copy(isSaving = false, isCreated = true) }
        }
    }

    private suspend fun copyImageToAppStorage(uriString: String, fileName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val imagesDir = File(context.filesDir, "vm_images")
                imagesDir.mkdirs()

                val sanitized = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val destFile = File(imagesDir, sanitized)

                Log.i(TAG, "Copying image from $uriString to ${destFile.absolutePath}")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                } ?: run {
                    Log.e(TAG, "Failed to open input stream for URI: $uriString")
                    return@withContext null
                }

                Log.i(TAG, "Image copied: ${destFile.absolutePath} (${destFile.length()} bytes)")
                destFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy image to app storage", e)
                null
            }
        }
    }
}
