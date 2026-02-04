package com.scanforge3d.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView

@Composable
fun ScanScreen(
    onScanComplete: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val scanState by viewModel.scanState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // AR camera preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (hasCameraPermission) {
                // ARSceneView integration
                var arSceneView by remember { mutableStateOf<ARSceneView?>(null) }

                AndroidView(
                    factory = { ctx ->
                        ARSceneView(ctx).apply {
                            // Configure ARCore session for depth scanning
                            configureSession { session, config ->
                                config.depthMode = Config.DepthMode.RAW_DEPTH_ONLY
                                config.focusMode = Config.FocusMode.AUTO
                                config.planeFindingMode =
                                    Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                                config.lightEstimationMode =
                                    Config.LightEstimationMode.ENVIRONMENTAL_HDR
                                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            }

                            // Frame update listener for depth processing
                            onSessionUpdated = { session, frame ->
                                if (frame.camera.trackingState == TrackingState.TRACKING) {
                                    viewModel.onArFrameUpdate(frame)
                                }
                            }

                            arSceneView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view ->
                        view.destroy()
                    }
                )

                // ARSceneView manages its own lifecycle through AndroidView's
                // attach/detach mechanism. The onRelease callback above handles cleanup.
            } else {
                // No camera permission
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Kamera-Berechtigung erforderlich",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }) {
                            Text("Berechtigung erteilen")
                        }
                    }
                }
            }

            // Real-time scan overlay
            if (scanState.isScanning) {
                ScanOverlay(
                    pointCount = scanState.pointCount,
                    coverage = scanState.surfaceCoverage,
                    quality = scanState.qualityScore
                )
            }

            // Tracking status indicator
            if (hasCameraPermission && !scanState.isScanning) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                ) {
                    Text(
                        text = "Gerät langsam um das Objekt bewegen",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Control bar
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ScanMetric("Punkte", "${scanState.pointCount / 1000}k")
                    ScanMetric("Frames", "${scanState.frameCount}")
                    ScanMetric("Abdeckung", "${(scanState.surfaceCoverage * 100).toInt()}%")
                    ScanMetric("Qualität", scanState.qualityLabel)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = onBack) {
                        Text("Abbrechen")
                    }

                    if (scanState.isScanning) {
                        Button(
                            onClick = {
                                viewModel.stopScan()
                                onScanComplete(scanState.scanId)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Scan beenden")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startScan() },
                            enabled = hasCameraPermission
                        ) {
                            Text("Scan starten")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
