package com.dasmo.grammar.updater

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.dasmo.grammar.BuildConfig
import com.dasmo.grammar.R
import com.dasmo.grammar.engine.DasmoLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val apkSize: Long,
    val isNewer: Boolean
)

/**
 * Enterprise In-App Updater for Dasmo Grammar via GitHub Releases.
 * Checks for latest APK release, presents release notes, downloads, and invokes system installer.
 */
class DasmoUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "DasmoUpdateManager"
        private const val PREFS_NAME = "dasmo_updater_prefs"
        private const val KEY_REPO = "github_repo"
        const val DEFAULT_REPO = "SUBHOJITPAUL797/DASMO-GRAMMAR"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getRepository(): String {
        return prefs.getString(KEY_REPO, DEFAULT_REPO) ?: DEFAULT_REPO
    }

    fun setRepository(repo: String) {
        prefs.edit().putString(KEY_REPO, repo.trim()).apply()
    }

    suspend fun checkForUpdates(customRepo: String? = null): AppReleaseInfo? = withContext(Dispatchers.IO) {
        val repo = (customRepo ?: getRepository()).trim()
        val apiUrl = "https://api.github.com/repos/$repo/releases/latest"

        DasmoLogger.i(TAG, "Checking for updates from: $apiUrl")

        try {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "DasmoGrammar-Android/${BuildConfig.VERSION_NAME}")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                DasmoLogger.w(TAG, "No releases found on GitHub repo $repo")
                return@withContext null
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                DasmoLogger.w(TAG, "GitHub API returned status $responseCode")
                return@withContext null
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val changelog = json.optString("body", "Bug fixes and performance improvements.")
            val cleanRemoteVersion = tagName.removePrefix("v").trim()
            val currentVersion = BuildConfig.VERSION_NAME

            // Find APK asset
            var apkUrl = ""
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            // Fallback to html_url if no direct APK asset
            if (apkUrl.isEmpty()) {
                apkUrl = json.optString("html_url", "")
            }

            val isNewer = isVersionNewer(cleanRemoteVersion, currentVersion)
            DasmoLogger.i(TAG, "Latest release on GitHub: $tagName (isNewer=$isNewer, current=$currentVersion)")

            AppReleaseInfo(
                tagName = tagName,
                versionName = cleanRemoteVersion,
                title = title,
                changelog = changelog,
                downloadUrl = apkUrl,
                apkSize = apkSize,
                isNewer = isNewer
            )
        } catch (e: Exception) {
            DasmoLogger.e(TAG, "Error checking GitHub release", e)
            null
        }
    }

    private fun isVersionNewer(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = if (i < remoteParts.size) remoteParts[i] else 0
                val l = if (i < localParts.size) localParts[i] else 0
                if (r > l) return true
                if (r < l) return false
            }
            return false
        } catch (e: Exception) {
            return remote != local
        }
    }

    fun showUpdateDialog(activity: Activity, release: AppReleaseInfo, onDismiss: (() -> Unit)? = null) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.attributes?.windowAnimations = R.style.DasmoDialogAnimation

        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_in_app_update, null)
        dialog.setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tvUpdateTitle)
        val tvVersion = view.findViewById<TextView>(R.id.tvUpdateVersion)
        val tvChangelog = view.findViewById<TextView>(R.id.tvUpdateChangelog)
        val tvSize = view.findViewById<TextView>(R.id.tvUpdateSize)
        val btnUpdateNow = view.findViewById<Button>(R.id.btnUpdateNow)
        val btnLater = view.findViewById<Button>(R.id.btnUpdateLater)
        val progressBar = view.findViewById<ProgressBar>(R.id.pbDownloadUpdate)
        val tvDownloadStatus = view.findViewById<TextView>(R.id.tvDownloadStatus)

        tvTitle.text = release.title
        tvVersion.text = "Version ${release.tagName} is now available (Current: v${BuildConfig.VERSION_NAME})"
        tvChangelog.text = release.changelog

        if (release.apkSize > 0) {
            val mb = release.apkSize / (1024.0 * 1024.0)
            tvSize.text = "Download Size: %.1f MB".format(mb)
            tvSize.visibility = View.VISIBLE
        } else {
            tvSize.visibility = View.GONE
        }

        btnLater.setOnClickListener {
            dialog.dismiss()
            onDismiss?.invoke()
        }

        btnUpdateNow.setOnClickListener {
            if (release.downloadUrl.endsWith(".apk", ignoreCase = true)) {
                // Direct APK Download
                btnUpdateNow.isEnabled = false
                btnLater.isEnabled = false
                progressBar.visibility = View.VISIBLE
                tvDownloadStatus.visibility = View.VISIBLE
                tvDownloadStatus.text = "Connecting to GitHub..."

                startDownloadAndInstall(activity, release.downloadUrl, release.tagName,
                    onProgress = { percent ->
                        mainHandler.post {
                            val anim = android.animation.ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, percent)
                            anim.duration = 200
                            anim.interpolator = android.view.animation.DecelerateInterpolator()
                            anim.start()
                            tvDownloadStatus.text = "Downloading update: $percent%"
                        }
                    },
                    onComplete = { apkFile ->
                        mainHandler.post {
                            dialog.dismiss()
                            promptInstallApk(activity, apkFile)
                        }
                    },
                    onError = { errorMsg ->
                        mainHandler.post {
                            btnUpdateNow.isEnabled = true
                            btnLater.isEnabled = true
                            progressBar.visibility = View.GONE
                            tvDownloadStatus.text = "Download failed: $errorMsg"
                            tvDownloadStatus.setTextColor(Color.parseColor("#F43F5E"))
                            Toast.makeText(activity, "Update error: $errorMsg", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            } else {
                // Open browser release page
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))
                activity.startActivity(browserIntent)
                dialog.dismiss()
            }
        }

        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun startDownloadAndInstall(
        context: Context,
        downloadUrl: String,
        tagName: String,
        onProgress: (Int) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val url = URL(downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true

                var currentConnection: HttpURLConnection = conn
                var redirects = 0
                while (currentConnection.responseCode in 300..399 && redirects < 5) {
                    val location = currentConnection.getHeaderField("Location")
                    currentConnection = URL(location).openConnection() as HttpURLConnection
                    redirects++
                }

                val totalLength = currentConnection.contentLength
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val apkFile = File(dir, "dasmo_grammar_${tagName}.apk")

                currentConnection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalLength > 0) {
                                val progress = ((totalRead * 100) / totalLength).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                }

                DasmoLogger.i(TAG, "Update APK downloaded successfully to: ${apkFile.absolutePath}")
                onComplete(apkFile)
            } catch (e: Exception) {
                DasmoLogger.e(TAG, "Failed downloading update APK", e)
                onError(e.message ?: "Network error downloading update")
            }
        }.start()
    }

    fun promptInstallApk(activity: Activity, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                    Toast.makeText(activity, "Please allow Dasmo Grammar to install updates", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            activity.startActivity(installIntent)
        } catch (e: Exception) {
            DasmoLogger.e(TAG, "Error launching package installer", e)
            Toast.makeText(activity, "Unable to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
