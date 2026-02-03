package com.scanforge3d.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    onStartScan: () -> Unit,
    onStartPhotoCapture: () -> Unit,
    onOpenProjects: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "ScanForge3D",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "3D-Scanner fuer Reverse Engineering",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Depth scan button - only visible when supported
        if (state.isDepthSupported) {
            Button(
                onClick = onStartScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Icon(Icons.Default.ViewInAr, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("3D-Scan (Tiefenkamera)", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Photo mode button - always visible
        Button(
            onClick = onStartPhotoCapture,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = if (state.isDepthSupported) {
                ButtonDefaults.outlinedButtonColors()
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Foto-Modus", style = MaterialTheme.typography.titleMedium)
        }

        if (!state.isDepthSupported) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Dein Geraet unterstuetzt keinen Tiefensensor. " +
                        "Verwende den Foto-Modus fuer Cloud-basierte 3D-Rekonstruktion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onOpenProjects,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Projekte (${state.totalScans})")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Recent projects
        if (state.recentProjects.isNotEmpty()) {
            Text(
                text = "Letzte Scans",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(state.recentProjects) { project ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${project.triangleCount} Dreiecke",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
