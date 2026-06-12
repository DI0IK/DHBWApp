package dev.dominikstahl.dhbwapp.ui.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dominikstahl.dhbwapp.data.remote.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiClient: ApiClient,
    currentSite: String?,
    currentUserType: String?,
    currentCourse: String?,
    onSiteSelected: (String) -> Unit,
    onCourseSelected: (String) -> Unit,
    onUserTypeSelected: (String) -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(apiClient),
    )
    val state by viewModel.uiState.collectAsState()
    var siteExpanded by remember { mutableStateOf(false) }
    var courseExpanded by remember { mutableStateOf(false) }
    var userTypeExpanded by remember { mutableStateOf(false) }

    val userTypes = listOf("Studierende", "Mitarbeitende", "Gast")

    LaunchedEffect(state.sites, currentSite) {
        if (currentSite != null && state.sites.isNotEmpty() && state.selectedSite == null) {
            if (state.sites.any { it.site == currentSite }) {
                viewModel.selectSite(currentSite)
            }
        }
    }

    LaunchedEffect(state.courses, currentCourse) {
        if (currentCourse != null && state.courses.isNotEmpty() && state.selectedCourse == null) {
            if (state.courses.contains(currentCourse)) {
                viewModel.selectCourse(currentCourse)
            }
        }
    }

    LaunchedEffect(currentUserType) {
        if (currentUserType != null && state.selectedUserType == null) {
            if (userTypes.contains(currentUserType)) {
                viewModel.selectUserType(currentUserType)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Einstellungen",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Wähle deinen DHBW-Standort",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))

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
                                    onSiteSelected(site.site)
                                    siteExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Wähle deine Benutzergruppe",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = userTypeExpanded,
                    onExpandedChange = { userTypeExpanded = !userTypeExpanded },
                ) {
                    OutlinedTextField(
                        value = state.selectedUserType ?: currentUserType ?: "Studierende",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Benutzergruppe") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = userTypeExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = userTypeExpanded,
                        onDismissRequest = { userTypeExpanded = false },
                    ) {
                        userTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    viewModel.selectUserType(type)
                                    onUserTypeSelected(type)
                                    userTypeExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                if (state.selectedSite != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Wähle deinen Kurs",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

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
                                            onCourseSelected(course)
                                            courseExpanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
