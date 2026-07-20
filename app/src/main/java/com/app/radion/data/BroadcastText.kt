package com.app.radion.data

/**
 * 방송사가 준 제목·진행자·곡 문자열을 화면에 올릴 모양으로 다듬는다.
 *
 * 방송 정보 한 줄([NowPlayingRepository])과 편성표([ScheduleRepository])가 같은 규칙을 써야
 * 두 화면에서 같은 편성이 다르게 보이지 않는다.
 */

/** 방송사 입력값에 연속 공백이나 탭이 섞여 오는 일이 잦다("비처럼  음악처럼", "1~4부 \t with …"). */
private val WHITESPACE = Regex("\\s+")

internal fun String.tidy(): String = replace(WHITESPACE, " ").trim()

/**
 * 있는 항목만 " · "로 잇는다.
 *
 * 방송사가 주지 않는 항목(진행자 없음, 게스트 없음, 곡 없음)은 자연히 빠지고,
 * 앞 조각에 이미 들어 있는 말도 반복하지 않는다 — "가비의 슈퍼라디오"의 진행자가 "가비"인 것처럼
 * 제목에 이름이 그대로 든 편성이 흔해서, 그대로 이으면 "가비의 슈퍼라디오 · 가비"가 된다.
 */
internal fun joinParts(vararg parts: String?): String {
    val kept = mutableListOf<String>()
    for (part in parts) {
        val value = part?.tidy()?.takeIf { it.isNotEmpty() } ?: continue
        kept += freshNames(value, kept).takeIf { it.isNotEmpty() }?.joinToString(", ") ?: continue
    }
    return kept.joinToString(" · ")
}

/**
 * 제목에 이미 든 이름을 뺀 진행자만 남긴다. 전부 겹치면 null.
 * 편성표는 제목과 진행자를 다른 서체로 따로 그리므로 [joinParts]처럼 잇지 않고 진행자만 돌려준다.
 */
internal fun hostApartFrom(title: String?, host: String?): String? {
    val value = host?.tidy()?.takeIf { it.isNotEmpty() } ?: return null
    val base = listOfNotNull(title?.tidy())
    return freshNames(value, base).takeIf { it.isNotEmpty() }?.joinToString(", ")
}

/**
 * 쉼표로 나열된 이름은 하나씩 따져야 한다 — "이은선의 영화관, 정여울의 도서관"의 진행자가
 * "이은선, 정여울"이면 통째로는 안 걸리지만 이름은 이미 제목에 다 들어 있다.
 */
private fun freshNames(value: String, alreadySaid: List<String>): List<String> =
    value.split(",")
        .map { it.trim() }
        .filter { item -> item.isNotEmpty() && alreadySaid.none { it.contains(item) } }
