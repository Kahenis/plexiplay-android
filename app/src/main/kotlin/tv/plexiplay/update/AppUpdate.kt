package tv.plexiplay.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateOffer(val version: String, val url: String)

class AppUpdate(private val http: OkHttpClient) {
    /** Canal unique : dernière release (non pre-release) du dépôt GitHub Kahenis/plexiplay-android. */
    fun latest(localVersion: String): UpdateOffer? = githubLatest(localVersion)

    private fun githubLatest(localVersion: String): UpdateOffer? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/Kahenis/plexiplay-android/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Plexiplay")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string().orEmpty())
            val tag = json.optString("tag_name")
            if (!newer(tag, localVersion)) return null
            val assets = json.optJSONArray("assets") ?: return null
            var url = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk")) url = asset.optString("browser_download_url")
            }
            if (url.isBlank()) return null
            return UpdateOffer(tag.removePrefix("v"), url)
        }
    }

    /** Mesures d'un téléchargement : temps réseau/disque, protocole, serveur, vitesse de pointe et finale. */
    class DownloadStats {
        @Volatile var protocol = ""
        @Volatile var host = ""
        @Volatile var readMs = 0L
        @Volatile var writeMs = 0L
        @Volatile var peak = 0.0
        @Volatile var last = 0.0
    }

    /** Client réseau dédié au téléchargement : HTTP/1.1, ni pool ni file d'attente partagés avec les affiches. */
    private val downloader: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun download(url: String, dest: File, stats: DownloadStats = DownloadStats(), onProgress: (Long, Long, Double) -> Unit = { _, _, _ -> }) {
        val request = Request.Builder().url(url).header("User-Agent", "Plexiplay").get().build()
        downloader.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Téléchargement ${response.code}")
            stats.protocol = response.protocol.toString()
            stats.host = response.request.url.host
            val body = response.body ?: error("Téléchargement vide")
            val total = body.contentLength()
            var done = 0L
            var windowAt = System.nanoTime()
            var windowBytes = 0L
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val r0 = System.nanoTime()
                        val n = input.read(buffer)
                        stats.readMs += (System.nanoTime() - r0) / 1_000_000
                        if (n < 0) break
                        val w0 = System.nanoTime()
                        out.write(buffer, 0, n)
                        stats.writeMs += (System.nanoTime() - w0) / 1_000_000
                        done += n
                        windowBytes += n
                        val now = System.nanoTime()
                        if (now - windowAt >= 2_000_000_000L) {
                            val speed = windowBytes / 1048576.0 / ((now - windowAt) / 1e9)
                            stats.last = speed
                            if (speed > stats.peak) stats.peak = speed
                            windowAt = now
                            windowBytes = 0
                        }
                        if (total > 0) onProgress(done, total, stats.last)
                    }
                }
            }
            if (total > 0 && done != total) error("Téléchargement incomplet")
        }
    }

    /**
     * Télécharge puis remet l'APK à Android, en chronométrant chaque étape.
     * [onDetail] reçoit le texte d'avancement (Mo, vitesse, étape), [onProgress] la fraction 0..1.
     * Renvoie un message d'erreur, ou null quand l'installation est lancée.
     */
    fun fetchAndInstall(context: Context, offer: UpdateOffer, onDetail: (String) -> Unit, onProgress: (Float) -> Unit): String? {
        UpdateBus.timing = ""
        return runCatching {
            val file = File(context.cacheDir, "plexiplay-update.apk")
            val t0 = System.currentTimeMillis()
            var lastText = 0L
            val stats = DownloadStats()
            val progress: (Long, Long, Double) -> Unit = { done, total, instant ->
                onProgress(done.toFloat() / total)
                val now = System.currentTimeMillis()
                if (now - lastText >= 500 || done == total) {
                    lastText = now
                    onDetail(
                        "%.0f / %.0f Mo · %.1f Mo/s (moyenne %.1f) · %s".format(
                            done / 1048576.0, total / 1048576.0, instant,
                            done / 1048576.0 / ((now - t0) / 1000.0).coerceAtLeast(0.1), stats.protocol,
                        ),
                    )
                }
            }
            download(offer.url, file, stats, progress)
            checkApk(context, file, offer.version)
            val t1 = System.currentTimeMillis()
            onProgress(1f)
            onDetail("Préparation de l'installation…")
            val result = install(context, file)
            val t2 = System.currentTimeMillis()
            val throttled = stats.peak > 0 && stats.last < stats.peak / 4 && stats.readMs > stats.writeMs * 3
            UpdateBus.timing = " (reçu en ${(t1 - t0) / 1000} s, préparé en ${(t2 - t1) / 1000} s ; réseau ${stats.readMs / 1000} s, disque ${stats.writeMs / 1000} s ; " +
                "pointe %.1f Mo/s, fin %.1f Mo/s ; ${stats.protocol} ${stats.host}".format(stats.peak, stats.last) +
                (if (throttled) " ; débit réseau limité côté serveur" else "") + ")"
            result?.let { it + UpdateBus.timing } ?: ("Confirmez l'installation à l'écran" + UpdateBus.timing)
        }.getOrElse { it.message ?: "Installation impossible" }
    }

    /** Vérifie que le fichier est bien un APK de Plexiplay, plus récent que la version installée (et de la version annoncée par la release). */
    private fun checkApk(context: Context, apk: File, expected: String?) {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: error("Fichier de mise à jour illisible")
        if (info.packageName != context.packageName) error("Ce fichier n'est pas Plexiplay")
        val found = info.versionName.orEmpty()
        if (expected != null && found != expected) error("La release annonce $expected mais le fichier contient $found")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        if (!newer(found, installed)) error("Le fichier téléchargé ($found) n'est pas plus récent que $installed")
    }

    /** Plan B : l'installateur classique d'Android, quand la session ne montre aucune fenêtre. */
    fun installClassic(context: Context, apk: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun install(context: Context, apk: File): String? {
        KeepOpen.on = true
        UpdateBus.seen = false
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return "Autorisez l'installation pour Plexiplay, puis réessayez."
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("plexiplay", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateReceiver::class.java),
                flags,
            )
            session.commit(pending.intentSender)
        }
        // Si Android n'a rien montré après 8 secondes, on bascule sur l'installateur classique.
        Thread {
            Thread.sleep(8000)
            if (!UpdateBus.seen) {
                UpdateBus.post("Ouverture de l'installateur classique…")
                runCatching { installClassic(context, apk) }
                    .onFailure { UpdateBus.post("Installation impossible : ${it.message}") }
            }
        }.start()
        return null
    }

    private fun newer(remote: String, local: String): Boolean {
        // « 1.0.0-RC » (release candidate) est plus ancienne que « 1.0.0 ».
        fun numbers(value: String) = value.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        // « 1.0.0-RC2 » est postérieure à « 1.0.0-RC » ; la version finale passe devant toutes les candidates.
        fun rank(value: String) = if (!value.contains('-')) Int.MAX_VALUE else value.substringAfter('-').filter { it.isDigit() }.toIntOrNull() ?: 0
        val left = numbers(remote)
        val right = numbers(local)
        val count = maxOf(left.size, right.size)
        for (index in 0 until count) {
            val delta = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
            if (delta != 0) return delta > 0
        }
        return rank(remote) > rank(local)
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        UpdateBus.seen = true
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm == null) {
                    UpdateBus.seen = false
                    return
                }
                UpdateBus.post("Confirmez l'installation à l'écran")
                runCatching { context.startActivity(confirm) }
                    .onFailure { UpdateBus.post("Installation impossible : ${it.message}") }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateBus.post("Installation terminée")
            else -> {
                val text = "Installation refusée : " + detail.ifBlank { "code $status" }
                UpdateBus.post(text)
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** Passerelle entre l'installateur (récepteur, fils secondaires) et l'écran. */
object UpdateBus {
    @Volatile var seen = false
    @Volatile var timing = ""
    @Volatile var listener: ((String) -> Unit)? = null
    fun post(message: String) {
        val l = listener ?: return
        val text = if (message.startsWith("Confirmez")) message + timing else message
        android.os.Handler(android.os.Looper.getMainLooper()).post { l(text) }
    }
}

/** Vrai pendant une installation de mise à jour : l'appli ne doit pas se fermer quand l'installateur passe devant. */
object KeepOpen {
    @Volatile
    var on = false
}
