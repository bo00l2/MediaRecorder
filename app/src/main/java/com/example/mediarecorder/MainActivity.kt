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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
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

    override fun onResume() {
        super.onResume()
        viewModel.registerSensor()
    }

    override fun onPause() {
        super.onPause()
        viewModel.unregisterSensor()
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

    LaunchedEffect(hasLocation) {
        if (hasLocation) {
            viewModel.fetchLocationAndWeather()
        }
    }

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

    val focusRequester = remember { FocusRequester() }
    var fileToRename by remember { mutableStateOf<ConvertedFile?>(null) }

    LaunchedEffect(viewModel.currentScreen) {
        if (viewModel.currentScreen == AppScreen.GENRE_SELECTION) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && 
                        keyEvent.key == Key.Spacebar) {
                        if (viewModel.currentScreen == AppScreen.GENRE_SELECTION) {
                            viewModel.triggerRandomGenreChange()
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
        ) {
            if (!hasMic) {
                PermissionRationaleScreen(
                    onRequestPermission = { permissionsState.launchMultiplePermissionRequest() },
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                when (viewModel.currentScreen) {
                    AppScreen.MAIN -> Screen1_Record(
                        viewModel = viewModel, 
                        hasLocation = hasLocation, 
                        onRequestLocation = { permissionsState.launchMultiplePermissionRequest() },
                        onRenameClick = { fileToRename = it }
                    )
                    AppScreen.GENRE_SELECTION -> Screen2_GenreSelection(viewModel = viewModel)
                    AppScreen.GENERATING -> Screen3_Generating(viewModel = viewModel)
                    AppScreen.PLAYBACK -> Screen4_Playback(
                        viewModel = viewModel,
                        onRenameClick = { fileToRename = it }
                    )
                }
            }

            if (fileToRename != null) {
                var tempName by remember(fileToRename) { mutableStateOf(fileToRename!!.name.substringBeforeLast(".")) }
                AlertDialog(
                    onDismissRequest = { fileToRename = null },
                    title = {
                        Text(
                            text = "곡 이름 변경",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = "새로운 곡 이름을 입력하세요:",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = tempName,
                                onValueChange = { tempName = it },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val file = fileToRename
                                if (file != null && tempName.isNotBlank()) {
                                    viewModel.renameConvertedFile(file, tempName)
                                }
                                fileToRename = null
                            }
                        ) {
                            Text(text = "변경", color = Color(0xFF00E5FF))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { fileToRename = null }) {
                            Text(text = "취소", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF1E1C2A),
                    titleContentColor = Color.White,
                    textContentColor = Color.White
                )
            }
        }
    }
}

@Composable
fun Screen1_Record(
    viewModel: MainViewModel,
    hasLocation: Boolean,
    onRequestLocation: () -> Unit,
    onRenameClick: (ConvertedFile) -> Unit
) {
    var showHistoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hasLocation) viewModel.locationName else "위치 권한 대기 중",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                val weatherIcon = when (viewModel.weatherStatus) {
                    "Sunny" -> "☀️"
                    "Rainy" -> "🌧️"
                    "Cloudy" -> "☁️"
                    "Snowy" -> "❄️"
                    else -> "❓"
                }
                val weatherName = when (viewModel.weatherStatus) {
                    "Sunny" -> "맑음"
                    "Rainy" -> "비"
                    "Cloudy" -> "흐림"
                    "Snowy" -> "눈"
                    else -> "분석 중"
                }
                Text(
                    text = if (hasLocation && viewModel.temperature != null) {
                        "$weatherIcon ${String.format(Locale.getDefault(), "%.1f°C", viewModel.temperature)} $weatherName"
                    } else {
                        "날씨 정보 가져오는 중"
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
            IconButton(
                onClick = { showHistoryDialog = true },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "당신의 허밍을 음악으로",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFD0BCFF), Color(0xFF00E5FF))
                )
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (viewModel.isRecording) "녹음 중입니다..." else "버튼을 눌러 녹음을 시작하세요",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1.5f)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (viewModel.isRecording) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = viewModel.recordingDuration,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                RecordButton(
                    isRecording = viewModel.isRecording,
                    amplitude = viewModel.amplitude,
                    onClick = { viewModel.toggleRecording() }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (viewModel.isRecording) "탭하여 녹음 완료" else "탭하여 녹음 시작",
                    color = Color(0xFFD0BCFF).copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "날씨에 맞는 장르를 추천해드려요",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }

    if (showHistoryDialog) {
        HistoryDialog(
            viewModel = viewModel,
            onDismiss = { showHistoryDialog = false },
            onRenameClick = onRenameClick
        )
    }
}

@Composable
fun Screen2_GenreSelection(viewModel: MainViewModel) {
    val genres = listOf("팝", "재즈", "클래식", "록", "R&B", "힙합", "일렉트로닉", "발라드")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.currentScreen = AppScreen.MAIN }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "장르 선택",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "원하는 음악 스타일을 골라주세요",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFD0BCFF).copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "녹음 완료",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val weatherKo = when (viewModel.weatherStatus) {
                        "Sunny" -> "맑음"
                        "Rainy" -> "비"
                        "Cloudy" -> "흐림"
                        "Snowy" -> "눈"
                        else -> "분석 완료"
                    }
                    Text(
                        text = "0:08 | $weatherKo 날씨",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (j in 0 until 2) {
                        val index = i * 2 + j
                        if (index < genres.size) {
                            val genre = genres[index]
                            val isSelected = viewModel.selectedGenre == genre
                            val isRecommended = (viewModel.recommendedGenre == "Pop" && genre == "팝") ||
                                    (viewModel.recommendedGenre == "Jazz" && genre == "재즈") ||
                                    (viewModel.recommendedGenre == "Lo-Fi" && genre == "일렉트로닉") ||
                                    (viewModel.recommendedGenre == "Classical" && genre == "클래식")

                            val cardBgColor = if (isSelected) Color.White else Color(0xFF1E1C2A).copy(alpha = 0.6f)
                            val cardTextColor = if (isSelected) Color(0xFF6200EE) else Color.White
                            val borderColor = if (isRecommended) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.05f)

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(cardBgColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectGenre(genre) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = genre,
                                        color = cardTextColor,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isRecommended) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "★",
                                            color = if (isSelected) Color(0xFF6200EE) else Color(0xFF00E5FF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = Color(0xFFD0BCFF),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "📱 ⌨️ 기기를 흔들거나 Spacebar를 눌러 랜덤 선택",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.generateMusic() },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "음악 생성하기",
                color = Color(0xFF6200EE),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun Screen3_Generating(viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "generatingRotate")
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
            modifier = Modifier.size(100.dp)
        ) {
            CircularProgressIndicator(
                color = Color(0xFF00E5FF),
                strokeWidth = 4.dp,
                modifier = Modifier.fillMaxSize()
            )
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0xFFD0BCFF),
                modifier = Modifier
                    .size(44.dp)
                    .scale(rotation / 360f * 0.2f + 0.9f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "AI가 음악을 만들고 있어요",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "허밍을 분석하여 맞춤 반주를 생성 중입니다",
            color = Color.Gray,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Column(
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .padding(24.dp)
                .fillMaxWidth(0.9f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProgressItem(text = "허밍 분석 중", isActive = viewModel.generatingStep >= 1)
            ProgressItem(text = "날씨 정보 반영 중", isActive = viewModel.generatingStep >= 2)
            ProgressItem(text = "${viewModel.selectedGenre} 스타일 적용 중", isActive = viewModel.generatingStep >= 3)
            ProgressItem(text = "가사 작성 중", isActive = viewModel.generatingStep >= 4)
        }

        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "잠시만 기다려주세요...",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ProgressItem(text: String, isActive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isActive) Color(0xFF00E5FF) else Color.Gray.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = if (isActive) Color.White else Color.Gray,
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun Screen4_Playback(
    viewModel: MainViewModel,
    onRenameClick: (ConvertedFile) -> Unit
) {
    val file = viewModel.latestConvertedFile ?: return
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.currentScreen = AppScreen.MAIN }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "재생 & 가사",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            IconButton(
                onClick = { showDeleteConfirm = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Red.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF2C2A3D), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = viewModel.selectedGenre,
                        color = Color(0xFFD0BCFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = file.name.substringBeforeLast("."),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { onRenameClick(file) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val dateClean = file.dateStr.substringBefore(" ")
                Text(
                    text = "AI Generated • $dateClean",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        if (file.lyrics.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
            ) {
                Text(
                    text = "AI 추천 가사 (1절)",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = viewModel.playbackPosition.toFloat(),
                onValueChange = { viewModel.seekTo(it.toLong()) },
                valueRange = 0f..viewModel.playbackDuration.toFloat().coerceAtLeast(1f),
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
                    text = formatTime(viewModel.playbackPosition),
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Text(
                    text = formatTime(viewModel.playbackDuration),
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.seekTo(0L) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Rewind",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.White, CircleShape)
                    .clickable {
                        if (viewModel.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
                    }
            ) {
                Icon(
                    imageVector = if (viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (viewModel.isPlaying) "Pause" else "Play",
                    tint = Color(0xFF6200EE),
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            IconButton(
                onClick = { viewModel.seekTo(viewModel.playbackDuration) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Forward",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "곡 삭제",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "정말로 이 곡을 영구 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConvertedFile(file)
                        viewModel.currentScreen = AppScreen.MAIN
                        showDeleteConfirm = false
                    }
                ) {
                    Text(text = "삭제", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(text = "취소", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1C2A),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

@Composable
fun HistoryDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onRenameClick: (ConvertedFile) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) }
    var fileToDelete by remember { mutableStateOf<Any?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1C2A)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "히스토리",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12111E), shape = RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    TabButton(
                        title = "녹음 (${viewModel.recordingsList.size})",
                        isActive = activeTab == 0,
                        onClick = { activeTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        title = "변환 곡 (${viewModel.convertedList.size})",
                        isActive = activeTab == 1,
                        onClick = { activeTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (activeTab == 0) {
                        if (viewModel.recordingsList.isEmpty()) {
                            EmptyPlaceholder(text = "녹음 파일이 없습니다.")
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(viewModel.recordingsList) { recording ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2A3D).copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color(0xFFD0BCFF),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = recording.name,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(text = "${recording.dateStr} | ${recording.size}", color = Color.Gray, fontSize = 10.sp)
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.latestRecordedFile = recording
                                                    viewModel.currentScreen = AppScreen.GENRE_SELECTION
                                                    onDismiss()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = "Convert",
                                                    tint = Color(0xFF00E5FF),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { fileToDelete = recording },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color.Red.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (viewModel.convertedList.isEmpty()) {
                            EmptyPlaceholder(text = "변환된 곡이 없습니다.")
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(viewModel.convertedList) { file ->
                                    val isCurrentPlaying = viewModel.currentPlayingFile?.path == file.path
                                    val cardColor = if (isCurrentPlaying) Color(0xFF1E3547) else Color(0xFF2C2A3D).copy(alpha = 0.6f)
                                    val iconColor = if (isCurrentPlaying) Color(0xFF00E5FF) else Color(0xFFD0BCFF)

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = cardColor),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            viewModel.latestConvertedFile = file
                                            viewModel.playFile(file)
                                            viewModel.currentScreen = AppScreen.PLAYBACK
                                            onDismiss()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = file.name.substringBeforeLast("."),
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(text = "${file.dateStr} | ${file.size}", color = Color.Gray, fontSize = 10.sp)
                                            }
                                            Icon(
                                                imageVector = if (isCurrentPlaying && viewModel.isPlaying) Icons.Default.Pause else Icons.Default.VolumeUp,
                                                contentDescription = null,
                                                tint = iconColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(
                                                onClick = { onRenameClick(file) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Rename",
                                                    tint = Color(0xFF00E5FF),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { fileToDelete = file },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color.Red.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (fileToDelete != null) {
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = {
                Text(
                    text = "파일 삭제",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "정말로 이 파일을 영구 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = fileToDelete
                        if (file is ConvertedFile) {
                            viewModel.deleteConvertedFile(file)
                        } else if (file is RecordingFile) {
                            viewModel.deleteRecordingFile(file)
                        }
                        fileToDelete = null
                    }
                ) {
                    Text(text = "삭제", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text(text = "취소", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1C2A),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
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