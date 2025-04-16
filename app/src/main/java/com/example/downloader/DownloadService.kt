package com.example.downloader

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ProtocolException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLProtocolException

class DownloadService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_CANCEL = "ACTION_CANCEL"
        const val EXTRA_URL = "EXTRA_URL"
        const val DOWNLOAD_BUFFER_SIZE = 8192
    }

    private val clint = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private var downloadJob: Job? = null
    private var currentFile: RandomAccessFile? = null
    private var downloadedBytes = 0L
    private var totalBytes = 0L
    private var currentUrl: String? = null
    private val fileLock = Any()
    private var lastNotificationUpdateTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        Log.d("DownloadService", "Service created")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Log.d("DownloadService", "Received action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val destination = getPublicDownloadFile("downloaded_file_name.mp4")

                currentUrl = url
                startDownload(url, destination)
            }
            ACTION_PAUSE -> pauseDownload()
            ACTION_RESUME -> resumeDownload()
            ACTION_CANCEL -> cancelDownload()
        }
        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startDownload(url: String, destination: File?) {
        Log.d("DownloadService", "Starting download: $url")

        downloadedBytes = if (destination!!.exists()) destination.length() else 0L

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        if (downloadedBytes > 0) {
                            header("Range", "bytes=$downloadedBytes-")
                            Log.d("DownloadService", "Using Range header; already downloaded: $downloadedBytes bytes")
                        }
                    }
                    .build()

                clint.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) throw IOException("Unexpected code ${response.code}")

                    totalBytes = response.body!!.contentLength() + downloadedBytes

                    Log.d("DownloadService", "Total size: $totalBytes bytes")

                    RandomAccessFile(destination, "rw").use {file ->
                        currentFile = file
                        file.seek(downloadedBytes)

                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        var bytesRead: Int

                        response.body!!.byteStream().use { input ->
                            while (true) {
                                try {
                                    bytesRead = input.read(buffer)
                                    if (bytesRead == -1) break
                                    synchronized(fileLock) {
                                        file.write(buffer, 0, bytesRead)
                                    }
                                    downloadedBytes += bytesRead
                                    updateNotification()
                                    ensureActive()
                                } catch (e: IOException) {
                                    if (destination.length() >= totalBytes) {
                                        Log.d("DownloadService", "Download completed successfully despite IOException")
                                        break
                                    } else {
                                        throw e
                                    }
                                }
                            }
                        }
                    }
                    if (destination.length() >= totalBytes) {
                        Log.d("DownloadService", "Download completed successfully")
                        sendBroadcast(Intent("DOWNLOAD_COMPLETE"))
                    } else {
                        throw IOException("File incomplete: downloaded ${destination.length()} of $totalBytes")
                    }
                }
            } catch (e: CancellationException) {
                Log.d("DownloadService", "Download paused")
                throw e
            } catch (e: IOException) {
                Log.e("DownloadService", "Download failed due to IOException", e)
                sendBroadcast(Intent("DOWNLOAD_ERROR").apply {
                    putExtra("error", e.message)
                })
            } catch (e: Exception) {
                Log.e("DownloadService", "Download failed", e)
                sendBroadcast(Intent("DOWNLOAD_ERROR").apply {
                    putExtra("error", e.message)
                })
            } finally {
                currentFile?.close()
                stopForeground(true)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active downloads"
                setSound(null, null)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int): Notification {
        val pauseResumeAction = if (downloadJob?.isActive == true) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, DownloadService::class.java).apply {
                        action = ACTION_PAUSE
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Resume",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, DownloadService::class.java).apply {
                        action = ACTION_RESUME
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (downloadJob?.isActive == true) "Downloading..." else "Paused")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, totalBytes == 0L)
            .setOngoing(progress in 0 .. 99)
            .addAction(pauseResumeAction)
            .addAction(
                android.R.drawable.ic_delete,
                "Cancel",
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, DownloadService::class.java).apply {
                        action = ACTION_CANCEL
                    },
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("ForegroundServiceType", "MissingPermission")
    private fun updateNotification() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNotificationUpdateTime < 3000) return
        lastNotificationUpdateTime = currentTime

        val progress = (downloadedBytes.toFloat() / totalBytes.toFloat() * 100).toInt()
        val notification = buildNotification(progress)

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun pauseDownload() {
        downloadJob?.cancel()
        currentFile?.close()
        updateNotification()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun resumeDownload() {

        if (currentUrl == null) {
            sendBroadcast(Intent("DOWNLOAD_ERROR").apply {
                putExtra("error", "Nothing to resume")
            })
            return
        }

        val destination = getPublicDownloadFile("your_filename.mp4")

        if (!destination.exists()) {
            sendBroadcast(Intent("DOWNLOAD_ERROR").apply {
                putExtra("error", "File corruption detected")
            })
            return
        }

        downloadedBytes = destination.length()

        Log.d("DownloadService", "Resuming from $downloadedBytes bytes")

        startDownload(currentUrl!!, destination)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun cancelDownload() {
        downloadJob?.cancel()
        currentFile?.close()
        getPublicDownloadFile("your_filename.mp4").delete()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        currentFile?.close()

        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getPublicDownloadFile(fileName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, fileName)
    }

}