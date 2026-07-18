package com.app.radion.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * 방송사 API·스트림 서버가 요구하는 브라우저 User-Agent.
 * ExoPlayer의 DataSource도 같은 값을 써야 일부 스트림이 403을 내지 않는다.
 */
internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/**
 * GET 요청을 보내고 연결을 [block]에 넘긴다. 2xx가 아니면 예외를 던지고,
 * 끝나면 항상 연결을 끊는다. 응답 본문만 필요하면 [httpGetText]를 쓸 것.
 */
internal fun <T> httpGet(
    url: String,
    referer: String? = null,
    userAgent: String = BROWSER_USER_AGENT,
    connectTimeoutMs: Int = 10_000,
    readTimeoutMs: Int = 10_000,
    block: (HttpURLConnection) -> T,
): T {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", userAgent)
        referer?.let { setRequestProperty("Referer", it) }
    }
    return try {
        check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}: $url" }
        block(conn)
    } finally {
        conn.disconnect()
    }
}

/** GET 응답 본문을 문자열로 읽는다. */
internal fun httpGetText(
    url: String,
    referer: String? = null,
    userAgent: String = BROWSER_USER_AGENT,
): String = httpGet(url, referer, userAgent) { conn ->
    conn.inputStream.bufferedReader().use { it.readText() }
}
