package com.dub.calorietracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dub.calorietracker.ai.ImageUtils
import com.dub.calorietracker.R
import androidx.compose.ui.res.painterResource
import com.dub.calorietracker.calc.WeekPlanner
import com.dub.calorietracker.data.FoodMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * The screen the app opens on: a live camera block in the middle, a note box underneath,
 * send at the bottom right and X at the top left to go to today's totals.
 */
@Composable
fun CaptureScreen(vm: AppViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val busyMessage by vm.busyMessage.collectAsStateWithLifecycle()

    var note by rememberSaveable { mutableStateOf("") }
    // Photo URIs, newline-separated so it survives rotation via rememberSaveable.
    var photosRaw by rememberSaveable { mutableStateOf("") }
    val photos = photosRaw.split('\n').filter { it.isNotEmpty() }
    fun setPhotos(list: List<String>) { photosRaw = list.take(AppViewModel.MAX_PHOTOS).joinToString("\n") }
    val canAddMore = photos.size < AppViewModel.MAX_PHOTOS
    /** Index of the photo shown big in the block; null = live camera. */
    var viewing by remember { mutableStateOf<Int?>(null) }
    val bitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    var capturing by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) askPermission.launch(Manifest.permission.CAMERA) }

    val controller = remember {
        LifecycleCameraController(ctx).apply { setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE) }
    }
    LaunchedEffect(hasPermission) { if (hasPermission) controller.bindToLifecycle(lifecycleOwner) }

    // Decode any photo we haven't shown yet.
    LaunchedEffect(photosRaw) {
        photos.filter { it !in bitmaps }.forEach { u ->
            withContext(Dispatchers.IO) { runCatching { ImageUtils.loadPreview(ctx, Uri.parse(u)) }.getOrNull() }
                ?.let { bitmaps[u] = it }
        }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AppViewModel.MAX_PHOTOS)
    ) { uris -> if (uris.isNotEmpty()) { setPhotos(photos + uris.map { it.toString() }); viewing = null } }

    fun removePhoto(i: Int) {
        val u = photos[i]
        // Camera shots live in our private cache — delete the file; gallery picks are left untouched.
        if (u.startsWith("file:")) runCatching { File(Uri.parse(u).path!!).delete() }
        bitmaps.remove(u)
        setPhotos(photos.filterIndexed { j, _ -> j != i })
        viewing = null
    }

    fun takePhoto() {
        if (capturing || !canAddMore) return
        capturing = true
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    setPhotos(photos + Uri.fromFile(file).toString())
                }
                override fun onError(e: ImageCaptureException) {
                    capturing = false
                    vm.error.value = "Couldn't take the photo: ${e.message}"
                }
            },
        )
    }

    fun send() {
        if (photos.isNotEmpty()) vm.estimateFromPhotos(photos.map(Uri::parse), note)
        else vm.estimateFromText(note)
    }

    BackHandler(onBack = { if (viewing != null) viewing = null else onClose() })

    val now = System.currentTimeMillis()
    val coolingDown = state.ai.cooldownUntil > now
    val today = LocalDate.now()
    val eaten = WeekPlanner.eaten(state.entries, today).calories
    val target = WeekPlanner.dayTarget(state, today)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp)) {
            // ----- top bar: X on the left, today's total on the right -----
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", Modifier.size(28.dp)) }
                Spacer(Modifier.weight(1f))
                if (state.plan != null) {
                    Text("Today  ", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${num(eaten)} / ${num(target)} cal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))

            // ----- camera block -----
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // Show a photo big when one is tapped, or the last one once the limit is reached.
                val bigIndex = viewing ?: if (!canAddMore) photos.lastIndex else null
                val big = bigIndex?.let { photos.getOrNull(it) }?.let { bitmaps[it] }
                when {
                    big != null -> Image(big.asImageBitmap(), "Your photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    hasPermission -> AndroidView(
                        factory = { c ->
                            PreviewView(c).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                this.controller = controller
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("Camera permission is needed to take food photos.", color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { askPermission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    }
                }

                // Thumbnails along the top: tap to view, × to remove.
                if (photos.isNotEmpty()) Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    photos.forEachIndexed { i, u ->
                        Box(Modifier.size(64.dp)) {
                            val bmp = bitmaps[u]
                            Box(
                                Modifier.matchParentSize().clip(RoundedCornerShape(12.dp))
                                    .background(Color.DarkGray)
                                    .border(if (bigIndex == i) 3.dp else 2.dp, if (bigIndex == i) AccentBlue else Color.White, RoundedCornerShape(12.dp))
                                    .clickable { viewing = if (viewing == i) null else i },
                            ) {
                                if (bmp != null) Image(bmp.asImageBitmap(), "Photo ${i + 1}", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                            }
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(22.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.75f)).clickable { removePhoto(i) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Close, "Remove photo ${i + 1}", Modifier.size(14.dp), tint = Color.White) }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${photos.size}/${AppViewModel.MAX_PHOTOS}", color = Color.White, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 4.dp))
                }

                // Bottom controls inside the block
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val hint = when {
                        viewing != null -> "Tap the photo again to go back to the camera"
                        !canAddMore -> "That's the max — send it, or remove one to retake"
                        photos.isNotEmpty() -> "Add another angle, or send"
                        else -> null
                    }
                    if (hint != null) {
                        Text(hint, color = Color.White, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 4.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(
                            enabled = canAddMore,
                            onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
                        ) { Icon(Icons.Filled.PhotoLibrary, "Pick from gallery") }
                        Spacer(Modifier.weight(1f))
                        if (hasPermission && canAddMore && viewing == null) {
                            // Shutter
                            Box(
                                Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))
                                    .border(4.dp, Color.White, CircleShape).clickable(enabled = !capturing) { takePhoto() },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (capturing) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White)
                                else Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White))
                            }
                        } else Spacer(Modifier.size(72.dp))
                        Spacer(Modifier.weight(1f))
                        FilledIconButton(
                            onClick = { scanFoodBarcode(ctx, onCode = vm::lookupBarcode, onError = { vm.error.value = it }) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
                        ) { Icon(painterResource(R.drawable.ic_barcode), "Scan a barcode") }
                    }
                }

                if (busy) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            Text(busyMessage.ifBlank { "Estimating calories…" }, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (coolingDown) {
                Text(
                    "AI limit reached — photos work again in ${AppViewModel.formatWait(state.ai.cooldownUntil - now)}. " +
                        "You can still type a saved food.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // ----- note + send -----
            Spacer(Modifier.height(12.dp))
            val suggestions = if (photos.isEmpty()) FoodMemory.suggestions(note, state.savedFoods, 3) else emptyList()
            suggestions.forEach { f ->
                Text(
                    "↳ ${f.name} · ${num(f.calories)} cal",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { vm.useSaved(f, note) }.padding(vertical = 6.dp, horizontal = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (photos.isNotEmpty()) "Add details (e.g. \"about 6 oz, fried in oil\")"
                        else "Take a photo, or just type what you ate")
                    },
                    minLines = 1, maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                )
                FilledIconButton(
                    onClick = { send() },
                    enabled = !busy && (photos.isNotEmpty() || note.isNotBlank()),
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    }
}
