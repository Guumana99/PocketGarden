package com.studioguu.miniaturegarden.domain.repository

import com.studioguu.miniaturegarden.domain.model.PlannedNotification

/** Posts notifications at set times, whether or not the app is running then. */
interface NotificationScheduler {

    /** Replaces everything planned so far with [plan]. An empty plan leaves nothing planned. */
    fun replaceAll(plan: List<PlannedNotification>)

    /** Takes back everything planned, and anything of the garden's still showing. */
    fun cancelAll()
}
