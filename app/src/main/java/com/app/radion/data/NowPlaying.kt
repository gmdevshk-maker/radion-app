package com.app.radion.data

import java.time.LocalDateTime

/**
 * 화면에 그대로 뿌릴 수 있게 조합이 끝난 방송 정보 한 줄.
 *
 * 방송사마다 주는 항목이 제각각이라(진행자만, 게스트만, 곡만, 아무것도 없음) 필드를 나눠 두면
 * UI가 매번 빈 자리를 따져야 한다. 조합은 [NowPlayingRepository]가 끝내고 UI는 [text]를 그리기만 한다.
 */
data class NowPlaying(
    /** "명연주 명음반 · 정만섭" 처럼 있는 항목만 이어 붙인 문자열. 비어 있지 않음이 보장된다. */
    val text: String,
    /**
     * 다음에 다시 물어볼 시각. 보통은 프로그램이 끝나는 시각이지만,
     * 곡 정보가 붙는 채널(MBC)은 곡이 수시로 바뀌므로 그보다 이른 시각이 온다.
     * 모르면 null — 호출 측이 알아서 주기적으로 갱신한다.
     */
    val refreshAt: LocalDateTime?,
)
