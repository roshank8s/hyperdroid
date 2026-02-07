package com.hyperdroid.ui.terminal

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hyperdroid.data.VMRepository
import com.hyperdroid.model.VMStatus
import com.hyperdroid.vm.VMEngine
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vmEngine: VMEngine,
    private val vmRepository: VMRepository
) : ViewModel() {

    companion object {
        private const val TAG = "TerminalVM"
        private const val SSH_PORT = 22
        private const val SSH_USER = "root"
        private const val SSH_PASSWORD = "hyperdroid"
        private const val SSH_CONNECT_TIMEOUT = 10_000
        private const val SSH_RETRY_DELAY = 3_000L
        private const val SSH_MAX_RETRIES = 20 // ~60 seconds total
    }

    private val vmId: String = checkNotNull(savedStateHandle["vmId"])

    data class TerminalUiState(
        val vmName: String = "",
        val vmStatus: VMStatus = VMStatus.STOPPED,
        val vmIp: String? = null,
        val outputLines: List<String> = emptyList(),
        val isConnected: Boolean = false,
        val connectionMode: ConnectionMode = ConnectionMode.NONE
    )

    enum class ConnectionMode { NONE, CONSOLE, SSH }

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var readerJob: Job? = null
    private var consoleOutputPfd: ParcelFileDescriptor? = null
    private var consoleInputPfd: ParcelFileDescriptor? = null
    private var consoleWriter: OutputStreamWriter? = null

    // SSH state
    private var sshSession: Session? = null
    private var sshChannel: ChannelShell? = null
    private var sshOutputStream: OutputStream? = null

    private val maxLines = 1000

    init {
        loadVMInfo()
        connect()
    }

    private fun loadVMInfo() {
        viewModelScope.launch {
            vmRepository.getVMById(vmId)?.let { vm ->
                _uiState.update {
                    it.copy(vmName = vm.name, vmStatus = vm.status)
                }
            }
        }
    }

    private fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            // Try console first (works on debuggable builds)
            if (tryConsoleConnect()) return@launch

            // Fall back to SSH
            appendLine("[Console not available on this device, connecting via SSH...]")
            connectSSH()
        }
    }

    private fun tryConsoleConnect(): Boolean {
        try {
            consoleOutputPfd = vmEngine.getConsoleOutput(vmId)
            if (consoleOutputPfd == null) return false

            try {
                consoleInputPfd = vmEngine.getConsoleInput(vmId)
            } catch (_: Exception) { }

            _uiState.update { it.copy(isConnected = true, connectionMode = ConnectionMode.CONSOLE) }
            consoleInputPfd?.let {
                consoleWriter = OutputStreamWriter(
                    ParcelFileDescriptor.AutoCloseOutputStream(it)
                )
            }
            if (consoleWriter == null) {
                // Have output but no input — not fully usable, try SSH instead
                try { consoleOutputPfd?.close() } catch (_: Exception) {}
                consoleOutputPfd = null
                return false
            }
            startConsoleReading()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun startConsoleReading() {
        readerJob = viewModelScope.launch(Dispatchers.IO) {
            val pfd = consoleOutputPfd ?: return@launch
            try {
                val reader = BufferedReader(
                    InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd))
                )
                while (isActive) {
                    val line = reader.readLine() ?: break
                    appendLine(line)
                }
            } catch (e: Exception) {
                if (isActive) appendLine("[Connection lost: ${e.message}]")
            } finally {
                _uiState.update { it.copy(isConnected = false, connectionMode = ConnectionMode.NONE) }
            }
        }
    }

    private suspend fun connectSSH() {
        // Wait for VM to get an IP and SSH to be ready
        var vmIp: String? = null
        for (attempt in 1..SSH_MAX_RETRIES) {
            vmIp = vmEngine.discoverVMIp()
            if (vmIp != null) {
                _uiState.update { it.copy(vmIp = vmIp) }
                appendLine("[VM IP: $vmIp]")

                // Try SSH connection
                try {
                    val jsch = JSch()
                    val session = jsch.getSession(SSH_USER, vmIp, SSH_PORT)
                    session.setPassword(SSH_PASSWORD)
                    session.setConfig("StrictHostKeyChecking", "no")
                    session.setConfig("PreferredAuthentications", "password")
                    session.connect(SSH_CONNECT_TIMEOUT)

                    val channel = session.openChannel("shell") as ChannelShell
                    channel.setPtyType("xterm", 120, 40, 0, 0)

                    sshSession = session
                    sshChannel = channel
                    sshOutputStream = channel.outputStream

                    channel.connect(SSH_CONNECT_TIMEOUT)

                    _uiState.update { it.copy(isConnected = true, connectionMode = ConnectionMode.SSH) }
                    appendLine("[Connected via SSH to $vmIp]")
                    Log.i(TAG, "SSH connected to $vmIp")

                    startSSHReading()
                    return
                } catch (e: Exception) {
                    Log.d(TAG, "SSH attempt $attempt failed: ${e.message}")
                    if (attempt < SSH_MAX_RETRIES) {
                        appendLine("[SSH connecting... (attempt $attempt)]")
                    }
                }
            } else {
                if (attempt == 1) {
                    appendLine("[Waiting for VM network...]")
                } else if (attempt % 5 == 0) {
                    appendLine("[Still waiting for VM network... (attempt $attempt)]")
                }
            }
            delay(SSH_RETRY_DELAY)
        }

        appendLine("[Failed to connect via SSH after ${SSH_MAX_RETRIES} attempts]")
        if (vmIp != null) {
            appendLine("[VM IP: $vmIp — SSH may not be available yet]")
        } else {
            appendLine("[Could not discover VM IP address]")
        }
    }

    private fun startSSHReading() {
        readerJob = viewModelScope.launch(Dispatchers.IO) {
            val channel = sshChannel ?: return@launch
            try {
                val inputStream = channel.inputStream
                val buffer = ByteArray(4096)
                val lineBuilder = StringBuilder()

                while (isActive && channel.isConnected) {
                    val available = inputStream.available()
                    if (available > 0) {
                        val n = inputStream.read(buffer, 0, minOf(available, buffer.size))
                        if (n <= 0) break
                        val text = String(buffer, 0, n)
                        // Process character by character for line handling
                        for (ch in text) {
                            when (ch) {
                                '\n' -> {
                                    appendLine(lineBuilder.toString())
                                    lineBuilder.clear()
                                }
                                '\r' -> { } // ignore CR
                                else -> lineBuilder.append(ch)
                            }
                        }
                    } else {
                        // Flush partial line if no more data coming
                        if (lineBuilder.isNotEmpty()) {
                            appendLine(lineBuilder.toString())
                            lineBuilder.clear()
                        }
                        delay(50)
                    }
                }
            } catch (e: Exception) {
                if (isActive) appendLine("[SSH connection lost: ${e.message}]")
            } finally {
                _uiState.update { it.copy(isConnected = false, connectionMode = ConnectionMode.NONE) }
            }
        }
    }

    fun sendCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (_uiState.value.connectionMode) {
                    ConnectionMode.CONSOLE -> {
                        consoleWriter?.let { writer ->
                            writer.write(command + "\n")
                            writer.flush()
                        }
                    }
                    ConnectionMode.SSH -> {
                        sshOutputStream?.let { out ->
                            out.write((command + "\n").toByteArray())
                            out.flush()
                        }
                    }
                    ConnectionMode.NONE -> {
                        appendLine("[Not connected]")
                    }
                }
            } catch (e: Exception) {
                appendLine("[Send failed: ${e.message}]")
            }
        }
    }

    fun sendRawBytes(bytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (_uiState.value.connectionMode) {
                    ConnectionMode.CONSOLE -> {
                        consoleWriter?.let { writer ->
                            writer.write(String(bytes))
                            writer.flush()
                        }
                    }
                    ConnectionMode.SSH -> {
                        sshOutputStream?.let { out ->
                            out.write(bytes)
                            out.flush()
                        }
                    }
                    ConnectionMode.NONE -> { }
                }
            } catch (_: Exception) { }
        }
    }

    fun stopVM() {
        viewModelScope.launch {
            vmEngine.stopVM(vmId)
            _uiState.update { it.copy(vmStatus = VMStatus.STOPPED) }
        }
    }

    private fun appendLine(line: String) {
        _uiState.update { state ->
            val lines = state.outputLines.toMutableList()
            lines.add(line)
            if (lines.size > maxLines) {
                lines.removeAt(0)
            }
            state.copy(outputLines = lines)
        }
    }

    override fun onCleared() {
        super.onCleared()
        readerJob?.cancel()
        // Console cleanup
        try { consoleWriter?.close() } catch (_: Exception) {}
        try { consoleOutputPfd?.close() } catch (_: Exception) {}
        // SSH cleanup
        try { sshChannel?.disconnect() } catch (_: Exception) {}
        try { sshSession?.disconnect() } catch (_: Exception) {}
    }
}
