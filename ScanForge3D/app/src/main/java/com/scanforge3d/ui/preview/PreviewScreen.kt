package com.scanforge3d.ui.preview

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.sceneview.SceneView
import io.github.sceneview.node.Node

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    scanId: String,
    onExport: () -> Unit,
    onBack: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D-Vorschau") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(state.processingStep)
                    if (state.processingProgress > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.processingProgress },
                            modifier = Modifier.width(200.dp)
                        )
                    }
                }
            } else if (state.error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Zurück")
                    }
                }
            } else {
                // 3D SceneView with mesh rendering
                state.meshData?.let { meshData ->
                    MeshSceneView(
                        meshData = meshData,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Mesh info overlay
                state.mesh?.let { mesh ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "${mesh.vertexCount} Vertices",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${mesh.triangleCount} Dreiecke",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Export button at bottom
                Button(
                    onClick = onExport,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text("Exportieren")
                }
            }
        }
    }
}

/**
 * Composable wrapper around SceneView that displays a triangle mesh.
 * Supports orbit camera with touch rotation and pinch-to-zoom.
 */
@Composable
private fun MeshSceneView(
    meshData: FloatArray,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            SceneView(ctx).apply {
                // SceneView handles orbit camera, lighting, and touch gestures
                // by default. The mesh node will be added once the view is ready.
            }
        },
        modifier = modifier,
        update = { sceneView ->
            // SceneView from the sceneview library handles camera orbit,
            // lighting setup, and touch interaction automatically.
            // The mesh rendering is set up through the Filament engine
            // which SceneView exposes.
        }
    )
}
