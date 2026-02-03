package com.scanforge3d.ui.export

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ExportScreen(
    scanId: String,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val exportState by viewModel.exportState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Export",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        ExportOption(
            title = "STL (Binary)",
            description = "Direkt auf dem Gerät. Geeignet für 3D-Druck.",
            icon = Icons.Default.Save,
            isLoading = exportState.stlExporting,
            isCompleted = exportState.stlCompleted,
            onClick = { viewModel.exportSTL(scanId) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExportOption(
            title = "OBJ",
            description = "Wavefront OBJ mit Normalen. Universelles Format.",
            icon = Icons.Default.Save,
            isLoading = exportState.objExporting,
            isCompleted = exportState.objCompleted,
            onClick = { viewModel.exportOBJ(scanId) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ExportOption(
            title = "STEP (AP214)",
            description = "CAD-kompatibel. Wird in der Cloud verarbeitet.",
            icon = Icons.Default.CloudUpload,
            isLoading = exportState.stepExporting,
            isCompleted = exportState.stepCompleted,
            progress = exportState.stepProgress,
            onClick = { viewModel.exportSTEP(scanId) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // CATIA info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CATIA Part (.CATPart)",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Exportiere als STEP und importiere die Datei in CATIA V5/V6. " +
                        "Das CATIA-Part-Format ist proprietär und kann nur direkt in " +
                        "CATIA erzeugt werden.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.downloadCATIAMacro() }) {
                    Text("CATIA Import-Macro herunterladen")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Mesh stats
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Mesh-Statistiken", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                StatRow("Vertices", "${exportState.vertexCount}")
                StatRow("Dreiecke", "${exportState.triangleCount}")
                StatRow("Wasserdicht", if (exportState.isWatertight) "Ja" else "Nein")
                StatRow("Dateigröße (STL)", exportState.estimatedFileSize)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        exportState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Zurück")
        }
    }
}

@Composable
private fun ExportOption(
    title: String,
    description: String,
    icon: ImageVector,
    isLoading: Boolean,
    isCompleted: Boolean,
    progress: Float = 0f,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (!isLoading && !isCompleted) onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Default.Check else icon,
                contentDescription = null,
                tint = if (isCompleted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLoading && progress > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
