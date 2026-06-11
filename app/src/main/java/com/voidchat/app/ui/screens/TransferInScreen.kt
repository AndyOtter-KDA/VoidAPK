package com.voidchat.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.voidchat.app.crypto.IdentityManager
import com.voidchat.app.ui.theme.*
import com.voidchat.app.data.local.AppDatabase
import com.voidchat.app.data.models.LocalIdentity
import com.voidchat.app.data.local.PreferencesManager
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.UUID

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)
        )
        setHints(hints)
    }

    override fun analyze(image: ImageProxy) {
        val planes = image.planes
        if (planes.isEmpty()) {
            image.close()
            return
        }

        val buffer: ByteBuffer = planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val width = image.width
        val height = image.height

        val source = PlanarYUVLuminanceSource(
            data,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        try {
            val result = reader.decode(binaryBitmap)
            val code = result.text
            if (code != null) {
                onQrCodeScanned(code)
            }
        } catch (e: Exception) {
            // Decoded nothing or error
        } finally {
            image.close()
        }
    }
}

@Composable
fun CameraPreview(
    onQrCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderErrorState = remember { mutableStateOf<String?>(null) }
    
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(key1 = previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Preview setup
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Selector setup
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // ImageAnalysis setup
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                
                imageAnalysis.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    QrCodeAnalyzer { code ->
                        onQrCodeScanned(code)
                    }
                )

                // Unbind any previous bind
                cameraProvider.unbindAll()

                // Bind to lifecycle
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                cameraProviderErrorState.value = e.message
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        if (cameraProviderErrorState.value != null) {
            Text(
                text = "Camera Failed: ${cameraProviderErrorState.value}",
                color = Color.Red,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(16.dp).align(Alignment.Center)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferInScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var recoveryCodeInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    // Read camera permission status
    var hasCameraPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission required for QR scan.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(key1 = hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Function to parse the recovery code string and restore identity
    fun handleCodeRestoration(cleanCode: String) {
        if (isImporting) return
        isImporting = true
        scope.launch {
            try {
                val res = IdentityManager.restoreFromRecoveryCode(cleanCode)
                res.fold(
                    onSuccess = { displayId ->
                        val db = AppDatabase.getDatabase(context)
                        val prefs = PreferencesManager(context)
                        val username = prefs.username ?: "recovered_node"
                        val pubKeyBase64 = IdentityManager.getPublicKeyBase64() ?: ""

                        val localIdentity = LocalIdentity(
                            id = UUID.randomUUID().toString(),
                            keyPairAlias = "void_identity",
                            publicKeyBase64 = pubKeyBase64,
                            displayId = displayId,
                            username = username,
                            recoveryPhraseHash = UUID.randomUUID().toString().hashCode().toString(),
                            createdAt = System.currentTimeMillis(),
                            deviceName = android.os.Build.MODEL
                        )
                        db.identityDao().insertIdentity(localIdentity)
                        prefs.username = username

                        Toast.makeText(context, "Identity restored. Welcome back, $username.", Toast.LENGTH_LONG).show()
                        onNavigateToHome()
                    },
                    onFailure = {
                        Toast.makeText(context, "Invalid recovery code. Check code and try again.", Toast.LENGTH_LONG).show()
                        isImporting = false
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Handshake failed: ${e.message}", Toast.LENGTH_LONG).show()
                isImporting = false
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = VoidBlack,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "IMPORT SYSTEM NODE TERMINAL",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidBlack),
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("transfer_in_back_button")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                        }
                    }
                )
                Divider(color = BorderDark, thickness = 1.dp)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScanlineOverlay()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Point camera sensor to the Migration QR displayed on your old device or paste the recovery code raw value below.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Camera Scan Finder Port
                Surface(
                    color = VoidDarkNavy,
                    border = BorderStroke(1.dp, NeonCyan),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .size(260.dp)
                        .padding(vertical = 16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPermission) {
                            CameraPreview(
                                onQrCodeScanned = { decodedString ->
                                    if (!isImporting) {
                                        android.util.Log.d("VoidChatScan", "Decoded scanned: $decodedString")
                                        handleCodeRestoration(decodedString.trim())
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .background(VoidDarkNavy),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Camera Permission Required",
                                    color = ErrorRed,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "GRANT",
                                        fontFamily = FontFamily.Monospace,
                                        color = VoidBlack,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Overlay corners lines
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val l = 30.dp.toPx()
                            val t = 4.dp.toPx()
                            // Top Left
                            drawLine(color = NeonCyan, start = Offset(0f, 0f), end = Offset(l, 0f), strokeWidth = t)
                            drawLine(color = NeonCyan, start = Offset(0f, 0f), end = Offset(0f, l), strokeWidth = t)

                            // Top Right
                            drawLine(color = NeonCyan, start = Offset(size.width, 0f), end = Offset(size.width - l, 0f), strokeWidth = t)
                            drawLine(color = NeonCyan, start = Offset(size.width, 0f), end = Offset(size.width, l), strokeWidth = t)

                            // Bottom Left
                            drawLine(color = NeonCyan, start = Offset(0f, size.height), end = Offset(l, size.height), strokeWidth = t)
                            drawLine(color = NeonCyan, start = Offset(0f, size.height), end = Offset(0f, size.height - l), strokeWidth = t)

                            // Bottom Right
                            drawLine(color = NeonCyan, start = Offset(size.width, size.height), end = Offset(size.width - l, size.height), strokeWidth = t)
                            drawLine(color = NeonCyan, start = Offset(size.width, size.height), end = Offset(size.width, size.height - l), strokeWidth = t)
                        }

                        if (isImporting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(VoidBlack.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = NeonCyan)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "RESTORING SYSTEM...",
                                        color = NeonCyan,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                                    .background(VoidBlack.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "[ Scanning QR... ]",
                                    color = HotPinkLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = recoveryCodeInput,
                        onValueChange = { recoveryCodeInput = it },
                        label = { Text("OR PASTE MIGRATION RECOVERY CODE", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_in_code_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val cleanCode = recoveryCodeInput.trim()
                            if (cleanCode.isEmpty()) {
                                Toast.makeText(context, "Please scan or paste the recovery code first", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            handleCodeRestoration(cleanCode)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("transfer_in_restore_button")
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(color = VoidBlack, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = "MIGRATE IDENTITY",
                                fontFamily = FontFamily.Monospace,
                                color = VoidBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
