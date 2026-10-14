package com.studioguu.miniaturegarden.domain.model

/**
 * What the garden may tell a player who is away. Only good news and gentle reminders: nothing
 * about work that is simply done (a demolition, putting something away) and nothing that
 * scolds (withering).
 */
enum class GardenNotificationKind {
    /** A crop has come into full bloom and can be harvested. */
    HARVEST_READY,

    /** A crop's soil has dried out: it would like some water. */
    NEEDS_WATER,

    /** A building is finished and waits under its wraps to be unveiled. */
    BUILDING_READY,
}

/**
 * One notification to post later.
 *
 * @property atMillis when to post it (epoch millis), already moved out of the quiet hours.
 * @property objectId what it is about: the first of its kind to get there.
 * @property count how many things it covers: that one and the others of its kind that get
 *   there within [NotificationPlan.GROUPING_MILLIS] after it.
 */
data class PlannedNotification(
    val kind: GardenNotificationKind,
    val atMillis: Long,
    val objectId: String,
    val count: Int = 1,
)

/** The rules for when the garden speaks up. */
object NotificationPlan {
    /** How far ahead anything is planned; offline progress is not counted further than this either. */
    const val HORIZON_MILLIS = 7L * 24 * 60 * 60 * 1000

    /** Things of one kind that get there this soon after the first are told about together. */
    const val GROUPING_MILLIS = 60L * 60 * 1000

    /** No notification is posted from this hour of the evening ... */
    const val QUIET_FROM_HOUR = 21

    /** ... until this hour of the morning; one that falls in between waits until then. */
    const val QUIET_UNTIL_HOUR = 8
}

/** Where notifications stand for the player, all things considered. */
enum class NotificationAccess {
    /** Turned on in the game and allowed by the device: they will be posted. */
    ON,

    /** Turned off in the game's settings. */
    OFF,

    /** Turned on in the game, but Android 13 or later has not been given the permission yet. */
    NEEDS_PERMISSION,

    /** Turned on in the game, but switched off for this app in the device's own settings. */
    BLOCKED_BY_DEVICE;

    companion object {
        /** Android 13 (API 33) is where posting notifications became a runtime permission. */
        const val PERMISSION_SDK = 33

        /**
         * @param sdkInt the device's API level.
         * @param permissionGranted whether POST_NOTIFICATIONS has been granted; ignored below [PERMISSION_SDK].
         * @param deviceAllows whether the device lets this app post at all (its notification settings).
         * @param enabledInGame the player's own switch in the game's settings.
         */
        fun of(sdkInt: Int, permissionGranted: Boolean, deviceAllows: Boolean, enabledInGame: Boolean): NotificationAccess = when {
            !enabledInGame -> OFF
            sdkInt >= PERMISSION_SDK && !permissionGranted -> NEEDS_PERMISSION
            !deviceAllows -> BLOCKED_BY_DEVICE
            else -> ON
        }

        /**
         * What has to happen when the player turns the switch on, beyond remembering that they did:
         * ask for the permission (Android 13+, not granted yet), send them to the device's settings
         * (the app is switched off there), or nothing more.
         */
        fun whenTurnedOn(sdkInt: Int, permissionGranted: Boolean, deviceAllows: Boolean): TurnOnStep = when {
            sdkInt >= PERMISSION_SDK && !permissionGranted -> TurnOnStep.REQUEST_PERMISSION
            !deviceAllows -> TurnOnStep.OPEN_DEVICE_SETTINGS
            else -> TurnOnStep.NONE
        }

        /** Whether to offer the one-time invitation to turn notifications on. */
        fun shouldInvite(sdkInt: Int, permissionGranted: Boolean, enabledInGame: Boolean, alreadyInvited: Boolean): Boolean =
            sdkInt >= PERMISSION_SDK && !permissionGranted && enabledInGame && !alreadyInvited
    }
}

/** See [NotificationAccess.whenTurnedOn]. */
enum class TurnOnStep { NONE, REQUEST_PERMISSION, OPEN_DEVICE_SETTINGS }
