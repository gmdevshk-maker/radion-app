package com.radion.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
)

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
