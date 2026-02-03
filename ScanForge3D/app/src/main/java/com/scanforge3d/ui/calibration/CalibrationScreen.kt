package com.scanforge3d.ui.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.sceneview.SceneView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    scanId: String,
    onCalibrated: () -> Unit,
    onSkip: () -> Unit,
    viewModel: CalibrationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var knownDistanceInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kalibrierung") },
                navigationIcon = {
                    IconButton(onClick = onSkip) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Zurücksetzen")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                text = "Referenzmaß setzen",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tippe auf zwei Punkte mit bekanntem Abstand auf dem Scan, " +
                    "um die Genauigkeit zu verbessern.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3D view with touch-based point selection
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // SceneView for 3D mesh display
                    AndroidView(
                        factory = { ctx ->
                            SceneView(ctx).apply {
                                // SceneView handles orbit camera and lighting
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Touch overlay for point selection
                    // Tap to place calibration markers on the 3D mesh
                    var tapPoint1 by remember { mutableStateOf<Offset?>(null) }
                    var tapPoint2 by remember { mutableStateOf<Offset?>(null) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    if (state.point1 == null || (state.point1 != null && state.point2 != null)) {
                                        // Set or reset point 1
                                        if (state.point1 != null && state.point2 != null) {
                                            viewModel.reset()
                                            tapPoint2 = null
                                        }
                                        tapPoint1 = offset
                                        // Ray-cast through SceneView would provide actual
                                        // 3D coordinates. For now use screen-projected
                                        // approximation based on normalized screen coords.
                                        val nx = offset.x / size.width
                                        val ny = offset.y / size.height
                                        viewModel.setPoint1(nx, ny, 0f)
                                    } else {
                                        // Set point 2
                                        tapPoint2 = offset
                                        val nx = offset.x / size.width
                                        val ny = offset.y / size.height
                                        viewModel.setPoint2(nx, ny, 0f)
                                    }
                                }
                            }
                    ) {
                        // Draw point 1 marker
                        tapPoint1?.let { p1 ->
                            drawCircle(
                                color = Color.Red,
                                radius = 12f,
                                center = p1
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 12f,
                                center = p1,
                                style = Stroke(width = 2f)
                            )
                        }

                        // Draw point 2 marker
                        tapPoint2?.let { p2 ->
                            drawCircle(
                                color = Color.Blue,
                                radius = 12f,
                                center = p2
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 12f,
                                center = p2,
                                style = Stroke(width = 2f)
                            )
                        }

                        // Draw line between points
                        if (tapPoint1 != null && tapPoint2 != null) {
                            drawLine(
                                color = Color.Yellow,
                                start = tapPoint1!!,
                                end = tapPoint2!!,
                                strokeWidth = 2f
                            )
                        }
                    }

                    // Point status indicators
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (state.point1 != null) Color.Red else Color.Gray,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("P1", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (state.point2 != null) Color.Blue else Color.Gray,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("P2", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Known distance input
            OutlinedTextField(
                value = knownDistanceInput,
                onValueChange = {
                    knownDistanceInput = it
                    it.toFloatOrNull()?.let { dist ->
                        viewModel.setKnownDistance(dist)
                    }
                },
                label = { Text("Bekannter Abstand (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Z.B. Kantenlänge eines bekannten Objekts")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Calibration results
            if (state.measuredDistance > 0f) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Kalibrierungsergebnis",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Gemessen:", style = MaterialTheme.typography.bodyMedium)
                            Text("%.2f mm".format(state.measuredDistance))
                        }
                        if (state.knownDistance > 0f) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Soll-Wert:", style = MaterialTheme.typography.bodyMedium)
                                Text("%.2f mm".format(state.knownDistance))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Skalierungsfaktor:", style = MaterialTheme.typography.bodyMedium)
                            Text("%.4f".format(state.scaleFactor))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSkip) {
                    Text("Überspringen")
                }

                Button(
                    onClick = onCalibrated,
                    enabled = state.isCalibrated
                ) {
                    Text("Anwenden")
                }
            }
        }
    }
}
