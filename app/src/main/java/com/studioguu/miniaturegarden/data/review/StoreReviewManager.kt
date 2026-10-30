package com.studioguu.miniaturegarden.data.review

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Play's own review sheet. Play decides for itself whether it shows anything, and says
 * nothing about it either way, so there is nothing to wait for and nothing to report back.
 */
@Singleton
class StoreReviewManager @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun ask(activity: Activity) {
        val manager = ReviewManagerFactory.create(context)
        manager.requestReviewFlow().addOnSuccessListener { info -> manager.launchReviewFlow(activity, info) }
    }
}
