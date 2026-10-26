package com.studioguu.miniaturegarden.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.studioguu.miniaturegarden.MainActivity
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GardenNotificationKind
import com.studioguu.miniaturegarden.domain.model.NotificationAccess
import com.studioguu.miniaturegarden.domain.model.PlannedNotification
import com.studioguu.miniaturegarden.domain.repository.NotificationScheduler
import com.studioguu.miniaturegarden.domain.repository.SettingsRepository
import com.studioguu.miniaturegarden.ui.objectNameRes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import com.studioguu.miniaturegarden.domain.repository.GameClock
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The garden's notifications on Android: one channel, quiet (no sound, no vibration, no pop-up),
 * and one WorkManager job per [GardenNotificationKind], so a new plan simply replaces the old one.
 * WorkManager keeps the jobs across restarts of the app and of the device.
 */
@Singleton
class WorkManagerNotificationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: GameClock,
) : NotificationScheduler {

    override fun replaceAll(plan: List<PlannedNotification>) {
        val workManager = WorkManager.getInstance(context)
        // The plan is on the game's clock: so is the wait.
        val now = clock.now()
        for (kind in GardenNotificationKind.entries) {
            val planned = plan.firstOrNull { it.kind == kind }
            if (planned == null) {
                workManager.cancelUniqueWork(workName(kind))
                continue
            }
            val request = OneTimeWorkRequest.Builder(GardenNotificationWorker::class.java)
                .setInitialDelay((planned.atMillis - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_KIND, kind.name)
                        .putString(KEY_OBJECT_ID, planned.objectId)
                        .putInt(KEY_COUNT, planned.count)
                        .build()
                )
                .addTag(TAG)
                .build()
            workManager.enqueueUniqueWork(workName(kind), ExistingWorkPolicy.REPLACE, request)
        }
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        // What is already showing is out of date too: the player is looking at the garden.
        NotificationManagerCompat.from(context).let { manager ->
            GardenNotificationKind.entries.forEach { manager.cancel(notificationId(it)) }
        }
    }

    companion object {
        const val TAG = "garden_notification"
        const val CHANNEL_ID = "garden"
        const val KEY_KIND = "kind"
        const val KEY_OBJECT_ID = "object_id"
        const val KEY_COUNT = "count"

        fun workName(kind: GardenNotificationKind) = "${TAG}_${kind.name.lowercase()}"

        fun notificationId(kind: GardenNotificationKind) = 1000 + kind.ordinal

        /** Whether this app may post right now: the permission (Android 13+) and the device's own switch. */
        fun devicePermits(context: Context): Boolean {
            val granted = Build.VERSION.SDK_INT < NotificationAccess.PERMISSION_SDK ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        /** Creates the garden's channel if it is not there yet. Safe to call any number of times. */
        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                // Quiet: it shows in the status bar and the shade, and that is all.
                setSound(null, null)
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** What the notification says, in the garden's own gentle voice. */
        fun message(context: Context, kind: GardenNotificationKind, objectId: String, count: Int): String {
            val name = objectNameRes(objectId)?.let(context::getString) ?: objectId
            val many = count > 1
            return when (kind) {
                GardenNotificationKind.HARVEST_READY ->
                    if (many) context.getString(R.string.notification_harvest_many, name, count) else context.getString(R.string.notification_harvest, name)
                GardenNotificationKind.NEEDS_WATER ->
                    if (many) context.getString(R.string.notification_water_many, name, count) else context.getString(R.string.notification_water, name)
                GardenNotificationKind.BUILDING_READY ->
                    if (many) context.getString(R.string.notification_building_many, name, count) else context.getString(R.string.notification_building, name)
            }
        }
    }
}

/** Lets the worker, which Hilt does not build, read the player's settings. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationEntryPoint {
    fun settingsRepository(): SettingsRepository
}

/**
 * Posts one planned notification when its time comes. Everything is checked again at that
 * moment: the player's switch and the device's permission. Without them nothing is posted and
 * nothing goes wrong.
 */
class GardenNotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val kind = inputData.getString(WorkManagerNotificationScheduler.KEY_KIND)
            ?.let { name -> GardenNotificationKind.entries.firstOrNull { it.name == name } }
            ?: return Result.success()
        val objectId = inputData.getString(WorkManagerNotificationScheduler.KEY_OBJECT_ID) ?: return Result.success()
        val count = inputData.getInt(WorkManagerNotificationScheduler.KEY_COUNT, 1)

        val settings = EntryPointAccessors.fromApplication(applicationContext, NotificationEntryPoint::class.java).settingsRepository()
        val enabled = runBlocking { settings.settings.first().notificationsEnabled }
        if (!enabled || !WorkManagerNotificationScheduler.devicePermits(applicationContext)) return Result.success()

        WorkManagerNotificationScheduler.ensureChannel(applicationContext)
        val open = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = WorkManagerNotificationScheduler.message(applicationContext, kind, objectId, count)
        val notification = NotificationCompat.Builder(applicationContext, WorkManagerNotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_garden)
            .setColor(ContextCompat.getColor(applicationContext, R.color.notification_leaf))
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(WorkManagerNotificationScheduler.notificationId(kind), notification)
        } catch (_: SecurityException) {
            // The permission was taken away between the check and the post: nothing to tell.
        }
        return Result.success()
    }
}
