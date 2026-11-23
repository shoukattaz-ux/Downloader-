package com.stashly.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.stashly.app.MainActivity
import com.stashly.app.R
import com.stashly.app.core.Formatters
import com.stashly.app.data.db.DownloadEntity
import com.stashly.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    companion object {
        const val CHANNEL_PROGRESS = "downloads_progress"
        const val CHANNEL_RESULT = "downloads_result"
        fun notificationId(downloadId: Long) = 10_000 + (downloadId % 100_000).toInt()
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Shows ongoing downloads"; setShowBadge(false) })
        manager.createNotificationChannel(NotificationChannel(CHANNEL_RESULT, "Download results", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Completed and failed downloads" })
    }

    private fun openIntent(id: Long): PendingIntent = PendingIntent.getActivity(
        context, id.toInt(),
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DOWNLOAD_ID, id)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun foregroundInfo(e: DownloadEntity): ForegroundInfo {
        val n = progress(e)
        val id = notificationId(e.id)
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id, n)
    }

    fun progress(e: DownloadEntity): Notification {
        val b = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(e.title)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(openIntent(e.id))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (e.totalBytes > 0) {
            b.setProgress(100, e.progressPercent, false)
            b.setContentText("${e.progressPercent}% · ${Formatters.speed(e.speedBps)}")
        } else {
            b.setProgress(0, 0, true).setContentText("Starting…")
        }
        return b.build()
    }

    suspend fun completed(e: DownloadEntity) {
        if (!settings.current().notifications) return
        manager.cancel(notificationId(e.id))
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_download).setContentTitle("Download complete")
            .setContentText(e.fileName).setAutoCancel(true).setContentIntent(openIntent(e.id)).build()
        runCatching { manager.notify(notificationId(e.id), n) }
    }

    suspend fun failed(e: DownloadEntity, message: String) {
        if (!settings.current().notifications) return
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_download).setContentTitle("Download failed")
            .setContentText("${e.title}: $message").setStyle(NotificationCompat.BigTextStyle().bigText("${e.title}: $message"))
            .setAutoCancel(true).setContentIntent(openIntent(e.id)).build()
        runCatching { manager.notify(notificationId(e.id), n) }
    }

    fun dismiss(id: Long) = manager.cancel(notificationId(id))
}
