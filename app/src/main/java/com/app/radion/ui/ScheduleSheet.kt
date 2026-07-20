package com.app.radion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.radion.data.ScheduleItem
import com.app.radion.data.freqText
import com.app.radion.data.minutesOfDay
import com.app.radion.ui.theme.RadionColors
import com.app.radion.ui.theme.RadionType

/**
 * 오늘 하루 편성표 시트.
 *
 * 방송 정보 한 줄을 눌러 연다 — "지금 뭐 하지"를 보고 있던 자리에서 "이따 뭐 하지"로 이어지는 흐름이라
 * 별도 화면을 만들지 않았다. 지금 방송 중인 편성은 앰버로 표시하고 그 자리로 스크롤해 둔다.
 *
 * **머티리얼 [androidx.compose.material3.ModalBottomSheet]를 쓰지 않고 직접 그린다.**
 * 그쪽은 끌어서 닫는 게 본체라, 목록을 훑다 맨 위에서 한 번 더 내리면 시트째 따라 내려간다.
 * 이 버전에는 그 제스처를 끄는 옵션(`sheetGesturesEnabled`)이 없어서 중첩 스크롤과 포인터를
 * 가로채 막아 봤지만 길게 눌러 끄는 경로가 남았다. 여기서는 끌기를 붙이지 않으므로 경로 자체가 없다 —
 * 닫는 길은 닫기 버튼·뒤로가기·바깥(스크림) 탭 셋뿐이다.
 */
@Composable
fun ScheduleSheet(
    state: ScheduleSheetState?,
    onDismiss: () -> Unit,
) {
    if (state == null) return

    BackHandler(onBack = onDismiss)

    // 첫 컴포지션에서 한 번 올라오게 한다. 닫을 때는 상태가 null이 되며 바로 사라지므로
    // 나가는 애니메이션은 돌지 않는다 — 열 때만 시트답게 보이면 된다
    val entering = remember { MutableTransitionState(false) }
    entering.targetState = true

    Box(modifier = Modifier.fillMaxSize()) {
        // 스크림. 뒤 화면을 눌러 닫는 유일한 통로이자, 시트 밖 탭이 뒤 화면으로 새지 않게 막는 뚜껑
        AnimatedVisibility(visibleState = entering, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RadionColors.Bg.copy(alpha = 0.7f))
                    .clickable(
                        // 스크림에 물결(리플)이 번지면 시트가 아니라 뒤 화면을 누른 것처럼 보인다
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
            )
        }

        // 편성이 많은 채널(KBS 1라디오는 84건)에서는 시트가 화면 꼭대기까지 차오른다.
        // 상태바만큼 남겨 두지 않으면 헤더가 시계와 겹쳐 읽히지 않는다
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            AnimatedVisibility(
                visibleState = entering,
                enter = slideInVertically { height -> height },
                exit = slideOutVertically { height -> height },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(RadionColors.Surface)
                        // 시트의 빈 곳(헤더·닫기 줄 여백)을 눌러도 아래 스크림이 받아 닫히지 않게
                        // 탭만 여기서 끝낸다. **이동은 절대 소비하지 말 것** — 소비하면 목록의 드래그가
                        // 취소돼 스크롤이 아예 안 된다(실제로 겪은 함정)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .navigationBarsPadding(),
                ) {
                    SheetHeader(channelName = state.channel.name, freq = state.channel.freqText)

                    HorizontalDivider(thickness = 1.dp, color = RadionColors.Line)

                    when {
                        state.loading -> SheetMessage("편성표를 받아오는 중…")
                        state.items.isEmpty() -> SheetMessage("편성표를 받지 못했습니다.")
                        // 목록이 짧으면 시트도 짧아야 하므로 fill = false — 남는 높이를 억지로 채우지 않는다
                        else -> ScheduleList(
                            items = state.items,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }

                    CloseButton(onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * 목록 아래 고정된 닫기 버튼.
 *
 * 스크롤을 끝까지 내려야 나오면 84건짜리 채널(KBS 1라디오)에서는 닫으려고 한참 내려야 한다 —
 * 목록 안이 아니라 시트 바닥에 붙여 둔다.
 */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = "닫기",
            style = RadionType.Button.copy(color = RadionColors.Muted),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .border(1.dp, RadionColors.Line, RoundedCornerShape(999.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 11.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SheetHeader(channelName: String, freq: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(text = "오늘 편성표", style = RadionType.DialogSubtitle)
        Text(
            text = "$channelName · $freq",
            style = RadionType.Caption,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SheetMessage(message: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
    ) {
        Text(text = message, style = RadionType.BodyMuted)
    }
}

@Composable
private fun ScheduleList(items: List<ScheduleItem>, modifier: Modifier = Modifier) {
    // 지금 방송 중인 줄로 열어 준다 — 새벽 편성부터 훑어 내려오게 하면 매번 스크롤해야 한다.
    val nowMinutes = minutesOfDay()
    val onAirIndex = items.indexOfFirst { it.isOnAir(nowMinutes) }
    val listState = rememberLazyListState()
    LaunchedEffect(items) {
        if (onAirIndex > 0) listState.scrollToItem(onAirIndex)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        // 같은 시각·제목이 두 번 오는 편성이 실제로 있어(MBC) 키는 순번으로 잡는다 —
        // 내용으로 만들면 키가 겹쳐 크래시한다
        itemsIndexed(items = items) { index, item ->
            // 시각 열 아래는 비워 두고 제목 열부터 선을 그어야 시각이 한 덩어리로 읽힌다
            if (index > 0) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = RadionColors.Line,
                    modifier = Modifier.padding(start = 66.dp, end = 20.dp),
                )
            }
            ScheduleRow(item = item, isOnAir = item.isOnAir(nowMinutes))
        }
    }
}

@Composable
private fun ScheduleRow(item: ScheduleItem, isOnAir: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = item.timeText,
            style = RadionType.MonoXs.copy(
                color = if (isOnAir) RadionColors.Amber else RadionColors.Muted,
            ),
            modifier = Modifier
                .width(46.dp)
                .padding(top = 2.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = RadionType.ChannelName.copy(
                    color = if (isOnAir) RadionColors.Amber else RadionColors.Text,
                ),
            )
            item.host?.let { host ->
                Text(
                    text = host,
                    style = RadionType.Caption,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (isOnAir) {
            Text(
                text = "ON AIR",
                style = RadionType.Badge,
                modifier = Modifier
                    .padding(start = 8.dp, top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, RadionColors.AmberDim, RoundedCornerShape(4.dp))
                    .background(RadionColors.Surface2)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}
