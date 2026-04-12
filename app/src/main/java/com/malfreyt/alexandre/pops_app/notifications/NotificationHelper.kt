package com.malfreyt.alexandre.pops_app.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.malfreyt.alexandre.pops_app.MainActivity
import com.malfreyt.alexandre.pops_app.R
import com.malfreyt.alexandre.pops_app.data.AppSettings
import com.malfreyt.alexandre.pops_app.data.NoteChangeType
import com.malfreyt.alexandre.pops_app.data.SyncChange

object NotificationHelper {
    private const val CHANNEL_GROUP_ID = "grades"
    private const val CHANNEL_NEW_GRADES = "grades_new"
    private const val CHANNEL_PENDING_GRADES = "grades_pending"
    private const val CHANNEL_UPDATED_GRADES = "grades_updated"
    private const val CHANNEL_SYNC_ERRORS = "grades_errors"
    private const val NEW_NOTIFICATION_ID = 1001
    private const val PENDING_NOTIFICATION_ID = 1002
    private const val UPDATED_NOTIFICATION_ID = 1003
    private const val FAILURE_NOTIFICATION_ID = 1004

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(CHANNEL_GROUP_ID, context.getString(R.string.notification_channel_group_name))
        )
        manager.createNotificationChannels(
            listOf(
                buildChannel(
                    id = CHANNEL_NEW_GRADES,
                    context = context,
                    nameRes = R.string.notification_channel_new_name,
                    descriptionRes = R.string.notification_channel_new_description,
                ),
                buildChannel(
                    id = CHANNEL_PENDING_GRADES,
                    context = context,
                    nameRes = R.string.notification_channel_pending_name,
                    descriptionRes = R.string.notification_channel_pending_description,
                ),
                buildChannel(
                    id = CHANNEL_UPDATED_GRADES,
                    context = context,
                    nameRes = R.string.notification_channel_updated_name,
                    descriptionRes = R.string.notification_channel_updated_description,
                ),
                buildChannel(
                    id = CHANNEL_SYNC_ERRORS,
                    context = context,
                    nameRes = R.string.notification_channel_error_name,
                    descriptionRes = R.string.notification_channel_error_description,
                ),
            )
        )
    }

    fun notifyChanges(context: Context, settings: AppSettings, changes: List<SyncChange>) {
        if (changes.isEmpty() || !settings.notificationsEnabled || !canNotify(context)) {
            return
        }

        GradeNotificationKind.entries.forEach { kind ->
            val matchingChanges = changes.filter { classify(it) == kind }
            if (matchingChanges.isEmpty() || !kind.isEnabled(settings)) {
                return@forEach
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                kind.notificationId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, kind.channelId)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(context.getString(kind.titleRes))
                .setContentText(buildSummary(context, kind, matchingChanges.size))
                .setStyle(NotificationCompat.BigTextStyle().bigText(buildDetails(context, kind, matchingChanges)))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(kind.category)
                .build()

            NotificationManagerCompat.from(context).notify(kind.notificationId, notification)
        }
    }

    fun notifySyncFailure(
        context: Context,
        attempts: Int,
        errorMessage: String,
        technicalDetails: String,
        onlyAlertOnce: Boolean,
    ) {
        if (!canNotify(context)) {
            return
        }

        val deleteIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_SYNC_FAILURE_DISMISSED
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = buildString {
            append(context.getString(R.string.notification_sync_failure_body_prefix, attempts))
            append("\n")
            append(errorMessage)
            if (technicalDetails.isNotBlank()) {
                append("\n\n")
                append(technicalDetails)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC_ERRORS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.notification_sync_failure_title))
            .setContentText(context.getString(R.string.notification_sync_failure_content, attempts))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setDeleteIntent(deleteIntent)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setOnlyAlertOnce(onlyAlertOnce)
            .build()

        NotificationManagerCompat.from(context).notify(FAILURE_NOTIFICATION_ID, notification)
    }

    fun clearSyncFailureNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(FAILURE_NOTIFICATION_ID)
    }

    private fun buildChannel(
        id: String,
        context: Context,
        nameRes: Int,
        descriptionRes: Int,
    ): NotificationChannel {
        return NotificationChannel(
            id,
            context.getString(nameRes),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            group = CHANNEL_GROUP_ID
            description = context.getString(descriptionRes)
        }
    }

    private fun buildSummary(context: Context, kind: GradeNotificationKind, count: Int): String {
        return context.getString(kind.summaryRes, count)
    }

    private fun formatLine(context: Context, kind: GradeNotificationKind, change: SyncChange): String {
        val prefix = when (kind) {
            GradeNotificationKind.NEW -> context.getString(R.string.notification_line_prefix_new)
            GradeNotificationKind.PENDING -> context.getString(R.string.notification_line_prefix_pending)
            GradeNotificationKind.UPDATED -> context.getString(R.string.notification_line_prefix_updated)
        }
        return context.getString(R.string.notification_line_format, prefix, change.subject, change.name, change.gradeLabel)
    }

    private fun buildDetails(context: Context, kind: GradeNotificationKind, changes: List<SyncChange>): String {
        return buildString {
            changes.take(5).forEach { change ->
                appendLine(formatLine(context, kind, change))
            }
            if (changes.size > 5) {
                append(context.getString(R.string.notification_more_changes, changes.size - 5))
            }
        }.trim()
    }

    private fun classify(change: SyncChange): GradeNotificationKind {
        return when {
            change.type == NoteChangeType.NEW && !change.gradePublished -> GradeNotificationKind.PENDING
            change.type == NoteChangeType.NEW -> GradeNotificationKind.NEW
            else -> GradeNotificationKind.UPDATED
        }
    }

    private fun canNotify(context: Context): Boolean {
        return !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
    }

    private enum class GradeNotificationKind(
        val channelId: String,
        val notificationId: Int,
        val titleRes: Int,
        val summaryRes: Int,
        val category: String,
    ) {
        NEW(
            channelId = CHANNEL_NEW_GRADES,
            notificationId = NEW_NOTIFICATION_ID,
            titleRes = R.string.notification_channel_new_name,
            summaryRes = R.string.notification_summary_new,
            category = Notification.CATEGORY_STATUS,
        ),
        PENDING(
            channelId = CHANNEL_PENDING_GRADES,
            notificationId = PENDING_NOTIFICATION_ID,
            titleRes = R.string.notification_channel_pending_name,
            summaryRes = R.string.notification_summary_pending,
            category = Notification.CATEGORY_REMINDER,
        ),
        UPDATED(
            channelId = CHANNEL_UPDATED_GRADES,
            notificationId = UPDATED_NOTIFICATION_ID,
            titleRes = R.string.notification_channel_updated_name,
            summaryRes = R.string.notification_summary_updated,
            category = Notification.CATEGORY_STATUS,
        );

        fun isEnabled(settings: AppSettings): Boolean {
            return when (this) {
                NEW -> settings.notifyNewGrades
                PENDING -> settings.notifyPendingGrades
                UPDATED -> settings.notifyUpdatedGrades
            }
        }
    }
}