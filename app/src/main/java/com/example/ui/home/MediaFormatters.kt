package com.example.ui.home

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import com.example.R
import com.example.data.MediaItem
import com.example.data.MediaType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface DeleteRequestResult {
    data object DeletedDirectly : DeleteRequestResult
    data class NeedsSystemConfirmation(val intentSender: IntentSender) : DeleteRequestResult
    data object NeedsLegacyConfirmation : DeleteRequestResult
    data object Failed : DeleteRequestResult
}

object MediaFormatters {

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    private const val MIN_VALID_EPOCH_MILLIS = 86_400_000L // Ignore 0 / 1970-01-01 placeholder timestamps

    fun formatDate(item: MediaItem): String {
        val millis = when {
            item.dateTakenMillis > MIN_VALID_EPOCH_MILLIS -> item.dateTakenMillis
            item.dateAddedSeconds > (MIN_VALID_EPOCH_MILLIS / 1000L) -> item.dateAddedSeconds * 1000L
            else -> return ""
        }
        return runCatching {
            val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            formatter.format(Date(millis))
        }.getOrDefault("")
    }

    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return ""
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0
        return when {
            sizeBytes >= tb -> String.format(Locale.getDefault(), "%.2f TB", sizeBytes / tb)
            sizeBytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", sizeBytes / gb)
            sizeBytes >= mb -> String.format(Locale.getDefault(), "%.1f MB", sizeBytes / mb)
            sizeBytes >= kb -> String.format(Locale.getDefault(), "%.0f KB", sizeBytes / kb)
            else -> "$sizeBytes B"
        }
    }

    fun formatMetadataSubtitle(item: MediaItem): String {
        val dateText = formatDate(item)
        val sizeText = formatFileSize(item.sizeBytes)
        return listOf(dateText, sizeText)
            .filter { it.isNotBlank() }
            .joinToString(separator = " · ")
    }

    fun sanitizeFileName(rawName: String): String {
        return rawName
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .trim()
    }

    fun displayTitle(context: Context, item: MediaItem): String {
        val cleanName = sanitizeFileName(item.displayName)
        if (cleanName.isNotEmpty()) return cleanName
        return when (item.type) {
            MediaType.VIDEO -> context.getString(R.string.feed_untitled_video)
            MediaType.PHOTO -> context.getString(R.string.feed_untitled_photo)
        }
    }

    fun shareMedia(context: Context, item: MediaItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType.ifBlank {
                if (item.type == MediaType.VIDEO) "video/*" else "image/*"
            }
            putExtra(Intent.EXTRA_STREAM, item.contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(
            shareIntent,
            context.getString(R.string.feed_share_title),
        )
        runCatching { context.startActivity(chooser) }
    }

    fun prepareDeleteRequest(context: Context, item: MediaItem): DeleteRequestResult {
        val resolver = context.contentResolver
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                runCatching {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        resolver,
                        listOf(item.contentUri),
                    )
                    DeleteRequestResult.NeedsSystemConfirmation(pendingIntent.intentSender)
                }.getOrElse { DeleteRequestResult.Failed }
            }

            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                try {
                    val deletedRows = resolver.delete(item.contentUri, null, null)
                    if (deletedRows > 0) {
                        DeleteRequestResult.DeletedDirectly
                    } else {
                        DeleteRequestResult.Failed
                    }
                } catch (securityException: SecurityException) {
                    val recoverable = securityException as? RecoverableSecurityException
                    if (recoverable != null) {
                        DeleteRequestResult.NeedsSystemConfirmation(
                            recoverable.userAction.actionIntent.intentSender,
                        )
                    } else {
                        DeleteRequestResult.Failed
                    }
                } catch (_: Exception) {
                    DeleteRequestResult.Failed
                }
            }

            else -> DeleteRequestResult.NeedsLegacyConfirmation
        }
    }

    fun deleteDirectly(context: Context, item: MediaItem): Boolean {
        return runCatching {
            context.contentResolver.delete(item.contentUri, null, null) > 0
        }.getOrDefault(false)
    }
}
