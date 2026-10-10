package com.eplik.ioniq5.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eplik.ioniq5.R
import com.eplik.ioniq5.connection.ConnectionStatus
import com.eplik.ioniq5.ui.screens.statusText

private const val MAX_CLIPBOARD_CHARS = 200_000

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(
    onConnectionClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val traffic by viewModel.traffic.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val capturing by viewModel.capturing.collectAsStateWithLifecycle()
    val captureFile by viewModel.captureFile.collectAsStateWithLifecycle()
    var note by rememberSaveable { mutableStateOf("") }

    fun share(file: java.io.File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    }

    var input by rememberSaveable { mutableStateOf("") }
    var ecu by rememberSaveable { mutableStateOf("7E5") }
    var didFrom by rememberSaveable { mutableStateOf("E000") }
    var didTo by rememberSaveable { mutableStateOf("E0FF") }
    val connected = status is ConnectionStatus.Connected
    val busy = progress != null
    val listState = rememberLazyListState()

    // Diagnostika ajal taustal pollimist ei toimu: konsooli käsud ja logi ei segune
    DisposableEffect(Unit) {
        viewModel.connection.pausePolling()
        onDispose { viewModel.connection.resumePolling() }
    }

    LaunchedEffect(traffic.size) {
        if (traffic.isNotEmpty()) listState.scrollToItem(traffic.size - 1)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(statusText(status), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onConnectionClick) {
                Text(stringResource(if (connected) R.string.connection_title else R.string.connect))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = Mono,
                placeholder = { Text("22E011 → ECU", style = Mono) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.send(input, ecu) }),
            )
            Button(onClick = { viewModel.send(input, ecu) }, enabled = connected && !busy) { Text(stringResource(R.string.send)) }
        }

        // Arenduslogi
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.capture_note)) },
            )
            if (capturing) {
                OutlinedButton(onClick = {
                    viewModel.addNote(note)
                    note = ""
                }) { Text(stringResource(R.string.capture_add_note)) }
                Button(onClick = { viewModel.stopCapture() }) { Text(stringResource(R.string.capture_stop)) }
            } else {
                Button(
                    onClick = {
                        viewModel.startCapture(note)
                        note = ""
                    },
                    enabled = connected && !busy,
                ) { Text(stringResource(R.string.capture_start)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HexField(ecu, { ecu = it }, "ECU", Modifier.width(88.dp))
            HexField(didFrom, { didFrom = it }, "DID", Modifier.width(96.dp))
            HexField(didTo, { didTo = it }, "…", Modifier.width(96.dp))
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.scanEcus() }, enabled = connected && !busy) { Text(stringResource(R.string.scan_ecus)) }
            OutlinedButton(onClick = { viewModel.scanDids(ecu, didFrom, didTo) }, enabled = connected && !busy) {
                Text(stringResource(R.string.scan_dids))
            }
            OutlinedButton(onClick = { viewModel.runDiscovery() }, enabled = connected && !busy) {
                Text(stringResource(R.string.discover_stage3))
            }
            if (busy) Button(onClick = { viewModel.stop() }) { Text(stringResource(R.string.stop)) }
            if (captureFile != null && !capturing) {
                Button(onClick = { captureFile?.let { share(it) } }) { Text(stringResource(R.string.capture_share)) }
            }
            OutlinedButton(
                onClick = { share(viewModel.exportLog()) },
                enabled = traffic.isNotEmpty(),
            ) { Text(stringResource(R.string.share_log)) }
            OutlinedButton(
                onClick = {
                    val text = viewModel.logText().takeLast(MAX_CLIPBOARD_CHARS)
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("ioniq5-log", text))
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                },
                enabled = traffic.isNotEmpty(),
            ) { Text(stringResource(R.string.copy)) }
            OutlinedButton(onClick = { viewModel.clear() }) { Text(stringResource(R.string.clear)) }
        }

        progress?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (results.isNotEmpty()) {
            Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                results.forEach { Text(it, style = Mono, color = MaterialTheme.colorScheme.primary) }
            }
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            items(traffic) { entry ->
                SelectionContainer {
                    Column(Modifier.padding(vertical = 2.dp)) {
                        Text("> ${entry.command}", style = Mono, color = MaterialTheme.colorScheme.primary)
                        entry.response.forEach { Text(it, style = Mono) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HexField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.uppercase().filter { c -> c in '0'..'9' || c in 'A'..'F' }.take(4)) },
        modifier = modifier,
        singleLine = true,
        textStyle = Mono,
        label = { Text(label) },
    )
}
