package com.example.mediarecorder

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen {
    MAIN,
    GENRE_SELECTION,
    GENERATING,
    PLAYBACK
}

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val audioRecorder by lazy {
        AndroidAudioRecorder(application.applicationContext)
    }

    private val locationHelper by lazy {
        LocationHelper(application.applicationContext)
    }

    private val weatherApiService by lazy {
        WeatherApiService.create()
    }

    private val geminiMusicService by lazy {
        GeminiMusicService.create()
    }

    private val sensorManager by lazy {
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // --- FR-06 & Redesign UI States ---
    var currentScreen by mutableStateOf(AppScreen.MAIN)

    var latestRecordedFile by mutableStateOf<RecordingFile?>(null)

    var latestConvertedFile by mutableStateOf<ConvertedFile?>(null)

    var selectedGenre by mutableStateOf("팝")

    var generatingStep by mutableStateOf(1)
        private set

    // --- FR-05 Player States ---
    private var exoPlayer: ExoPlayer? = null

    var currentPlayingFile by mutableStateOf<ConvertedFile?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var playbackPosition by mutableStateOf(0L)
        private set

    var playbackDuration by mutableStateOf(0L)
        private set

    private var positionJob: Job? = null

    // --- FR-01 Recording States ---
    var isRecording by mutableStateOf(false)
        private set

    var recordingDuration by mutableStateOf("00:00")
        private set

    var amplitude by mutableStateOf(0f)
        private set

    var recordingsList by mutableStateOf<List<RecordingFile>>(emptyList())
        private set

    // --- FR-02 Context/Weather States ---
    var locationName by mutableStateOf("위치 수집 대기 중")
        private set

    var temperature by mutableStateOf<Double?>(null)
        private set

    var weatherStatus by mutableStateOf("알 수 없음") // Sunny, Rainy, Cloudy, Snowy
        private set

    var recommendedGenre by mutableStateOf("Pop")
        private set

    // --- FR-03 Genre Selection & AI Conversion States ---
    var selectedGenres by mutableStateOf<Set<String>>(emptySet())
        private set

    var isConverting by mutableStateOf(false)
        private set

    var convertingFile by mutableStateOf<RecordingFile?>(null)
        private set

    var conversionProgressText by mutableStateOf("")
        private set

    var convertedList by mutableStateOf<List<ConvertedFile>>(emptyList())
        private set

    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var currentFile: File? = null

    init {
        loadRecordings()
        loadConvertedRecordings()
    }

    // --- FR-02 Location & Weather Methods ---
    fun fetchLocationAndWeather() {
        viewModelScope.launch {
            locationName = "위치를 가져오는 중..."
            val location = locationHelper.getCurrentLocation()
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                
                // Get local address name
                locationName = locationHelper.getLocalName(lat, lon)
                
                // Get weather info
                try {
                    val weatherResponse = weatherApiService.getWeather(lat, lon)
                    val current = weatherResponse.current_weather
                    updateWeatherDetails(current.weathercode, current.temperature)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fail-safe with mock weather if API fails
                    updateWeatherDetails(0, 22.5) // Default to sunny 22.5°C
                }
            } else {
                locationName = "위치 획득 실패 (GPS를 켜주세요)"
                // Default fallback
                updateWeatherDetails(0, 20.0)
            }
        }
    }

    private fun updateWeatherDetails(code: Int, temp: Double) {
        temperature = temp
        weatherStatus = when (code) {
            0, 1 -> "Sunny"
            2, 3 -> "Cloudy"
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> "Rainy"
            71, 73, 75, 77, 85, 86 -> "Snowy"
            else -> "Cloudy"
        }
        recommendedGenre = when (weatherStatus) {
            "Sunny" -> "Pop"
            "Rainy" -> "Jazz"
            "Cloudy" -> "Lo-Fi"
            "Snowy" -> "Classical"
            else -> "Pop"
        }
    }

    // --- FR-03 Genre Methods ---
    fun selectGenre(genre: String) {
        selectedGenre = genre
    }

    // --- FR-03 AI Conversion Logic ---
    fun convertHummingToMusic(recording: RecordingFile) {
        latestRecordedFile = recording
        generateMusic()
    }

    fun generateMusic() {
        val recording = latestRecordedFile ?: return

        viewModelScope.launch {
            currentScreen = AppScreen.GENERATING
            isConverting = true
            convertingFile = recording
            
            val context = getApplication<Application>().applicationContext
            
            try {
                // Step 1: Humming analysis
                generatingStep = 1
                conversionProgressText = "허밍 분석 중"
                delay(1200)

                // Step 2: Weather info
                generatingStep = 2
                conversionProgressText = "날씨 정보 반영 중"
                delay(1200)

                // Step 3: Synthesis
                generatingStep = 3
                conversionProgressText = "$selectedGenre 스타일 적용 중"
                delay(1200)

                // Step 4: Lyrics
                generatingStep = 4
                conversionProgressText = "가사 작성 중"
                delay(1200)

                // Call service
                val file = File(recording.path)
                val requestFile = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("audio", file.name, requestFile)
                val genrePart = selectedGenre.toRequestBody("text/plain".toMediaTypeOrNull())
                val weatherPart = weatherStatus.toRequestBody("text/plain".toMediaTypeOrNull())
                val locationPart = locationName.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = geminiMusicService.convertMusic(body, genrePart, weatherPart, locationPart)
                var generatedFile: ConvertedFile? = null

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val outputDir = File(context.getExternalFilesDir(null), "Converted").apply {
                            if (!exists()) mkdirs()
                        }
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val cleanName = recording.name.substringBeforeLast(".")
                        val outputFileName = "converted_${cleanName}_$timestamp.m4a"
                        val outputFile = File(outputDir, outputFileName)
                        
                        responseBody.byteStream().use { input ->
                            FileOutputStream(outputFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        val serverMood = response.headers()["x-music-mood"] ?: response.headers()["x-mood"]
                        val serverLyrics = response.headers()["x-music-lyrics"] ?: response.headers()["x-lyrics"]
                        
                        val mood = decodeHeader(serverMood) ?: getMockMood(weatherStatus, selectedGenre)
                        val lyrics = decodeHeader(serverLyrics) ?: getMockLyrics(weatherStatus, selectedGenre)
                        
                        val jsonFile = File(outputDir, "converted_${cleanName}_$timestamp.json")
                        val jsonMap = mapOf("mood" to mood, "lyrics" to lyrics)
                        jsonFile.writeText(Gson().toJson(jsonMap))

                        generatedFile = ConvertedFile(
                            name = outputFileName,
                            path = outputFile.absolutePath,
                            size = formatFileSize(outputFile.length()),
                            dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(outputFile.lastModified())),
                            mood = mood,
                            lyrics = lyrics
                        )
                    }
                } else {
                    generatedFile = saveMockConvertedFileAndReturn(recording, selectedGenre)
                }

                if (generatedFile != null) {
                    latestConvertedFile = generatedFile
                    playFile(generatedFile)
                    currentScreen = AppScreen.PLAYBACK
                } else {
                    currentScreen = AppScreen.MAIN
                }

            } catch (e: Exception) {
                e.printStackTrace()
                val generatedFile = saveMockConvertedFileAndReturn(recording, selectedGenre)
                if (generatedFile != null) {
                    latestConvertedFile = generatedFile
                    playFile(generatedFile)
                    currentScreen = AppScreen.PLAYBACK
                } else {
                    currentScreen = AppScreen.MAIN
                }
            } finally {
                isConverting = false
                convertingFile = null
                conversionProgressText = ""
                loadConvertedRecordings()
            }
        }
    }

    private suspend fun saveMockConvertedFileAndReturn(recording: RecordingFile, genre: String): ConvertedFile? = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        val outputDir = File(context.getExternalFilesDir(null), "Converted").apply {
            if (!exists()) mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanName = recording.name.substringBeforeLast(".")
        val mockFileName = "converted_${cleanName}_$timestamp.m4a"
        val mockFile = File(outputDir, mockFileName)

        val sourceFile = File(recording.path)
        if (sourceFile.exists()) {
            try {
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(mockFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        } else {
            return@withContext null
        }

        val mockJsonFile = File(outputDir, "converted_${cleanName}_$timestamp.json")
        val mood = getMockMood(weatherStatus, genre)
        val lyrics = getMockLyrics(weatherStatus, genre)
        val jsonMap = mapOf("mood" to mood, "lyrics" to lyrics)
        try {
            mockJsonFile.writeText(Gson().toJson(jsonMap))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext ConvertedFile(
            name = mockFileName,
            path = mockFile.absolutePath,
            size = formatFileSize(mockFile.length()),
            dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(mockFile.lastModified())),
            mood = mood,
            lyrics = lyrics
        )
    }

    // --- File Deletion Logic ---
    fun deleteConvertedFile(file: ConvertedFile) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioFile = File(file.path)
            if (audioFile.exists()) {
                audioFile.delete()
            }
            val jsonFile = File(audioFile.parent, audioFile.name.substringBeforeLast(".") + ".json")
            if (jsonFile.exists()) {
                jsonFile.delete()
            }
            
            withContext(Dispatchers.Main) {
                if (currentPlayingFile?.path == file.path) {
                    stopPlayback()
                }
                loadConvertedRecordings()
            }
        }
    }

    fun deleteRecordingFile(file: RecordingFile) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioFile = File(file.path)
            if (audioFile.exists()) {
                audioFile.delete()
            }
            
            withContext(Dispatchers.Main) {
                if (latestRecordedFile?.path == file.path) {
                    latestRecordedFile = null
                }
                loadRecordings()
            }
        }
    }

    private fun decodeHeader(value: String?): String? {
        if (value == null) return null
        return try {
            java.net.URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            value
        }
    }

    private fun getMockMood(weather: String, genre: String): String {
        return when (weather) {
            "Sunny" -> "맑은 햇살 아래 흐르는 밝고 활기찬 $genre 분위기"
            "Rainy" -> "창밖의 빗소리와 어우러지는 촉촉하고 감성적인 $genre 분위기"
            "Cloudy" -> "차분하고 포근한 구름 사이로 스며드는 나른한 $genre 분위기"
            "Snowy" -> "하얀 눈밭 위에 펼쳐지는 따뜻하고 평화로운 $genre 분위기"
            else -> "편안하고 어쿠스틱한 $genre 분위기"
        }
    }

    private fun getMockLyrics(weather: String, genre: String): String {
        return when (weather) {
            "Sunny" -> """
                (Verse 1)
                눈부신 햇살이 가득한 날에
                우리는 함께 거리를 걸어
                너의 미소가 내 맘을 밝혀주고
                이 음악 속에 우리 노랜 시작돼
            """.trimIndent()
            "Rainy" -> """
                (Verse 1)
                창밖에 내리는 빗소리를 따라
                $genre 선율이 방안에 퍼지네
                따뜻한 커피 한 잔을 손에 쥐고
                흘러간 기억들을 가만히 그리네
            """.trimIndent()
            "Cloudy" -> """
                (Verse 1)
                구름 뒤에 숨은 햇살 사이로
                시간이 느리게만 흘러가네
                오래된 턴테이블 위로 흐르는 곡
                아무 생각 없이 나른해지는 오후
            """.trimIndent()
            "Snowy" -> """
                (Verse 1)
                하늘에서 조용히 내리는 눈송이
                세상을 하얗게 덮어 가네
                피아노 건반 위에 쌓인 멜로디
                따뜻한 겨울의 노래가 울려 퍼지네
            """.trimIndent()
            else -> """
                (Verse 1)
                조용히 흐르는 시간 속에서
                작은 멜로디를 건네어 본다
                마음속에 깊이 담아두었던 말들
                노래가 되어 너에게 전해지기를
            """.trimIndent()
        }
    }

    // --- FR-06 Accelerometer Sensor Listener ---
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastUpdate = 0L
    private var lastShakeTime = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        
        val currentTime = System.currentTimeMillis()
        val timeDifference = currentTime - lastUpdate
        if (timeDifference > 100) {
            lastUpdate = currentTime
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / timeDifference * 10000
            if (speed > 800) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > 1000) {
                    lastShakeTime = now
                    triggerRandomGenreChange()
                }
            }
            lastX = x
            lastY = y
            lastZ = z
        }
    }

    fun registerSensor() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun unregisterSensor() {
        sensorManager.unregisterListener(this)
    }

    fun triggerRandomGenreChange() {
        val genres = listOf("팝", "재즈", "클래식", "록", "R&B", "힙합", "일렉트로닉", "발라드")
        selectedGenre = genres.random()
    }

    // --- FR-05 ExoPlayer Controller Methods ---
    private fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(getApplication()).build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        this@MainViewModel.isPlaying = playing
                        if (playing) {
                            startPositionTracker()
                        } else {
                            stopPositionTracker()
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            playbackDuration = duration.coerceAtLeast(0L)
                        } else if (state == Player.STATE_ENDED) {
                            playbackPosition = 0L
                            this@MainViewModel.isPlaying = false
                        }
                    }
                })
            }
        }
        return exoPlayer!!
    }

    private fun startPositionTracker() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                exoPlayer?.let {
                    playbackPosition = it.currentPosition
                    playbackDuration = it.duration.coerceAtLeast(0L)
                }
                delay(200)
            }
        }
    }

    private fun stopPositionTracker() {
        positionJob?.cancel()
        positionJob = null
    }

    fun playFile(file: ConvertedFile) {
        val player = getPlayer()
        if (currentPlayingFile?.path == file.path) {
            if (isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        } else {
            player.stop()
            currentPlayingFile = file
            val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(File(file.path)))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    fun pausePlayback() {
        exoPlayer?.pause()
    }

    fun resumePlayback() {
        exoPlayer?.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
        playbackPosition = positionMs
    }

    fun stopPlayback() {
        exoPlayer?.stop()
        currentPlayingFile = null
        isPlaying = false
        playbackPosition = 0L
        playbackDuration = 0L
    }

    override fun onCleared() {
        super.onCleared()
        stopPositionTracker()
        exoPlayer?.release()
        exoPlayer = null
        timerJob?.cancel()
        amplitudeJob?.cancel()
    }

    fun loadConvertedRecordings() {
        val context = getApplication<Application>().applicationContext
        val directory = File(context.getExternalFilesDir(null), "Converted")
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles { file -> file.isFile && file.extension == "m4a" }
            convertedList = files?.map { file ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                
                val jsonFile = File(file.parent, file.name.substringBeforeLast(".") + ".json")
                var mood = ""
                var lyrics = ""
                if (jsonFile.exists()) {
                    try {
                        val jsonText = jsonFile.readText()
                        val map = Gson().fromJson(jsonText, Map::class.java)
                        mood = map["mood"] as? String ?: ""
                        lyrics = map["lyrics"] as? String ?: ""
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                ConvertedFile(
                    name = file.name,
                    path = file.absolutePath,
                    size = formatFileSize(file.length()),
                    dateStr = dateStr,
                    mood = mood,
                    lyrics = lyrics
                )
            }?.sortedByDescending { it.name } ?: emptyList()
        } else {
            convertedList = emptyList()
        }
    }

    // --- FR-01 Recording Methods ---
    fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val context = getApplication<Application>().applicationContext
        val outputDir = File(context.getExternalFilesDir(null), "Recordings").apply {
            if (!exists()) mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(outputDir, "humming_$timestamp.m4a")
        currentFile = file

        audioRecorder.start(file)
        isRecording = true
        
        startTimer()
        startAmplitudeTracking()
    }

    private fun stopRecording() {
        audioRecorder.stop()
        isRecording = false
        
        stopTimer()
        stopAmplitudeTracking()
        
        loadRecordings()

        val lastRecording = recordingsList.firstOrNull()
        if (lastRecording != null) {
            latestRecordedFile = lastRecording
            currentScreen = AppScreen.GENRE_SELECTION
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1000)
                seconds++
                val minutes = seconds / 60
                val secs = seconds % 60
                recordingDuration = String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        recordingDuration = "00:00"
    }

    private fun startAmplitudeTracking() {
        amplitudeJob?.cancel()
        amplitudeJob = viewModelScope.launch {
            while (isActive) {
                delay(100)
                val maxAmp = audioRecorder.getMaxAmplitude()
                amplitude = (maxAmp.toFloat() / 32767f).coerceIn(0f, 1f)
            }
        }
    }

    private fun stopAmplitudeTracking() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        amplitude = 0f
    }

    fun loadRecordings() {
        val context = getApplication<Application>().applicationContext
        val directory = File(context.getExternalFilesDir(null), "Recordings")
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles { file -> file.isFile && file.extension == "m4a" }
            recordingsList = files?.map { file ->
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                RecordingFile(
                    name = file.name,
                    path = file.absolutePath,
                    size = formatFileSize(file.length()),
                    dateStr = dateStr
                )
            }?.sortedByDescending { it.name } ?: emptyList()
        } else {
            recordingsList = emptyList()
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

data class ConvertedFile(
    val name: String,
    val path: String,
    val size: String,
    val dateStr: String,
    val mood: String = "",
    val lyrics: String = ""
)

data class RecordingFile(
    val name: String,
    val path: String,
    val size: String,
    val dateStr: String
)
