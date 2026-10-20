package tv.plexiplay.ui.shell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Affiches réduites : mémoire pour le retour immédiat à l'écran, disque pour
 * ne pas re-télécharger. Deux fils en basse priorité, le défilement passe devant.
 */
class PosterStore(
    cacheDir: File,
    private val http: OkHttpClient,
) {
    private val dir = File(cacheDir, "posters").apply { mkdirs() }
    private val indexFile = File(dir, "sections.tsv")
    private val memory = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val urgent = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "poster").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val idle = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "poster-idle").apply { priority = Thread.MIN_PRIORITY }
    }
    private val main = Handler(Looper.getMainLooper())
    private val locks = ConcurrentHashMap<String, Any>()
    private val sections = loadIndex()
    private val visible = AtomicInteger(0)

    /** Nombre d'affiches visibles encore en cours de chargement. */
    fun pendingVisible(): Int = visible.get()

    fun peek(ratingKey: String, path: String): Bitmap? = memory.get(id(ratingKey, path))

    fun load(ratingKey: String, path: String, urls: List<String>, onReady: (Bitmap) -> Unit) {
        if (urls.isEmpty()) return
        val key = id(ratingKey, path)
        memory.get(key)?.let { onReady(it); return }
        visible.incrementAndGet()
        urgent.execute {
            try {
                val bitmap = readOrFetch(key, urls) ?: return@execute
                memory.put(key, bitmap)
                main.post { onReady(bitmap) }
            } finally {
                visible.decrementAndGet()
            }
        }
    }

    fun prefetch(entries: List<Triple<String, String, List<String>>>) {
        idle.execute {
            for ((ratingKey, path, urls) in entries) {
                if (urls.isEmpty()) continue
                while (visible.get() > 0) Thread.sleep(40)
                val key = id(ratingKey, path)
                if (memory.get(key) != null || File(dir, "$key.jpg").exists()) continue
                readOrFetch(key, urls)
            }
        }
    }

    /**
     * Uniquement après la liste complète d'une bibliothèque. On retire les
     * affiches des titres qui n'y sont plus, pas celles des autres rayons.
     */
    fun syncSection(sectionKey: String, ratingKeys: Set<String>) {
        idle.execute {
            val previous = sections[sectionKey].orEmpty()
            (previous - ratingKeys).forEach { ratingKey ->
                dir.listFiles { file ->
                    (file.name.startsWith("p2-$ratingKey-") || file.name.startsWith("$ratingKey-")) && file.name.endsWith(".jpg")
                }?.forEach { it.delete() }
                memory.snapshot().keys.filter { it.startsWith("p2-$ratingKey-") || it.startsWith("$ratingKey-") }.forEach { memory.remove(it) }
            }
            sections[sectionKey] = ratingKeys
            saveIndex()
            trimDisk()
        }
    }

    private fun readOrFetch(key: String, urls: List<String>): Bitmap? {
        val file = File(dir, "$key.jpg")
        synchronized(locks.getOrPut(key) { Any() }) {
            if (!file.exists() || file.length() == 0L || !decodes(file)) {
                file.delete()
                for (url in urls) {
                    if (download(url, file)) break
                }
            }
        }
        if (!file.exists() || !decodes(file)) {
            file.delete()
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 480 && bounds.outWidth > 0) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodes(file: File): Boolean {
        if (!file.exists() || file.length() < 32L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private fun download(url: String, file: File): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "image/jpeg,image/png,image/*")
            .get()
            .build()
        val client = http.newBuilder().callTimeout(12, TimeUnit.SECONDS).build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val tmp = File(dir, file.name + ".part")
                response.body?.byteStream()?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                if (!decodes(tmp)) {
                    tmp.delete()
                    false
                } else {
                    tmp.renameTo(file)
                    true
                }
            }
        }.getOrDefault(false)
    }

    private fun trimDisk() {
        val files = dir.listFiles { file -> file.extension == "jpg" }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= DISK_CAP) return
            total -= file.length()
            file.delete()
        }
    }

    private fun loadIndex(): MutableMap<String, Set<String>> {
        if (!indexFile.exists()) return mutableMapOf()
        val map = mutableMapOf<String, MutableSet<String>>()
        indexFile.readLines().forEach { line ->
            val parts = line.split('\t')
            if (parts.size == 2) map.getOrPut(parts[0]) { mutableSetOf() }.add(parts[1])
        }
        return map.mapValues { it.value.toSet() }.toMutableMap()
    }

    private fun saveIndex() {
        indexFile.writeText(sections.entries.joinToString("\n") { (section, keys) ->
            keys.joinToString("\n") { "$section\t$it" }
        })
    }

    private fun id(ratingKey: String, url: String): String {
        val path = url.substringBefore('?')
        return "p2-" + ratingKey + "-" + path.hashCode().toUInt().toString(16)
    }

    private companion object {
        const val DISK_CAP = 200L * 1024L * 1024L
    }
}
