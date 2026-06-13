package dev.dominikstahl.dhbwapp.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MoreScreen(
    onParkingClick: () -> Unit,
    onRoomsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDirectoryClick: () -> Unit,
    onDualisClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Mehr",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        MoreItem(
            icon = Icons.Default.FactCheck,
            title = "Dualis",
            description = "Noten und Studienergebnisse abrufen",
            onClick = onDualisClick,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MoreItem(
            icon = Icons.Default.LocalParking,
            title = "Parken",
            description = "Parkhausauslastung anzeigen",
            onClick = onParkingClick,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MoreItem(
            icon = Icons.Default.MeetingRoom,
            title = "Raumverfügbarkeit",
            description = "Freie Räume suchen",
            onClick = onRoomsClick,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MoreItem(
            icon = Icons.Default.Search,
            title = "Verzeichnis",
            description = "Vorlesungspläne nach Dozent, Raum oder Kurs",
            onClick = onDirectoryClick,
        )
        Spacer(modifier = Modifier.height(8.dp))
        MoreItem(
            icon = Icons.Default.Settings,
            title = "Einstellungen",
            description = "Standort und Kurs verwalten",
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun MoreItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
