package com.app.radion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.LocalDateTime

/**
 * 방송사 API에서 지금 방송 중인 프로그램 정보를 가져와 한 줄 문자열로 조합한다.
 *
 * 방송사마다 주는 항목이 달라 UI에서 분기하지 않도록 여기서 [NowPlaying.text]까지 완성해 넘긴다.
 * 방송 정보는 재생에 영향을 주지 않는 부가 기능이라, 실패하면 예외를 올리지 않고 null을 돌려준다.
 */
class NowPlayingRepository {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(channel: Channel): NowPlaying? = withContext(Dispatchers.IO) {
        try {
            when (channel.infoProvider) {
                null -> null
                InfoProvider.KBS -> fetchKbs(requireNotNull(channel.infoCode))
                InfoProvider.MBC -> fetchMbc(requireNotNull(channel.infoCode))
                InfoProvider.SBS -> fetchSbs(requireNotNull(channel.infoCode))
                InfoProvider.EBS -> fetchEbs(requireNotNull(channel.infoCode))
                InfoProvider.YTN -> fetchYtn()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 현재 + 다음 편성 2건이 온다. 진행자는 `program_actor`. */
    private fun fetchKbs(channelCode: String): NowPlaying? {
        val body = httpGetText(KBS_ONAIR_NOW + channelCode, referer = KBS_REFERER)
        val schedules = json.parseToJsonElement(body).jsonArray
            .firstOrNull()?.jsonObject
            ?.get("schedules")?.jsonArray
            ?.map { it.jsonObject }
            ?: return null

        val nowMinutes = minutesOfDay()
        // 보통 첫 항목이 현재 방송이지만 경계 시각에는 어긋날 수 있어 시각으로 한 번 더 고른다
        val item = schedules.firstOrNull {
            val start = hhmmMinutes(it.str("program_planned_start_time"))
            val end = hhmmMinutes(it.str("program_planned_end_time"))
            start != null && end != null && nowMinutes in start until end
        } ?: schedules.firstOrNull() ?: return null

        val text = joinParts(item.str("program_title"), item.str("program_actor"))
        return text.toNowPlaying(hhmmMinutes(item.str("program_planned_end_time"))?.let(::atToday))
    }

    /**
     * 프로그램은 주간 편성표에서 요일·시각으로 찾고, 지금 나오는 곡은 따로 받아 붙인다.
     * 곡이 없는 시간대(뉴스·토크)에는 프로그램만 남는다.
     */
    private fun fetchMbc(channelName: String): NowPlaying? {
        val program = mbcProgramNow(channelName)
        val text = joinParts(program?.title, mbcSong(channelName))

        // 곡은 프로그램 종료를 기다리면 놓치므로 더 이른 쪽으로 다시 물어본다
        val programEnd = program?.let { atToday(it.endMinutes) }
        val songRefresh = LocalDateTime.now().plusSeconds(MBC_SONG_REFRESH_SEC)
        return text.toNowPlaying(programEnd?.takeIf { it.isBefore(songRefresh) } ?: songRefresh)
    }

    private fun mbcProgramNow(channelName: String): MbcProgram? {
        val dayLabel = MbcSchedule.todayLabel()
        val nowMinutes = minutesOfDay()
        return MbcSchedule.programs().lastOrNull { it.isOnAir(channelName, dayLabel, nowMinutes) }
    }

    /** 전 채널의 현재 곡이 한 번에 오므로 이 채널 것만 골라낸다. 곡이 없으면 항목 자체가 빠진다. */
    private fun mbcSong(channelName: String): String? {
        val body = httpGetText(MBC_SOM_ITEM, referer = MBC_MINI_REFERER)
        val item = json.parseToJsonElement(body).jsonObject["SomItemList"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it.str("Channel") == channelName }
            ?: return null
        // 앞에 붙는 '♬'는 서브셋 폰트에 없는 글자라 떼어 낸다
        return item.str("SomItem")?.trimStart('♬')?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun fetchSbs(channelId: String): NowPlaying? {
        val body = httpGetText(SBS_ONAIR + channelId + SBS_ONAIR_SUFFIX)
        val info = json.parseToJsonElement(body).jsonObject["onair"]?.jsonObject
            ?.get("info")?.jsonObject
            ?: return null
        val text = joinParts(info.str("title"), info.str("guest_name"))
        return text.toNowPlaying(hhmmMinutes(info.str("endtime"))?.let(::atToday))
    }

    private fun fetchEbs(channelCode: String): NowPlaying? {
        val body = httpGetText(EBS_ONAIR + channelCode)
        val program = json.parseToJsonElement(body).jsonObject["nowProgram"]?.jsonObject ?: return null
        val text = joinParts(program.str("title"), program.str("chrctNm"))
        return text.toNowPlaying(hhmmMinutes(program.str("end"))?.let(::atToday))
    }

    /**
     * 하루 편성이 아니라 지금 시간대 앞뒤 몇 건만 오는 XML이다.
     * 시작 시각이 현재보다 이르면서 가장 늦은 항목이 방송 중인 프로그램.
     */
    private fun fetchYtn(): NowPlaying? {
        val xml = httpGetText(YTN_SCHEDULE)
        val slots = YTN_SLOT.findAll(xml)
            .mapNotNull { match ->
                val minutes = hhmmMinutes(match.groupValues[1].trim()) ?: return@mapNotNull null
                minutes to unescapeXml(match.groupValues[2].trim())
            }
            .toList()

        val nowMinutes = minutesOfDay()
        val current = slots.lastOrNull { it.first <= nowMinutes } ?: return null
        val next = slots.firstOrNull { it.first > nowMinutes }
        return current.second.toNowPlaying(next?.let { atToday(it.first) })
    }

    private companion object {
        const val KBS_ONAIR_NOW =
            "https://static.api.kbs.co.kr/mediafactory/v1/schedule/onair_now" +
                "?rtype=json&local_station_code=00&channel_code="
        const val KBS_REFERER = "https://onair.kbs.co.kr/"

        const val MBC_SOM_ITEM = "https://miniunit.imbc.com/list/somitem?rtype=json"
        const val MBC_MINI_REFERER = "https://mini.imbc.com/"
        const val MBC_SONG_REFRESH_SEC = 60L

        const val SBS_ONAIR = "https://apis.sbs.co.kr/play-api/1.0/onair/channel/"
        const val SBS_ONAIR_SUFFIX = "?v_type=2"

        const val EBS_ONAIR = "https://www.ebs.co.kr/schedule/cururentOnair.json?channelCd="

        const val YTN_SCHEDULE = "https://radio.ytn.co.kr/incfile/nowSchedule.xml"
        val YTN_SLOT = Regex("<time>(.*?)</time>\\s*<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    }
}

/** 빈 문자열이면 표시할 게 없다는 뜻이므로 null로 바꾼다. */
private fun String.toNowPlaying(refreshAt: LocalDateTime?): NowPlaying? =
    takeIf { it.isNotEmpty() }?.let { NowPlaying(it, refreshAt) }

private fun unescapeXml(value: String): String = value
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
