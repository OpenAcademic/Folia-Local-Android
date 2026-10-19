package dev.folialocal.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.folialocal.app.R
import dev.folialocal.app.data.audio.AudioEffectParameter
import dev.folialocal.app.data.audio.AudioEffects
import dev.folialocal.app.data.audio.AudioPreset
import kotlin.math.roundToInt

/**
 * 音效面板：六套预设 + 十段均衡 + 九项效果链。
 * 所有改动实时写入 StateFlow，音频线程在下一个缓冲区就会应用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEffectsSheet(
    effects: AudioEffects,
    onEffectsChange: (AudioEffects) -> Unit,
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
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.effects_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.effects_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = effects.enabled,
                    onCheckedChange = { onEffectsChange(effects.copy(enabled = it)) },
                )
            }

            Text(
                text = stringResource(R.string.effects_section_presets),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(AudioPreset.entries, key = { it.id }) { preset ->
                    FilterChip(
                        selected = effects.enabled && effects.presetId == preset.id,
                        onClick = { onEffectsChange(preset.create()) },
                        label = { Text(stringResource(preset.titleRes)) },
                    )
                }
                if (effects.enabled && effects.presetId == AudioEffects.CUSTOM_PRESET_ID) {
                    item(key = "custom") {
                        FilterChip(
                            selected = true,
                            onClick = { },
                            label = { Text(stringResource(R.string.effects_custom)) },
                        )
                    }
                }
            }
            val presetDescription = AudioPreset.fromId(effects.presetId)?.descriptionRes?.let { stringResource(it) }
                ?: stringResource(R.string.effects_custom_hint)
            Text(
                text = presetDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SectionTitle(stringResource(R.string.effects_section_eq), trailing = "±12 dB")
            AudioEffects.BAND_LABELS.forEachIndexed { index, label ->
                val gain = effects.gains.getOrElse(index) { 0f }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(42.dp),
                    )
                    Slider(
                        value = gain,
                        onValueChange = { onEffectsChange(effects.withGain(index, it)) },
                        valueRange = AudioEffects.MIN_GAIN_DB..AudioEffects.MAX_GAIN_DB,
                        steps = 23,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${gain.roundToInt()} dB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(54.dp),
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            SectionTitle(stringResource(R.string.effects_section_chain), trailing = stringResource(R.string.effects_chain_note))
            AudioEffectParameter.entries.forEach { parameter ->
                val value = effects.parameterValue(parameter)
                val parameterLabel = stringResource(parameter.labelRes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = parameterLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(78.dp),
                    )
                    Slider(
                        value = parameter.toSlider(value),
                        onValueChange = { position ->
                            onEffectsChange(effects.withParameter(parameter, parameter.fromSlider(position)))
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = parameter.format(value),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(66.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.effects_noise_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { onEffectsChange(AudioEffects()) }) {
                    Text(stringResource(R.string.effects_reset_all))
                }
                TextButton(onClick = { onEffectsChange(AudioPreset.LOFI.create()) }) {
                    Text(stringResource(R.string.effects_try_lofi))
                }
            }
            Text(
                text = stringResource(R.string.effects_local_only),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
