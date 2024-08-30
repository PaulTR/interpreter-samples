package com.google.edgeai.examples.model_personalization

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: MainViewModel by viewModels { MainViewModel.getFactory(this) }

        setContent {
            val context = LocalContext.current
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                if (isGranted) {
                    // Do nothing
                } else {
                    // Permission Denied
                    Toast.makeText(context, "Camera permission is denied", Toast.LENGTH_SHORT)
                        .show()
                }
            }

            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Do nothing
                } else {
                    launcher.launch(Manifest.permission.CAMERA)
                }
            }

            Column {
                val width = LocalConfiguration.current.screenWidthDp
                val height = width / 3 * 4
                Box(
                    modifier = Modifier
                        .width(width.dp)
                        .height(height.dp)
                ) {
                    CameraPreview(
                        onImageAnalyzed = { imageProxy ->
                            Log.d("xxx", "onImageAnalyzedonImageAnalyzedonImageAnalyzed: ");
                            viewModel.addSample(imageProxy, "")
                        })
                }

                Button(onClick = { viewModel.startTraining() }) {
                    Text(text = "Train")
                }
            }

        }
    }

    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
        executor: ExecutorService,
        previewView: PreviewView,
        onImageAnalyzed: (ImageProxy) -> Unit,
    ) {
        Handler().postDelayed({
        val preview: Preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val cameraSelector: CameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
            val imageAnalysis = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                Log.d("xxx", "bindCameraUseCases: ${imageProxy.height}");
                onImageAnalyzed(imageProxy)
            }
            val cameraProvider = cameraProviderFuture.get()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis, preview)

        }, 1000)



    }

    @Composable
    fun CameraPreview(
        modifier: Modifier = Modifier,
        onImageAnalyzed: (ImageProxy) -> Unit,
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val cameraProviderFuture by remember {
            mutableStateOf(ProcessCameraProvider.getInstance(context))
        }

        DisposableEffect(lifecycleOwner) {
            onDispose {
                cameraProviderFuture.get().unbindAll()
            }
        }

        AndroidView(modifier = modifier, factory = {
            val previewView = PreviewView(it).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_START
            }

            val executor = Executors.newSingleThreadExecutor()
            cameraProviderFuture.addListener({
                bindCameraUseCases(
                    lifecycleOwner = lifecycleOwner,
                    cameraProviderFuture = cameraProviderFuture,
                    executor = executor,
                    previewView = previewView,
                    onImageAnalyzed = onImageAnalyzed
                )
            }, ContextCompat.getMainExecutor(context))

            previewView
        })
    }
}
