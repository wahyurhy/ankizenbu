package com.wahyurhy.ankizenbu.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
fun ImagePreviewer(bitmap: Bitmap) {
    // State untuk mengatur apakah gambar sedang dipreview
    var isPreviewVisible by remember { mutableStateOf(false) }

    // Gambar dengan klik untuk preview
    androidx.compose.foundation.Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Selected Image",
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color.White)
            .clickable { isPreviewVisible = true } // Klik untuk memunculkan preview
    )

    // Dialog untuk menampilkan gambar dalam ukuran penuh
    if (isPreviewVisible) {
        AlertDialog(
            onDismissRequest = { isPreviewVisible = false },
            confirmButton = {},
            dismissButton = {},
            text = {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Preview Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                )
            }
        )
    }
}