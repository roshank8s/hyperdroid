package com.hyperdroid.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hyperdroid.R
import com.hyperdroid.model.VMConfig
import com.hyperdroid.model.VMStatus

@Composable
fun VMCard(
    vmConfig: VMConfig,
    errorMessage: String? = null,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: name + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = vmConfig.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(vmConfig.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Specs row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SpecLabel(label = vmConfig.osType.label)
                SpecLabel(label = "${vmConfig.cpuCores} CPU")
                SpecLabel(
                    label = if (vmConfig.memoryMB >= 1024)
                        "${vmConfig.memoryMB / 1024} GB RAM"
                    else
                        "${vmConfig.memoryMB} MB RAM"
                )
                SpecLabel(label = "${vmConfig.diskSizeGB} GB")
            }

            // Error message
            if (vmConfig.status == VMStatus.ERROR && errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when (vmConfig.status) {
                    VMStatus.STOPPED, VMStatus.ERROR -> {
                        IconButton(onClick = onStart) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Start",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    VMStatus.RUNNING -> {
                        IconButton(onClick = onOpenTerminal) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = "Terminal",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onStop) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    VMStatus.STARTING -> {
                        IconButton(onClick = onStop, enabled = false) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    VMStatus.PAUSED -> {
                        IconButton(onClick = onStart) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onStop) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { showDeleteDialog = true },
                    enabled = vmConfig.status != VMStatus.RUNNING && vmConfig.status != VMStatus.STARTING
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = if (vmConfig.status != VMStatus.RUNNING && vmConfig.status != VMStatus.STARTING)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.vm_delete_confirm_title)) },
            text = { Text(stringResource(R.string.vm_delete_confirm_message, vmConfig.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text(
                        stringResource(R.string.vm_delete_confirm_yes),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.vm_delete_confirm_no))
                }
            }
        )
    }
}

@Composable
private fun StatusChip(status: VMStatus) {
    val (text, color) = when (status) {
        VMStatus.RUNNING -> stringResource(R.string.vm_status_running) to MaterialTheme.colorScheme.primary
        VMStatus.STOPPED -> stringResource(R.string.vm_status_stopped) to MaterialTheme.colorScheme.outline
        VMStatus.STARTING -> stringResource(R.string.vm_status_starting) to MaterialTheme.colorScheme.tertiary
        VMStatus.PAUSED -> stringResource(R.string.vm_status_paused) to MaterialTheme.colorScheme.secondary
        VMStatus.ERROR -> stringResource(R.string.vm_status_error) to MaterialTheme.colorScheme.error
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}

@Composable
private fun SpecLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
