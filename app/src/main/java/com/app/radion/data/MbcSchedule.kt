package com.app.radion.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

/** MBC 주간 편성표의 한 편성. 종료 시각은 안 오는 날이 있어 [runningMinutes]로 계산한다. */
internal class MbcProgram(
    val channel: String,
    val title: String,
    val host: String?,
    val days: String,
    val startMinutes: Int,
    val runningMinutes: Int,
) {
    val endMinutes: Int get() = startMinutes + runningMinutes

    /** 오늘 이 채널에 편성됐고 [nowMinutes]가 방송 시간 안에 드는가. */
    fun isOnAir(channelName: String, dayLabel: String, nowMinutes: Int): Boolean =
        channel == channelName &&
            days.contains(dayLabel) &&
            nowMinutes >= startMinutes &&
            nowMinutes < endMinutes
}

/**
 * MBC 주간 편성표. 280KB나 되는데 하루 내내 그대로라 날짜가 바뀔 때만 다시 받는다.
 *
 * 방송 정보 한 줄([NowPlayingRepository])과 편성표 시트([ScheduleRepository])가 같은 표를 보므로
 * 여기 한 곳에 캐시해 두 곳이 나눠 쓴다 — 따로 받으면 280KB를 두 번 내려받게 된다.
 * 채널 필터 파라미터는 없다(붙여도 전 채널이 그대로 온다).
 */
internal object MbcSchedule {

    private val json = Json { ignoreUnknownKeys = true }

    private var cached: Pair<LocalDate, List<MbcProgram>>? = null

    /** MBC 편성표의 `LiveDays`는 "월", "금" 같은 한 글자 요일이다. */
    private val DAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")

    private const val SCHEDULE_URL = "https://miniunit.imbc.com/Schedule?rtype=json"
    private const val MINI_REFERER = "https://mini.imbc.com/"

    fun todayLabel(): String = DAY_LABELS[LocalDate.now().dayOfWeek.value - 1]

    /**
     * 오늘자 전 채널 편성. 호출부가 IO 디스패처에서 부르므로 여기서 그대로 네트워크를 탄다.
     * 두 저장소가 동시에 처음 부를 수 있어 잠가 둔다 — 안 그러면 280KB를 나란히 두 번 받는다.
     */
    @Synchronized
    fun programs(): List<MbcProgram> {
        val today = LocalDate.now()
        cached?.takeIf { it.first == today }?.let { return it.second }
        return load().also { cached = today to it }
    }

    private fun load(): List<MbcProgram> {
        val body = httpGetText(SCHEDULE_URL, referer = MINI_REFERER)
        val programs = json.parseToJsonElement(body).jsonObject["Programs"]?.jsonArray ?: return emptyList()
        return programs.mapNotNull { element ->
            val o = element.jsonObject
            MbcProgram(
                channel = o.str("Channel") ?: return@mapNotNull null,
                title = o.str("ProgramTitle") ?: return@mapNotNull null,
                host = o.str("DJ"),
                days = o.str("LiveDays").orEmpty(),
                startMinutes = hhmmMinutes(o.str("StartTime")) ?: return@mapNotNull null,
                runningMinutes = o.str("RunningTime")?.toIntOrNull() ?: return@mapNotNull null,
            )
        }
    }
}
