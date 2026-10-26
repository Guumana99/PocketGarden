package com.studioguu.miniaturegarden.domain.repository

import com.studioguu.miniaturegarden.domain.model.UserProfile

interface SecurityRepository {

    /** True when [profile]'s signature matches its contents. */
    fun isSignatureValid(profile: UserProfile): Boolean

    /** Raised once a signature has been found broken; it stays until cleared. */
    fun isTamperFlagged(): Boolean
    fun setTamperFlag(flagged: Boolean)
}
