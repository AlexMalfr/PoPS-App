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
import com.malfreyt.alexandre.pops_app.data.AccountNotificationType
import com.malfreyt.alexandre.pops_app.data.AppSettings
import com.malfreyt.alexandre.pops_app.data.NoteChangeType
import com.malfreyt.alexandre.pops_app.data.OasisAccount
import com.malfreyt.alexandre.pops_app.data.SettingsStore
import com.malfreyt.alexandre.pops_app.data.SyncChange

object NotificationHelper {
    private const val CHANNEL_GROUP_PREFIX = "grades_group_"
    private const val CHANNEL_NEW_PREFIX = "grades_new_"
    private const val CHANNEL_PENDING_PREFIX = "grades_pending_"
    private const val CHANNEL_UPDATED_PREFIX = "grades_updated_"
    private const val CHANNEL_SYNC_ERRORS_PREFIX = "grades_errors_"
    private const val NEW_NOTIFICATION_ID_BASE = 1000
    private const val PENDING_NOTIFICATION_ID_BASE = 2000
    private const val UPDATED_NOTIFICATION_ID_BASE = 3000
    private const val PREVIEW_NOTIFICATION_ID_BASE = 40_000
    private const val FAILURE_NOTIFICATION_ID_BASE = 10_000

    fun createChannel(context: Context, settings: AppSettings = SettingsStore(context).readSettings()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        val syncableAccounts = settings.accounts.filter(OasisAccount::hasCredentials)
        val multipleAccounts = syncableAccounts.size > 1

        syncableAccounts.forEach { account ->
            manager.createNotificationChannelGroup(
                NotificationChannelGroup(groupIdFor(account.id), groupName(context, account, multipleAccounts))
            )
            manager.createNotificationChannels(
                listOf(
                    buildChannel(
                        id = channelIdFor(GradeNotificationKind.NEW, account.id),
                        groupId = groupIdFor(account.id),
                        context = context,
                        account = account,
                        multipleAccounts = multipleAccounts,
                        nameRes = R.string.notification_channel_new_name,
                        descriptionRes = R.string.notification_channel_new_description,
                    ),
                    buildChannel(
                        id = channelIdFor(GradeNotificationKind.PENDING, account.id),
                        groupId = groupIdFor(account.id),
                        context = context,
                        account = account,
                        multipleAccounts = multipleAccounts,
                        nameRes = R.string.notification_channel_pending_name,
                        descriptionRes = R.string.notification_channel_pending_description,
                    ),
                    buildChannel(
                        id = channelIdFor(GradeNotificationKind.UPDATED, account.id),
                        groupId = groupIdFor(account.id),
                        context = context,
                        account = account,
                        multipleAccounts = multipleAccounts,
                        nameRes = R.string.notification_channel_updated_name,
                        descriptionRes = R.string.notification_channel_updated_description,
                    ),
                    buildChannel(
                        id = channelIdFor(GradeNotificationKind.ERROR, account.id),
                        groupId = groupIdFor(account.id),
                        context = context,
                        account = account,
                        multipleAccounts = multipleAccounts,
                        nameRes = R.string.notification_channel_error_name,
                        descriptionRes = R.string.notification_channel_error_description,
                    ),
                )
            )
        }
    }

    fun notifyChanges(context: Context, settings: AppSettings, changes: List<SyncChange>) {
        if (changes.isEmpty() || !canNotify(context)) {
            return
        }

        createChannel(context, settings)

        val multipleAccounts = settings.accounts.count(OasisAccount::hasCredentials) > 1

        changes.groupBy { it.accountId }.forEach { (accountId, accountChanges) ->
            val account = settings.accounts.firstOrNull { it.id == accountId } ?: return@forEach
            GradeNotificationKind.entries.filter { it != GradeNotificationKind.ERROR }.forEach { kind ->
                val matchingChanges = accountChanges.filter { classify(it) == kind }
                if (matchingChanges.isEmpty() || !kind.isEnabled(settings, account)) {
                    return@forEach
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationIdFor(kind, account.id),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

                val notification = NotificationCompat.Builder(context, channelIdFor(kind, account.id))
                    .setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle(channelDisplayName(context, kind.titleRes, account, multipleAccounts))
                    .setContentText(buildSummary(context, kind, matchingChanges.size))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(buildDetails(context, kind, matchingChanges, false)))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setCategory(kind.category)
                    .build()

                NotificationManagerCompat.from(context).notify(notificationIdFor(kind, account.id), notification)
            }
        }
    }

    fun notifySyncFailure(
        context: Context,
        settings: AppSettings,
        account: OasisAccount,
        attempts: Int,
        errorMessage: String,
        technicalDetails: String,
        onlyAlertOnce: Boolean,
    ) {
        if (!canNotify(context) || settings.pollingMinutes == 0 || !settings.notificationsEnabled ||
            !account.isNotificationEnabled(AccountNotificationType.ERROR)) {
            return
        }

        createChannel(context, settings)
        val multipleAccounts = settings.accounts.count(OasisAccount::hasCredentials) > 1

        val deleteIntent = PendingIntent.getBroadcast(
            context,
            failureNotificationId(account.id),
            Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_SYNC_FAILURE_DISMISSED
                putExtra(NotificationActionReceiver.EXTRA_ACCOUNT_ID, account.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            failureNotificationId(account.id),
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

        val notification = NotificationCompat.Builder(context, channelIdFor(GradeNotificationKind.ERROR, account.id))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(channelDisplayName(context, R.string.notification_channel_error_name, account, multipleAccounts))
            .setContentText(context.getString(R.string.notification_sync_failure_content, attempts))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setDeleteIntent(deleteIntent)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setOnlyAlertOnce(onlyAlertOnce)
            .build()

        NotificationManagerCompat.from(context).notify(failureNotificationId(account.id), notification)
    }

    fun notifyPreview(
        context: Context,
        settings: AppSettings,
        account: OasisAccount,
        type: NotificationPreviewType,
        sampleChanges: List<SyncChange>?,
    ) {
        if (!canNotify(context)) {
            return
        }

        createChannel(context, settings)
        val multipleAccounts = settings.accounts.count(OasisAccount::hasCredentials) > 1

        when (type) {
            NotificationPreviewType.ERROR -> {
                val title = channelDisplayName(context, R.string.notification_channel_error_name, account, multipleAccounts) +
                    " " + context.getString(R.string.notification_preview_example_suffix)
                val notification = NotificationCompat.Builder(context, channelIdFor(GradeNotificationKind.ERROR, account.id))
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle(title)
                    .setContentText(context.getString(R.string.notification_sync_failure_content, 3))
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            context.getString(R.string.notification_preview_error_details, account.resolvedDisplayName())
                        )
                    )
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(previewNotificationId(account.id, type), notification)
            }
            else -> {
                val kind = when (type) {
                    NotificationPreviewType.NEW -> GradeNotificationKind.NEW
                    NotificationPreviewType.PENDING -> GradeNotificationKind.PENDING
                    NotificationPreviewType.UPDATED -> GradeNotificationKind.UPDATED
                    NotificationPreviewType.ERROR -> GradeNotificationKind.ERROR
                }
                val previewChanges = sampleChanges?.takeIf { it.isNotEmpty() } ?: listOf(SyncChange(
                    accountId = account.id,
                    accountLabel = account.resolvedDisplayName(),
                    type = when (type) {
                        NotificationPreviewType.NEW -> NoteChangeType.NEW
                        NotificationPreviewType.PENDING -> NoteChangeType.NEW
                        NotificationPreviewType.UPDATED -> NoteChangeType.UPDATED
                        NotificationPreviewType.ERROR -> NoteChangeType.UPDATED
                    },
                    subject = context.getString(R.string.notification_preview_subject),
                    name = context.getString(R.string.notification_preview_fallback_name, 1),
                    gradeLabel = context.getString(R.string.notification_preview_grade_value),
                    gradePublished = type != NotificationPreviewType.PENDING,
                    changedFields = if (type == NotificationPreviewType.UPDATED) context.getString(R.string.notification_preview_changed_fields) else null,
                ))

                val title = channelDisplayName(context, kind.titleRes, account, multipleAccounts) +
                    " " + context.getString(R.string.notification_preview_example_suffix)
                val notification = NotificationCompat.Builder(context, channelIdFor(kind, account.id))
                    .setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle(title)
                    .setContentText(buildSummary(context, kind, previewChanges.size))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(buildDetails(context, kind, previewChanges, false)))
                    .setAutoCancel(true)
                    .setCategory(kind.category)
                    .build()
                NotificationManagerCompat.from(context).notify(previewNotificationId(account.id, type), notification)
            }
        }
    }

    fun clearSyncFailureNotification(context: Context, accountId: String) {
        NotificationManagerCompat.from(context).cancel(failureNotificationId(accountId))
    }

    private fun buildChannel(
        id: String,
        groupId: String,
        context: Context,
        account: OasisAccount,
        multipleAccounts: Boolean,
        nameRes: Int,
        descriptionRes: Int,
    ): NotificationChannel {
        return NotificationChannel(
            id,
            channelDisplayName(context, nameRes, account, multipleAccounts),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            group = groupId
            description = context.getString(descriptionRes)
        }
    }

    private fun buildSummary(context: Context, kind: GradeNotificationKind, count: Int): String {
        return context.getString(kind.summaryRes, count)
    }

    private fun formatLine(context: Context, kind: GradeNotificationKind, change: SyncChange, showAccountLabel: Boolean): String {
        val prefix = when (kind) {
            GradeNotificationKind.NEW -> context.getString(R.string.notification_line_prefix_new)
            GradeNotificationKind.PENDING -> context.getString(R.string.notification_line_prefix_pending)
            GradeNotificationKind.UPDATED -> context.getString(R.string.notification_line_prefix_updated)
            GradeNotificationKind.ERROR -> context.getString(R.string.notification_line_prefix_updated)
        }
        val subjectLabel = if (showAccountLabel) {
            "${change.accountLabel} · ${change.subject}"
        } else {
            change.subject
        }
        return when (kind) {
            GradeNotificationKind.PENDING -> context.getString(R.string.notification_line_format_no_grade, prefix, subjectLabel, change.name)
            GradeNotificationKind.UPDATED -> {
                val base = context.getString(R.string.notification_line_format, prefix, subjectLabel, change.name, change.gradeLabel)
                if (!change.changedFields.isNullOrBlank()) "$base\n  ↳ ${change.changedFields}" else base
            }
            else -> context.getString(R.string.notification_line_format, prefix, subjectLabel, change.name, change.gradeLabel)
        }
    }

    private fun buildDetails(context: Context, kind: GradeNotificationKind, changes: List<SyncChange>, showAccountLabel: Boolean): String {
        return buildString {
            changes.take(5).forEach { change ->
                appendLine(formatLine(context, kind, change, showAccountLabel))
            }
            if (changes.size > 5) {
                append(context.getString(R.string.notification_more_changes, changes.size - 5))
            }
        }.trim()
    }

    private fun failureNotificationId(accountId: String): Int {
        return FAILURE_NOTIFICATION_ID_BASE + ((accountId.hashCode() and 0x7fffffff) % 100_000)
    }

    private fun previewNotificationId(accountId: String, type: NotificationPreviewType): Int {
        val offset = when (type) {
            NotificationPreviewType.NEW -> 1
            NotificationPreviewType.PENDING -> 2
            NotificationPreviewType.UPDATED -> 3
            NotificationPreviewType.ERROR -> 4
        }
        return PREVIEW_NOTIFICATION_ID_BASE + ((accountId.hashCode() and 0x7fffffff) % 10_000) + offset
    }

    private fun notificationIdFor(kind: GradeNotificationKind, accountId: String): Int {
        val base = when (kind) {
            GradeNotificationKind.NEW -> NEW_NOTIFICATION_ID_BASE
            GradeNotificationKind.PENDING -> PENDING_NOTIFICATION_ID_BASE
            GradeNotificationKind.UPDATED -> UPDATED_NOTIFICATION_ID_BASE
            GradeNotificationKind.ERROR -> FAILURE_NOTIFICATION_ID_BASE
        }
        return base + ((accountId.hashCode() and 0x7fffffff) % 10_000)
    }

    private fun groupIdFor(accountId: String): String {
        return CHANNEL_GROUP_PREFIX + accountId
    }

    private fun channelIdFor(kind: GradeNotificationKind, accountId: String): String {
        val prefix = when (kind) {
            GradeNotificationKind.NEW -> CHANNEL_NEW_PREFIX
            GradeNotificationKind.PENDING -> CHANNEL_PENDING_PREFIX
            GradeNotificationKind.UPDATED -> CHANNEL_UPDATED_PREFIX
            GradeNotificationKind.ERROR -> CHANNEL_SYNC_ERRORS_PREFIX
        }
        return prefix + accountId
    }

    private fun groupName(context: Context, account: OasisAccount, multipleAccounts: Boolean): String {
        return "${account.resolvedDisplayName()} – ${context.getString(R.string.notification_channel_group_name)}"
    }

    private fun channelDisplayName(context: Context, titleRes: Int, account: OasisAccount, multipleAccounts: Boolean): String {
        val base = context.getString(titleRes)
        return if (multipleAccounts) "${account.resolvedDisplayName()} – $base" else base
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
        val titleRes: Int,
        val summaryRes: Int,
        val category: String,
        val accountType: AccountNotificationType,
    ) {
        NEW(
            titleRes = R.string.notification_channel_new_name,
            summaryRes = R.string.notification_summary_new,
            category = Notification.CATEGORY_STATUS,
            accountType = AccountNotificationType.NEW,
        ),
        PENDING(
            titleRes = R.string.notification_channel_pending_name,
            summaryRes = R.string.notification_summary_pending,
            category = Notification.CATEGORY_REMINDER,
            accountType = AccountNotificationType.PENDING,
        ),
        UPDATED(
            titleRes = R.string.notification_channel_updated_name,
            summaryRes = R.string.notification_summary_updated,
            category = Notification.CATEGORY_STATUS,
            accountType = AccountNotificationType.UPDATED,
        ),
        ERROR(
            titleRes = R.string.notification_channel_error_name,
            summaryRes = R.string.notification_summary_updated,
            category = Notification.CATEGORY_ERROR,
            accountType = AccountNotificationType.ERROR,
        );

        fun isEnabled(settings: AppSettings, account: OasisAccount): Boolean {
            return account.isNotificationEnabled(accountType)
        }
    }

    enum class NotificationPreviewType {
        NEW,
        PENDING,
        UPDATED,
        ERROR,
    }
}
