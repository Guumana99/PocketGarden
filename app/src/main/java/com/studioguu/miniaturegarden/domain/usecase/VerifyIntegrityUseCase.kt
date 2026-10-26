package com.studioguu.miniaturegarden.domain.usecase

import com.studioguu.miniaturegarden.domain.repository.GameClock
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.repository.SecurityRepository
import javax.inject.Inject

data class IntegrityResult(
    val signatureValid: Boolean,
    /** Sticky flag: stays true once a broken signature has been found, until it is explicitly cleared. */
    val tamperFlagged: Boolean,
) {
    val isTrusted: Boolean get() = signatureValid && !tamperFlagged
}

/**
 * Checks the saved profile's signature, which guards the coins and the level against the save
 * being edited. The device's clock is no longer checked: the game's own clock ([GameClock])
 * does not follow it, so a clock set forward or back has nothing to gain and nothing to answer
 * for. Run this on launch / resume *before* saving the profile, which signs it afresh.
 */
class VerifyIntegrityUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val securityRepository: SecurityRepository,
) {
    suspend operator fun invoke(): IntegrityResult {
        val profile = gardenRepository.getUserProfile()
            ?: return IntegrityResult(signatureValid = true, tamperFlagged = securityRepository.isTamperFlagged())

        val signatureValid = securityRepository.isSignatureValid(profile)
        if (!signatureValid) securityRepository.setTamperFlag(true)

        return IntegrityResult(signatureValid = signatureValid, tamperFlagged = securityRepository.isTamperFlagged())
    }
}
