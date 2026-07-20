package com.app.radion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.app.radion.ui.theme.RadionColors
import com.app.radion.ui.theme.RadionType
import kotlinx.coroutines.delay

/** 오른쪽 새로고침 버튼의 지름. 왼쪽에 같은 폭을 비워 글자를 화면 가운데에 맞춘다. */
private val REFRESH_BUTTON_SIZE = 28.dp

/** 방송사가 정보를 주긴 하는데 이번 조회에서 받아온 게 없을 때 대신 띄우는 문구. */
private const val EMPTY_MESSAGE = "수신된 방송 정보가 없습니다."

/** 글자가 들어와 자리를 잡고 자동으로 한 번 흐르기 시작하기까지의 뜸. 앞부분을 읽을 틈을 준다. */
private const val AUTO_SCROLL_DELAY_MS = 1_200L

/** 자동으로 흐를 때의 속도. basicMarquee 기본값과 같게 맞춰 체감이 달라지지 않게 했다. */
private const val AUTO_SCROLL_DP_PER_SEC = 30f

/** 끝까지 흐른 뒤 맨 앞으로 되감기 전에 두는 뜸. 마지막 조각을 읽을 틈을 준다. */
private const val AUTO_SCROLL_END_HOLD_MS = 1_500L

/**
 * 스테이지 아래 한 줄로 붙는 현재 방송 정보와 양옆 버튼(왼쪽 편성표, 오른쪽 새로고침).
 *
 * 문자열은 [com.app.radion.data.NowPlayingRepository]가 이미 조합해 넘기므로 여기선 그리기만 한다.
 * 폭을 넘치면 **한 번만** 왼쪽으로 흘려 끝까지 보여 준 뒤 멈추고, 그 뒤로는 손으로 좌우로 밀어
 * 다시 볼 수 있다. 짧으면 그냥 가운데 멈춰 있는다.
 *
 * 예전엔 `basicMarquee(iterations = Int.MAX_VALUE)`로 끝없이 흘렸는데, 애니메이션이 끝나지 않아
 * 화면이 켜져 있는 내내 매 프레임 다시 그렸다. 실측(S24 Ultra 120Hz, 재생 정지 상태)으로
 * **CPU 74~78% · 약 113fps가 계속** 나왔고 — 오디오만 재생할 때가 5~6%다 — 멈추게 하자 0%로
 * 떨어졌다. `basicMarquee`는 완료 시점을 알려 주지 않고 드래그도 못 받아 [horizontalScroll]로 바꿨다.
 * `Modifier.preferredFrameRate(30f)`로 프레임을 낮춰 보려 했으나 주사율 협상 힌트일 뿐이라 효과가 없었다.
 *
 * 정보를 주지 않는 방송사의 채널(CBS·TBS·국악방송)에서는 줄 전체가 접힌다. 반대로 정보를 주는
 * 채널이면 호출이 실패해 글자가 비어도 줄은 남긴다 — 그래야 새로고침을 누를 수 있다.
 *
 * 편성표까지 주는 채널(KBS·MBC·SBS)에서는 글자를 눌러도 시트가 열린다 — 지금 방송을 보다가
 * 다음 편성이 궁금해지는 흐름이라 왼쪽 아이콘을 정확히 겨냥하게 만들 이유가 없다.
 */
@Composable
fun NowPlayingLine(
    text: String?,
    channelId: String?,
    hasProvider: Boolean,
    hasSchedule: Boolean,
    loading: Boolean,
    refreshEnabled: Boolean,
    onRefresh: () -> Unit,
    onOpenSchedule: () -> Unit,
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
            // 편성표가 없는 채널에서는 빈 자리로 남는다 — 오른쪽 새로고침 버튼과 폭을 맞춰야
            // 글자가 화면 정중앙에 온다
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(REFRESH_BUTTON_SIZE)
                    .clip(CircleShape)
                    .clickable(enabled = hasSchedule, onClick = onOpenSchedule),
            ) {
                if (hasSchedule) {
                    ScheduleIcon(color = RadionColors.Muted, modifier = Modifier.size(14.dp))
                }
            }

            // 글자가 바뀌면 처음으로 되감고 다시 한 번만 흘린다. 폭 안에 들어오는 짧은 글자는
            // maxValue가 0이라 애니메이션 자체가 시작되지 않는다 — 그때는 프레임을 한 장도 안 그린다.
            val scrollState = rememberScrollState()
            val density = LocalDensity.current
            LaunchedEffect(display, channelId) {
                scrollState.scrollTo(0)
                delay(AUTO_SCROLL_DELAY_MS)
                val distance = scrollState.maxValue
                if (distance > 0) {
                    val durationMs = distance / density.density / AUTO_SCROLL_DP_PER_SEC * 1000f
                    scrollState.animateScrollTo(
                        distance,
                        tween(durationMillis = durationMs.toInt(), easing = LinearEasing),
                    )
                    // 끝에 세워 두면 다음에 볼 때 뒤꽁무니만 남아 무슨 방송인지 알 수 없다.
                    // 되감기는 애니메이션 없이 한 프레임에 끝낸다 — 돌아가는 모습까지 보여 줄 이유가 없다.
                    //
                    // 흐르는 중에 손을 대면 드래그가 애니메이션을 밀어내며 이 코루틴째 취소되지만,
                    // 멈춰 있는 동안 민 것은 delay가 막지 못한다. 그새 위치가 바뀌었으면 그대로 둔다 —
                    // 사용자가 맞춰 놓은 자리를 도로 뺏지 않으려는 것.
                    val settled = scrollState.value
                    delay(AUTO_SCROLL_END_HOLD_MS)
                    if (scrollState.value == settled && !scrollState.isScrollInProgress) {
                        scrollState.scrollTo(0)
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = hasSchedule, onClick = onOpenSchedule)
                    .horizontalScroll(scrollState),
            ) {
                Text(
                    text = display,
                    style = RadionType.Caption,
                    maxLines = 1,
                    softWrap = false,
                )
            }

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
