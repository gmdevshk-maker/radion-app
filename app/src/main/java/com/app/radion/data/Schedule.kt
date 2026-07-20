package com.app.radion.data

/**
 * 편성표 한 줄. 시각은 자정 기준 분이라 심야 편성은 1440을 넘길 수 있다([formatHhmm]이 접어 표시).
 */
data class ScheduleItem(
    val startMinutes: Int,
    val endMinutes: Int,
    val title: String,
    /** 진행자. 제목에 이미 이름이 든 편성에서는 null([hostApartFrom]) */
    val host: String?,
) {
    val timeText: String get() = formatHhmm(startMinutes)

    /**
     * 심야 편성은 자정 넘긴 시각을 24시 이후로 표기하므로(KBS의 "26:00") 현재 시각도 하루를 더해 본다.
     * 새벽 2시에 트는 방송이 편성표 맨 끝의 "24:00~05:00"인 경우가 그렇다.
     */
    fun isOnAir(nowMinutes: Int): Boolean =
        nowMinutes in startMinutes until endMinutes ||
            (nowMinutes + MINUTES_PER_DAY) in startMinutes until endMinutes
}

/** 자정을 넘긴 편성을 하루 뒤로 밀 때 쓰는 값. [ScheduleRepository]도 같은 상수를 본다. */
internal const val MINUTES_PER_DAY = 24 * 60

/**
 * 하루치 편성표를 받을 수 있는 채널인지.
 *
 * EBS·YTN은 현재 방송만 주고 하루치 API가 없어([NowPlayingRepository]는 그래서 동작한다)
 * 편성표 시트를 열 수 없다. CBS·TBS·국악방송은 API 자체가 없다.
 */
val Channel.hasSchedule: Boolean
    get() = infoProvider == InfoProvider.KBS ||
        infoProvider == InfoProvider.MBC ||
        infoProvider == InfoProvider.SBS
