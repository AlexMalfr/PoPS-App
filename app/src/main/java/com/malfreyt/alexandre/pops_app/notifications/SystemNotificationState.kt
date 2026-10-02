package com.malfreyt.alexandre.pops_app.notifications

import com.malfreyt.alexandre.pops_app.data.AccountNotificationType

data class SystemNotificationState(
    val appEnabled: Boolean,
    val accountEnabled: Boolean,
    val blockedTypes: Set<AccountNotificationType>,
) {
    val masterEnabled: Boolean get() = appEnabled && accountEnabled
    fun isEnabled(type: AccountNotificationType): Boolean = masterEnabled && type !in blockedTypes
}
