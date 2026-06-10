package com.example.mediarecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mediarecorder.ui.theme.MediaRecorderTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaRecorderTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val micPermission = permissionsState.permissions.find { it.permission == android.Manifest.permission.RECORD_AUDIO }
    val locationPermission = permissionsState.permissions.find { it.permission == android.Manifest.permission.ACCESS_FINE_LOCATION }

    val hasMic = micPermission?.status?.isGranted == true
    val hasLocation = locationPermission?.status?.isGranted == true

    // Fetch location and weather as soon as location permission is granted
    LaunchedEffect(hasLocation) {
        if (hasLocation) {
            viewModel.fetchLocationAndWeather()
        }
    }

    // Dynamic background brush mapping weather status
    val backgroundBrush = when (viewModel.weatherStatus) {
        "Sunny" -> Brush.verticalGradient(
            colors = listOf(Color(0xFF13111C), Color(0xFF3A2426), Color(0xFF663B2F))
        )
        "Rainy" -> Brush.verticalGradient(
            colors = listOf(Color(0xFF0A0C16), Color(0xFF14243A), Color(0xFF1E3547))
        )
        "Cloudy" -> Brush.verticalGradient(
            colors = listOf(Color(0xFF0F0E13), Color(0xFF222030), Color(0xFF333045))
        )
        "Snowy" -> Brush.verticalGradient(
            colors = listOf(Color(0xFF070C1B), Color(0xFF1C2742), Color(0xFF405775))
        )
        else -> Brush.verticalGradient(
            colors = listOf(Color(0xFF0F0E17), Color(0xFF1B1A24))
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Title
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Humming Recorder",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFD0BCFF), Color(0xFF00E5FF))
                        )
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "AI 음악 변환 & 상황 정보 수집",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (!hasMic) {
                    PermissionRationaleScreen(
                        onRequestPermission = { permissionsState.launchMultiplePermissionRequest() },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // Weather Glassmorphic Widget
                    WeatherWidget(
                        locationName = viewModel.locationName,
                        temperature = viewModel.temperature,
                        weatherStatus = viewModel.weatherStatus,
                        recommendedGenre = viewModel.recommendedGenre,
                        hasLocationPermission = hasLocation,
                        onRequestLocationPermission = { permissionsState.launchMultiplePermissionRequest() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Genre Selector
                    GenreSelector(
                        selectedGenres = viewModel.selectedGenres,
                        recommendedGenre = viewModel.recommendedGenre,
                        onGenreToggle = { viewModel.toggleGenre(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Recording Panel (Visualizer + Buttons)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (viewModel.isRecording) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "dot")
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 0.2f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(800, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "dotAlpha"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF4D4D).copy(alpha = alpha))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = viewModel.recordingDuration,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "중앙 버튼을 눌러 허밍 시작",
                                    color = Color(0xFFD0BCFF).copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            AudioVisualizer(
                                amplitude = viewModel.amplitude,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            RecordButton(
                                isRecording = viewModel.isRecording,
                                amplitude = viewModel.amplitude,
                                onClick = { viewModel.toggleRecording() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Recordings & Converted List Tabs
                    RecordingsTabContainer(
                        recordings = viewModel.recordingsList,
                        convertedList = viewModel.convertedList,
                        selectedGenres = viewModel.selectedGenres,
                        onConvertClick = { viewModel.convertHummingToMusic(it) },
                        currentPlayingFile = viewModel.currentPlayingFile,
                        isPlaying = viewModel.isPlaying,
                        onItemClick = { viewModel.playFile(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f)
                    )
                }
            }

            // AI Conversion Loading Overlay Dialog
            if (viewModel.isConverting) {
                AIConversionDialog(
                    progressText = viewModel.conversionProgressText,
                    targetFile = viewModel.convertingFile?.name ?: ""
                )
            }

            // FR-05 Floating Glassmorphic Music Player
            val currentPlaying = viewModel.currentPlayingFile
            if (currentPlaying != null) {
                SmartMusicPlayer(
                    file = currentPlaying,
                    isPlaying = viewModel.isPlaying,
                    position = viewModel.playbackPosition,
                    duration = viewModel.playbackDuration,
                    onPlayPause = {
                        if (viewModel.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
                    },
                    onSeek = { viewModel.seekTo(it) },
                    onClose = { viewModel.stopPlayback() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                )
            }
        }
    }
}

@Composable
fun WeatherWidget(
    locationName: String,
    temperature: Double?,
    weatherStatus: String,
    recommendedGenre: String,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (hasLocationPermission) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = locationName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        val weatherIcon = when (weatherStatus) {
                            "Sunny" -> "☀️"
                            "Rainy" -> "🌧️"
                            "Cloudy" -> "☁️"
                            "Snowy" -> "❄️"
                            else -> "❓"
                        }
                        val weatherName = when (weatherStatus) {
                            "Sunny" -> "맑음"
                            "Rainy" -> "비"
                            "Cloudy" -> "흐림"
                            "Snowy" -> "눈"
                            else -> "분석 중"
                        }
                        Text(
                            text = "$weatherIcon $weatherName",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (temperature != null) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f°C", temperature),
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = "상황 맞춤 날씨 분석 대기 중",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "위치 권한 허용 시 주변 날씨에 맞는 음악 추천을 제공합니다.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Recommended Badge Card
            if (hasLocationPermission) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color(0xFF2C2A3D), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "날씨 추천 장르",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = recommendedGenre,
                        color = Color(0xFFD0BCFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Button(
                    onClick = onRequestLocationPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C2A3D)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "위치 승인",
                        color = Color(0xFFD0BCFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun GenreSelector(
    selectedGenres: Set<String>,
    recommendedGenre: String,
    onGenreToggle: (String) -> Unit
) {
    val genres = listOf("Pop", "Jazz", "Rock", "Classical", "Lo-Fi")

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "장르 선택 (최소 1개 선택)",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genres.forEach { genre ->
                val isSelected = selectedGenres.contains(genre)
                val isRecommended = genre == recommendedGenre

                val chipBgColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFFD0BCFF) else Color(0xFF1E1C2A),
                    label = "chipBg"
                )
                val chipTextColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF0F0E17) else Color.White,
                    label = "chipText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(chipBgColor)
                        .border(
                            width = 1.dp,
                            color = if (isRecommended) Color(0xFF00E5FF) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onGenreToggle(genre) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = genre,
                            color = chipTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isRecommended) {
                            Text(
                                text = "★추천",
                                color = if (isSelected) Color(0xFF0F0E17) else Color(0xFF00E5FF),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingsTabContainer(
    recordings: List<RecordingFile>,
    convertedList: List<ConvertedFile>,
    selectedGenres: Set<String>,
    onConvertClick: (RecordingFile) -> Unit,
    currentPlayingFile: ConvertedFile?,
    isPlaying: Boolean,
    onItemClick: (ConvertedFile) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Humming, 1: AI Converted

    Column(modifier = modifier) {
        // Tab Layout Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12111E), shape = RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            TabButton(
                title = "허밍 파일 (${recordings.size})",
                isActive = activeTab == 0,
                onClick = { activeTab = 0 },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                title = "AI 변환 곡 (${convertedList.size})",
                isActive = activeTab == 1,
                onClick = { activeTab = 1 },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content lists
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (activeTab == 0) {
                HummingList(
                    recordings = recordings,
                    selectedGenres = selectedGenres,
                    onConvertClick = onConvertClick
                )
            } else {
                ConvertedList(
                    convertedList = convertedList,
                    currentPlayingFile = currentPlayingFile,
                    isPlaying = isPlaying,
                    onItemClick = onItemClick
                )
            }
        }
    }
}

@Composable
fun TabButton(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) Color(0xFF2C2A3D) else Color.Transparent,
        label = "tabBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color.Gray,
        label = "tabText"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun HummingList(
    recordings: List<RecordingFile>,
    selectedGenres: Set<String>,
    onConvertClick: (RecordingFile) -> Unit
) {
    if (recordings.isEmpty()) {
        EmptyPlaceholder(text = "아직 녹음된 파일이 없습니다.\n마이크 버튼을 눌러 첫 허밍을 녹음해 보세요.")
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(recordings) { recording ->
                HummingItem(
                    recording = recording,
                    selectedGenres = selectedGenres,
                    onConvertClick = onConvertClick
                )
            }
        }
    }
}

@Composable
fun HummingItem(
    recording: RecordingFile,
    selectedGenres: Set<String>,
    onConvertClick: (RecordingFile) -> Unit
) {
    var showSnackBarMessage by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0xFFD0BCFF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(text = recording.dateStr, color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = recording.size, color = Color(0xFF00E5FF), fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // AI Convert Action Button
            IconButton(
                onClick = {
                    if (selectedGenres.isEmpty()) {
                        showSnackBarMessage = true
                    } else {
                        onConvertClick(recording)
                    }
                },
                modifier = Modifier
                    .background(Color(0xFF2C2A3D), CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Convert",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showSnackBarMessage) {
        AlertDialog(
            onDismissRequest = { showSnackBarMessage = false },
            title = { Text(text = "알림", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(text = "상단의 장르 목록에서 변환을 희망하는 장르를 최소 1개 이상 선택해 주세요.", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { showSnackBarMessage = false }) {
                    Text(text = "확인", color = Color(0xFF00E5FF))
                }
            },
            containerColor = Color(0xFF1E1C2A),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

@Composable
fun ConvertedList(
    convertedList: List<ConvertedFile>,
    currentPlayingFile: ConvertedFile?,
    isPlaying: Boolean,
    onItemClick: (ConvertedFile) -> Unit
) {
    if (convertedList.isEmpty()) {
        EmptyPlaceholder(text = "변환된 AI 음원이 없습니다.\n허밍 파일 카드의 별 모양(AI) 버튼을 눌러보세요.")
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(convertedList) { file ->
                val isCurrentPlaying = currentPlayingFile?.path == file.path
                ConvertedItem(
                    file = file,
                    isCurrentPlaying = isCurrentPlaying,
                    isPlaying = isCurrentPlaying && isPlaying,
                    onClick = { onItemClick(file) }
                )
            }
        }
    }
}

@Composable
fun ConvertedItem(
    file: ConvertedFile,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val cardColor = if (isCurrentPlaying) Color(0xFF1E3547) else Color(0xFF14243A)
    val iconColor = if (isCurrentPlaying) Color(0xFF00E5FF) else Color(0xFFD0BCFF)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(text = file.dateStr, color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = file.size, color = Color(0xFFD0BCFF), fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.VolumeUp,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun EmptyPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF12111E).copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.Gray.copy(alpha = 0.4f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text,
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun AIConversionDialog(
    progressText: String,
    targetFile: String
) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Nebula / Rotating AI Indicator
                val infiniteTransition = rememberInfiniteTransition(label = "aiRotate")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotate"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(80.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 4.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.1f)
                    )
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier
                            .size(36.dp)
                            .scale(rotation / 360f * 0.4f + 0.8f) // Pulsing icon
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Gemini Lyria 3 작곡 합성 중",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "대상 파일: $targetFile",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = progressText,
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PermissionRationaleScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFF1E1C2A), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color(0xFFD0BCFF),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "마이크 및 위치 권한 필요",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "허밍을 녹음하고 실시간 날씨 맞춤 추천을 받으려면 마이크와 위치 권한이 모두 활성화되어야 합니다.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "만약 버튼을 눌러도 권한 요청 창이 뜨지 않는다면, 아래의 '설정으로 이동' 버튼을 통해 앱 설정에서 직접 권한을 활성화해 주세요.",
            color = Color.Gray.copy(alpha = 0.8f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD0BCFF)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "권한 허용하기",
                    color = Color(0xFF0F0E17),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = {
                    try {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2A3D)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "설정으로 이동",
                    color = Color(0xFFD0BCFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AudioVisualizer(amplitude: Float, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "amplitude"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val barCount = 30
        val barWidth = width / (barCount * 1.5f)
        val gap = barWidth * 0.5f

        for (i in 0 until barCount) {
            val x = i * (barWidth + gap) + barWidth / 2
            val fraction = i.toFloat() / barCount
            val sineVal = kotlin.math.sin(fraction * 3 * Math.PI.toFloat() + phase)
            val centerMultiplier = 1f - kotlin.math.abs(fraction - 0.5f) * 2f
            
            val baseHeight = 6.dp.toPx()
            val reactiveHeight = (animatedAmplitude * (height - 20.dp.toPx()) * centerMultiplier * (0.4f + 0.6f * kotlin.math.abs(sineVal)))
            val totalBarHeight = baseHeight + reactiveHeight

            val startY = centerY - totalBarHeight / 2

            val barColor = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF00E5FF),
                    Color(0xFFD0BCFF)
                )
            )

            drawRoundRect(
                brush = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(x - barWidth / 2, startY),
                size = androidx.compose.ui.geometry.Size(barWidth, totalBarHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Composable
fun RecordButton(
    isRecording: Boolean,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.35f else 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 1000 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isRecording) 0.6f else 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 1000 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    val reactiveScale by animateFloatAsState(
        targetValue = if (isRecording) 1f + amplitude * 0.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "reactiveScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(160.dp)
            .scale(reactiveScale)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(pulseScale)
                .background(
                    color = if (isRecording) Color(0xFFFF4D4D).copy(alpha = pulseAlpha) else Color(0xFFD0BCFF).copy(alpha = pulseAlpha),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(125.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = if (isRecording) {
                            listOf(Color(0xFFFF4D4D).copy(alpha = 0.4f), Color.Transparent)
                        } else {
                            listOf(Color(0xFFD0BCFF).copy(alpha = 0.2f), Color.Transparent)
                        }
                    ),
                    shape = CircleShape
                )
        )

        val buttonGradient = if (isRecording) {
            Brush.linearGradient(
                colors = listOf(Color(0xFFFF4D4D), Color(0xFFFF1A1A))
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0xFFD0BCFF), Color(0xFF6650a4))
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(buttonGradient)
                .clickable { onClick() }
        ) {
            if (isRecording) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Recording",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Start Recording",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun SmartMusicPlayer(
    file: ConvertedFile,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1C2A).copy(alpha = 0.92f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Title and Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NOW PLAYING",
                        color = Color(0xFF00E5FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = file.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Player",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mood Badge (FR-04)
            if (file.mood.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF2C2A3D), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mood: ${file.mood}",
                        color = Color(0xFFD0BCFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Lyrics Display (FR-04)
            if (file.lyrics.isNotEmpty()) {
                Text(
                    text = "Lyrics",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = file.lyrics,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ExoPlayer Seek Bar (FR-05)
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = position.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                        thumbColor = Color(0xFFD0BCFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(position),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Player Buttons (FR-05)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFD0BCFF), CircleShape)
                        .clickable { onPlayPause() }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0F0E17),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}