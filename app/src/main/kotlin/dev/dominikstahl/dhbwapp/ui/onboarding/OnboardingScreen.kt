package dev.dominikstahl.dhbwapp.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    apiClient: ApiClient,
    onSiteSelected: (String) -> Unit,
    onCourseSelected: (String) -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        key = "onboarding",
        factory = SettingsViewModel.Factory(apiClient),
    )
    val state by viewModel.uiState.collectAsState()
    var siteExpanded by remember { mutableStateOf(false) }
    var courseExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Willkommen zur DHBW App",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Wähle deinen DHBW-Standort und Kurs aus, um zu starten",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> Text("Fehler: ${state.error}", color = MaterialTheme.colorScheme.error)
            else -> {
                ExposedDropdownMenuBox(
                    expanded = siteExpanded,
                    onExpandedChange = { siteExpanded = !siteExpanded },
                ) {
                    OutlinedTextField(
                        value = state.selectedSite ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Standort") },
                        placeholder = { Text("Wähle einen Standort") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = siteExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = siteExpanded,
                        onDismissRequest = { siteExpanded = false },
                    ) {
                        state.sites.forEach { site ->
                            DropdownMenuItem(
                                text = { Text(site.site) },
                                onClick = {
                                    viewModel.selectSite(site.site)
                                    siteExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                if (state.selectedSite != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    if (state.coursesLoading) {
                        CircularProgressIndicator()
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = courseExpanded,
                            onExpandedChange = { courseExpanded = !courseExpanded },
                        ) {
                            OutlinedTextField(
                                value = state.selectedCourse ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Kurs") },
                                placeholder = { Text("Wähle einen Kurs") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = courseExpanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = courseExpanded,
                                onDismissRequest = { courseExpanded = false },
                            ) {
                                state.courses.forEach { course ->
                                    DropdownMenuItem(
                                        text = { Text(course) },
                                        onClick = {
                                            viewModel.selectCourse(course)
                                            courseExpanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        state.selectedSite?.let { onSiteSelected(it) }
                        state.selectedCourse?.let { onCourseSelected(it) }
                    },
                    enabled = state.selectedSite != null && state.selectedCourse != null,
                ) {
                    Text("Weiter", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
