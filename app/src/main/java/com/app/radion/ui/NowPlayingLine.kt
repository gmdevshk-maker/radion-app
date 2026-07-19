package com.app.radion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.radion.ui.theme.RadionColors
import com.app.radion.ui.theme.RadionType

/** 오른쪽 새로고침 버튼의 지름. 왼쪽에 같은 폭을 비워 글자를 화면 가운데에 맞춘다. */
private val REFRESH_BUTTON_SIZE = 28.dp

/** 방송사가 정보를 주긴 하는데 이번 조회에서 받아온 게 없을 때 대신 띄우는 문구. */
private const val EMPTY_MESSAGE = "수신된 방송 정보가 없습니다."

/**
 * 스테이지 아래 한 줄로 붙는 현재 방송 정보와 수동 새로고침 버튼.
 *
 * 문자열은 [com.app.radion.data.NowPlayingRepository]가 이미 조합해 넘기므로 여기선 그리기만 한다.
 * 폭을 넘치면 [basicMarquee]가 왼쪽으로 흘려 보내고, 짧으면 그냥 가운데 멈춰 있는다.
 *
 * 정보를 주지 않는 방송사의 채널(CBS·TBS·국악방송)에서는 줄 전체가 접힌다. 반대로 정보를 주는
 * 채널이면 호출이 실패해 글자가 비어도 줄은 남긴다 — 그래야 새로고침을 누를 수 있다.
 */
@Composable
fun NowPlayingLine(
    text: String?,
    channelId: String?,
    hasProvider: Boolean,
    loading: Boolean,
    refreshEnabled: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 갱신 중에는 text가 잠깐 null이 되는데, 마지막 문자열을 붙들지 않으면 그때마다 줄이 빈다.
    // 채널이 바뀌면 버려야 한다 — 안 그러면 새 채널 정보를 받는 동안 이전 채널 것이 남아 보인다.
    var lastText by remember(channelId) { mutableStateOf("") }
    if (!text.isNullOrBlank()) lastText = text

    // 아직 받아오는 중이면 비워 둔다. 여기서 EMPTY_MESSAGE를 띄우면 조회가 끝나기도 전에
    // "없습니다"라고 단정했다가 곧바로 실제 정보로 바뀌어 깜빡인다.
    val display = when {
        lastText.isNotEmpty() -> lastText
        loading -> ""
        else -> EMPTY_MESSAGE
    }

    AnimatedVisibility(
        visible = hasProvider,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 오른쪽 버튼과 짝을 맞춰 글자가 화면 정중앙에 오게 하는 빈 자리
            Box(modifier = Modifier.size(REFRESH_BUTTON_SIZE))

            Text(
                text = display,
                style = RadionType.Caption,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(iterations = Int.MAX_VALUE),
            )

            val iconAlpha by animateFloatAsState(
                targetValue = if (refreshEnabled) 1f else 0.35f,
                label = "refreshAlpha",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(REFRESH_BUTTON_SIZE)
                    .clip(CircleShape)
                    .clickable(enabled = refreshEnabled, onClick = onRefresh),
            ) {
                RefreshIcon(
                    color = RadionColors.Muted,
                    modifier = Modifier
                        .size(14.dp)
                        .alpha(iconAlpha),
                )
            }
        }
    }
}
