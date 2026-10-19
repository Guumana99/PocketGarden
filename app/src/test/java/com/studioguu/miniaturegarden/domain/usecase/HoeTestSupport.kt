package com.studioguu.miniaturegarden.domain.usecase

/**
 * Strikes the cell until the hoe is done with it, however hard its ground ([HoeUseCase] answers
 * [ActionResult.HOE_STRUCK] until it has had enough strikes), and gives the last answer.
 */
suspend fun HoeUseCase.till(gridX: Int, gridY: Int, now: Long): ActionResult {
    var strikes = 0
    while (true) {
        val result = this(gridX, gridY, now, strikes)
        if (result != ActionResult.HOE_STRUCK) return result
        strikes++
    }
}
