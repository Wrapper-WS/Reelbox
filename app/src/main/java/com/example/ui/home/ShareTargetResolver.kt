package com.example.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.example.data.MediaItem
import com.example.data.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class ShareAppTarget(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap?,
)

object ShareTargetResolver {

    private const val MAX_SHARE_APPS = 8

    // Most common consumer sharing apps ranked first
    private val PRIORITY_PACKAGES = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.instagram.android",
        "com.facebook.orca",
        "org.thoughtcrime.securesms",
        "com.snapchat.android",
        "com.discord",
        "com.google.android.gm",
        "com.google.android.apps.docs",
        "com.google.android.apps.photos",
        "com.google.android.gms",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.Slack",
        "com.microsoft.office.outlook",
        "com.facebook.katana",
        "com.viber.voip",
        "jp.naver.line.android",
        "com.linkedin.android",
    )

    suspend fun loadShareTargets(
        context: Context,
        item: MediaItem,
        iconSizePx: Int,
    ): List<ShareAppTarget> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mimeType = item.mimeType.ifBlank {
            if (item.type == MediaType.VIDEO) "video/*" else "image/*"
        }
        val queryIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
        }

        val resolved = queryActivitiesSafely(pm, queryIntent)
        val selfPackage = context.packageName

        val uniqueByPackage = linkedMapOf<String, ResolveInfo>()
        for (info in resolved) {
            val activityInfo = info.activityInfo ?: continue
            val pkg = activityInfo.packageName ?: continue
            if (pkg == selfPackage) continue
            if (!activityInfo.exported) continue
            if (pkg !in uniqueByPackage) {
                uniqueByPackage[pkg] = info
            }
        }

        val sorted = uniqueByPackage.values.sortedWith(
            compareBy<ResolveInfo> { info ->
                val pkg = info.activityInfo?.packageName.orEmpty()
                val idx = PRIORITY_PACKAGES.indexOf(pkg)
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenBy { info ->
                runCatching { info.loadLabel(pm).toString() }.getOrDefault("")
                    .lowercase()
            },
        )

        sorted.take(MAX_SHARE_APPS).mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString().trim() }
                .getOrDefault("")
                .ifEmpty { activityInfo.packageName }
            val bitmap = runCatching {
                val drawable = info.loadIcon(pm)
                val safeSize = iconSizePx.coerceIn(48, 256)
                drawable.toBitmap(width = safeSize, height = safeSize).asImageBitmap()
            }.getOrNull()

            ShareAppTarget(
                packageName = activityInfo.packageName,
                activityName = activityInfo.name,
                label = label,
                icon = bitmap,
            )
        }
    }

    fun launchDirectShare(
        context: Context,
        item: MediaItem,
        target: ShareAppTarget,
    ) {
        val mimeType = item.mimeType.ifBlank {
            if (item.type == MediaType.VIDEO) "video/*" else "image/*"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, item.contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            component = ComponentName(target.packageName, target.activityName)
        }
        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (!launched) {
            MediaFormatters.shareMedia(context, item)
        }
    }

    @Suppress("DEPRECATION")
    private fun queryActivitiesSafely(
        pm: PackageManager,
        intent: Intent,
    ): List<ResolveInfo> {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
            } else {
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrDefault(emptyList())
    }
}
