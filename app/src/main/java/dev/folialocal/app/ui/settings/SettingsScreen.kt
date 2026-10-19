package dev.folialocal.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.folialocal.app.BuildConfig
import dev.folialocal.app.R
import dev.folialocal.app.data.settings.AppSettings
import dev.folialocal.app.data.settings.VisualStyle
import dev.folialocal.app.ui.components.ScreenHeader
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    rootName: String,
    trackCount: Int,
    onStyleChange: (VisualStyle) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onCoverAccentChange: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onPickFolder: (android.net.Uri) -> Unit,
    onClearLibrary: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onPickFolder(uri)
    }
    var showClearDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 播放效果：紧凑网格，占屏更少
            SectionLabel(stringResource(R.string.settings_section_effects))
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_effects_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                VisualStyle.entries.chunked(3).forEach { rowStyles ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowStyles.forEach { style ->
                            VisualStyleTile(
                                style = style,
                                selected = settings.visualStyle == style,
                                onClick = { onStyleChange(style) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowStyles.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    text = stringResource(
                        R.string.settings_style_selected_desc,
                        stringResource(settings.visualStyle.titleRes),
                        stringResource(settings.visualStyle.descriptionRes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 显示：字号 + 封面取色
            SectionLabel(stringResource(R.string.settings_section_display))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.settings_font_size),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(settings.lyricFontScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = settings.lyricFontScale,
                        onValueChange = onFontScaleChange,
                        valueRange = 0.75f..1.5f,
                        steps = 14,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_cover_accent), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.settings_cover_accent_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = settings.coverAccent, onCheckedChange = onCoverAccentChange)
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_section_library))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(
                            text = rootName.ifBlank { stringResource(R.string.settings_no_folder) },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.settings_track_count, trackCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { picker.launch(null) }) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_change_folder))
                        }
                        OutlinedButton(onClick = onRescan) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.rescan))
                        }
                    }
                    TextButton(
                        onClick = { showClearDialog = true },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_clear_index))
                    }
                }
            }

            SectionLabel(stringResource(R.string.settings_section_about))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Folia Local ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_network),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = stringResource(R.string.settings_view_original),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/chthollyphile/folia-major")
                        },
                    )
                    Text(
                        text = stringResource(R.string.settings_license_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_dialog_title)) },
            text = { Text(stringResource(R.string.settings_clear_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearLibrary()
                }) {
                    Text(stringResource(R.string.settings_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp),
    )
}

@Composable
private fun VisualStyleTile(
    style: VisualStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StyleSwatch(style, Modifier.fillMaxWidth().height(42.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(style.titleRes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun StyleSwatch(style: VisualStyle, modifier: Modifier = Modifier) {
    val colors = when (style) {
        VisualStyle.LUMI -> listOf(Color(0xFF070C15), Color(0xFFE7BD50))
        VisualStyle.FUME -> listOf(Color(0xFF0A0F19), Color(0xFFEFC863))
        VisualStyle.CAD -> listOf(Color(0xFF0B1020), Color(0xFF8FD3FF))
        VisualStyle.PAT -> listOf(Color(0xFF0B1320), Color(0xFFD9E6F2))
        VisualStyle.CAPPELLA -> listOf(Color(0xFF1A1424), Color(0xFFFF9FC7))
        VisualStyle.TILT -> listOf(Color(0xFF111729), Color(0xFF6DBBF4))
        VisualStyle.DIORAMA -> listOf(Color(0xFF080B14), Color(0xFFB6CDD3))
        VisualStyle.PENDOLO -> listOf(Color(0xFF020304), Color(0xFF6D898B))
        VisualStyle.SPECTRUM -> listOf(Color(0xFF080A12), Color(0xFFC9B8FF))
        VisualStyle.COVER -> listOf(Color(0xFF09090B), Color(0xFF9AA6B8))
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.linearGradient(colors)),
    )
}
