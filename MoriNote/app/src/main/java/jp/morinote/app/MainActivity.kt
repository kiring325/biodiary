package jp.morinote.app

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import coil3.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Spring = Color(0xFFD98298)
private val Summer = Color(0xFF4E986E)
private val Autumn = Color(0xFFD99648)
private val Winter = Color(0xFF6D9FBF)
private val Forest = Color(0xFF426B50)
private val Paper = Color(0xFFF7F7F2)
private val Season.color: Color
    get() = when (this) {
        Season.SPRING -> Spring
        Season.SUMMER -> Summer
        Season.AUTUMN -> Autumn
        Season.WINTER -> Winter
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Forest, background = Paper, surface = Color.White)) {
                NatureAlbumApp()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NatureAlbumApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val observations = remember { mutableStateListOf<Observation>() }
    val enabledSeasons = remember { mutableStateListOf(Season.SPRING) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { observations.addAll(ObservationStore.load(context)) }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pendingPhoto = uri
    }
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) pendingPhoto = cameraUri
    }

    val selected = observations.firstOrNull { it.id == selectedId }
    val visibleObservations = observations.filter { it.season in enabledSeasons }
    var mapPins by remember { mutableStateOf<List<MapPin>>(emptyList()) }
    LaunchedEffect(visibleObservations) {
        mapPins = withContext(Dispatchers.IO) {
            visibleObservations.map { observation ->
                MapPin(
                    id = observation.id,
                    latitude = observation.latitude,
                    longitude = observation.longitude,
                    seasonColor = observation.season.color.toMapHex(),
                    photoDataUrl = photoDataUrl(observation.photoPath),
                )
            }
        }
    }

    Scaffold(containerColor = Paper, topBar = {
        TopAppBar(title = {
            Column {
                Text("mori note", fontWeight = FontWeight.SemiBold)
                Text("わたしの自然アルバム", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text("日本の観察記録", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text("季節を重ねて見る", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                }
                Text("${visibleObservations.size}件", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(Season.entries) { season ->
                    val active = season in enabledSeasons
                    FilterChip(
                        selected = active,
                        onClick = {
                            if (active) enabledSeasons.remove(season) else enabledSeasons.add(season)
                        },
                        label = { Text("${season.label}  ${season.range}") },
                        border = if (active) BorderStroke(1.dp, season.color) else null,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp)) {
                OsmMap(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)),
                    pins = mapPins,
                    onMapTap = {},
                    onPinTap = { selectedId = it },
                )
                if (observations.isEmpty()) {
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 34.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .94f)),
                    ) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("最初の観察を記録しましょう", fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(5.dp))
                            Text("撮影した写真は、季節色の写真ピンになります", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
            selected?.let { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, item.season.color.copy(alpha = .65f)),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = File(item.photoPath), contentDescription = item.name, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(11.dp)).border(3.dp, item.season.color, RoundedCornerShape(11.dp)),
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.place.ifBlank { "撮影地点" } + " · " + formatDate(item.dateMillis), style = MaterialTheme.typography.labelSmall, color = item.season.color)
                            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text("${item.season.label}の記録", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            } ?: Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { showPhotoMenu = true }, modifier = Modifier.weight(1f)) { Text("写真を追加") }
                Button(onClick = { showPhotoMenu = true }, modifier = Modifier.weight(1f)) { Text("観察を記録") }
            }
        }
    }

    if (showPhotoMenu) {
        AlertDialog(
            onDismissRequest = { showPhotoMenu = false },
            title = { Text("写真を登録") },
            text = { Text("新しく撮影するか、端末の写真から選べます。") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        showPhotoMenu = false
                        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File(directory, "${UUID.randomUUID()}.jpg")
                        file.createNewFile()
                        cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        cameraCapture.launch(cameraUri!!)
                    }) { Text("カメラで撮影") }
                    TextButton(onClick = {
                        showPhotoMenu = false
                        galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("端末の写真から選ぶ") }
                }
            },
            dismissButton = { TextButton(onClick = { showPhotoMenu = false }) { Text("キャンセル") } },
        )
    }

    pendingPhoto?.let { uri ->
        ObservationEditor(
            photoUri = uri,
            onDismiss = { pendingPhoto = null },
            onSave = { name, place, dateMillis, location ->
                val draft = Observation(
                    id = ObservationStore.newId(), name = name, photoPath = "", dateMillis = dateMillis,
                    latitude = location.latitude, longitude = location.longitude, place = place,
                )
                scope.launch {
                    runCatching { ObservationStore.add(context, uri, draft) }
                        .onSuccess { saved -> observations.add(saved); selectedId = saved.id }
                    pendingPhoto = null
                }
            },
        )
    }
}

@Composable
private fun ObservationEditor(
    photoUri: Uri,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, GeoPoint) -> Unit,
) {
    val context = LocalContext.current
    val metadata = remember(photoUri) { readPhotoMetadata(context, photoUri) }
    val labeler = remember { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    var name by remember(photoUri) { mutableStateOf("") }
    var place by remember(photoUri) { mutableStateOf("") }
    var dateMillis by remember(photoUri) { mutableStateOf(metadata.dateMillis) }
    var location by remember(photoUri) { mutableStateOf(metadata.location) }
    var suggestions by remember(photoUri) { mutableStateOf<List<String>>(emptyList()) }
    var loadingSuggestions by remember { mutableStateOf(false) }

    DisposableEffect(labeler) { onDispose { labeler.close() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("観察を記録") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AsyncImage(
                    model = photoUri, contentDescription = "登録する写真", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("生きものの名前") }, placeholder = { Text("例：アゲハチョウ") }, singleLine = true,
                )
                TextField(
                    value = place, onValueChange = { place = it }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    label = { Text("場所のメモ（任意）") }, placeholder = { Text("例：井の頭公園") }, singleLine = true,
                )
                Text("名前は手入力できます。写真から候補を出すこともできます。", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 5.dp))
                OutlinedButton(
                    onClick = {
                        loadingSuggestions = true
                        runCatching { InputImage.fromFilePath(context, photoUri) }
                            .onSuccess { image ->
                                labeler.process(image)
                                    .addOnSuccessListener { labels ->
                                        suggestions = labels.sortedByDescending { it.confidence }
                                            .take(5).map { localLabel(it.text) }.distinct()
                                        loadingSuggestions = false
                                    }
                                    .addOnFailureListener { loadingSuggestions = false }
                            }
                            .onFailure { loadingSuggestions = false }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    enabled = !loadingSuggestions,
                ) { Text(if (loadingSuggestions) "候補を探しています…" else "写真から名前の候補を探す") }
                if (suggestions.isNotEmpty()) {
                    Text("候補をタップして入力", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 7.dp))
                    suggestions.forEach { candidate ->
                        TextButton(onClick = { name = candidate }) { Text(candidate) }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text("撮影日：${formatDate(dateMillis)} · ${Season.fromDate(dateMillis).label}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(if (location == null) "場所を地図でタップして指定してください" else "場所をタップして調整できます", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                LocationPickerMap(location = location, onLocationChange = { location = it })
                if (metadata.location != null) {
                    Text("写真の位置情報を読み取りました", style = MaterialTheme.typography.labelSmall, color = Forest, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { location?.let { onSave(name.trim(), place.trim(), dateMillis, it) } }, enabled = name.isNotBlank() && location != null) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}

@Composable
private fun LocationPickerMap(location: GeoPoint?, onLocationChange: (GeoPoint) -> Unit) {
    val initial = location ?: GeoPoint(36.2, 138.25)
    OsmMap(
        modifier = Modifier.fillMaxWidth().height(205.dp).padding(top = 6.dp).clip(RoundedCornerShape(14.dp)),
        initialCenter = initial,
        initialZoom = if (location == null) 5.2f else 12f,
        pins = location?.let { listOf(MapPin("selected", it.latitude, it.longitude, "#426B50", null)) }.orEmpty(),
        onMapTap = onLocationChange,
        onPinTap = {},
    )
}

@Composable
private fun OsmMap(
    modifier: Modifier,
    initialCenter: GeoPoint = GeoPoint(36.2, 138.25),
    initialZoom: Float = 5.15f,
    pins: List<MapPin>,
    onMapTap: (GeoPoint) -> Unit,
    onPinTap: (String) -> Unit,
) {
    val context = LocalContext.current
    val latestOnMapTap by rememberUpdatedState(onMapTap)
    val latestOnPinTap by rememberUpdatedState(onPinTap)
    var mapReady by remember { mutableStateOf(false) }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "MoriNote/0.1.0 (Android)"
            addJavascriptInterface(object {
                private val mainHandler = Handler(Looper.getMainLooper())
                @JavascriptInterface fun onMapReady() { mainHandler.post { mapReady = true } }
                @JavascriptInterface fun onMapTap(latitude: Double, longitude: Double) {
                    mainHandler.post { latestOnMapTap(GeoPoint(latitude, longitude)) }
                }
                @JavascriptInterface fun onPinTap(id: String) { mainHandler.post { latestOnPinTap(id) } }
            }, "MoriNote")
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)

                override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(Uri.parse(url))
            }
            loadUrl("https://appassets.androidplatform.net/assets/map.html")
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.stopLoading(); webView.destroy() }
    }
    LaunchedEffect(webView, mapReady) {
        if (mapReady) {
            webView.evaluateJavascript(
                "window.moriSetCenter(${initialCenter.latitude},${initialCenter.longitude},$initialZoom);",
                null,
            )
        }
    }
    LaunchedEffect(webView, mapReady, pins) {
        if (mapReady) {
            val json = org.json.JSONArray().apply {
                pins.forEach { pin ->
                    put(org.json.JSONObject().apply {
                        put("id", pin.id)
                        put("lat", pin.latitude)
                        put("lng", pin.longitude)
                        put("color", pin.seasonColor)
                        put("photo", pin.photoDataUrl ?: org.json.JSONObject.NULL)
                    })
                }
            }.toString()
            webView.evaluateJavascript("window.moriSetPins(JSON.parse(${org.json.JSONObject.quote(json)}));", null)
        }
    }
    AndroidView(factory = { webView }, modifier = modifier)
}

private fun photoDataUrl(path: String): String? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 240)
    }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
    val bytes = java.io.ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 72, output)
        bitmap.recycle()
        output.toByteArray()
    }
    "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}.getOrNull()

private fun Color.toMapHex(): String = String.format(Locale.US, "#%06X", 0xFFFFFF and toArgb())

private data class GeoPoint(val latitude: Double, val longitude: Double)
private data class MapPin(val id: String, val latitude: Double, val longitude: Double, val seasonColor: String, val photoDataUrl: String?)
private data class PhotoMetadata(val dateMillis: Long, val location: GeoPoint?)

private fun readPhotoMetadata(context: android.content.Context, uri: Uri): PhotoMetadata {
    return runCatching {
        context.contentResolver.openInputStream(uri).use { stream ->
            val exif = ExifInterface(requireNotNull(stream))
            val dateString = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val parsedDate = dateString?.let {
                runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time }.getOrNull()
            } ?: System.currentTimeMillis()
            val coordinates = FloatArray(2)
            val hasLocation = exif.getLatLong(coordinates)
            val latLng = if (hasLocation) GeoPoint(coordinates[0].toDouble(), coordinates[1].toDouble()) else null
            PhotoMetadata(parsedDate, latLng)
        }
    }.getOrElse { PhotoMetadata(System.currentTimeMillis(), null) }
}

private fun localLabel(label: String): String = when (label.lowercase(Locale.ROOT)) {
    "bird" -> "鳥"
    "animal" -> "動物"
    "insect" -> "昆虫"
    "butterfly" -> "チョウ"
    "flower", "petal" -> "花"
    "plant", "tree" -> "植物"
    "mushroom" -> "きのこ"
    "fish" -> "魚"
    "reptile" -> "は虫類"
    "amphibian" -> "両生類"
    else -> label
}

private fun formatDate(timeMillis: Long): String =
    SimpleDateFormat("yyyy年M月d日", Locale.JAPAN).format(Date(timeMillis))
