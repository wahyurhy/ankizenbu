package com.wahyurhy.ankizenbu

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.wahyurhy.ankizenbu.ui.components.DarkModeToggle
import com.wahyurhy.ankizenbu.ui.components.ImagePreviewer
import com.wahyurhy.ankizenbu.ui.theme.AnkiZenbuTheme
import com.wahyurhy.ankizenbu.utils.notifikasi.NotificationReceiver
import com.wahyurhy.ankizenbu.utils.tokenizeJapaneseText


class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Izin diberikan, Anda dapat menampilkan notifikasi
                println("Izin notifikasi diberikan")
            } else {
                // Izin ditolak, beri tahu pengguna
                println("Izin notifikasi ditolak")
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Periksa izin untuk SCHEDULE_EXACT_ALARM pada Android 12+ (API 31+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent) // Mengarahkan pengguna ke pengaturan untuk memberikan izin
            }
        }

        // Periksa dan minta izin jika diperlukan
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        createNotificationChannel()

        handleIntent(intent)

        setContent {
            MyApp(applicationContext, ::sendTextToExternalApp)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun sendTextToExternalApp(context: Context, text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.action == "SEND_WORD") {
                val word = it.getStringExtra("word")
                if (word != null) {
                    sendTextToExternalApp(applicationContext, word)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val name = "Word Reminder Channel"
        val descriptionText = "Channel for word reminders"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("word_reminder", name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MyApp(context: Context, sendTextToExternalApp: (Context, String) -> Unit) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var wordList by remember { mutableStateOf(listOf<String>()) }
    var recognizedText by remember { mutableStateOf("") }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isTextCleared by remember { mutableStateOf(false) }
    val sharedPreferences = context.getSharedPreferences("ClickedWords", Context.MODE_PRIVATE)
    val clickedWords = remember { mutableStateListOf<String>() }
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clickCounts = remember { mutableStateMapOf<String, Int>() }
    var isDarkMode by remember { mutableStateOf(false) }

    val totalClicksByCount = remember {
        mutableStateOf(mutableMapOf(1 to 0, 2 to 0, 3 to 0))
    }

    // Kalkulasi ulang `totalClicksByCount` setiap kali aplikasi di-render
    LaunchedEffect(Unit) {
        val counts = mutableMapOf(1 to 0, 2 to 0, 3 to 0)
        sharedPreferences.all.forEach { (key, value) ->
            if (key.endsWith("_clickCount") && value is Int) {
                when (value) {
                    1 -> counts[1] = counts[1]!! + 1
                    2 -> counts[2] = counts[2]!! + 1
                    3 -> counts[3] = counts[3]!! + 1
                }
            }
        }
        totalClicksByCount.value = counts
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bitmap = context.contentResolver.openInputStream(it)?.use { inputStream ->
                android.graphics.BitmapFactory.decodeStream(inputStream)
            }
            selectedImageBitmap = bitmap
        }
    }

    fun recognizeTextFromImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                recognizedText = visionText.text
                wordList = tokenizeJapaneseText(recognizedText)
            }
            .addOnFailureListener { exception ->
                recognizedText = "Error: ${exception.message}"
            }
    }

    LaunchedEffect(Unit) {
        clickedWords.addAll(sharedPreferences.all.keys)
    }

    AnkiZenbuTheme(darkTheme = isDarkMode) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                // Dark Mode Toggle
                DarkModeToggle(
                    isDarkMode = isDarkMode,
                    onToggle = { isDarkMode = it }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar
                    TopAppBar(
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Klik 1x: ${totalClicksByCount.value[1] ?: 0}",
                                    color = Color(0xFFBB0000), // Tetap merah cerah
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Klik 2x: ${totalClicksByCount.value[2] ?: 0}",
                                    color = Color(0xFF60D500), // Tetap hijau cerah
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Klik 3x: ${totalClicksByCount.value[3] ?: 0}",
                                    color = Color(0xFF447446), // Tetap hijau tua
                                    fontSize = 16.sp
                                )
                            }
                        },
                        colors = TopAppBarDefaults.smallTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface, // Warna latar belakang dari tema
                            titleContentColor = MaterialTheme.colorScheme.onPrimary // Warna teks dari tema
                        )
                    )

                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface, // Gunakan warna dari tema
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface // Gunakan warna teks dari tema
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tombol untuk menghitung teks manual atau clear text
                    Button(
                        onClick = {
                            if (!isTextCleared) {
                                wordList = tokenizeJapaneseText(inputText.text)
                            } else {
                                inputText = TextFieldValue("")
                                wordList = emptyList()
                            }
                            isTextCleared = !isTextCleared
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isTextCleared) Color(0xFFBC061F) else Color.Black)
                    ) {
                        Text(
                            text = if (!isTextCleared) "Hitung Teks Manual" else "Clear Text",
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Gambar yang dipilih
                    selectedImageBitmap?.let { bitmap ->
                        ImagePreviewer(bitmap = bitmap)
                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { recognizeTextFromImage(bitmap) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) {
                            Text(text = "Kenali Teks dari Gambar", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tombol untuk memilih gambar
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B7AD4))
                    ) {
                        Text(text = "Pilih Gambar", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Jumlah Kata
                    Text(text = "Jumlah Kata: ${wordList.size}", fontSize = 16.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Word Grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(87.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        // Filter kata-kata sebelum digunakan di grid
                        val filteredWordList = wordList.filter { word ->
                            word.trim().isNotEmpty() && !Regex("^[ :。、　.「」・·/()【】｜|①②③④⑤⑥⑦⑧⑨⓪~]+$").matches(word)
                        }

                        items(filteredWordList.size) { index ->
                            val word = filteredWordList[index].trim() // Trim untuk menghilangkan spasi di awal/akhir
                            val countedWords = sharedPreferences.getInt("${word}_clickCount", 0)
                            val clickCount = clickCounts[word] ?: countedWords // Ambil jumlah klik dari Map, default 0

                            // Warna tombol berdasarkan jumlah klik
                            val buttonColor = when (clickCount) {
                                1 -> Color(0xFFBB0000)
                                2 -> Color(0xFF60D500)
                                3 -> Color(0xFF447446)
                                else -> Color.Black // Tetap hitam jika belum pernah diklik
                            }

                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth()
                                    .background(
                                        color = buttonColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            // Perbarui jumlah klik
                                            val newClickCount = if (clickCount < 3) clickCount + 1 else 3 // Tetap di 3 jika sudah mencapai 3
                                            clickCounts[word] = newClickCount

                                            // Copy word to clipboard
                                            val clip = ClipData.newPlainText("Copied Text", word)
                                            clipboardManager.setPrimaryClip(clip)

                                            // Send word to external app
                                            sendTextToExternalApp(context, word)

                                            // Simpan jumlah klik di SharedPreferences
                                            sharedPreferences.edit { putInt("${word}_clickCount", newClickCount) }

                                            // Update `totalClicksByCount` secara langsung
                                            if (newClickCount < 3) { // Pastikan hanya dilakukan jika newClickCount < 3
                                                val currentCount = totalClicksByCount.value[newClickCount] ?: 0
                                                val previousCount = totalClicksByCount.value[clickCount] ?: 0
                                                totalClicksByCount.value = totalClicksByCount.value.toMutableMap().apply {
                                                    this[newClickCount] = currentCount + 1
                                                    this[clickCount] = maxOf(0, previousCount - 1)
                                                }
                                            } else if (newClickCount == 3 && clickCount < 3) {
                                                // Jika mencapai 3 dari kondisi sebelumnya <3, tambahkan ke `totalClicksByCount`
                                                val previousCount = totalClicksByCount.value[clickCount] ?: 0
                                                totalClicksByCount.value = totalClicksByCount.value.toMutableMap().apply {
                                                    this[3] = (this[3] ?: 0) + 1
                                                    this[clickCount] = maxOf(0, previousCount - 1)
                                                }
                                            }

                                            // Jika jumlah klik mencapai 3, jadwalkan notifikasi
                                            if (newClickCount == 3) {
                                                NotificationReceiver.scheduleReminder(context, word, 24 * 60 * 60 * 1000L) // 1 hari
                                            }
                                        },
                                        onLongClick = {
                                            // Reset jumlah klik
                                            clickCounts[word] = 0
                                            sharedPreferences.edit { putInt("${word}_clickCount", 0) }

                                            // Perbarui totalClicksByCount
                                            val previousCount = totalClicksByCount.value[clickCount] ?: 0
                                            if (clickCount > 0) {
                                                totalClicksByCount.value = totalClicksByCount.value.toMutableMap().apply {
                                                    this[clickCount] = maxOf(0, previousCount - 1)
                                                }
                                            }
                                        }
                                    )
                                    .padding(16.dp) // Untuk menambahkan padding ke dalam tombol
                            ) {
                                Text(
                                    text = word,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun MyAppPreview() {
//    MyApp(context = LocalContext.current, sendTextToExternalApp = ::sendTextToExternalApp)
//}