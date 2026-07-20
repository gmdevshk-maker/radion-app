package com.app.radion.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 업데이트 서버 응답. 미등록 패키지면 `{"error":"..."}`가 오는데,
 * 그때는 필수 필드가 없어 디코딩이 실패하고 [UpdateRepository.checkForUpdate]가 null을 돌려준다.
 */
@Serializable
data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val fileName: String? = null,
) {
    /** 서버가 파일명을 주지 않으면 버전으로 만들어 쓴다. */
    val downloadFileName: String get() = fileName ?: "RadiOn_$version.apk"
}

/**
 * 개인 배포(비 Play스토어)용 인앱 업데이트.
 * `api/version?package=<pkg>`로 최신 버전을 조회하고, 현재 버전보다 높으면 `apkUrl`에서 APK를 내려받는다.
 */
class UpdateRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 서버 버전이 현재 설치 버전보다 높으면 [UpdateInfo], 아니면 null.
     * 네트워크 오류·미등록 패키지 등은 조용히 null 처리(업데이트 확인 실패로 앱을 방해하지 않음).
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val body = httpGetText("$VERSION_API?package=${context.packageName}", userAgent = UPDATER_USER_AGENT)
            val info = json.decodeFromString<UpdateInfo>(body)
            if (isNewer(info.version, currentVersionName())) info else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * APK를 캐시(`cacheDir/updates`)에 내려받아 File을 반환한다.
     * [onProgress]는 0f..1f (전체 크기를 모르면 -1f).
     */
    suspend fun downloadApk(info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // 이전 다운로드 정리
            val out = File(dir, info.downloadFileName)

            httpGet(
                url = info.apkUrl,
                userAgent = UPDATER_USER_AGENT,
                connectTimeoutMs = 15_000,
                readTimeoutMs = 30_000,
            ) { conn ->
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            onProgress(if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else -1f)
                        }
                    }
                }
            }
            out
        }

    fun currentVersionName(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    companion object {
        private const val VERSION_API = "https://apk-update-server-gamma.vercel.app/api/version"
        private const val UPDATER_USER_AGENT = "RadiOn-Updater"

        /** "1.0.10" > "1.0.9" 처럼 점 단위 세그먼트를 숫자로 비교. */
        private fun isNewer(remote: String, current: String): Boolean {
            val r = remote.split(".").map { it.trim().toIntOrNull() ?: 0 }
            val c = current.split(".").map { it.trim().toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(r.size, c.size)) {
                val rv = r.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (rv != cv) return rv > cv
            }
            return false
        }
    }
}
