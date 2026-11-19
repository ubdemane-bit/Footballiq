package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.engine.FixtureOcrParser
import com.example.engine.ScannedFixture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "CameraScanner"

data class BoundCameraHandle(
  val camera: Camera,
  val imageCapture: ImageCapture?,
  val previewView: PreviewView,
  val hasMultipleCameras: Boolean,
  val activeLensFacing: Int,
)

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScannerPreview(
  recognizer: TextRecognizer,
  knownTeams: List<String>,
  isAutoScanActive: Boolean,
  preferBackCamera: Boolean,
  retryNonce: Int,
  onCameraBound: (BoundCameraHandle) -> Unit,
  onLiveTextDetected: (String) -> Unit,
  onFixtureDetected: (ScannedFixture) -> Unit,
  onCameraError: (String) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val previewView = remember {
    PreviewView(context).apply {
      // TextureView (COMPATIBLE) prevents black/clipped surfaces inside Compose cards & emulators.
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }

  val latestKnownTeams by rememberUpdatedState(knownTeams)
  val latestAutoScan by rememberUpdatedState(isAutoScanActive)
  val latestOnCameraBound by rememberUpdatedState(onCameraBound)
  val latestOnLiveText by rememberUpdatedState(onLiveTextDetected)
  val latestOnFixture by rememberUpdatedState(onFixtureDetected)
  val latestOnError by rememberUpdatedState(onCameraError)

  DisposableEffect(lifecycleOwner, preferBackCamera, retryNonce) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val analysisExecutor = Executors.newSingleThreadExecutor()
    val lastAnalyzedMs = AtomicLong(0L)
    val isFrameInFlight = AtomicBoolean(false)
    var disposed = false

    cameraProviderFuture.addListener(
      {
        if (disposed) return@addListener
        try {
          val cameraProvider = cameraProviderFuture.get()
          val hasBack = runCatching {
            cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
          }.getOrDefault(false)
          val hasFront = runCatching {
            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
          }.getOrDefault(false)

          val (selector, resolvedLens) = when {
            preferBackCamera && hasBack -> CameraSelector.DEFAULT_BACK_CAMERA to CameraSelector.LENS_FACING_BACK
            !preferBackCamera && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA to CameraSelector.LENS_FACING_FRONT
            hasBack -> CameraSelector.DEFAULT_BACK_CAMERA to CameraSelector.LENS_FACING_BACK
            hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA to CameraSelector.LENS_FACING_FRONT
            else -> {
              val firstInfo = cameraProvider.availableCameraInfos.firstOrNull()
              if (firstInfo != null) {
                firstInfo.cameraSelector to firstInfo.lensFacing
              } else {
                latestOnError("No camera hardware detected. Use System Camera or From Photo below.")
                return@addListener
              }
            }
          }

          val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
          }

          val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

          val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

          imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            val now = System.currentTimeMillis()
            if (disposed || !latestAutoScan || now - lastAnalyzedMs.get() < 650L || !isFrameInFlight.compareAndSet(false, true)) {
              imageProxy.close()
              return@setAnalyzer
            }
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
              isFrameInFlight.set(false)
              imageProxy.close()
              return@setAnalyzer
            }
            lastAnalyzedMs.set(now)
            try {
              val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
              recognizer.process(input)
                .addOnSuccessListener(mainExecutor) { visionText ->
                  if (disposed) return@addOnSuccessListener
                  val text = visionText.text
                  if (text.isNotBlank()) {
                    latestOnLiveText(text)
                    val parsed = FixtureOcrParser.parse(text, latestKnownTeams)
                    if (parsed != null) {
                      latestOnFixture(parsed)
                    }
                  }
                }
                .addOnFailureListener { e ->
                  Log.w(TAG, "Live OCR frame failed: ${e.message}")
                }
                .addOnCompleteListener {
                  isFrameInFlight.set(false)
                  imageProxy.close()
                }
            } catch (t: Throwable) {
              Log.w(TAG, "Failed to prepare ImageProxy for ML Kit", t)
              isFrameInFlight.set(false)
              imageProxy.close()
            }
          }

          cameraProvider.unbindAll()
          var boundCapture: ImageCapture? = imageCapture
          val camera = try {
            cameraProvider.bindToLifecycle(
              lifecycleOwner,
              selector,
              preview,
              imageCapture,
              imageAnalysis,
            )
          } catch (comboError: IllegalArgumentException) {
            Log.w(TAG, "3-stream binding unsupported; retrying Preview + ImageAnalysis", comboError)
            boundCapture = null
            cameraProvider.unbindAll()
            try {
              cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageAnalysis,
              )
            } catch (twoStreamError: IllegalArgumentException) {
              Log.w(TAG, "2-stream binding unsupported; retrying Preview only", twoStreamError)
              cameraProvider.unbindAll()
              cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
              )
            }
          }

          latestOnCameraBound(
            BoundCameraHandle(
              camera = camera,
              imageCapture = boundCapture,
              previewView = previewView,
              hasMultipleCameras = hasBack && hasFront,
              activeLensFacing = resolvedLens,
            )
          )
        } catch (e: Exception) {
          Log.e(TAG, "Failed to bind CameraX use cases", e)
          latestOnError(e.localizedMessage ?: "Camera preview unavailable. Try Retry, System Camera, or From Photo.")
        }
      },
      mainExecutor,
    )

    onDispose {
      disposed = true
      try {
        if (cameraProviderFuture.isDone) {
          cameraProviderFuture.get().unbindAll()
        }
      } catch (_: Exception) {
      }
      analysisExecutor.shutdown()
    }
  }

  AndroidView(
    factory = { previewView },
    modifier = Modifier.fillMaxSize(),
  )
}

@Composable
fun ScannerReticleOverlay(
  isLocked: Boolean,
  primaryColor: Color,
  modifier: Modifier = Modifier,
) {
  val scanTransition = rememberInfiniteTransition(label = "scannerLaser")
  val laserProgress by scanTransition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1700, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "laserY",
  )

  Canvas(modifier = modifier) {
    val padX = size.width * 0.10f
    val padY = size.height * 0.18f
    val left = padX
    val right = size.width - padX
    val top = padY
    val bottom = size.height - padY
    val cornerLen = 28.dp.toPx()
    val strokeW = if (isLocked) 3.5.dp.toPx() else 2.5.dp.toPx()
    val color = if (isLocked) primaryColor else Color.White.copy(alpha = 0.85f)

    if (!isLocked) {
      val laserY = top + (bottom - top) * laserProgress
      drawLine(
        color = primaryColor.copy(alpha = 0.55f),
        start = Offset(left + 12.dp.toPx(), laserY),
        end = Offset(right - 12.dp.toPx(), laserY),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
      )
    }

    drawLine(color, Offset(left, top), Offset(left + cornerLen, top), strokeW, StrokeCap.Round)
    drawLine(color, Offset(left, top), Offset(left, top + cornerLen), strokeW, StrokeCap.Round)
    drawLine(color, Offset(right, top), Offset(right - cornerLen, top), strokeW, StrokeCap.Round)
    drawLine(color, Offset(right, top), Offset(right, top + cornerLen), strokeW, StrokeCap.Round)
    drawLine(color, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW, StrokeCap.Round)
    drawLine(color, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeW, StrokeCap.Round)
    drawLine(color, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeW, StrokeCap.Round)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeW, StrokeCap.Round)
  }
}

@OptIn(ExperimentalGetImage::class)
fun captureAndScanFrame(
  context: Context,
  handle: BoundCameraHandle?,
  recognizer: TextRecognizer,
  knownTeams: List<String>,
  onResult: (ScannedFixture?, String) -> Unit,
  onError: (String) -> Unit,
) {
  if (handle == null) {
    onError("Camera is still initializing. Try again or use System Camera.")
    return
  }

  val cap = handle.imageCapture
  if (cap == null) {
    scanPreviewBitmapFallback(
      previewView = handle.previewView,
      recognizer = recognizer,
      knownTeams = knownTeams,
      onResult = onResult,
      onError = onError,
    )
    return
  }

  val mainExecutor = ContextCompat.getMainExecutor(context)
  cap.takePicture(
    mainExecutor,
    object : ImageCapture.OnImageCapturedCallback() {
      override fun onCaptureSuccess(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
          imageProxy.close()
          scanPreviewBitmapFallback(handle.previewView, recognizer, knownTeams, onResult, onError)
          return
        }
        try {
          val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
          recognizer.process(input)
            .addOnSuccessListener { visionText ->
              val raw = visionText.text
              onResult(FixtureOcrParser.parse(raw, knownTeams), raw)
            }
            .addOnFailureListener {
              scanPreviewBitmapFallback(handle.previewView, recognizer, knownTeams, onResult, onError)
            }
            .addOnCompleteListener {
              imageProxy.close()
            }
        } catch (_: Throwable) {
          imageProxy.close()
          scanPreviewBitmapFallback(handle.previewView, recognizer, knownTeams, onResult, onError)
        }
      }

      override fun onError(exception: ImageCaptureException) {
        Log.w(TAG, "ImageCapture failed; trying PreviewView bitmap fallback", exception)
        scanPreviewBitmapFallback(handle.previewView, recognizer, knownTeams, onResult, onError)
      }
    },
  )
}

private fun scanPreviewBitmapFallback(
  previewView: PreviewView,
  recognizer: TextRecognizer,
  knownTeams: List<String>,
  onResult: (ScannedFixture?, String) -> Unit,
  onError: (String) -> Unit,
) {
  val bmp: Bitmap? = runCatching { previewView.bitmap }.getOrNull()
  if (bmp == null) {
    onError("Could not capture preview frame. Try System Camera or From Photo.")
    return
  }
  val input = InputImage.fromBitmap(bmp, 0)
  recognizer.process(input)
    .addOnSuccessListener { visionText ->
      val raw = visionText.text
      onResult(FixtureOcrParser.parse(raw, knownTeams), raw)
    }
    .addOnFailureListener { e ->
      onError(e.localizedMessage ?: "Text recognition failed on frame.")
    }
}

fun runOcrOnUri(
  context: Context,
  uri: Uri,
  recognizer: TextRecognizer,
  knownTeams: List<String>,
  onResult: (ScannedFixture?, String) -> Unit,
  onError: (String) -> Unit,
) {
  try {
    val input = InputImage.fromFilePath(context, uri)
    recognizer.process(input)
      .addOnSuccessListener { visionText ->
        val raw = visionText.text
        onResult(FixtureOcrParser.parse(raw, knownTeams), raw)
      }
      .addOnFailureListener { e ->
        onError(e.localizedMessage ?: "Failed to scan image.")
      }
  } catch (e: Exception) {
    onError(e.localizedMessage ?: "Could not open image.")
  }
}

fun createTempCameraScanUri(context: Context): Uri {
  val dir = File(context.cacheDir, "camera_scans").apply { mkdirs() }
  val file = File(dir, "fixture_scan_${System.currentTimeMillis()}.jpg")
  return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
