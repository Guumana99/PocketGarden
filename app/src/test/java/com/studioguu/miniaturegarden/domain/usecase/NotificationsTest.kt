package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.model.AppSettings
import com.studioguu.miniaturegarden.domain.model.GardenNotificationKind
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.NotificationAccess
import com.studioguu.miniaturegarden.domain.model.NotificationPlan
import com.studioguu.miniaturegarden.domain.model.PlannedNotification
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.StructureData
import com.studioguu.miniaturegarden.domain.model.TurnOnStep
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.domain.model.WorkKind
import com.studioguu.miniaturegarden.domain.repository.NotificationScheduler
import com.studioguu.miniaturegarden.domain.repository.SettingsRepository
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsTest {

    private val utc: ZoneId = ZoneOffset.UTC

    /** 09:00 UTC on a weekday morning. */
    private val now = ZonedDateTime.of(2024, 1, 10, 9, 0, 0, 0, utc).toInstant().toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute

    private val plan = PlanGardenNotificationsUseCase(CalculateOfflineProgressUseCase())

    private fun plant(id: String, objectId: String = "plant_tulip", growth: Float = 0f, moisture: Float = 1f, x: Int = 0) = GridObject(
        instanceId = id, gridX = x, gridY = 0, objectId = objectId,
        growthProgress = growth, moistureLevel = moisture, lastUpdatedTimestamp = now,
    )

    private fun List<PlannedNotification>.of(kind: GardenNotificationKind) = singleOrNull { it.kind == kind }

    private fun assertNear(expected: Long, actual: Long?) {
        assertTrue("expected about $expected, was $actual", actual != null && abs(actual - expected) <= 2 * minute)
    }

    // --- What is told, and when ---

    @Test
    fun `a crop is announced when it comes into bloom`() {
        // 10 % to go of a tulip's 48 hours: 4.8 hours, well before its soil dries out.
        val planned = plan(listOf(plant("a", growth = 0.9f)), now, WeatherType.SUNNY)
        val harvest = planned.of(GardenNotificationKind.HARVEST_READY)
        assertNear(now + 288 * minute, harvest?.atMillis)
        assertEquals("plant_tulip", harvest?.objectId)
        assertEquals(1, harvest?.count)
    }

    @Test
    fun `a crop is announced when its soil has dried out`() {
        // From half full to thirsty (20 %) is 30 % of a tulip's 24 hours: 7.2 hours.
        val planned = plan(listOf(plant("a", moisture = 0.5f)), now, WeatherType.SUNNY)
        assertNear(now + 432 * minute, planned.of(GardenNotificationKind.NEEDS_WATER)?.atMillis)
    }

    @Test
    fun `a crop that dries out before it blooms is only announced as thirsty`() {
        // Unwatered, a seed stops growing once it is thirsty: it would never bloom by itself.
        val planned = plan(listOf(plant("a")), now, WeatherType.SUNNY)
        assertNull(planned.of(GardenNotificationKind.HARVEST_READY))
        assertTrue(planned.of(GardenNotificationKind.NEEDS_WATER) != null)
    }

    @Test
    fun `rain keeps the soil wet, so nothing asks for water`() {
        val planned = plan(listOf(plant("a", growth = 0.9f)), now, WeatherType.RAINY)
        assertNull(planned.of(GardenNotificationKind.NEEDS_WATER))
        // And it grows a fifth faster: 4 hours instead of 4.8.
        assertNear(now + 240 * minute, planned.of(GardenNotificationKind.HARVEST_READY)?.atMillis)
    }

    @Test
    fun `nothing is said about what is already so, or withered`() {
        val planned = plan(
            listOf(
                plant("bloomed", growth = 1f),
                plant("dry", moisture = 0.1f, growth = 0.5f),
                plant("gone", growth = 0.5f).copy(status = PlantStatus.WITHERED),
            ),
            now, WeatherType.SUNNY,
        )
        assertNull(planned.of(GardenNotificationKind.HARVEST_READY))
        // The bloomed one dries out later and is the only one that may ask for water.
        assertTrue(planned.none { it.kind == GardenNotificationKind.NEEDS_WATER && it.count > 1 })
    }

    @Test
    fun `a building is announced when it is ready to be unveiled, and nothing else is`() {
        fun site(id: String, work: WorkKind, endsIn: Long) = GridObject(
            instanceId = id, gridX = 3, gridY = 3, objectId = StructureData.HUT.objectId, lastUpdatedTimestamp = now,
            workKind = work, workStartedAt = now - minute, workEndsAt = now + endsIn,
        )
        val planned = plan(
            listOf(
                site("hut", WorkKind.BUILDING, 5 * minute),
                site("old", WorkKind.DEMOLISHING, 2 * minute),
                site("bag", WorkKind.PACKING, 2 * minute),
                site("wrapped", WorkKind.READY, 0),
            ),
            now, WeatherType.SUNNY,
        )
        assertEquals(listOf(PlannedNotification(GardenNotificationKind.BUILDING_READY, now + 5 * minute, StructureData.HUT.objectId)), planned)
    }

    @Test
    fun `nothing further ahead than a week is planned`() {
        val far = GridObject(
            instanceId = "hut", gridX = 3, gridY = 3, objectId = StructureData.HUT.objectId, lastUpdatedTimestamp = now,
            workKind = WorkKind.BUILDING, workStartedAt = now, workEndsAt = now + NotificationPlan.HORIZON_MILLIS + hour,
        )
        assertTrue(plan(listOf(far), now, WeatherType.SUNNY).isEmpty())
    }

    @Test
    fun `things of a kind that come within the hour are told about together, once`() {
        val planned = plan(
            listOf(
                plant("first", growth = 0.9f, x = 0), // in 288 minutes
                // A rose with 318 minutes to go: half an hour after the first.
                plant("soon", objectId = "plant_rose", growth = 1f - 318f / (120 * 60), x = 1),
                plant("later", growth = 0.8f, x = 2), // in 576 minutes: not with the others
            ),
            now, WeatherType.SUNNY,
        )
        val harvest = planned.filter { it.kind == GardenNotificationKind.HARVEST_READY }
        assertEquals(1, harvest.size)
        assertEquals(2, harvest.single().count)
        assertEquals("plant_tulip", harvest.single().objectId)
        assertNear(now + 288 * minute, harvest.single().atMillis)
    }

    @Test
    fun `the plan is in order of time, one per kind at most`() {
        val planned = plan(listOf(plant("a", growth = 0.9f), plant("b", moisture = 0.5f, x = 1)), now, WeatherType.SUNNY)
        assertEquals(planned.sortedBy { it.atMillis }, planned)
        assertEquals(planned.size, planned.map { it.kind }.toSet().size)
    }

    // --- Any hour ---

    @Test
    fun `a crop that blooms in the night is told about right then, not held until morning`() {
        // 13 hours from 09:00 is 22:00; its soil stays moist for 19 hours.
        val growth = 1f - 13f / 48f
        val planned = plan(listOf(plant("a", growth = growth)), now, WeatherType.SUNNY)
        assertNear(now + 13 * hour, planned.of(GardenNotificationKind.HARVEST_READY)?.atMillis)
    }

    @Test
    fun `the first moment is found to the minute, also when it comes and goes`() {
        val target = now + 7 * hour + 13 * minute
        assertNear(target, PlanGardenNotificationsUseCase.firstMoment(now, now + 24 * hour) { it >= target })
        assertNull(PlanGardenNotificationsUseCase.firstMoment(now, now + hour) { it >= target })
        // True only for a while, as dry soil is wet again by rain: the first time counts.
        val window = target until target + 2 * hour
        assertNear(target, PlanGardenNotificationsUseCase.firstMoment(now, now + 24 * hour) { it in window })
    }

    // --- Following the player's switch ---

    private class FakeSettings(enabled: Boolean) : SettingsRepository {
        val state = MutableStateFlow(AppSettings(notificationsEnabled = enabled))
        override val settings: Flow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    private class FakeScheduler : NotificationScheduler {
        var planned: List<PlannedNotification>? = null
        var cancelled = 0
        override fun replaceAll(plan: List<PlannedNotification>) {
            planned = plan
        }
        override fun cancelAll() {
            cancelled++
            planned = null
        }
    }

    @Test
    fun `with notifications on, leaving the garden plans them afresh`() = runBlocking {
        val repo = FakeGardenRepository().apply { put(plant("a", growth = 0.9f)) }
        val scheduler = FakeScheduler()
        val schedule = ScheduleGardenNotificationsUseCase(repo, FakeSettings(enabled = true), scheduler, plan)

        schedule(now, WeatherType.SUNNY)

        assertTrue(scheduler.planned?.any { it.kind == GardenNotificationKind.HARVEST_READY } == true)
        assertEquals(0, scheduler.cancelled)
        // Coming back takes it all back.
        schedule.cancel()
        assertNull(scheduler.planned)
    }

    @Test
    fun `with notifications off, nothing is planned and anything planned is taken back`() = runBlocking {
        val repo = FakeGardenRepository().apply { put(plant("a", growth = 0.9f)) }
        val scheduler = FakeScheduler().apply { planned = listOf(PlannedNotification(GardenNotificationKind.HARVEST_READY, now, "plant_tulip")) }
        val settings = FakeSettings(enabled = false)

        ScheduleGardenNotificationsUseCase(repo, settings, scheduler, plan)(now, WeatherType.SUNNY)

        assertNull(scheduler.planned)
        assertEquals(1, scheduler.cancelled)
    }

    // --- The device's permission ---

    @Test
    fun `notifications count as on only when the game and the device both allow them`() {
        assertEquals(NotificationAccess.ON, NotificationAccess.of(34, permissionGranted = true, deviceAllows = true, enabledInGame = true))
        assertEquals(NotificationAccess.OFF, NotificationAccess.of(34, permissionGranted = true, deviceAllows = true, enabledInGame = false))
        assertEquals(NotificationAccess.NEEDS_PERMISSION, NotificationAccess.of(33, permissionGranted = false, deviceAllows = true, enabledInGame = true))
        assertEquals(NotificationAccess.BLOCKED_BY_DEVICE, NotificationAccess.of(34, permissionGranted = true, deviceAllows = false, enabledInGame = true))
        // Before Android 13 there is no permission to ask for.
        assertEquals(NotificationAccess.ON, NotificationAccess.of(32, permissionGranted = false, deviceAllows = true, enabledInGame = true))
        assertEquals(NotificationAccess.BLOCKED_BY_DEVICE, NotificationAccess.of(26, permissionGranted = false, deviceAllows = false, enabledInGame = true))
    }

    @Test
    fun `turning the switch on without the permission explains it first, and the OK asks the system`() {
        assertEquals(TurnOnStep.EXPLAIN_PERMISSION, NotificationAccess.whenTurnedOn(33, permissionGranted = false, deviceAllows = false))
        assertEquals(TurnOnStep.EXPLAIN_PERMISSION, NotificationAccess.whenTurnedOn(34, permissionGranted = false, deviceAllows = true))
    }

    @Test
    fun `turning the switch on when only the device's settings say no goes to the device's settings`() {
        assertEquals(TurnOnStep.OPEN_DEVICE_SETTINGS, NotificationAccess.whenTurnedOn(33, permissionGranted = true, deviceAllows = false))
        assertEquals(TurnOnStep.OPEN_DEVICE_SETTINGS, NotificationAccess.whenTurnedOn(30, permissionGranted = false, deviceAllows = false))
        assertEquals(TurnOnStep.NONE, NotificationAccess.whenTurnedOn(34, permissionGranted = true, deviceAllows = true))
        assertEquals(TurnOnStep.NONE, NotificationAccess.whenTurnedOn(30, permissionGranted = false, deviceAllows = true))
    }

    @Test
    fun `the switch goes on only once the system says yes`() {
        assertEquals(
            TurnOnStep.NONE,
            NotificationAccess.afterPermissionAnswer(granted = true, deviceAllows = true, rationaleBefore = false, rationaleAfter = false),
        )
        // Allowed, but the app's notifications are still switched off in the device's settings.
        assertEquals(
            TurnOnStep.OPEN_DEVICE_SETTINGS,
            NotificationAccess.afterPermissionAnswer(granted = true, deviceAllows = false, rationaleBefore = false, rationaleAfter = false),
        )
    }

    @Test
    fun `a refusal leaves the switch off and asks nothing more`() {
        // The first refusal: the system now offers a rationale.
        assertEquals(
            TurnOnStep.STAY_OFF,
            NotificationAccess.afterPermissionAnswer(granted = false, deviceAllows = false, rationaleBefore = false, rationaleAfter = true),
        )
        // Refused again, after a rationale was on offer.
        assertEquals(
            TurnOnStep.STAY_OFF,
            NotificationAccess.afterPermissionAnswer(granted = false, deviceAllows = false, rationaleBefore = true, rationaleAfter = false),
        )
    }

    @Test
    fun `when the system refuses without asking, the device's settings are the way left`() {
        assertEquals(
            TurnOnStep.OPEN_DEVICE_SETTINGS,
            NotificationAccess.afterPermissionAnswer(granted = false, deviceAllows = false, rationaleBefore = false, rationaleAfter = false),
        )
    }

    @Test
    fun `the invitation comes once, and only where the device needs asking`() {
        assertTrue(NotificationAccess.shouldInvite(33, permissionGranted = false, enabledInGame = true, alreadyInvited = false))
        assertFalse(NotificationAccess.shouldInvite(33, permissionGranted = false, enabledInGame = true, alreadyInvited = true))
        assertFalse(NotificationAccess.shouldInvite(33, permissionGranted = true, enabledInGame = true, alreadyInvited = false))
        assertFalse(NotificationAccess.shouldInvite(32, permissionGranted = false, enabledInGame = true, alreadyInvited = false))
        // A player who switched them off is not asked to switch them on.
        assertFalse(NotificationAccess.shouldInvite(34, permissionGranted = false, enabledInGame = false, alreadyInvited = false))
    }
}
