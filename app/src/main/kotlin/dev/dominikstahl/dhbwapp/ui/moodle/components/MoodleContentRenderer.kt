package dev.dominikstahl.dhbwapp.ui.moodle.components

import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.compose.ui.graphics.toArgb
import dev.dominikstahl.dhbwapp.data.local.db.CachedMoodleContent

@Composable
fun MoodleLabelCard(
    item: CachedMoodleContent,
    modifier: Modifier = Modifier
) {
    val cleanHtml = remember(item.description) {
        item.description ?: item.name
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        setTextColor(textColor)
                        textSize = 14f
                    }
                },
                update = { textView ->
                    textView.setTextColor(textColor)
                    textView.text = HtmlCompat.fromHtml(cleanHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun MoodleForumCard(
    item: CachedMoodleContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MoodleActivityCard(
        title = item.name,
        subtitle = "Diskussionsforum",
        icon = Icons.Default.Forum,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun MoodleQuizCard(
    item: CachedMoodleContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MoodleActivityCard(
        title = item.name,
        subtitle = "Test / Quiz",
        icon = Icons.Default.Quiz,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun MoodleChoiceCard(
    item: CachedMoodleContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MoodleActivityCard(
        title = item.name,
        subtitle = "Abstimmung",
        icon = Icons.Default.Poll,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun MoodleFeedbackCard(
    item: CachedMoodleContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MoodleActivityCard(
        title = item.name,
        subtitle = "Feedback",
        icon = Icons.Default.Feedback,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun MoodleBigBlueButtonCard(
    item: CachedMoodleContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MoodleActivityCard(
        title = item.name,
        subtitle = "Klassenraum (BBB)",
        icon = Icons.Default.VideoCall,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun MoodleActivityCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Öffnen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
