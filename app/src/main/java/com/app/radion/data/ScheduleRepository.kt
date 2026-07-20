package com.app.radion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

/**
 * 오늘 하루치 편성표를 방송사에서 받아 온다.
 *
 * 현재 방송 한 줄을 만드는 [NowPlayingRepository]와 API가 아예 다르다 — 그쪽은 "지금 뭐 하는지"만
 * 주는 가벼운 엔드포인트고, 여기는 하루 전체를 주는 무거운 쪽이다. 그래서 저장소를 나눴다.
 *
 * 하루치가 오는 곳은 KBS·MBC·SBS뿐([Channel.hasSchedule]). 편성표는 부가 기능이라
 * 실패해도 예외를 올리지 않고 빈 목록을 돌려준다.
 */
class ScheduleRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /** 하루 내내 그대로인 표라 채널별로 날짜와 함께 담아 둔다(시트를 여닫을 때마다 받지 않게). */
    private val cache = mutableMapOf<String, Pair<LocalDate, List<ScheduleItem>>>()

    suspend fun fetchToday(channel: Channel): List<ScheduleItem> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        cache[channel.id]?.takeIf { it.first == today }?.let { return@withContext it.second }

        val items = try {
            when (channel.infoProvider) {
                InfoProvider.KBS -> fetchKbs(requireNotNull(channel.infoCode))
                InfoProvider.MBC -> fetchMbc(requireNotNull(channel.infoCode))
                InfoProvider.SBS -> fetchSbs(requireNotNull(channel.scheduleCode))
                else -> emptyList()
            }.let { mergeSplitPrograms(it.sortedBy { item -> item.startMinutes }) }
        } catch (_: Exception) {
            emptyList()
        }
        items.also { if (it.isNotEmpty()) cache[channel.id] = today to it }
    }

    /**
     * 날짜를 빼면 오늘 하루가 온다. 굳이 넣으려면 `program_planned_date_from`/`_to`를 써야 하고,
     * 단수형 `program_planned_date`는 400이 떨어진다.
     */
    private fun fetchKbs(channelCode: String): List<ScheduleItem> {
        val body = httpGetText(KBS_DAY + channelCode, referer = KBS_REFERER)
        val schedules = json.parseToJsonElement(body).jsonArray
            .firstOrNull()?.jsonObject
            ?.get("schedules")?.jsonArray
            ?: return emptyList()

        return schedules.mapNotNull { element ->
            val o = element.jsonObject
            val title = o.str("program_title") ?: return@mapNotNull null
            item(
                start = hhmmMinutes(o.str("program_planned_start_time")) ?: return@mapNotNull null,
                end = hhmmMinutes(o.str("program_planned_end_time")) ?: return@mapNotNull null,
                title = title,
                host = o.str("program_actor"),
            )
        }
    }

    /**
     * 전 채널 주간 편성표에서 이 채널의 오늘 요일만 뽑는다. 표는 [MbcSchedule]이 캐시해 둔 것을 쓴다.
     *
     * 같은 편성이 두 줄로 들어 있는 날이 있어(표준FM "낭만가요") 시각+제목으로 한 번 걸러 낸다 —
     * 그대로 두면 목록에 같은 줄이 겹쳐 보인다.
     */
    private fun fetchMbc(channelName: String): List<ScheduleItem> {
        val dayLabel = MbcSchedule.todayLabel()
        return MbcSchedule.programs()
            .filter { it.channel == channelName && it.days.contains(dayLabel) }
            .distinctBy { it.startMinutes to it.title }
            .sortedBy { it.startMinutes }
            .map { item(it.startMinutes, it.endMinutes, it.title, it.host) }
    }

    /** 월·일에 앞자리 0을 붙이면 404다(7월 20일 → `/2026/7/20/`). */
    private fun fetchSbs(scheduleCode: String): List<ScheduleItem> {
        val today = LocalDate.now()
        val url = "$SBS_DAY${today.year}/${today.monthValue}/${today.dayOfMonth}/$scheduleCode.json"
        val body = httpGetText(url)
        return json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
            val o = element.jsonObject
            val title = o.str("title") ?: return@mapNotNull null
            item(
                start = hhmmMinutes(o.str("start_time")) ?: return@mapNotNull null,
                end = hhmmMinutes(o.str("end_time")) ?: return@mapNotNull null,
                title = title,
                host = o.str("guest"),
            )
        }
    }

    /**
     * 한 프로그램이 여러 줄로 쪼개져 오는 걸 한 줄로 잇는다.
     *
     * KBS는 두 시간짜리 프로그램을 30분 단위로 끊어 주고("이현우의 음악앨범"이 네 줄),
     * 그 사이에 1~2분짜리 캠페인·스파트가 끼기도 한다("상쾌한 아침 → 재난재해 예방캠페인 → 상쾌한 아침").
     * 제목이 같은 줄이 이어지면 시각만 합치고, 사이에 낀 짧은 스파트는 버린다.
     *
     * 짧다고 다 버리지는 않는다 — 1라디오에는 "바른 말 고운 말"(2분), "58분 날씨"(2분)처럼
     * 진짜 2~3분짜리 편성이 수두룩해서, 같은 제목 사이에 끼인 것만 버려야 편성이 통째로 사라지지 않는다.
     * 대신 프로그램 중간에 낀 2분짜리 편성은 표에서 빠질 수 있다(방송 정보 줄에는 그대로 나온다).
     */
    private fun mergeSplitPrograms(items: List<ScheduleItem>): List<ScheduleItem> {
        val merged = mutableListOf<ScheduleItem>()
        // 같은 제목이 뒤따르면 버릴 후보 — 아니면 순서 그대로 되돌려 놓는다
        val heldSpots = mutableListOf<ScheduleItem>()

        for (item in items) {
            val previous = merged.lastOrNull()
            when {
                previous != null && previous.title == item.title -> {
                    merged[merged.lastIndex] = previous.copy(
                        endMinutes = item.endMinutes,
                        host = previous.host ?: item.host,
                    )
                    heldSpots.clear()
                }

                item.endMinutes - item.startMinutes < SPOT_MAX_MINUTES -> heldSpots += item

                else -> {
                    merged += heldSpots
                    heldSpots.clear()
                    merged += item
                }
            }
        }
        return merged + heldSpots
    }

    /**
     * 종료 시각이 시작보다 이르면 자정을 넘긴 편성이므로 하루를 더한다 —
     * 그래야 "지금 방송 중" 판정([ScheduleItem.isOnAir])이 심야에도 맞는다.
     */
    private fun item(start: Int, end: Int, title: String, host: String?) = ScheduleItem(
        startMinutes = start,
        endMinutes = if (end > start) end else end + MINUTES_PER_DAY,
        title = title.tidy(),
        host = hostApartFrom(title, host),
    )

    private companion object {
        const val KBS_DAY =
            "https://static.api.kbs.co.kr/mediafactory/v1/schedule/weekly" +
                "?rtype=json&local_station_code=00&channel_code="
        const val KBS_REFERER = "https://onair.kbs.co.kr/"

        const val SBS_DAY = "https://static.cloud.sbs.co.kr/schedule/"

        /** 이보다 짧고 같은 프로그램 사이에 낀 편성은 캠페인·스파트로 보고 버린다. */
        const val SPOT_MAX_MINUTES = 3
    }
}
