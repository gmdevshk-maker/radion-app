package com.app.radion.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime

/** assets/channels.json 로드 + 재생 직전 스트림 URL 해석(토큰 발급 API 호출). */
class ChannelRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private var cached: List<Channel>? = null

    suspend fun loadChannels(): List<Channel> = cached ?: withContext(Dispatchers.IO) {
        val text = context.assets.open("channels.json").bufferedReader().use { it.readText() }
        json.decodeFromString<ChannelCatalog>(text).channels.also { cached = it }
    }

    /**
     * 채널의 실제 재생 URL을 얻는다. 방송사 API는 만료 토큰이 붙은 URL을 돌려주므로
     * 재생 시점마다 호출해야 한다.
     */
    suspend fun resolveStreamUrl(channel: Channel): String = withContext(Dispatchers.IO) {
        when (channel.resolver) {
            ResolverType.DIRECT -> requireNotNull(channel.streamUrl) { "streamUrl 누락: ${channel.id}" }
            ResolverType.KBS -> resolveKbs(
                requireNotNull(channel.api),
                wantedMediaType = if (channel.type == ChannelType.VIDEO) "bora" else "radio",
            )
            ResolverType.MBC -> httpGetText(requireNotNull(channel.api)).trim()
            ResolverType.MBC_BORA -> resolveMbcBora(channel)
            ResolverType.SBS -> httpGetText(requireNotNull(channel.api)).trim()
        }
    }

    /** 영상 채널의 오디오 폴백 URL. 보이는 라디오가 미송출일 때 소리만 재생용. */
    suspend fun resolveAudioFallbackUrl(channel: Channel): String = withContext(Dispatchers.IO) {
        val api = requireNotNull(channel.audioApi) { "audioApi 누락: ${channel.id}" }
        when (channel.resolver) {
            ResolverType.KBS -> resolveKbs(api, wantedMediaType = "radio")
            else -> httpGetText(api).trim()
        }
    }

    private fun resolveKbs(api: String, wantedMediaType: String): String {
        val body = httpGetText(api, referer = KBS_REFERER)
        val items = json.parseToJsonElement(body).jsonObject["channel_item"]?.jsonArray
            ?: error("KBS 응답에 channel_item 없음: $api")
        val item = items.map { it.jsonObject }
            .firstOrNull { it["media_type"]?.jsonPrimitive?.content == wantedMediaType }
            ?: items.first().jsonObject
        return item["service_url"]?.jsonPrimitive?.content
            ?: error("KBS 응답에 service_url 없음: $api")
    }

    /**
     * MBC 보이는 라디오 (2026년 채널별 방식으로 개편됨).
     *
     * 주간 보라 편성표에서 지금 이 채널에 편성된 프로그램을 찾아 bid/startTime을 발급 API에 넘긴다.
     * 편성이 없으면 예외를 던져 오디오 폴백을 태운다.
     *
     * `bora/currentschedule`은 채널 파라미터를 무시하고 전역으로 1건만 주는데,
     * FM4U와 표준FM 보라가 겹치는 요일(수 14:20~15:57)이 있어 채널별 판별에 쓸 수 없다.
     */
    private fun resolveMbcBora(channel: Channel): String {
        val api = requireNotNull(channel.api) { "api 누락: ${channel.id}" }
        val code = MBC_CHANNEL_PARAM.find(api)?.groupValues?.get(1)
            ?: error("MBC 채널 코드 없음: ${channel.id}")
        val wantedChannel = MBC_CHANNEL_NAMES[code] ?: error("알 수 없는 MBC 채널: $code")

        val now = LocalDateTime.now()
        val today = now.toLocalDate().toString()
        val nowHhmm = "%02d%02d".format(now.hour, now.minute)

        val entry = json.parseToJsonElement(httpGetText(MBC_BORA_SCHEDULE_LIST, referer = MBC_MINI_REFERER))
            .jsonArray
            .map { it.jsonObject }
            .firstOrNull {
                it["Channel"]?.jsonPrimitive?.content == wantedChannel &&
                    it["BroadDate"]?.jsonPrimitive?.content == today &&
                    // StartTime/EndTime은 "1900" 형식이라 문자열 비교로 충분하다
                    (it["StartTime"]?.jsonPrimitive?.content ?: "9999") <= nowHhmm &&
                    nowHhmm <= (it["EndTime"]?.jsonPrimitive?.content ?: "0000")
            } ?: error("보이는 라디오 편성 아님: ${channel.id}")

        val bid = entry["BroadCastID"]?.jsonPrimitive?.content.orEmpty()
        // startTime은 편성표의 "1900" 형식 그대로 넘겨야 한다.
        // "19:00"으로 바꿔 보내면 방송 중이어도 BoraURL이 빈 문자열로 온다.
        val startTime = entry["StartTime"]?.jsonPrimitive?.content.orEmpty()

        val body = httpGetText("$api&bid=$bid&startTime=$startTime", referer = MBC_MINI_REFERER)
        val url = BORA_URL_REGEX.find(body)?.groupValues?.get(1)
        if (url.isNullOrBlank()) error("보이는 라디오 미송출: ${channel.id}")
        return url
    }

    companion object {
        // 빈 문자열("")도 잡아야 미송출을 판별할 수 있다
        private val BORA_URL_REGEX = Regex("\"BoraURL\"\\s*:\\s*\"([^\"]*)\"")
        private val MBC_CHANNEL_PARAM = Regex("channel=([a-z0-9]+)")
        private const val MBC_BORA_SCHEDULE_LIST = "https://miniapi.imbc.com/bora/scheduleList"
        private const val MBC_MINI_REFERER = "https://miniwebapp.imbc.com/"
        private const val KBS_REFERER = "https://onair.kbs.co.kr/"
        /** 편성표의 Channel 값 (표준FM은 "표준FM"이 아니라 "STFM") */
        private val MBC_CHANNEL_NAMES = mapOf("mfm" to "FM4U", "sfm" to "STFM")
    }
}
