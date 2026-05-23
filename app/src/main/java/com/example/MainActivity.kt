package com.example

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.DownloadItem
import com.example.data.DownloadRepository
import com.example.ui.DownloadUiState
import com.example.ui.DownloadViewModel
import com.example.ui.DownloadViewModelFactory
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: DownloadViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Room DB & ViewModel initialization
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = DownloadRepository(database.downloadDao())
        val factory = DownloadViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[DownloadViewModel::class.java]

        // Start real-time progress tracker for active items
        viewModel.startTrackingProgress(this)

        // Handle cold start intent
        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = VortexBackground
                ) { innerPadding ->
                    VortexAppScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                // Regex to cleanly extract URLs out of arbitrary text body
                val urlPattern = "(https?://[^\\s]+)".toRegex()
                val match = urlPattern.find(sharedText)
                val targetUrl = match?.value ?: sharedText.trim()
                viewModel.onUrlChange(targetUrl)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VortexAppScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val urlState by viewModel.urlInput.collectAsStateWithLifecycle()
    val qualityState by viewModel.selectedQuality.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadsList by viewModel.downloadHistory.collectAsStateWithLifecycle()
    val customApiUrlState by viewModel.customApiUrl.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current

    var showServerSettings by remember { mutableStateOf(false) }

    // Categorized downloads
    val activeDownloads = remember(downloadsList) {
        downloadsList.filter { it.status == "DOWNLOADING" || it.status == "PENDING" }
    }
    val finishedDownloads = remember(downloadsList) {
        downloadsList.filter { it.status == "COMPLETED" || it.status == "FAILED" }
    }

    // Dynamic gradient accents behind Header for Atmospheric depth
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            VortexBackground,
            Color(0xFFFCF4F0),
            VortexBackground
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Top Component
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "VORTEX",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = VortexPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Pulsing / glowing indicator
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(VortexPrimary)
                             )
                        }
                        Text(
                            text = stringResource(R.string.app_description),
                            fontSize = 12.sp,
                            color = VortexTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Server endpoint settings trigger
                    IconButton(
                        onClick = { showServerSettings = true },
                        modifier = Modifier
                            .background(VortexSurface, CircleShape)
                            .border(1.dp, Color(0xFFCAC4D0), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = VortexPrimary
                        )
                    }
                }
            }

            // Central URL Input Card Block
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = VortexSurface),
                    shape = RoundedCornerShape(24.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFFEADBFF), Color(0xFFCAC4D0))))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Enlace del Video",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = VortexTextPrimary
                            )

                            // Paste helper shortcut
                            TextButton(
                                onClick = {
                                    clipboardManager.getText()?.text?.let { text ->
                                        viewModel.onUrlChange(text)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Pegar",
                                    modifier = Modifier.size(16.dp),
                                    tint = VortexPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pegar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VortexPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // TextField Box design
                        OutlinedTextField(
                            value = urlState,
                            onValueChange = { viewModel.onUrlChange(it) },
                            placeholder = { Text(stringResource(R.string.input_hint), color = Color(0xFF79747E)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = VortexPrimary,
                                unfocusedBorderColor = Color(0xFFCAC4D0),
                                focusedTextColor = VortexTextPrimary,
                                unfocusedTextColor = VortexTextPrimary
                            ),
                            trailingIcon = {
                                if (urlState.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onUrlChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Borrar",
                                            tint = VortexTertiary
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { keyboardController?.hide() }
                            )
                        )

                        // Platform Detector Indicator Badge
                        AnimatedVisibility(
                            visible = urlState.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            val platform = viewModel.detectPlatform(urlState)
                            val badgeColor = when (platform) {
                                "youtube" -> VortexYouTube
                                "tiktok" -> VortexTikTok
                                "instagram" -> VortexInstagram
                                else -> VortexSecondary
                            }
                            val badgeLabel = when (platform) {
                                "youtube" -> "YouTube detectado"
                                "tiktok" -> "TikTok detectado"
                                "instagram" -> "Instagram detectado"
                                else -> "Enlace genérico"
                            }
                            val badgeIcon = when (platform) {
                                "youtube" -> Icons.Default.PlayCircleFilled
                                "tiktok" -> Icons.Default.MusicNote
                                "instagram" -> Icons.Default.CameraAlt
                                else -> Icons.Default.Link
                            }

                            Row(
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = badgeLabel,
                                    tint = badgeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = badgeLabel,
                                    fontSize = 11.sp,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Quality Selection Grid block
            item {
                Column {
                    Text(
                        text = stringResource(R.string.quality_label),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Compact horizontal/vertical grid of selectors with descriptions
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QualityCard(
                                title = "1080p",
                                subtitle = "Full HD MP4",
                                desc = "Detalle cristalino",
                                icon = Icons.Default.Hd,
                                isSelected = qualityState == "1080",
                                onClick = { viewModel.selectQuality("1080") },
                                modifier = Modifier.weight(1f)
                            )
                            QualityCard(
                                title = "720p",
                                subtitle = "HD MP4",
                                desc = "Excelente equilibrio",
                                icon = Icons.Default.HighQuality,
                                isSelected = qualityState == "720",
                                onClick = { viewModel.selectQuality("720") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QualityCard(
                                title = "480p",
                                subtitle = "Ligeros SD",
                                desc = "Muy económico",
                                icon = Icons.Default.VideoSettings,
                                isSelected = qualityState == "480",
                                onClick = { viewModel.selectQuality("480") },
                                modifier = Modifier.weight(1f)
                            )
                            QualityCard(
                                title = "Audio",
                                subtitle = "MP3 Audio",
                                desc = "Solo sonido, 320k",
                                icon = Icons.Default.Audiotrack,
                                isSelected = qualityState == "audio",
                                onClick = { viewModel.selectQuality("audio") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Download Trigger Action Button
            item {
                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.triggerDownload(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VortexPrimary,
                        disabledContainerColor = Color(0xFFEADDFF)
                    ),
                    enabled = urlState.isNotEmpty() && uiState !is DownloadUiState.Loading,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    if (uiState is DownloadUiState.Loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Obteniendo video...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Descargar",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.download_button),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Global UI state indicator alerts (Toast-like inline messages)
            item {
                AnimatedVisibility(
                    visible = uiState !is DownloadUiState.Idle,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    when (val state = uiState) {
                        is DownloadUiState.Success -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDF7ED)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFFC3E6CB), Color(0xFFEDF7ED))))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Éxito",
                                        tint = VortexSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.message,
                                        fontSize = 13.sp,
                                        color = VortexSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        is DownloadUiState.Error -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF0F2)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFFF5C6CB), Color(0xFFFDF0F2))))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Error",
                                        tint = VortexTertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.message,
                                        fontSize = 13.sp,
                                        color = VortexTertiary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }

            // Real-time Active Downloads Section
            if (activeDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.active_downloads),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(activeDownloads, key = { "active_${it.id}" }) { item ->
                    ActiveDownloadRow(item = item, onCancel = { viewModel.deleteHistoryItem(it) })
                }
            }

            // Completed / Historical Downloaded Items Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.download_history),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary
                    )

                    if (finishedDownloads.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearAllHistory() },
                            colors = ButtonDefaults.textButtonColors(contentColor = VortexTextSecondary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.clear_history),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.clear_history), fontSize = 12.sp)
                        }
                    }
                }
            }

            if (finishedDownloads.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = "Vacío",
                                tint = Color(0xFF2C2A4D),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.no_history),
                                fontSize = 13.sp,
                                color = VortexTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(finishedDownloads, key = { "history_${it.id}" }) { item ->
                    HistoryDownloadRow(
                        item = item,
                        onDelete = { viewModel.deleteHistoryItem(item) },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, item.videoUrl)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir enlace original"))
                        }
                    )
                }
            }
        }

        // Server Settings Configuration Dialog overlay
        if (showServerSettings) {
            var customUrlBuffer by remember { mutableStateOf(customApiUrlState) }

            AlertDialog(
                onDismissRequest = { showServerSettings = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = VortexPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = VortexTextPrimary
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Por defecto, Vortex utiliza el servidor oficial de Cobalt. Si deseas usar tu propio servidor, configúralo debajo:",
                            fontSize = 12.sp,
                            color = VortexTextSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = customUrlBuffer,
                            onValueChange = { customUrlBuffer = it },
                            placeholder = { Text("https://url-del-servidor", color = VortexTextSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = VortexPrimary,
                                unfocusedBorderColor = Color(0xFFCAC4D0),
                                focusedTextColor = VortexTextPrimary,
                                unfocusedTextColor = VortexTextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Selection buttons of fast presets
                        Text(
                            text = "Instancias públicas rápidas:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VortexPrimary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        val presets = listOf(
                            "https://api.cobalt.tools",
                            "https://cobalt.hyper.cool",
                            "https://cobalt.unon.cf"
                        )

                        presets.forEach { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { customUrlBuffer = preset }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(preset, fontSize = 11.sp, color = VortexTextPrimary)
                                if (customUrlBuffer == preset) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = VortexPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = VortexPrimary),
                        onClick = {
                            viewModel.setApiUrl(customUrlBuffer)
                            Toast.makeText(context, "Servidor actualizado.", Toast.LENGTH_SHORT).show()
                            showServerSettings = false
                        }
                    ) {
                        Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showServerSettings = false }) {
                        Text("Cancelar", color = VortexTextSecondary)
                    }
                },
                containerColor = VortexSurface,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

@Composable
fun QualityCard(
    title: String,
    subtitle: String,
    desc: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) VortexPrimary else Color(0xFFCAC4D0).copy(alpha = 0.5f)
    val containerColor = if (isSelected) VortexSecondary else VortexSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) VortexPrimary else VortexTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = VortexTextPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = if (isSelected) VortexPrimary else VortexTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = desc,
                    fontSize = 10.sp,
                    color = VortexTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ActiveDownloadRow(
    item: DownloadItem,
    onCancel: (DownloadItem) -> Unit
) {
    val progressPercent = item.progress
    val isPending = item.status == "PENDING"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = VortexSurfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Platform Icon with background
                val platformColor = when (item.platform) {
                    "youtube" -> VortexYouTube
                    "tiktok" -> VortexTikTok
                    "instagram" -> VortexInstagram
                    else -> VortexSecondary
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(platformColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (item.platform) {
                            "youtube" -> Icons.Default.PlayArrow
                            "tiktok" -> Icons.Default.MusicNote
                            "instagram" -> Icons.Default.CameraAlt
                            else -> Icons.Default.Link
                        },
                        contentDescription = item.platform,
                        tint = platformColor,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 13.sp,
                        color = VortexTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isPending) "Esperando cola..." else "Descargando... ${progressPercent}%",
                            fontSize = 11.sp,
                            color = VortexPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.fileSizeFormatted != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(VortexTextSecondary))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.fileSizeFormatted,
                                fontSize = 11.sp,
                                color = VortexTextSecondary
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onCancel(item) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = "Cancelar",
                        tint = VortexTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Beautiful Gradient Progress Bar animation mapping
            if (isPending) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = VortexPrimary,
                    trackColor = Color(0xFFE7E0EC)
                )
            } else {
                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = VortexPrimary,
                    trackColor = Color(0xFFE7E0EC)
                )
            }
        }
    }
}

@Composable
fun HistoryDownloadRow(
    item: DownloadItem,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = VortexSurfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isSuccess = item.status == "COMPLETED"
            val platformColor = when (item.platform) {
                "youtube" -> VortexYouTube
                "tiktok" -> VortexTikTok
                "instagram" -> VortexInstagram
                else -> VortexSecondary
            }

            // Platform indicator circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(platformColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.platform) {
                        "youtube" -> Icons.Default.PlayArrow
                        "tiktok" -> Icons.Default.MusicNote
                        "instagram" -> Icons.Default.CameraAlt
                        else -> Icons.Default.Link
                    },
                    contentDescription = item.platform,
                    tint = platformColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    color = VortexTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusText = if (isSuccess) "Guardado en Descargas" else "Descarga fallida"
                    val statusColor = if (isSuccess) VortexSuccess else VortexTertiary
                    
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )

                    if (item.fileSizeFormatted != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(VortexTextSecondary))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.fileSizeFormatted,
                            fontSize = 11.sp,
                            color = VortexTextSecondary
                        )
                    }
                }
            }

            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Compartir",
                    tint = VortexTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar",
                    tint = VortexTertiary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
