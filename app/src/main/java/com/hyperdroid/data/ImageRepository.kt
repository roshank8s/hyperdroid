package com.hyperdroid.data

import android.content.Context
import android.util.Log
import com.hyperdroid.model.OSImage
import com.hyperdroid.model.OSType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepository @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "ImageRepository"
        private const val IMAGES_DIR = "vm_images"
    }

    private val imagesDir: File
        get() = File(context.getExternalFilesDir(null), IMAGES_DIR).also { it.mkdirs() }

    fun getAvailableImages(): List<OSImage> {
        // Hardcoded for Phase 2 — single Debian image
        return listOf(
            OSImage(
                id = "debian-12-minimal",
                name = "Debian 12 Minimal",
                osType = OSType.DEBIAN,
                version = "12.0",
                downloadUrl = "", // To be configured with actual CDN URL
                sha256 = "",
                sizeBytes = 200 * 1024 * 1024, // ~200MB
                kernelFileName = "kernel",
                initrdFileName = "initrd.img",
                rootfsFileName = "rootfs.img"
            )
        )
    }

    fun isImageDownloaded(imageId: String): Boolean {
        val imageDir = File(imagesDir, imageId)
        if (!imageDir.exists()) return false

        val image = getAvailableImages().find { it.id == imageId } ?: return false
        return File(imageDir, image.rootfsFileName).exists()
    }

    fun getImageDir(imageId: String): File? {
        val dir = File(imagesDir, imageId)
        return if (dir.exists()) dir else null
    }

    fun getKernelPath(imageId: String): String? {
        val image = getAvailableImages().find { it.id == imageId } ?: return null
        val file = File(File(imagesDir, imageId), image.kernelFileName)
        return if (file.exists()) file.absolutePath else null
    }

    fun getRootfsPath(imageId: String): String? {
        val image = getAvailableImages().find { it.id == imageId } ?: return null
        val file = File(File(imagesDir, imageId), image.rootfsFileName)
        return if (file.exists()) file.absolutePath else null
    }

    suspend fun downloadImage(
        image: OSImage,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (image.downloadUrl.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Download URL not configured for ${image.name}")
                )
            }

            val imageDir = File(imagesDir, image.id).also { it.mkdirs() }
            val tempFile = File(imageDir, "${image.rootfsFileName}.tmp")
            val finalFile = File(imageDir, image.rootfsFileName)

            // Download with progress
            val url = URL(image.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(downloadedBytes.toFloat() / totalBytes)
                        }
                    }
                }
            }

            // Validate checksum if provided
            if (image.sha256.isNotBlank()) {
                val hash = computeSHA256(tempFile)
                if (!hash.equals(image.sha256, ignoreCase = true)) {
                    tempFile.delete()
                    return@withContext Result.failure(
                        SecurityException("SHA-256 checksum mismatch")
                    )
                }
            }

            // Rename temp to final
            tempFile.renameTo(finalFile)

            Log.i(TAG, "Image downloaded: ${image.name} -> ${finalFile.absolutePath}")
            Result.success(finalFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download image: ${image.name}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteImage(imageId: String): Boolean = withContext(Dispatchers.IO) {
        val imageDir = File(imagesDir, imageId)
        if (imageDir.exists()) {
            imageDir.deleteRecursively()
        } else {
            true
        }
    }

    private fun computeSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
