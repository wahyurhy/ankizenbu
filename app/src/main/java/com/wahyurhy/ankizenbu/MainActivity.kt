package com.wahyurhy.ankizenbu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.wahyurhy.ankizenbu.utils.tokenizeJapaneseText


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp(applicationContext)
        }
    }
}

@Composable
fun MyApp(context: Context) {
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var wordList by remember { mutableStateOf(listOf<String>()) }
    var recognizedText by remember { mutableStateOf("") }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isTextCleared by remember { mutableStateOf(false) }
    val sharedPreferences = context.getSharedPreferences("ClickedWords", Context.MODE_PRIVATE)
    val clickedWords = remember { mutableStateListOf<String>() }
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clickCounts = remember { mutableStateMapOf<String, Int>() }

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

    fun sendTextToExternalApp(context: Context, text: String) {
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

    LaunchedEffect(Unit) {
        clickedWords.addAll(sharedPreferences.all.keys)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Input Manual
        Text(text = "Input Manual", fontSize = 16.sp, modifier = Modifier.align(Alignment.Start))
        BasicTextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.White, shape = RoundedCornerShape(12.dp))
                .padding(12.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp)
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
            colors = ButtonDefaults.buttonColors(containerColor = if (isTextCleared) Color.Red else Color.Black)
        ) {
            Text(
                text = if (!isTextCleared) "Hitung Teks Manual" else "Clear Text",
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Gambar yang dipilih
        selectedImageBitmap?.let { bitmap ->
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(Color.LightGray)
            )
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
            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
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

                Button(
                    onClick = {
                        // Perbarui jumlah klik
                        val newClickCount = (clickCount + 1) % 4 // Siklus ulang ke 0 setelah 3
                        clickCounts[word] = newClickCount

                        // Copy word to clipboard
                        val clip = ClipData.newPlainText("Copied Text", word)
                        clipboardManager.setPrimaryClip(clip)

                        // Send word to external app
                        sendTextToExternalApp(context, word)

                        // Simpan jumlah klik di SharedPreferences
                        sharedPreferences.edit { putInt("${word}_clickCount", newClickCount) }
                    },
                    modifier = Modifier
                        .padding(4.dp)
                        .fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(word, fontSize = 14.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MyAppPreview() {
    MyApp(context = LocalContext.current)
}