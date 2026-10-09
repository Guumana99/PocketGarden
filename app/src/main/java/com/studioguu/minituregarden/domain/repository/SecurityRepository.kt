package com.studioguu.minituregarden.domain.repository

import com.studioguu.minituregarden.domain.model.UserProfile

interface SecurityRepository {

    /** True when [profile]'s signature matches its contents. */
    fun isSignatureValid(profile: UserProfile): Boolean

    /**
     * True when the device clock looks manipulated relative to [profile]'s last save.
     * A positive result also raises the tamper flag.
     */
    fun isTimeTampered(profile: UserProfile): Boolean

    fun isTamperFlagged(): Boolean
    fun setTamperFlag(flagged: Boolean)
}
