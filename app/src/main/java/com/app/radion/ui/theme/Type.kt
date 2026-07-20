package com.app.radion.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 목업에서 쓰는 텍스트 스타일 토큰. 각 화면에서 `TextStyle(...)`을 직접 만들지 말고
 * 여기서 가져다 쓰고, 색만 다를 때는 `.copy(color = ...)`로 파생시킨다.
 *
 * 본문·제목은 Pretendard, 숫자·라벨(주파수·시각·영문 오버라인)은 PlexMono가 기본이다.
 */
object RadionType {

    // ── Pretendard (본문·제목) ────────────────────────────────

    /** 헤더의 "라디온" 워드마크 */
    val AppTitle = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = (-0.02).em,
        color = RadionColors.Amber,
    )

    /** 다이얼로그 제목 ("새 버전이 있습니다") */
    val DialogTitle = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        letterSpacing = (-0.01).em,
        color = RadionColors.Text,
    )

    /** 다이얼로그 진행 상태 제목 ("다운로드 중…") */
    val DialogSubtitle = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = RadionColors.Text,
    )

    /** 채널 리스트의 채널명 */
    val ChannelName = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = RadionColors.Text,
    )

    /** 미니 플레이어의 현재 채널명 */
    val PlayerTitle = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 14.5.sp,
        color = RadionColors.Text,
    )

    /** 영상 스테이지 플레이스홀더의 채널명 */
    val StageTitle = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = (-0.01).em,
        color = RadionColors.Text,
    )

    /** 다이얼로그 버튼 라벨 */
    val Button = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = RadionColors.Text,
    )

    /** 토스트 본문 */
    val Body = TextStyle(
        fontFamily = Pretendard,
        fontSize = 12.5.sp,
        color = RadionColors.Text,
    )

    /** 튜너 하단의 방송국명 등 보조 문구 */
    val BodyMuted = Body.copy(color = RadionColors.Muted)

    /** 채널 설명, 취침 타이머 칩 */
    val Caption = TextStyle(
        fontFamily = Pretendard,
        fontSize = 12.sp,
        color = RadionColors.Muted,
    )

    /** 채널명 옆 "보이는" 배지 */
    val Badge = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        color = RadionColors.Amber,
    )

    // ── PlexMono (숫자·영문 라벨) ─────────────────────────────

    /** 튜너의 대형 주파수 표시 */
    val FreqDisplay = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        color = RadionColors.Amber,
    )

    /** 헤더 시계의 시/분 숫자 */
    val ClockTime = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        color = RadionColors.Text,
    )

    /** 채널 행 왼쪽의 주파수 */
    val FreqRow = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = RadionColors.Muted,
    )

    /** "MHz" 단위, 다이얼로그의 버전 비교 문구 */
    val MonoMd = TextStyle(
        fontFamily = PlexMono,
        fontSize = 13.sp,
        color = RadionColors.Muted,
    )

    /** 다운로드 진행률 % */
    val MonoSm = TextStyle(
        fontFamily = PlexMono,
        fontSize = 12.sp,
        color = RadionColors.Muted,
    )

    /** 미니 플레이어의 "93.1 MHz · 재생 중" */
    val MonoStatus = TextStyle(
        fontFamily = PlexMono,
        fontSize = 11.5.sp,
        color = RadionColors.Muted,
    )

    /** 튜너 눈금 라벨, 영상 스테이지의 "93.1 MHz · STUDIO LIVE" */
    val MonoXs = TextStyle(
        fontFamily = PlexMono,
        fontSize = 11.sp,
        color = RadionColors.Muted,
    )

    /** 미니 플레이어의 주파수 배지 (2줄) */
    val FreqBadge = TextStyle(
        fontFamily = PlexMono,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        color = RadionColors.Amber,
    )

    /** 헤더의 AM/PM, 버전 표기 */
    val Overline = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 0.06.em,
        color = RadionColors.Muted,
    )

    /** 다이얼로그 상단의 "RADION UPDATE" */
    val DialogOverline = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.1.em,
        color = RadionColors.Amber,
    )

    /** 채널 리스트의 그룹 헤더 */
    val GroupLabel = TextStyle(
        fontFamily = PlexMono,
        fontSize = 10.5.sp,
        letterSpacing = 0.18.em,
        color = RadionColors.Muted,
    )
}
