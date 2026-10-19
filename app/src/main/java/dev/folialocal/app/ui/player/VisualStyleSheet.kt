package dev.folialocal.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.folialocal.app.R
import dev.folialocal.app.data.settings.VisualStyle

/**
 * 播放效果选择器：紧凑网格，点击即时切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualStyleSheet(
    selected: VisualStyle,
    onSelect: (VisualStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 720.dp,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.player_open_styles), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.settings_effects_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VisualStyle.entries.chunked(3).forEach { rowStyles ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowStyles.forEach { style ->
                        StyleTile(
                            style = style,
                            isSelected = style == selected,
                            onClick = { onSelect(style) },
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
                    stringResource(selected.titleRes),
                    stringResource(selected.descriptionRes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StyleTile(
    style: VisualStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (isSelected) style.accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) style.accent else MaterialTheme.colorScheme.outlineVariant,
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
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) style.accent else MaterialTheme.colorScheme.onSurface,
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
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(7.dp)
                .size(width = 26.dp, height = 2.dp)
                .background(colors.last().copy(alpha = 0.9f), RoundedCornerShape(2.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(4.dp)
                .background(colors.last().copy(alpha = 0.6f), RoundedCornerShape(50)),
        )
    }
}
