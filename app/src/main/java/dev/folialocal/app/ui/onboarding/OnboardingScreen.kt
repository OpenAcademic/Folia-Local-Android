package dev.folialocal.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.folialocal.app.R
import dev.folialocal.app.data.library.ScanProgress
import dev.folialocal.app.ui.theme.DefaultAccent
import dev.folialocal.app.ui.theme.FoliaBackground

@Composable
fun OnboardingScreen(
    progress: ScanProgress,
    onPickFolder: (android.net.Uri) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onPickFolder(uri)
    }
    val tagline = stringResource(R.string.onboarding_tagline)
    val headline = stringResource(R.string.onboarding_headline)
    val body = stringResource(R.string.onboarding_body)
    val localTitle = stringResource(R.string.onboarding_feature_local_title)
    val localDesc = stringResource(R.string.onboarding_feature_local_desc)
    val lyricsTitle = stringResource(R.string.onboarding_feature_lyrics_title)
    val lyricsDesc = stringResource(R.string.onboarding_feature_lyrics_desc)
    val offlineTitle = stringResource(R.string.onboarding_feature_offline_title)
    val offlineDesc = stringResource(R.string.onboarding_feature_offline_desc)
    val pickFolderLabel = stringResource(R.string.onboarding_pick_folder)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF231B36),
                        Color(0xFF121016),
                        FoliaBackground,
                    ),
                    radius = 1400f,
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .widthIn(max = 560.dp)
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Folia",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 52.sp,
                    letterSpacing = 6.sp,
                ),
                color = Color(0xFFF5F2EA),
            )
            Text(
                text = tagline,
                style = MaterialTheme.typography.labelMedium,
                color = Color(DefaultAccent),
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            FeatureRow(Icons.Rounded.FolderOpen, localTitle, localDesc)
            FeatureRow(Icons.AutoMirrored.Rounded.TextSnippet, lyricsTitle, lyricsDesc)
            FeatureRow(Icons.Rounded.Lock, offlineTitle, offlineDesc)
            Spacer(Modifier.height(32.dp))

            if (progress.active) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = Color(DefaultAccent), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.scanning_progress, progress.scanned, progress.currentFolder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Button(
                    onClick = { launcher.launch(null) },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = pickFolderLabel,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (progress.error != null) {
                    Text(
                        text = progress.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(DefaultAccent).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color(DefaultAccent), modifier = Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
