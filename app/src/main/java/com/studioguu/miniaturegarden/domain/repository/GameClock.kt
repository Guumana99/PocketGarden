package com.studioguu.miniaturegarden.domain.repository

/**
 * The game's time, in epoch millis: what plants, seasons, building and the weather go by. It
 * moves on only as much as time has really passed, as far as the device can tell, so setting
 * the device's clock forward (or back) changes nothing in the garden. Nothing is held against a
 * player who does: the clock simply does not follow.
 *
 * How the garden looks (morning, evening, night) goes by the device's clock instead.
 */
fun interface GameClock {
    fun now(): Long
}
