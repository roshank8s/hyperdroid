package com.hyperdroid.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hyperdroid.model.VMStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new output arrives
    LaunchedEffect(uiState.outputLines.size) {
        if (uiState.outputLines.isNotEmpty()) {
            listState.animateScrollToItem(uiState.outputLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.vmName.ifEmpty { "Terminal" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = when {
                                uiState.isConnected && uiState.connectionMode == TerminalViewModel.ConnectionMode.SSH ->
                                    "SSH ${uiState.vmIp ?: ""}"
                                uiState.isConnected -> "Console"
                                uiState.vmStatus == VMStatus.RUNNING -> "Connecting..."
                                else -> "Disconnected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                uiState.isConnected -> Color(0xFF4CAF50)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.vmStatus == VMStatus.RUNNING) {
                        IconButton(onClick = viewModel::stopVM) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop VM",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(Color(0xFF1E1E1E))
        ) {
            // Console output
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(uiState.outputLines) { line ->
                    Text(
                        text = line,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFFD4D4D4)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Shortcut keys bar + command input
            CommandInputArea(
                onSendCommand = viewModel::sendCommand,
                onSendRawBytes = viewModel::sendRawBytes,
                enabled = uiState.isConnected
            )
        }
    }
}

@Composable
private fun CommandInputArea(
    onSendCommand: (String) -> Unit,
    onSendRawBytes: (ByteArray) -> Unit,
    enabled: Boolean
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var ctrlActive by remember { mutableStateOf(false) }

    val onSend = {
        onSendCommand(textFieldValue.text)
        textFieldValue = TextFieldValue("")
    }

    // Insert text at cursor position
    val insertText: (String) -> Unit = { text ->
        val current = textFieldValue
        val before = current.text.substring(0, current.selection.start)
        val after = current.text.substring(current.selection.end)
        val newText = before + text + after
        val newCursor = before.length + text.length
        textFieldValue = TextFieldValue(newText, TextRange(newCursor))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2D2D2D))
    ) {
        // Shortcut keys bar
        ShortcutBar(
            enabled = enabled,
            ctrlActive = ctrlActive,
            onCtrlToggle = { ctrlActive = !ctrlActive },
            onSendRawBytes = onSendRawBytes,
            onInsertText = insertText
        )

        // Command input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    if (ctrlActive && newValue.text.length > textFieldValue.text.length) {
                        // Ctrl mode: intercept the typed character and send as control byte
                        val typed = newValue.text.last()
                        val ctrlByte = when {
                            typed in 'a'..'z' -> (typed - 'a' + 1).toByte()
                            typed in 'A'..'Z' -> (typed - 'A' + 1).toByte()
                            else -> null
                        }
                        if (ctrlByte != null) {
                            onSendRawBytes(byteArrayOf(ctrlByte))
                        }
                        ctrlActive = false
                    } else {
                        textFieldValue = newValue
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (ctrlActive) "Ctrl+..." else "Enter command...",
                        color = if (ctrlActive) Color(0xFFFFAB40) else Color(0xFF808080),
                        fontFamily = FontFamily.Monospace
                    )
                },
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFFD4D4D4)
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                leadingIcon = {
                    Text(
                        "$",
                        color = Color(0xFF4CAF50),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            )
            IconButton(
                onClick = { onSend() },
                enabled = enabled
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled) Color(0xFF4CAF50) else Color(0xFF808080)
                )
            }
        }
    }
}

@Composable
private fun ShortcutBar(
    enabled: Boolean,
    ctrlActive: Boolean,
    onCtrlToggle: () -> Unit,
    onSendRawBytes: (ByteArray) -> Unit,
    onInsertText: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Ctrl toggle
        ShortcutKey(
            label = "Ctrl",
            enabled = enabled,
            highlighted = ctrlActive,
            onClick = onCtrlToggle
        )

        // Common Ctrl combos
        ShortcutKey("^C", enabled) { onSendRawBytes(byteArrayOf(0x03)) } // ETX - interrupt
        ShortcutKey("^D", enabled) { onSendRawBytes(byteArrayOf(0x04)) } // EOT - logout/EOF
        ShortcutKey("^Z", enabled) { onSendRawBytes(byteArrayOf(0x1A)) } // SUB - suspend
        ShortcutKey("^L", enabled) { onSendRawBytes(byteArrayOf(0x0C)) } // FF - clear screen

        // Special keys
        ShortcutKey("Esc", enabled) { onSendRawBytes(byteArrayOf(0x1B)) }
        ShortcutKey("Tab", enabled) { onSendRawBytes(byteArrayOf(0x09)) }

        // Arrow keys (ANSI escape sequences)
        ShortcutKey("\u2191", enabled) { onSendRawBytes(byteArrayOf(0x1B, 0x5B, 0x41)) } // Up
        ShortcutKey("\u2193", enabled) { onSendRawBytes(byteArrayOf(0x1B, 0x5B, 0x42)) } // Down
        ShortcutKey("\u2190", enabled) { onSendRawBytes(byteArrayOf(0x1B, 0x5B, 0x44)) } // Left
        ShortcutKey("\u2192", enabled) { onSendRawBytes(byteArrayOf(0x1B, 0x5B, 0x43)) } // Right

        // Hard-to-type characters
        ShortcutKey("/", enabled) { onInsertText("/") }
        ShortcutKey("-", enabled) { onInsertText("-") }
        ShortcutKey("|", enabled) { onInsertText("|") }
        ShortcutKey("~", enabled) { onInsertText("~") }
        ShortcutKey("_", enabled) { onInsertText("_") }
        ShortcutKey(".", enabled) { onInsertText(".") }
    }
}

@Composable
private fun ShortcutKey(
    label: String,
    enabled: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(4.dp),
        color = when {
            highlighted -> Color(0xFFFFAB40)
            enabled -> Color(0xFF3C3C3C)
            else -> Color(0xFF2A2A2A)
        },
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 36.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = when {
                    highlighted -> Color(0xFF1E1E1E)
                    enabled -> Color(0xFFD4D4D4)
                    else -> Color(0xFF606060)
                }
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
