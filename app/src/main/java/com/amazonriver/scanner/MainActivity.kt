package com.amazonriver.scanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

private val RiverGreen = Color(0xFF12805C)
private val RiverGreenLight = Color(0xFF4FD6A0)
private val RiverDark = Color(0xFF0B1512)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RiverTheme { App() } }
    }
}

@Composable
fun RiverTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = RiverGreenLight, onPrimary = RiverDark,
        background = RiverDark, surface = Color(0xFF14231E), onSurface = Color(0xFFE6F2ED),
        onBackground = Color(0xFFE6F2ED)
    ) else lightColorScheme(
        primary = RiverGreen, onPrimary = Color.White,
        background = Color(0xFFF4FAF7), surface = Color.White, onSurface = Color(0xFF10211B),
        onBackground = Color(0xFF10211B)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App(vm: ScannerViewModel = viewModel()) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    var tab by remember { mutableIntStateOf(0) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Text(
                "AMAZON RIVER  ·  Scanner",
                modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 6.dp),
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Scanner") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Prova (${vm.kept})") })
            }
            if (tab == 0) ScannerTab(vm, granted) { launcher.launch(Manifest.permission.CAMERA) }
            else ExamTab(vm)
        }
    }
}

@Composable
fun ScannerTab(vm: ScannerViewModel, granted: Boolean, askPermission: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
            if (granted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        val view = PreviewView(c)
                        val future = ProcessCameraProvider.getInstance(c)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                            val selector = ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                                ).build()
                            val analysis = ImageAnalysis.Builder()
                                .setResolutionSelector(selector)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { it.setAnalyzer(ContextCompat.getMainExecutor(c), TextAnalyzer { l -> vm.onFrame(l) }) }
                            provider.unbindAll()
                            camera = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        }, ContextCompat.getMainExecutor(c))
                        view
                    }
                )
            } else {
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Permita o acesso à câmera para escanear.", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = askPermission) { Text("Permitir câmera") }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Color(0xCC000000)).padding(10.dp)
            ) {
                Text("${vm.kept} linhas aprovadas  ·  ${vm.cut} cortadas", color = RiverGreenLight, fontSize = 12.sp)
                if (vm.live.isNotBlank()) {
                    Text(vm.live.lines().takeLast(3).joinToString("\n"), color = Color.White, fontSize = 13.sp, maxLines = 3)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.auto, onCheckedChange = { vm.auto = it })
            Spacer(Modifier.width(8.dp))
            Text(if (vm.auto) "Contínuo: escrevendo sozinho" else "Manual: toque em Capturar", fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { vm.captureNow() }, modifier = Modifier.weight(1f)) { Text("Capturar") }
            OutlinedButton(onClick = {
                torch = !torch; camera?.cameraControl?.enableTorch(torch)
            }, modifier = Modifier.weight(1f)) { Text(if (torch) "Lanterna ON" else "Lanterna") }
            OutlinedButton(onClick = { vm.clear() }, modifier = Modifier.weight(1f)) { Text("Limpar") }
        }
    }
}

@Composable
fun ExamTab(vm: ScannerViewModel) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = vm.exam,
            onValueChange = { vm.onExamEdited(it) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            placeholder = { Text("Passe a câmera sobre o texto, devagar. A prova aparece aqui.") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("prova", vm.exam))
                Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.weight(1f)) { Text("Copiar") }
            OutlinedButton(onClick = {
                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, vm.exam) }
                ctx.startActivity(Intent.createChooser(i, "Compartilhar prova"))
            }, modifier = Modifier.weight(1f)) { Text("Enviar") }
            OutlinedButton(onClick = { vm.remount() }, modifier = Modifier.weight(1f)) { Text("Remontar") }
        }
    }
}
