package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.GardenNotificationKind
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.NotificationPlan
import com.studioguu.miniaturegarden.domain.model.PlannedNotification
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.WeatherForecast
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.repository.NotificationScheduler
import com.studioguu.miniaturegarden.domain.repository.SettingsRepository
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Works out what the garden will have to tell a player who is away, and when.
 *
 * The garden's future is known: plants follow the same rules offline as on screen
 * ([CalculateOfflineProgressUseCase]), and the weather of every day is fixed in advance
 * ([WeatherForecast.Daily]). So each plant is simply run forward to find the moment it comes
 * into bloom, or its soil dries out; buildings say themselves when they will be done.
 *
 * At most one notification per [GardenNotificationKind]: the earliest, covering whatever else of
 * its kind gets there within the hour after it. None lands in the quiet hours of the night
 * ([NotificationPlan.QUIET_FROM_HOUR] to [NotificationPlan.QUIET_UNTIL_HOUR]); one that would
 * waits until the morning.
 */
class PlanGardenNotificationsUseCase @Inject constructor(
    private val calculateOfflineProgress: CalculateOfflineProgressUseCase,
) {
    operator fun invoke(
        objects: List<GridObject>,
        now: Long,
        weather: WeatherForecast = WeatherForecast.Daily,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PlannedNotification> {
        val until = now + NotificationPlan.HORIZON_MILLIS
        val events = buildList {
            for (plant in objects.filter { it.isPlant && it.status != PlantStatus.WITHERED }) {
                fun at(time: Long) = calculateOfflineProgress(plant, time, weather, objects)
                // Where it stands now, not where it stood when it was last saved.
                val current = at(now)
                if (current.status == PlantStatus.WITHERED) continue
                if (current.growthProgress < 1f) {
                    firstMoment(now, until) { at(it).let { p -> p.growthProgress >= 1f && p.status != PlantStatus.WITHERED } }
                        ?.let { add(Event(GardenNotificationKind.HARVEST_READY, it, plant.objectId)) }
                }
                if (!isThirsty(current)) {
                    firstMoment(now, until) { at(it).let { p -> isThirsty(p) && p.status != PlantStatus.WITHERED } }
                        ?.let { add(Event(GardenNotificationKind.NEEDS_WATER, it, plant.objectId)) }
                }
            }
            for (building in objects.filter { it.workKind == WorkKind.BUILDING }) {
                if (building.workEndsAt in (now + 1)..until) {
                    add(Event(GardenNotificationKind.BUILDING_READY, building.workEndsAt, building.objectId))
                }
            }
        }
        return events.groupBy { it.kind }.map { (kind, ofKind) ->
            val first = ofKind.minWith(compareBy({ it.at }, { it.objectId }))
            PlannedNotification(
                kind = kind,
                atMillis = outOfQuietHours(first.at, zone),
                objectId = first.objectId,
                count = ofKind.count { it.at - first.at <= NotificationPlan.GROUPING_MILLIS },
            )
        }.sortedBy { it.atMillis }
    }

    private data class Event(val kind: GardenNotificationKind, val at: Long, val objectId: String)

    companion object {
        /** How finely the future is stepped through before the moment is pinned down. */
        private const val STEP_MILLIS = 30L * 60 * 1000

        /** How closely the moment is pinned down. */
        private const val PRECISION_MILLIS = 60L * 1000

        private fun isThirsty(plant: GridObject): Boolean =
            plant.moistureLevel <= CalculateOfflineProgressUseCase.THIRSTY_THRESHOLD

        /**
         * The first moment in (from, until] at which [happened] holds, to the minute; null if
         * it does not happen by then. Stepped through rather than halved from the start, as a
         * thing can come and go (rain wets dry soil again); within one step it is halved down.
         */
        fun firstMoment(from: Long, until: Long, happened: (Long) -> Boolean): Long? {
            var before = from
            while (before < until) {
                val after = minOf(before + STEP_MILLIS, until)
                if (happened(after)) {
                    var low = before
                    var high = after
                    while (high - low > PRECISION_MILLIS) {
                        val middle = low + (high - low) / 2
                        if (happened(middle)) high = middle else low = middle
                    }
                    return high
                }
                before = after
            }
            return null
        }

        /** [at], or the end of the quiet hours it falls in, in the player's own time zone. */
        fun outOfQuietHours(at: Long, zone: ZoneId): Long {
            val local = ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), zone)
            val hour = local.hour
            val morning = local.toLocalDate().atTime(NotificationPlan.QUIET_UNTIL_HOUR, 0).atZone(zone)
            return when {
                hour >= NotificationPlan.QUIET_FROM_HOUR -> morning.plusDays(1).toInstant().toEpochMilli()
                hour < NotificationPlan.QUIET_UNTIL_HOUR -> morning.toInstant().toEpochMilli()
                else -> at
            }
        }
    }
}

/**
 * Plans the garden's notifications afresh, or takes them all back when the player has turned
 * them off. Run as the player leaves the garden; [cancel] is for when they come back.
 */
class ScheduleGardenNotificationsUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: NotificationScheduler,
    private val plan: PlanGardenNotificationsUseCase,
) {
    suspend operator fun invoke(now: Long, weather: WeatherForecast = WeatherForecast.Daily) {
        if (!settingsRepository.settings.first().notificationsEnabled) {
            scheduler.cancelAll()
            return
        }
        scheduler.replaceAll(plan(gardenRepository.getGridObjects(), now, weather))
    }

    /** The player is in the garden and sees it all for themselves. */
    fun cancel() = scheduler.cancelAll()
}
