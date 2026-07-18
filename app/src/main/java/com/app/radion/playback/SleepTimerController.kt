package com.app.radion.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 취침 타이머 — 만료 시 onExpire 콜백 실행. 서비스 스코프에서 동작한다. */
class SleepTimerController(
    private val scope: CoroutineScope,
    private val onExpire: () -> Unit,
) {
    private var job: Job? = null

    /** [minutes]분 후 만료 예약. 0 이하면 해제. */
    fun schedule(minutes: Int) {
        job?.cancel()
        job = if (minutes > 0) {
            scope.launch {
                delay(minutes * 60_000L)
                onExpire()
            }
        } else {
            null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
