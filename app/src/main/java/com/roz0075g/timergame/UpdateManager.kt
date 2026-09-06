package com.roz0075g.timergame

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val RELEASES_API = "https://api.github.com/repos/roz0075g/timer-game/releases/latest"
private const val UPDATE_FILE_NAME = "timer-game-update.apk"

private data class UpdateInfo(
    val versionName: String,
    val apkUrl: String
)

@Composable
fun CheckForAppUpdate() {
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        updateInfo = runCatching { checkLatestRelease(context) }.getOrNull()
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { if (!downloading) updateInfo = null },
            title = { Text("新しいバージョンがあります") },
            text = {
                Text(
                    if (downloading) "v${info.versionName} をダウンロードしています…"
                    else "v${info.versionName} に更新できます。更新しますか？"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (downloading) return@Button
                        downloading = true
                        errorMessage = null
                        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                            runCatching { downloadAndInstall(context, info) }
                                .onFailure { errorMessage = it.message ?: "更新に失敗しました" }
                            downloading = false
                        }
                    },
                    enabled = !downloading
                ) { Text("更新") }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }, enabled = !downloading) { Text("後で") }
            }
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("更新できませんでした") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } }
        )
    }
}

private suspend fun checkLatestRelease(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
    val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "TimerGame/${context.packageName}")
    }
    try {
        if (connection.responseCode !in 200..299) return@withContext null
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val tag = Regex("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(json)?.groupValues?.get(1)
            ?: return@withContext null
        val apkUrl = Regex("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+timer-game-release\\.apk)\\\"")
            .find(json)?.groupValues?.get(1) ?: return@withContext null
        val latest = tag.removePrefix("v")
        val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        if (compareVersions(latest, current) > 0) UpdateInfo(latest, apkUrl) else null
    } finally {
        connection.disconnect()
    }
}

private suspend fun downloadAndInstall(context: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
    val updateDir = File(context.cacheDir, "update").apply { mkdirs() }
    val apkFile = File(updateDir, UPDATE_FILE_NAME)
    val connection = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 60_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "TimerGame/${context.packageName}")
    }
    try {
        if (connection.responseCode !in 200..299) error("APKの取得に失敗しました (HTTP ${connection.responseCode})")
        connection.inputStream.use { input -> apkFile.outputStream().use { output -> input.copyTo(output) } }
    } finally {
        connection.disconnect()
    }

    withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(settingsIntent)
            error("「この提供元を許可」を有効にしてから、もう一度アプリを起動してください")
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

private fun compareVersions(left: String, right: String): Int {
    val a = left.split(".").map { it.toIntOrNull() ?: 0 }
    val b = right.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val av = a.getOrElse(i) { 0 }
        val bv = b.getOrElse(i) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}
