package com.studioguu.miniaturegarden.data.repository

import com.studioguu.miniaturegarden.data.local.mapper.toEntity
import com.studioguu.miniaturegarden.data.security.SecurityManager
import com.studioguu.miniaturegarden.domain.model.UserProfile
import com.studioguu.miniaturegarden.domain.repository.SecurityRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityRepositoryImpl @Inject constructor(
    private val securityManager: SecurityManager,
) : SecurityRepository {

    override fun isSignatureValid(profile: UserProfile): Boolean =
        securityManager.verifySignature(profile.toEntity())

    override fun isTamperFlagged(): Boolean = securityManager.isTamperFlagged()

    override fun setTamperFlag(flagged: Boolean) = securityManager.setTamperFlag(flagged)
}
