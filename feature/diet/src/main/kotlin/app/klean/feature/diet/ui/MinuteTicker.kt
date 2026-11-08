package app.klean.feature.diet.ui

import app.klean.shared.time.MINUTE_MS
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits now and then once a minute, so ongoing gaps and the logical day stay current on screen. */
open class MinuteTicker @Inject constructor() {
    open fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE_MS)
        }
    }
}
