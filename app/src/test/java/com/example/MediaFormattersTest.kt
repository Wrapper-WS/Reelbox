package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaItem
import com.example.data.MediaType
import com.example.ui.home.MediaFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaFormattersTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun sampleItem(
        id: Long = 1L,
        displayName: String = "clip.mp4",
        type: MediaType = MediaType.VIDEO,
        dateAddedSeconds: Long = 0L,
        dateTakenMillis: Long = 0L,
        durationMillis: Long = 0L,
        width: Int = 1920,
        height: Int = 1080,
        orientationDegrees: Int = 0,
        sizeBytes: Long = 0L,
    ) = MediaItem(
        id = id,
        contentUri = Uri.parse("content://media/external/video/media/$id"),
        displayName = displayName,
        type = type,
        mimeType = if (type == MediaType.VIDEO) "video/mp4" else "image/jpeg",
        dateAddedSeconds = dateAddedSeconds,
        dateTakenMillis = dateTakenMillis,
        durationMillis = durationMillis,
        width = width,
        height = height,
        orientationDegrees = orientationDegrees,
        sizeBytes = sizeBytes,
        bucketId = "camera",
        bucketName = "Camera",
    )

    @Test
    fun `formatDuration handles negative zero seconds minutes and hours`() {
        assertEquals("0:00", MediaFormatters.formatDuration(-5000L))
        assertEquals("0:00", MediaFormatters.formatDuration(0L))
        assertEquals("0:42", MediaFormatters.formatDuration(42_000L))
        assertEquals("3:05", MediaFormatters.formatDuration(185_000L))
        assertEquals("1:02:09", MediaFormatters.formatDuration(3_729_000L))
    }

    @Test
    fun `formatDate ignores missing or 1970 placeholder timestamps`() {
        val noDate = sampleItem(dateTakenMillis = 0L, dateAddedSeconds = 0L)
        assertEquals("", MediaFormatters.formatDate(noDate))

        val tinyEpoch = sampleItem(dateTakenMillis = 1_000L, dateAddedSeconds = 60L)
        assertEquals("", MediaFormatters.formatDate(tinyEpoch))

        val validDateTaken = sampleItem(dateTakenMillis = 1_700_000_000_000L)
        assertTrue(MediaFormatters.formatDate(validDateTaken).contains("2023"))

        val fallbackDateAdded = sampleItem(dateTakenMillis = 0L, dateAddedSeconds = 1_700_000_000L)
        assertTrue(MediaFormatters.formatDate(fallbackDateAdded).contains("2023"))
    }

    @Test
    fun `formatFileSize handles zero negative tiny and huge files`() {
        assertEquals("", MediaFormatters.formatFileSize(0L))
        assertEquals("", MediaFormatters.formatFileSize(-128L))
        assertEquals("1 B", MediaFormatters.formatFileSize(1L))
        assertEquals("512 B", MediaFormatters.formatFileSize(512L))
        assertEquals("2 KB", MediaFormatters.formatFileSize(2048L))
        assertEquals("1.5 MB", MediaFormatters.formatFileSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.50 GB", MediaFormatters.formatFileSize((2.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("1.25 TB", MediaFormatters.formatFileSize((1.25 * 1024 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `displayTitle sanitizes multiline and very long names and falls back cleanly`() {
        val multiline = sampleItem(displayName = "  Summer\nTrip\t2025.mp4  ")
        assertEquals("Summer Trip 2025.mp4", MediaFormatters.displayTitle(context, multiline))

        val veryLong = "a".repeat(500) + ".mp4"
        assertEquals(veryLong, MediaFormatters.displayTitle(context, sampleItem(displayName = veryLong)))

        val blankVideo = sampleItem(displayName = "   \n  ", type = MediaType.VIDEO)
        assertEquals("Video", MediaFormatters.displayTitle(context, blankVideo))

        val blankPhoto = sampleItem(displayName = "", type = MediaType.PHOTO)
        assertEquals("Photo", MediaFormatters.displayTitle(context, blankPhoto))
    }

    @Test
    fun `formatMetadataSubtitle omits blank pieces without stray separators`() {
        val missingBoth = sampleItem(dateTakenMillis = 0L, dateAddedSeconds = 0L, sizeBytes = 0L)
        assertEquals("", MediaFormatters.formatMetadataSubtitle(missingBoth))

        val sizeOnly = sampleItem(dateTakenMillis = 0L, dateAddedSeconds = 0L, sizeBytes = 1024L * 1024L)
        assertEquals("1.0 MB", MediaFormatters.formatMetadataSubtitle(sizeOnly))

        val both = sampleItem(dateTakenMillis = 1_700_000_000_000L, sizeBytes = 2048L)
        val subtitle = MediaFormatters.formatMetadataSubtitle(both)
        assertTrue(subtitle.contains(" · "))
        assertTrue(subtitle.endsWith("2 KB"))
    }

    @Test
    fun `effectiveAspectRatio handles landscape portrait rotation and zero dimensions`() {
        assertEquals(16f / 9f, sampleItem(width = 1920, height = 1080).effectiveAspectRatio, 0.001f)
        assertEquals(9f / 16f, sampleItem(width = 1920, height = 1080, orientationDegrees = 90).effectiveAspectRatio, 0.001f)
        assertEquals(9f / 16f, sampleItem(width = 1920, height = 1080, orientationDegrees = 270).effectiveAspectRatio, 0.001f)
        assertEquals(1f, sampleItem(width = 1080, height = 1080).effectiveAspectRatio, 0.001f)
        assertEquals(0f, sampleItem(width = 0, height = 0).effectiveAspectRatio, 0.001f)
        assertEquals(0f, sampleItem(width = -100, height = 200).effectiveAspectRatio, 0.001f)
    }
}
