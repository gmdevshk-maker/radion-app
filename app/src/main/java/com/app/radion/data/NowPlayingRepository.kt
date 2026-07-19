package com.app.radion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 방송사 API에서 지금 방송 중인 프로그램 정보를 가져와 한 줄 문자열로 조합한다.
 *
 * 방송사마다 주는 항목이 달라 UI에서 분기하지 않도록 여기서 [NowPlaying.text]까지 완성해 넘긴다.
 * 방송 정보는 재생에 영향을 주지 않는 부가 기능이라, 실패하면 예외를 올리지 않고 null을 돌려준다.
 */
class NowPlayingRepository {

    private val json = Json { ignoreUnknownKeys = true }

    /** MBC 주간 편성표는 280KB나 되는데 하루 내내 그대로다 — 날짜가 바뀔 때만 다시 받는다. */
    private var mbcSchedule: Pair<LocalDate, List<MbcProgram>>? = null

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
        } catch (e: Exception) {
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
        val programEnd = program?.let { atToday(it.startMinutes + it.runningMinutes) }
        val songRefresh = LocalDateTime.now().plusSeconds(MBC_SONG_REFRESH_SEC)
        val refreshAt = when {
            programEnd == null -> songRefresh
            programEnd.isBefore(songRefresh) -> programEnd
            else -> songRefresh
        }
        return text.toNowPlaying(refreshAt)
    }

    private fun mbcProgramNow(channelName: String): MbcProgram? {
        val today = LocalDate.now()
        val programs = mbcSchedule?.takeIf { it.first == today }?.second
            ?: loadMbcSchedule().also { mbcSchedule = today to it }

        val dayLabel = DAY_LABELS[today.dayOfWeek.value - 1]
        val nowMinutes = minutesOfDay()
        return programs.lastOrNull {
            it.channel == channelName &&
                it.days.contains(dayLabel) &&
                nowMinutes >= it.startMinutes &&
                nowMinutes < it.startMinutes + it.runningMinutes
        }
    }

    private fun loadMbcSchedule(): List<MbcProgram> {
        val body = httpGetText(MBC_SCHEDULE, referer = MBC_MINI_REFERER)
        val programs = json.parseToJsonElement(body).jsonObject["Programs"]?.jsonArray ?: return emptyList()
        return programs.mapNotNull { element ->
            val o = element.jsonObject
            val start = hhmmMinutes(o.str("StartTime")) ?: return@mapNotNull null
            MbcProgram(
                channel = o.str("Channel") ?: return@mapNotNull null,
                title = o.str("ProgramTitle") ?: return@mapNotNull null,
                days = o.str("LiveDays").orEmpty(),
                startMinutes = start,
                runningMinutes = o.str("RunningTime")?.toIntOrNull() ?: return@mapNotNull null,
            )
        }
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

    private class MbcProgram(
        val channel: String,
        val title: String,
        val days: String,
        val startMinutes: Int,
        val runningMinutes: Int,
    )

    private companion object {
        const val KBS_ONAIR_NOW =
            "https://static.api.kbs.co.kr/mediafactory/v1/schedule/onair_now" +
                "?rtype=json&local_station_code=00&channel_code="
        const val KBS_REFERER = "https://onair.kbs.co.kr/"

        const val MBC_SCHEDULE = "https://miniunit.imbc.com/Schedule?rtype=json"
        const val MBC_SOM_ITEM = "https://miniunit.imbc.com/list/somitem?rtype=json"
        const val MBC_MINI_REFERER = "https://mini.imbc.com/"
        const val MBC_SONG_REFRESH_SEC = 60L

        const val SBS_ONAIR = "https://apis.sbs.co.kr/play-api/1.0/onair/channel/"
        const val SBS_ONAIR_SUFFIX = "?v_type=2"

        const val EBS_ONAIR = "https://www.ebs.co.kr/schedule/cururentOnair.json?channelCd="

        const val YTN_SCHEDULE = "https://radio.ytn.co.kr/incfile/nowSchedule.xml"
        val YTN_SLOT = Regex("<time>(.*?)</time>\\s*<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)

        /** MBC 편성표의 `LiveDays`는 "월", "금" 같은 한 글자 요일이다. */
        val DAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
    }
}

/** 빈 문자열이면 표시할 게 없다는 뜻이므로 null로 바꾼다. */
private fun String.toNowPlaying(refreshAt: LocalDateTime?): NowPlaying? =
    takeIf { it.isNotEmpty() }?.let { NowPlaying(it, refreshAt) }

/**
 * 있는 항목만 " · "로 잇는다.
 *
 * 방송사가 주지 않는 항목(진행자 없음, 게스트 없음, 곡 없음)은 자연히 빠지고,
 * 앞 조각에 이미 들어 있는 말도 반복하지 않는다 — "가비의 슈퍼라디오"의 진행자가 "가비"인 것처럼
 * 제목에 이름이 그대로 든 편성이 흔해서, 그대로 이으면 "가비의 슈퍼라디오 · 가비"가 된다.
 */
private fun joinParts(vararg parts: String?): String {
    val kept = mutableListOf<String>()
    for (part in parts) {
        // 방송사 입력값에 연속 공백이나 탭이 섞여 오는 일이 잦다("비처럼  음악처럼", "1~4부 \t with …")
        val value = part?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        // 쉼표로 나열된 이름은 하나씩 따져야 한다 — "이은선의 영화관, 정여울의 도서관"의 진행자가
        // "이은선, 정여울"이면 통째로는 안 걸리지만 이름은 이미 제목에 다 들어 있다
        val fresh = value.split(",")
            .map { it.trim() }
            .filter { item -> item.isNotEmpty() && kept.none { it.contains(item) } }
        if (fresh.isEmpty()) continue
        kept += fresh.joinToString(", ")
    }
    return kept.joinToString(" · ")
}

private val WHITESPACE = Regex("\\s+")

/** 값이 없거나 빈 문자열, 문자열 "null"이면 없는 것으로 본다(방송사 API가 셋을 섞어 쓴다). */
private fun JsonObject.str(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

private fun minutesOfDay(): Int = LocalDateTime.now().let { it.hour * 60 + it.minute }

/**
 * 자정 기준 분으로 바꾼다. 방송사마다 형식이 달라("16:00", "1600", KBS의 "16000000")
 * 숫자만 남긴 뒤 앞 네 자리를 시·분으로 읽는다.
 * 심야 편성은 "24000000"처럼 24시를 넘겨 오는데, 그대로 1440 이상으로 돌려준다([atToday]가 처리).
 */
private fun hhmmMinutes(value: String?): Int? {
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
private fun atToday(minutes: Int): LocalDateTime {
    val at = LocalDate.now().atStartOfDay().plusMinutes(minutes.toLong())
    return if (at.isAfter(LocalDateTime.now())) at else at.plusDays(1)
}

private fun unescapeXml(value: String): String = value
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
