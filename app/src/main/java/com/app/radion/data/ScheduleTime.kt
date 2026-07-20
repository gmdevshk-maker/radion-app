package com.app.radion.data

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 편성 시각을 다루는 공용 헬퍼.
 *
 * 방송사 API의 시각은 전부 "오늘 자정 기준 분"으로 바꿔 놓고 비교한다 —
 * 형식이 방송사마다 다른데다("16:00", "1600", "16000000") 심야 편성은 24시를 넘겨 오기 때문에,
 * 한 번 분으로 눕혀 두면 [NowPlayingRepository]와 [ScheduleRepository]가 같은 규칙으로 읽을 수 있다.
 */

internal fun minutesOfDay(): Int = LocalDateTime.now().let { it.hour * 60 + it.minute }

/**
 * 자정 기준 분으로 바꾼다. 방송사마다 형식이 달라("16:00", "1600", KBS의 "16000000")
 * 숫자만 남긴 뒤 앞 네 자리를 시·분으로 읽는다.
 * 심야 편성은 "24000000"처럼 24시를 넘겨 오는데, 그대로 1440 이상으로 돌려준다([atToday]가 처리).
 */
internal fun hhmmMinutes(value: String?): Int? {
    val digits = value?.filter { it.isDigit() } ?: return null
    if (digits.length < 4) return null
    val hour = digits.substring(0, 2).toIntOrNull() ?: return null
    val minute = digits.substring(2, 4).toIntOrNull() ?: return null
    return hour * 60 + minute
}

/**
 * 자정 기준 분을 실제 시각으로 바꾼다.
 * 24시를 넘긴 값(심야 편성)이나 이미 지난 시각은 다음 날로 넘긴다.
 */
internal fun atToday(minutes: Int): LocalDateTime {
    val at = LocalDate.now().atStartOfDay().plusMinutes(minutes.toLong())
    return if (at.isAfter(LocalDateTime.now())) at else at.plusDays(1)
}

/** 자정 기준 분을 "16:00"으로 포맷한다. 24시를 넘긴 심야 편성은 "01:00"으로 접어 표시한다. */
internal fun formatHhmm(minutes: Int): String =
    "%02d:%02d".format((minutes / 60) % 24, minutes % 60)
