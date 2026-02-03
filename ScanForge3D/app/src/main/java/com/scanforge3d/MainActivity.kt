package com.scanforge3d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scanforge3d.ui.theme.ScanForge3DTheme
import com.scanforge3d.ui.home.HomeScreen
import com.scanforge3d.ui.scan.ScanScreen
import com.scanforge3d.ui.preview.PreviewScreen
import com.scanforge3d.ui.calibration.CalibrationScreen
import com.scanforge3d.ui.export.ExportScreen
import com.scanforge3d.ui.projects.ProjectsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ScanForge3DTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            HomeScreen(
                                onStartScan = { navController.navigate("scan") },
                                onOpenProjects = { navController.navigate("projects") }
                            )
                        }

                        composable("scan") {
                            ScanScreen(
                                onScanComplete = { scanId ->
                                    navController.navigate("calibration/$scanId")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("calibration/{scanId}") { backStackEntry ->
                            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
                            CalibrationScreen(
                                scanId = scanId,
                                onCalibrated = {
                                    navController.navigate("preview/$scanId")
                                },
                                onSkip = {
                                    navController.navigate("preview/$scanId")
                                }
                            )
                        }

                        composable("preview/{scanId}") { backStackEntry ->
                            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
                            PreviewScreen(
                                scanId = scanId,
                                onExport = {
                                    navController.navigate("export/$scanId")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("export/{scanId}") { backStackEntry ->
                            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
                            ExportScreen(
                                scanId = scanId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("projects") {
                            ProjectsScreen(
                                onOpenProject = { scanId ->
                                    navController.navigate("preview/$scanId")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
