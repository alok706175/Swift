package com.swiftapp.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.swiftapp.R
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * System notification helper for completed PDF operations (Merge, Compress, Scan, Image to PDF).
 */
object NotificationHelper {

    const val CHANNEL_OPERATIONS_ID = "swift_operations_channel"
    private const val CHANNEL_NAME = "Operation Alerts"
    private const val CHANNEL_DESC = "Notifications and sound alerts for completed PDF merges, compressions, and scans"
    
    private val notificationCounter = AtomicInteger(1000)

    fun init(context: Context) {
        createNotificationChannels(context.applicationContext)
    }

    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build()

            val channel = NotificationChannel(
                CHANNEL_OPERATIONS_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 150)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Show heads-up banner notification with sound alert when an operation completes.
     */
    fun showOperationCompleteNotification(
        context: Context,
        title: String,
        message: String,
        file: File? = null
    ) {
        // Check if user disabled notifications in app settings
        if (!NotificationSettingsManager.isNotificationEnabledFlow.value) {
            return
        }

        try {
            val appContext = context.applicationContext
            val notificationId = notificationCounter.incrementAndGet()
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(appContext, CHANNEL_OPERATIONS_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 150, 80, 150))
                .setAutoCancel(true)

            if (file != null && file.exists()) {
                val fileUri: Uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file
                )

                // Tap action: View PDF
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val viewPendingIntent = PendingIntent.getActivity(
                    appContext,
                    notificationId,
                    viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(viewPendingIntent)

                // Action 1: Open PDF
                builder.addAction(
                    android.R.drawable.ic_menu_view,
                    "Open PDF",
                    viewPendingIntent
                )

                // Action 2: Share PDF
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val shareChooserIntent = Intent.createChooser(shareIntent, "Share PDF")
                val sharePendingIntent = PendingIntent.getActivity(
                    appContext,
                    notificationId + 10000,
                    shareChooserIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(
                    android.R.drawable.ic_menu_share,
                    "Share",
                    sharePendingIntent
                )
            }

            val notificationManager = NotificationManagerCompat.from(appContext)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission not granted by user
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
