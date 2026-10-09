package com.studioguu.minituregarden.domain.usecase

import com.studioguu.minituregarden.domain.repository.GardenRepository
import com.studioguu.minituregarden.domain.repository.SecurityRepository
import javax.inject.Inject

data class IntegrityResult(
    val signatureValid: Boolean,
    val timeConsistent: Boolean,
    /** Sticky flag: stays true once any check has failed, until it is explicitly cleared. */
    val tamperFlagged: Boolean,
) {
    val isTrusted: Boolean get() = signatureValid && timeConsistent && !tamperFlagged
}

/**
 * Checks the saved profile's signature and the device clock. Run this on launch / resume
 * *before* saving the profile, because saving re-issues the timestamps the checks compare against.
 */
class VerifyIntegrityUseCase @Inject constructor(
    private val gardenRepository: GardenRepository,
    private val securityRepository: SecurityRepository,
) {
    suspend operator fun invoke(): IntegrityResult {
        val profile = gardenRepository.getUserProfile()
            ?: return IntegrityResult(
                signatureValid = true,
                timeConsistent = true,
                tamperFlagged = securityRepository.isTamperFlagged(),
            )

        val signatureValid = securityRepository.isSignatureValid(profile)
        val timeConsistent = !securityRepository.isTimeTampered(profile)
        if (!signatureValid) securityRepository.setTamperFlag(true)

        return IntegrityResult(
            signatureValid = signatureValid,
            timeConsistent = timeConsistent,
            tamperFlagged = securityRepository.isTamperFlagged(),
        )
    }
}
