package com.hyperdroid.ui.createvm

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperdroid.R
import com.hyperdroid.model.OSType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateVMScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateVMViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isCreated) {
        if (uiState.isCreated) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_vm_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // VM Name
            SectionLabel(stringResource(R.string.create_vm_name_label))
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.create_vm_name_hint)) },
                singleLine = true,
                isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { { Text(it) } }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // OS Type
            SectionLabel(stringResource(R.string.create_vm_os_type))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OSType.entries.forEach { os ->
                    FilterChip(
                        selected = uiState.osType == os,
                        onClick = { viewModel.updateOsType(os) },
                        label = { Text(os.label) },
                        enabled = os.isDebianBased || os == OSType.CUSTOM
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // OS Image picker
            SectionLabel(
                stringResource(
                    if (uiState.osType == OSType.CUSTOM) R.string.create_vm_image_required
                    else R.string.create_vm_image_optional
                )
            )
            ImagePickerCard(
                fileName = uiState.imageFileName,
                error = uiState.imageError,
                onImageSelected = { uri, name -> viewModel.updateImageUri(uri, name) },
                onClearImage = viewModel::clearImage
            )

            Spacer(modifier = Modifier.height(20.dp))

            // CPU Cores
            SectionLabel(stringResource(R.string.create_vm_cpu_cores, uiState.cpuCores))
            Slider(
                value = uiState.cpuCores.toFloat(),
                onValueChange = { viewModel.updateCpuCores(it.roundToInt()) },
                valueRange = 1f..8f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // RAM
            SectionLabel(stringResource(R.string.create_vm_memory, formatMemory(uiState.memoryMB)))
            Slider(
                value = uiState.memoryMB.toFloat(),
                onValueChange = { viewModel.updateMemoryMB((it / 256).roundToInt() * 256) },
                valueRange = 512f..8192f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Disk Size
            SectionLabel(stringResource(R.string.create_vm_disk_size, uiState.diskSizeGB))
            Slider(
                value = uiState.diskSizeGB.toFloat(),
                onValueChange = { viewModel.updateDiskSizeGB((it / 4).roundToInt() * 4) },
                valueRange = 4f..128f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Networking
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.create_vm_networking),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = uiState.enableNetworking,
                    onCheckedChange = viewModel::updateNetworking
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Create Button
            Button(
                onClick = viewModel::createVM,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.name.isNotBlank() && !uiState.isSaving
            ) {
                Text(stringResource(R.string.create_vm_button))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ImagePickerCard(
    fileName: String?,
    error: String?,
    onImageSelected: (uri: String, name: String) -> Unit,
    onClearImage: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission so the URI survives restarts
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val name = getFileNameFromUri(context, it) ?: "Unknown file"
            onImageSelected(it.toString(), name)
        }
    }

    val borderColor = if (error != null) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                launcher.launch(arrayOf("application/octet-stream", "*/*"))
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                if (fileName != null) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap to change",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Select ISO or IMG file",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Supports .iso, .img, .raw formats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            if (fileName != null) {
                IconButton(onClick = onClearImage, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove image",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment
}

private fun formatMemory(mb: Int): String {
    return if (mb >= 1024) "${mb / 1024} GB" else "$mb MB"
}
