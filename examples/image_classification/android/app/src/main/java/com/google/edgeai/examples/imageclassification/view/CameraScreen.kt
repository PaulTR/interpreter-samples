package com.google.edgeai.examples.imageclassification.view

import android.content.pm.PackageManager
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio.RATIO_4_3
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.edgeai.examples.imageclassification.UiState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    uiState: UiState,
    modifier: Modifier = Modifier,
    onImageAnalyzed: (ImageProxy) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Do nothing
        } else {
            // Permission Denied
            Toast.makeText(context, "Camera permission is denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(key1 = uiState.errorMessage) {
        if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Do nothing
        } else {
            launcher.launch(android.Manifest.permission.CAMERA)
        }
    }
    Box(modifier) {
        CameraPreview(onImageAnalyzed = { imageProxy ->
            onImageAnalyzed(imageProxy)
        })
    }

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
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            scaleType = PreviewView.ScaleType.FILL_CENTER
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

fun bindCameraUseCases(
    lifecycleOwner: LifecycleOwner,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    executor: ExecutorService,
    previewView: PreviewView,
    onImageAnalyzed: (ImageProxy) -> Unit,
) {
    val preview: Preview = Preview.Builder().setTargetAspectRatio(RATIO_4_3).build()

    preview.setSurfaceProvider(previewView.surfaceProvider)

    val cameraSelector: CameraSelector =
        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
    val imageAnalysis = ImageAnalysis.Builder().setTargetAspectRatio(RATIO_4_3)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()

    imageAnalysis.setAnalyzer(executor) { imageProxy ->
        onImageAnalyzed(imageProxy)
    }
    val cameraProvider = cameraProviderFuture.get()

    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis, preview)
}