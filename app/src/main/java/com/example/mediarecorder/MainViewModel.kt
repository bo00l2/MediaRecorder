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
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.MediaMetadataRetriever

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

    private val replicateMusicService by lazy {
        ReplicateMusicService.create()
    }

    private val sensorManager by lazy {
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // --- FR-06 & Redesign UI States ---
    var currentScreen by mutableStateOf(AppScreen.MAIN)

    var latestRecordedFile by mutableStateOf<RecordingFile?>(null)

    var latestConvertedFile by mutableStateOf<ConvertedFile?>(null)

    var selectedGenre by mutableStateOf("팝")

    var includeWeatherInLyrics by mutableStateOf(true)

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
                delay(1000)

                // Step 2: Weather info
                generatingStep = 2
                conversionProgressText = "날씨 정보 반영 중"
                delay(1000)

                // Step 3: Synthesis
                generatingStep = 3
                conversionProgressText = "$selectedGenre 스타일 적용 중"
                delay(1000)

                // Step 4: Lyrics
                generatingStep = 4
                conversionProgressText = "가사 작성 중"
                delay(1000)

                var generatedFile: ConvertedFile? = null

                if (REPLICATE_API_TOKEN.isNotBlank()) {
                    generatingStep = 3
                    conversionProgressText = "Replicate AI 음악 생성 요청 중..."
                    
                    val file = File(recording.path)
                    val base64Audio = fileToBase64DataUri(file)
                    val timeSlotKo = getCurrentTimeSlot()
                    val timeSlotEng = getCurrentTimeSlotEng()
                    val weatherPart = if (includeWeatherInLyrics) "$weatherStatus weather theme, matching atmosphere of $locationName." else ""
                    val prompt = "A $selectedGenre humming song created during $timeSlotEng ($timeSlotKo), $weatherPart Make it high quality, catchy, matching melody."
                    
                    val request = ReplicatePredictionRequest(
                        version = "671ac645ce5e552cc63a54a2bbff63fcf798043055d2dac5fc9e36a837eedcfb",
                        input = mapOf(
                            "prompt" to prompt,
                            "input_audio" to base64Audio,
                            "model_version" to "stereo-melody-large",
                            "duration" to 15,
                            "output_format" to "mp3"
                        )
                    )
                    
                    val createResponse = replicateMusicService.createPrediction("Token $REPLICATE_API_TOKEN", request)
                    if (createResponse.isSuccessful && createResponse.body() != null) {
                        var prediction = createResponse.body()!!
                        val predictionId = prediction.id
                        var attempts = 0
                        val maxAttempts = 30 // 60 seconds timeout
                        
                        while ((prediction.status != "succeeded" && prediction.status != "failed") && attempts < maxAttempts) {
                            delay(2000)
                            attempts++
                            val statusResponse = replicateMusicService.getPrediction("Token $REPLICATE_API_TOKEN", predictionId)
                            if (statusResponse.isSuccessful && statusResponse.body() != null) {
                                prediction = statusResponse.body()!!
                                conversionProgressText = "AI 음악 생성 중 (상태: ${prediction.status})"
                            } else {
                                break
                            }
                        }
                        
                        if (prediction.status == "succeeded" && prediction.output != null) {
                            val outputUrl = when (val out = prediction.output) {
                                is String -> out
                                is List<*> -> out.firstOrNull() as? String
                                else -> null
                            }
                            
                            if (outputUrl != null) {
                                conversionProgressText = "완료된 곡 다운로드 중..."
                                val outputDir = File(context.getExternalFilesDir(null), "Converted").apply {
                                    if (!exists()) mkdirs()
                                }
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val cleanName = recording.name.substringBeforeLast(".")
                                val outputFileName = "converted_${cleanName}_$timestamp.mp3"
                                val outputFile = File(outputDir, outputFileName)
                                
                                val client = OkHttpClient()
                                val downloadRequest = Request.Builder().url(outputUrl).build()
                                client.newCall(downloadRequest).execute().use { downloadResponse ->
                                    if (downloadResponse.isSuccessful && downloadResponse.body != null) {
                                        downloadResponse.body!!.byteStream().use { input ->
                                            FileOutputStream(outputFile).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        
                                        val mood = getMockMood(includeWeatherInLyrics, weatherStatus, selectedGenre)
                                        val lyrics = getMockLyrics(includeWeatherInLyrics, weatherStatus, selectedGenre, recording)
                                        
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
                                }
                            }
                        }
                    }
                }

                if (generatedFile == null) {
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

    private fun fileToBase64DataUri(file: File): String {
        val bytes = file.readBytes()
        val base64String = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:audio/x-m4a;base64,$base64String"
    }

    private suspend fun saveMockConvertedFileAndReturn(recording: RecordingFile, genre: String): ConvertedFile? = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        val outputDir = File(context.getExternalFilesDir(null), "Converted").apply {
            if (!exists()) mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanName = recording.name.substringBeforeLast(".")
        val mockFileName = "converted_${cleanName}_$timestamp.mp3"
        val mockFile = File(outputDir, mockFileName)

        val sourceFile = File(recording.path)
        var fileDownloaded = false
        try {
            val client = OkHttpClient()
            val index = (recording.name.hashCode() + genre.hashCode()).let { kotlin.math.abs(it) % 16 + 1 }
            val sampleUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-$index.mp3"
            val downloadRequest = Request.Builder().url(sampleUrl).build()
            client.newCall(downloadRequest).execute().use { downloadResponse ->
                if (downloadResponse.isSuccessful && downloadResponse.body != null) {
                    downloadResponse.body!!.byteStream().use { input ->
                        FileOutputStream(mockFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    fileDownloaded = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (!fileDownloaded) {
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
        }

        val mockJsonFile = File(outputDir, "converted_${cleanName}_$timestamp.json")
        val mood = getMockMood(includeWeatherInLyrics, weatherStatus, genre)
        val lyrics = getMockLyrics(includeWeatherInLyrics, weatherStatus, genre, recording)
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

    fun renameConvertedFile(file: ConvertedFile, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val audioFile = File(file.path)
            if (!audioFile.exists()) return@launch
            
            val ext = audioFile.extension
            val cleanNewName = if (newName.endsWith(".$ext")) newName else "$newName.$ext"
            val newAudioFile = File(audioFile.parentFile, cleanNewName)
            
            val jsonFile = File(audioFile.parent, audioFile.name.substringBeforeLast(".") + ".json")
            val newJsonFile = File(audioFile.parent, cleanNewName.substringBeforeLast(".") + ".json")
            
            if (audioFile.renameTo(newAudioFile)) {
                if (jsonFile.exists()) {
                    jsonFile.renameTo(newJsonFile)
                }
                
                withContext(Dispatchers.Main) {
                    val updatedFile = ConvertedFile(
                        name = cleanNewName,
                        path = newAudioFile.absolutePath,
                        size = file.size,
                        dateStr = file.dateStr,
                        mood = file.mood,
                        lyrics = file.lyrics
                    )
                    
                    if (latestConvertedFile?.path == file.path) {
                        latestConvertedFile = updatedFile
                    }
                    if (currentPlayingFile?.path == file.path) {
                        currentPlayingFile = updatedFile
                    }
                    loadConvertedRecordings()
                }
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

    fun getCurrentTimeSlot(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5 -> "새벽"
            in 6..11 -> "아침"
            in 12..17 -> "한낮"
            in 18..20 -> "저녁"
            else -> "야간"
        }
    }

    fun getCurrentTimeSlotEng(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5 -> "dawn"
            in 6..11 -> "morning"
            in 12..17 -> "midday"
            in 18..20 -> "evening"
            else -> "night"
        }
    }

    private fun getMockMood(useWeather: Boolean, weather: String, genre: String): String {
        val timeSlot = getCurrentTimeSlot()
        if (!useWeather) {
            return "허밍의 리듬을 살려 연주되는 깊고 풍부한 $genre 분위기 ($timeSlot)"
        }
        return when (weather) {
            "Sunny" -> "맑음 아래 흐르는 밝고 활기찬 $genre 분위기 ($timeSlot)"
            "Rainy" -> "빗소리와 어우러지는 촉촉하고 감성적인 $genre 분위기 ($timeSlot)"
            "Cloudy" -> "차분하고 포근한 나른한 $genre 분위기 ($timeSlot)"
            "Snowy" -> "눈밭 위에 펼쳐지는 따뜻하고 평화로운 $genre 분위기 ($timeSlot)"
            else -> "편안하고 어쿠스틱한 $genre 분위기 ($timeSlot)"
        }
    }

    private fun getMockLyrics(useWeather: Boolean, weather: String, genre: String, recording: RecordingFile): String {
        val durationMs = getAudioDurationMs(recording.path)
        val durationSec = durationMs / 1000
        val seed = recording.name.hashCode() + genre.hashCode() + (if (useWeather) weather.hashCode() else 17)
        val random = java.util.Random(seed.toLong())

        val timeSlot = getCurrentTimeSlot()
        
        val lines1 = if (useWeather) {
            when (weather) {
                "Sunny" -> listOf(
                    "눈부신 햇살이 가득한 오늘",
                    "푸른 하늘 아래 바람을 느끼며",
                    "노란 햇볕이 내 어깨를 비추는 날",
                    "반짝이는 거리를 가볍게 걸을 때",
                    "맑게 갠 날씨가 나를 미소 짓게 해"
                )
                "Rainy" -> listOf(
                    "창밖에 조용히 빗소리가 내리고",
                    "우산 아래 너와 나란히 서서",
                    "촉촉이 젖은 거리를 바라보며",
                    "흐린 유리창에 비친 내 모습",
                    "빗방울이 하나둘 떨어지는 오후"
                )
                "Cloudy" -> listOf(
                    "구름 가득한 하늘 아래 차분해지는 시간",
                    "안개 낀 아침 공기를 마시며",
                    "어두워진 하늘을 가만히 올려다봐",
                    "빛바랜 오후의 회색빛 분위기",
                    "흐릿한 세상이 오히려 포근해"
                )
                "Snowy" -> listOf(
                    "하얀 눈송이가 소복이 쌓이는 날",
                    "차가운 겨울바람이 뺨을 스칠 때",
                    "온 세상이 하얗게 변해버린 오늘",
                    "창밖으로 조용히 내리는 함박눈",
                    "하얀 김이 입가에 번지는 차가운 아침"
                )
                else -> listOf(
                    "조용히 흐르는 시간의 틈 사이로",
                    "바쁜 하루 끝에 찾아온 이 평화",
                    "조용한 방 안 가만히 앉아",
                    "어디선가 불어오는 미풍을 따라",
                    "혼자만의 생각에 잠기는 시간"
                )
            }
        } else {
            listOf(
                "귓가에 맴도는 부드러운 콧노래",
                "작은 목소리로 시작된 멜로디",
                "이 순간 흘러나오는 나만의 고백",
                "조용히 쌓여가는 우리의 노래",
                "마음속에서 피어난 부드러운 음율"
            )
        }

        val lines2 = if (useWeather) {
            when (weather) {
                "Sunny" -> listOf(
                    "너와 함께 걷는 이 길이 즐거워",
                    "마음속 깊이 쌓인 걱정은 날려버려",
                    "콧노래가 흥얼흥얼 흘러나와",
                    "작은 설렘이 내 맘에 가득 차올라",
                    "어디로든 떠나고 싶은 기분이야"
                )
                "Rainy" -> listOf(
                    "오래된 음악을 조용히 틀어봐",
                    "기억 속의 너를 가만히 떠올려",
                    "따뜻한 커피 향이 방을 채우고",
                    "지나간 추억들이 문득 그리워져",
                    "차분한 이 감정에 나를 맡겨둘래"
                )
                "Cloudy" -> listOf(
                    "서두르지 않고 한 걸음씩 걸어가",
                    "생각이 꼬리를 물고 이어지는 밤",
                    "마음의 소리에 귀를 기울여봐",
                    "나른한 오후의 여유를 즐기며",
                    "조금은 느려져도 괜찮을 것 같아"
                )
                "Snowy" -> listOf(
                    "시린 손을 주머니에 쏙 넣고서",
                    "어릴 적 타오르던 벽난로가 그리워",
                    "너와 함께 나누던 따뜻한 온기",
                    "소리 없이 다가온 하얀 겨울이야",
                    "작은 온기를 나누며 미소 짓네"
                )
                else -> listOf(
                    "소박한 일상 속 행복을 찾아봐",
                    "너에게 전하고 싶은 말이 있어",
                    "머릿속 복잡한 일은 다 잊은 채",
                    "나만의 쉼표 하나를 그려봐",
                    "마음이 흐르는 대로 따라가네"
                )
            }
        } else {
            listOf(
                "작은 흥얼거림이 큰 울림이 되어",
                "마음이 닿는 곳으로 뻗어가네",
                "서툴지만 진심 어린 나의 고백을",
                "이 리듬에 실어 네게 보낼게",
                "말하지 못한 내 비밀스러운 이야기들"
            )
        }

        val lines3 = when (weather) {
            "Sunny" -> listOf(
                "신나는 $genre 비트가 울려 퍼지고",
                "밝은 $genre 멜로디에 몸을 실어봐",
                "상큼한 $genre 선율이 귓가를 스치네",
                "이 톡톡 튀는 $genre 노래를 부르며",
                "너와 나의 $genre 하모니가 어우러져"
            )
            "Rainy" -> listOf(
                "차분한 $genre 선율이 빗방울과 닮았어",
                "촉촉한 $genre 리듬에 내 맘을 적셔",
                "슬픈 $genre 코드가 가슴을 파고들어",
                "부드러운 $genre 감성이 흘러넘치네",
                "빗소리와 하나가 되는 $genre 음악"
            )
            "Cloudy" -> listOf(
                "은은한 $genre 톤이 오늘따라 깊어",
                "나른한 $genre 템포에 맞춰 쉬어가",
                "몽환적인 $genre 분위기에 취해보는 오후",
                "조금은 쓸쓸한 $genre 멜로디의 온기",
                "마음을 어루만지는 $genre 리듬"
            )
            "Snowy" -> listOf(
                "따뜻한 $genre 멜로디가 흩날리고",
                "포근한 $genre 편곡이 세상을 감싸네",
                "클래식한 $genre 감성이 피어나는 밤",
                "벽난로 앞 들려오는 $genre 노래",
                "하얀 겨울에 어울리는 $genre 연주"
            )
            else -> listOf(
                "잔잔한 $genre 선율이 마음을 달래고",
                "귓가에 속삭이는 $genre 멜로디",
                "이 아름다운 $genre 음율 속에서",
                "언제 들어도 편안한 $genre 비트",
                "마음속에 깊이 남는 $genre 한 구절"
            )
        }

        val lines4 = listOf(
            "오늘의 이 순간을 노랫말에 담아볼게.",
            "영원히 기억될 우리만의 멜로디야.",
            "이 음악이 너에게 작은 위로가 되길.",
            "내일도 우리는 함께 노래 부를 거야.",
            "마음속 깊이 간직할 추억이 하나 더 늘었어."
        )

        val timeLines = when (timeSlot) {
            "새벽" -> listOf(
                "고요한 새벽길을 홀로 걸으며",
                "새벽녘 스며드는 푸른 어둠 속에",
                "모두가 잠든 차가운 새벽에"
            )
            "아침" -> listOf(
                "눈부신 아침 햇살에 눈을 뜨며",
                "새 아침이 시작되는 기분 좋은 소리",
                "싱그러운 아침 공기를 품에 안고"
            )
            "한낮" -> listOf(
                "따스한 한낮의 햇볕 아래에서",
                "나른한 오후의 햇살이 가득할 때",
                "한낮의 눈부신 하늘을 우러러보며"
            )
            "저녁" -> listOf(
                "붉게 물드는 저녁 노을을 보며",
                "하루가 저무는 노을빛 아래 서서",
                "어스름한 저녁 거리의 불빛들을 지나"
            )
            else -> listOf( // "야간" (밤)
                "어두운 밤하늘 별빛을 따라서",
                "조용히 깊어가는 밤바람 소리 속에",
                "이 어둡고 차분한 밤의 정적 속에서"
            )
        }

        val line1 = lines1[random.nextInt(lines1.size)]
        val line2 = lines2[random.nextInt(lines2.size)]
        val line3 = lines3[random.nextInt(lines3.size)]
        val line4 = lines4[random.nextInt(lines4.size)]
        val timeLine = timeLines[random.nextInt(timeLines.size)]

        val humIntro = if (durationSec > 0) {
            "(${durationSec}초 동안 흥얼거린 $genre 멜로디에 담긴 이야기)"
        } else {
            "(${genre} 멜로디에 담긴 이야기)"
        }

        return """
            $humIntro
            (Verse 1)
            $timeLine
            $line1
            $line2
            $line3
            $line4
        """.trimIndent()
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
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                }
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
        exoPlayer?.let {
            if (it.playbackState == Player.STATE_ENDED) {
                it.seekTo(0)
            }
            it.play()
        }
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
            val files = directory.listFiles { file -> file.isFile && (file.extension == "m4a" || file.extension == "mp3") }
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

    fun getAudioDurationMs(path: String): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun getAudioDurationString(path: String): String {
        val ms = getAudioDurationMs(path)
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
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
