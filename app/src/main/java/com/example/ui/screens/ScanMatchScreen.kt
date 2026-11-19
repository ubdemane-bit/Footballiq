package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.engine.FootballIqModel
import com.example.engine.MatchInput
import com.example.engine.MathUtils
import com.example.engine.PredictionOutcome
import com.example.engine.ScannedFixture
import com.example.ui.components.BoundCameraHandle
import com.example.ui.components.CameraScannerPreview
import com.example.ui.components.ConfidenceBadge
import com.example.ui.components.ProbabilitySegmentedBar
import com.example.ui.components.ScannerReticleOverlay
import com.example.ui.components.captureAndScanFrame
import com.example.ui.components.createTempCameraScanUri
import com.example.ui.components.runOcrOnUri
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@Composable
fun ScanMatchScreen(
  matches: List<MatchInput>,
  knownTeams: List<String>,
  isBusy: Boolean,
  onLoadSampleSeason: () -> Unit,
  onOpenFullPrediction: (home: String, away: String) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasCameraPermission =
          ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasCameraPermission = granted
  }

  val recognizer = remember {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }
  DisposableEffect(Unit) {
    onDispose { recognizer.close() }
  }

  var scannedFixture by remember { mutableStateOf<ScannedFixture?>(null) }
  var lastOcrPreview by remember { mutableStateOf<String?>(null) }
  var scanError by remember { mutableStateOf<String?>(null) }
  var isProcessingImage by remember { mutableStateOf(false) }
  var isLiveAutoScan by remember { mutableStateOf(true) }
  var torchEnabled by remember { mutableStateOf(false) }
  var preferBackCamera by remember { mutableStateOf(true) }
  var cameraRetryNonce by remember { mutableIntStateOf(0) }
  var boundHandle by remember { mutableStateOf<BoundCameraHandle?>(null) }
  var pendingSystemCameraUri by remember { mutableStateOf<Uri?>(null) }

  val handleOcrUriResult: (Uri?) -> Unit = { uri ->
    if (uri != null) {
      isProcessingImage = true
      scanError = null
      runOcrOnUri(
        context = context,
        uri = uri,
        recognizer = recognizer,
        knownTeams = knownTeams,
        onResult = { fixture, rawText ->
          isProcessingImage = false
          lastOcrPreview = rawText.take(160).ifBlank { null }
          if (fixture != null) {
            scannedFixture = fixture
            isLiveAutoScan = false
          } else {
            scanError = "Could not detect two teams in that image. Try a clearer fixture card or scoreboard."
          }
        },
        onError = { msg ->
          isProcessingImage = false
          scanError = msg
        },
      )
    }
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri: Uri? ->
    handleOcrUriResult(uri)
  }

  val systemCameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture(),
  ) { success: Boolean ->
    val uri = pendingSystemCameraUri
    pendingSystemCameraUri = null
    if (success && uri != null) {
      handleOcrUriResult(uri)
    }
  }

  val launchSystemCamera: () -> Unit = {
    runCatching { createTempCameraScanUri(context) }
      .onSuccess { uri ->
        pendingSystemCameraUri = uri
        systemCameraLauncher.launch(uri)
      }
      .onFailure { err ->
        scanError = err.localizedMessage ?: "Unable to launch system camera."
      }
  }

  val model = remember(matches) {
    if (matches.isNotEmpty()) FootballIqModel(matches) else null
  }

  val livePrediction = remember(scannedFixture, model) {
    val fix = scannedFixture ?: return@remember null
    model?.predict(fix.homeTeam, fix.awayTeam)
      ?: PredictionOutcome.InsufficientData(
        home = fix.homeTeam,
        away = fix.awayTeam,
        homeGames = 0,
        awayGames = 0,
      )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (knownTeams.size < 2) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "Load match history to unlock instant predictions after scanning.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          FilledTonalButton(
            onClick = onLoadSampleSeason,
            enabled = !isBusy,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("scan_load_sample_button"),
          ) {
            Icon(Icons.Filled.Dataset, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Load 132-Match Dataset")
          }
        }
      }
    }

    // Viewfinder Card
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(20.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "LIVE FIXTURE OCR SCANNER",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Point at a match poster, TV banner, or fixture list",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (hasCameraPermission) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (boundHandle?.hasMultipleCameras == true) {
                IconButton(
                  onClick = {
                    torchEnabled = false
                    preferBackCamera = !preferBackCamera
                    scanError = null
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("scan_switch_camera_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = "Switch front or back camera",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              if (boundHandle?.camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(
                  onClick = {
                    val next = !torchEnabled
                    torchEnabled = next
                    boundHandle?.camera?.cameraControl?.enableTorch(next)
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("scan_torch_button"),
                ) {
                  Icon(
                    imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (torchEnabled) "Disable camera flash" else "Enable camera flash",
                    tint = if (torchEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        if (hasCameraPermission) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(250.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.Black),
          ) {
            CameraScannerPreview(
              recognizer = recognizer,
              knownTeams = knownTeams,
              isAutoScanActive = isLiveAutoScan && scannedFixture == null,
              preferBackCamera = preferBackCamera,
              retryNonce = cameraRetryNonce,
              onCameraBound = { handle ->
                boundHandle = handle
                scanError = null
              },
              onLiveTextDetected = { rawText ->
                if (rawText.isNotBlank()) {
                  lastOcrPreview = rawText.take(140)
                }
              },
              onFixtureDetected = { fixture ->
                scannedFixture = fixture
                scanError = null
                isLiveAutoScan = false
              },
              onCameraError = { msg ->
                boundHandle = null
                scanError = msg
              },
            )

            ScannerReticleOverlay(
              isLocked = scannedFixture != null,
              primaryColor = MaterialTheme.colorScheme.primary,
              modifier = Modifier.fillMaxSize(),
            )

            Surface(
              color = Color.Black.copy(alpha = 0.72f),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp),
            ) {
              val lensLabel = when (boundHandle?.activeLensFacing) {
                CameraSelector.LENS_FACING_FRONT -> "Front"
                CameraSelector.LENS_FACING_BACK -> "Back"
                else -> "Camera"
              }
              Text(
                text = when {
                  isProcessingImage -> "Scanning frame..."
                  scannedFixture != null -> "Match locked: ${scannedFixture!!.homeTeam} vs ${scannedFixture!!.awayTeam}"
                  boundHandle == null -> "Initializing camera viewfinder..."
                  else -> "$lensLabel camera ready · Point at 'Team A vs Team B'"
                },
                style = MonoMetricSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                isProcessingImage = true
                scanError = null
                captureAndScanFrame(
                  context = context,
                  handle = boundHandle,
                  recognizer = recognizer,
                  knownTeams = knownTeams,
                  onResult = { fixture, rawText ->
                    isProcessingImage = false
                    lastOcrPreview = rawText.take(160).ifBlank { null }
                    if (fixture != null) {
                      scannedFixture = fixture
                      isLiveAutoScan = false
                    } else {
                      scanError = "No match pair recognized in captured photo. Try aligning the two team names inside the brackets."
                    }
                  },
                  onError = { msg ->
                    isProcessingImage = false
                    scanError = msg
                  },
                )
              },
              enabled = !isProcessingImage,
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .semantics { }
                .testTag("capture_scan_button"),
            ) {
              if (isProcessingImage) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
              }
              Spacer(modifier = Modifier.width(6.dp))
              Text("Capture & Scan")
            }

            OutlinedButton(
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              enabled = !isProcessingImage,
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("pick_photo_scan_button"),
            ) {
              Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("From Photo")
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(
              onClick = {
                scanError = null
                cameraRetryNonce++
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("retry_camera_button"),
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Restart Camera")
            }
            TextButton(
              onClick = launchSystemCamera,
              enabled = !isProcessingImage,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("system_camera_button"),
            ) {
              Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Use System Camera")
            }
          }
        } else {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerLow)
              .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Camera permission needed",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
              )
            }
            Text(
              text = "Enable Camera to Scan Fixtures",
              style = MaterialTheme.typography.titleMedium,
              textAlign = TextAlign.Center,
            )
            Text(
              text = "Grant camera access to scan match posters, TV banners, or fixture tables on-device, use the system camera, or pick an existing screenshot.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("request_camera_permission_button"),
            ) {
              Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Enable Camera Scanner")
            }
            OutlinedButton(
              onClick = launchSystemCamera,
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("system_camera_fallback_button"),
            ) {
              Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Use System Camera")
            }
            OutlinedButton(
              onClick = {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("pick_photo_fallback_button"),
            ) {
              Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Scan Fixture Screenshot")
            }
          }
        }
      }
    }

    if (scanError != null) {
      Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = scanError!!,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(12.dp),
        )
      }
    }

    val detected = scannedFixture
    if (detected != null) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
          .testTag("scanned_prediction_card"),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "SCANNED MATCHUP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "${detected.homeTeam} vs ${detected.awayTeam}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              FilledTonalIconButton(
                onClick = {
                  scannedFixture = detected.copy(
                    homeTeam = detected.awayTeam,
                    awayTeam = detected.homeTeam,
                  )
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("scan_swap_teams_button"),
              ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap home and away")
              }
              FilledTonalIconButton(
                onClick = {
                  scannedFixture = null
                  scanError = null
                  isLiveAutoScan = true
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("scan_again_button"),
              ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Scan another fixture")
              }
            }
          }

          when (val pred = livePrediction) {
            null -> Unit
            is PredictionOutcome.InsufficientData -> {
              Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
              ) {
                Icon(
                  imageVector = Icons.Filled.Info,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = pred.message,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = "${pred.home}: ${pred.homeGames}g · ${pred.away}: ${pred.awayGames}g (min ${pred.minRequired} matches per club required in dataset).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            is PredictionOutcome.Ready -> {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(
                    text = "EXPECTED GOALS (xG)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = "${MathUtils.formatFixed(pred.expectedHomeGoals, 2)} - ${MathUtils.formatFixed(pred.expectedAwayGoals, 2)}",
                    style = MonoMetricLarge,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = "MODAL SCORE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = pred.option3Advanced.correctScore,
                    style = MonoMetricLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                  )
                }
              }

              ProbabilitySegmentedBar(
                probHome = pred.probHome,
                probDraw = pred.probDraw,
                probAway = pred.probAway,
                homeName = pred.home,
                awayName = pred.away,
              )

              Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "Safe: ${pred.option1Safe.pick} (${MathUtils.formatFixed(pred.option1Safe.probability * 100, 1)}%)",
                      style = MonoMetricMedium,
                      color = MaterialTheme.colorScheme.primary,
                    )
                    ConfidenceBadge(pred.option1Safe.confidence)
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text(
                      text = "Smart: ${pred.option2Smart.pick} (${MathUtils.formatFixed(pred.option2Smart.probability * 100, 1)}%)",
                      style = MonoMetricMedium,
                      color = MaterialTheme.colorScheme.secondary,
                    )
                    ConfidenceBadge(pred.option2Smart.confidence)
                  }
                  Text(
                    text = "Half-Time Leader: ${pred.option3Advanced.halfTime} (${MathUtils.formatFixed(pred.option3Advanced.halfTimeProbability * 100, 1)}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              Button(
                onClick = { onOpenFullPrediction(pred.home, pred.away) },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .minimumInteractiveComponentSize()
                  .testTag("open_full_matrix_button"),
              ) {
                Icon(Icons.Filled.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Full Score Matrix & Seal")
              }
            }
          }
        }
      }
    } else if (!lastOcrPreview.isNullOrBlank()) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "LAST OCR TEXT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = lastOcrPreview!!,
            style = MonoMetricSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
