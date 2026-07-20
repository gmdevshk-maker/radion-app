package com.app.radion.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class ChannelCatalog(
    val version: Int,
    val channels: List<Channel>,
)

@Serializable
data class Channel(
    val id: String,
    val group: String,
    val name: String,
    val freq: Double,
    val desc: String,
    val type: ChannelType,
    val resolver: ResolverType,
    val api: String? = null,
    val streamUrl: String? = null,
    /** 영상 채널의 오디오 폴백 API — 보이는 라디오 미송출 시 소리만 재생 */
    val audioApi: String? = null,
    /** 현재 방송 정보를 주는 방송사. null이면 이 채널은 정보 표시 없음(CBS·TBS·국악방송) */
    val infoProvider: InfoProvider? = null,
    /** [infoProvider]가 채널을 구분하는 코드. 스트림 API의 코드와 다를 수 있어 따로 둔다 */
    val infoCode: String? = null,
    /**
     * 하루치 편성표 API의 채널 코드. SBS만 현재 방송 API(S07/S08)와 편성표 API(Power/Love)의
     * 코드가 달라 따로 둔다. KBS·MBC는 [infoCode]를 그대로 쓰므로 없다.
     */
    val scheduleCode: String? = null,
)

/**
 * 주파수를 "93.1" 형태로 포맷한다.
 * 로케일에 따라 소수점이 쉼표로 바뀌지 않도록 [Locale.US] 고정.
 */
fun formatFreq(freq: Double): String = String.format(Locale.US, "%.1f", freq)

/** 채널 주파수 표시 문자열 ("93.1"). */
val Channel.freqText: String get() = formatFreq(freq)

@Serializable
enum class ChannelType {
    @SerialName("audio") AUDIO,
    @SerialName("video") VIDEO,
}

@Serializable
enum class ResolverType {
    /** streamUrl을 그대로 사용 */
    @SerialName("direct") DIRECT,

    /** KBS landing API — channel_item에서 media_type(radio/bora)으로 선택 */
    @SerialName("kbs") KBS,

    /** MBC aacplay.ashx — 응답 본문이 곧 스트림 URL */
    @SerialName("mbc") MBC,

    /** MBC boraplay.ashx — JSONP 응답에서 BoraURL 추출 */
    @SerialName("mbc_bora") MBC_BORA,

    /** SBS play-api — 응답 본문이 곧 스트림 URL */
    @SerialName("sbs") SBS,
}

/**
 * 현재 방송 정보(프로그램·진행자·곡)를 주는 방송사.
 *
 * 스트림 발급과는 API도 채널 코드도 달라 [ResolverType]과 분리했다.
 * CBS·TBS·국악방송은 공개 API가 없어 여기 없다.
 */
@Serializable
enum class InfoProvider {
    /** mediafactory onair_now — 프로그램 + 진행자(`program_actor`) */
    @SerialName("kbs") KBS,

    /** miniunit 주간 편성표 + 실시간 선곡(`somitem`) */
    @SerialName("mbc") MBC,

    /** play-api onair — 프로그램 + 게스트(`guest_name`) */
    @SerialName("sbs") SBS,

    /** cururentOnair.json — 프로그램 + 진행자(`chrctNm`) */
    @SerialName("ebs") EBS,

    /** nowSchedule.xml — 시간대별 제목만 */
    @SerialName("ytn") YTN,
}
