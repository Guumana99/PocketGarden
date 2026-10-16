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
 * @property atMillis when to post it (epoch millis): the moment it happens, at any hour.
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
         * What happens when the player turns the switch on. When the device lets the app post,
         * the switch simply goes on ([TurnOnStep.NONE]). Otherwise it stays off for now: on
         * Android 13+ without the permission, a board first says why it is needed and its OK
         * brings up the system's question ([TurnOnStep.EXPLAIN_PERMISSION]); when only the
         * device's own settings stand in the way, the player is taken there
         * ([TurnOnStep.OPEN_DEVICE_SETTINGS]). The game's switch can never be on while the
         * device says no.
         */
        fun whenTurnedOn(sdkInt: Int, permissionGranted: Boolean, deviceAllows: Boolean): TurnOnStep = when {
            sdkInt >= PERMISSION_SDK && !permissionGranted -> TurnOnStep.EXPLAIN_PERMISSION
            !deviceAllows -> TurnOnStep.OPEN_DEVICE_SETTINGS
            else -> TurnOnStep.NONE
        }

        /**
         * What follows the system's answer to the permission question asked from the switch.
         *
         * Granted: the switch goes on, unless the device's settings still block the app, in
         * which case the player is taken there. Refused: the switch stays off, except when the
         * system refused without asking at all (the player had said no for good before): then
         * the player, who has just said yes on our board, is taken to the device's settings,
         * the only place left to allow it. That case is told by the system offering no
         * rationale either before or after the question; after a first refusal it offers one.
         *
         * @param rationaleBefore `shouldShowRequestPermissionRationale` just before asking.
         * @param rationaleAfter the same, just after the answer came back.
         */
        fun afterPermissionAnswer(
            granted: Boolean,
            deviceAllows: Boolean,
            rationaleBefore: Boolean,
            rationaleAfter: Boolean,
        ): TurnOnStep = when {
            granted && deviceAllows -> TurnOnStep.NONE
            granted -> TurnOnStep.OPEN_DEVICE_SETTINGS
            !rationaleBefore && !rationaleAfter -> TurnOnStep.OPEN_DEVICE_SETTINGS
            else -> TurnOnStep.STAY_OFF
        }

        /** Whether the device, for its part, lets this app post notifications. */
        fun deviceReady(sdkInt: Int, permissionGranted: Boolean, deviceAllows: Boolean): Boolean =
            (sdkInt < PERMISSION_SDK || permissionGranted) && deviceAllows

        /** Whether to offer the one-time invitation to turn notifications on. */
        fun shouldInvite(sdkInt: Int, permissionGranted: Boolean, enabledInGame: Boolean, alreadyInvited: Boolean): Boolean =
            sdkInt >= PERMISSION_SDK && !permissionGranted && enabledInGame && !alreadyInvited
    }
}

/** See [NotificationAccess.whenTurnedOn] and [NotificationAccess.afterPermissionAnswer]. */
enum class TurnOnStep {
    /** Nothing stands in the way: the switch goes on. */
    NONE,

    /** Show the board that says why the permission is needed; its OK asks the system. */
    EXPLAIN_PERMISSION,

    /** Take the player to this app's notification settings on the device. */
    OPEN_DEVICE_SETTINGS,

    /** The player said no: the switch stays off and nothing more is asked. */
    STAY_OFF,
}
