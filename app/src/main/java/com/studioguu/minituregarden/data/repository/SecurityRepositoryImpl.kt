package com.studioguu.minituregarden.data.repository

import com.studioguu.minituregarden.data.local.mapper.toEntity
import com.studioguu.minituregarden.data.security.SecurityManager
import com.studioguu.minituregarden.domain.model.UserProfile
import com.studioguu.minituregarden.domain.repository.SecurityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityRepositoryImpl @Inject constructor(
    private val securityManager: SecurityManager,
) : SecurityRepository {

    override fun isSignatureValid(profile: UserProfile): Boolean =
        securityManager.verifySignature(profile.toEntity())

    override fun isTimeTampered(profile: UserProfile): Boolean =
        securityManager.detectTimeTampering(profile.lastUpdatedTimestamp, profile.lastMonoClockTime)

    override fun isTamperFlagged(): Boolean = securityManager.isTamperFlagged()

    override fun setTamperFlag(flagged: Boolean) = securityManager.setTamperFlag(flagged)
}
