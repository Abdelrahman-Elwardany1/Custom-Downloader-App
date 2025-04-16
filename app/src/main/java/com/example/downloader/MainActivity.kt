package com.example.downloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.example.downloader.MainActivity.Companion.DOWNLOAD_URL
import com.example.downloader.ui.theme.DownloaderTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val DOWNLOAD_URL = "https://link.testfile.org/500MB"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DownloaderTheme {
                DownloadScreen()
            }
        }
    }
}

@Composable
fun DownloadScreen() {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                val intent = Intent(context, DownloadService::class.java).apply {
                    action = DownloadService.ACTION_START
                    putExtra(DownloadService.EXTRA_URL, DOWNLOAD_URL)
                }
                context.startService(intent)
                isDownloading = true
            }
        ) {
            Text("Start Download")
        }
    }
}