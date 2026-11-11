package app.klean.feature.diet.calories

/** Fills an entry's missing calories. See [CalorieFiller]; [None] for tests and when nothing is wired. */
fun interface CalorieFill {
    suspend fun fill(entryId: String): FillResult

    companion object {
        val None = CalorieFill { FillResult.NotPossible }
    }
}

sealed interface FillResult {
    data class Filled(val source: app.klean.shared.diet.KcalSource) : FillResult
    data object QueuedForAi : FillResult
    data object NotPossible : FillResult
}
