package com.app.radion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.radion.data.Channel
import com.app.radion.data.ChannelType
import com.app.radion.data.freqText
import com.app.radion.ui.theme.RadionColors
import com.app.radion.ui.theme.RadionType

private const val OTHER_GROUP = "기타"

/** 그룹 헤더 + 채널 행 리스트. 목업의 .list 재현. */
@Composable
fun ChannelList(
    channels: List<Channel>,
    currentChannelId: String?,
    isPlaying: Boolean,
    favorites: Set<String>,
    contentPadding: PaddingValues,
    onSelect: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 즐겨찾기 그룹은 별표된 채널이 있을 때만 맨 위에 노출 (원래 그룹에도 그대로 유지)
    // 그룹 안에서는 주파수 오름차순 — channels.json 순서와 무관하게 정렬한다
    val favoriteChannels = remember(channels, favorites) {
        channels.filter { it.id in favorites }.sortedBy { it.freq }
    }
    // 그룹 순서는 channels.json 순서를 따르되, "기타"는 항상 마지막
    val groups = remember(channels) {
        channels.map { it.group }.distinct().sortedBy { if (it == OTHER_GROUP) 1 else 0 }
    }
    val channelsByGroup = remember(channels) {
        channels.groupBy { it.group }.mapValues { (_, list) -> list.sortedBy { it.freq } }
    }

    // 그룹이 위에 삽입되면 LazyColumn이 스크롤을 고정해 새 그룹이 화면 밖으로 밀려난다.
    // 즐겨찾기 그룹이 처음 생길 때는 맨 위로 올려 사용자가 바로 볼 수 있게 한다.
    val listState = rememberLazyListState()
    val hasFavorites = favoriteChannels.isNotEmpty()
    LaunchedEffect(hasFavorites) {
        if (hasFavorites) listState.animateScrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        if (favoriteChannels.isNotEmpty()) {
            item(key = "group-favorites") {
                GroupLabel("즐겨찾기", topPadding = 6.dp, modifier = Modifier.animateItem())
            }
            // 같은 채널이 즐겨찾기와 원래 그룹에 모두 나오므로 키가 겹치지 않게 접두사를 붙인다
            items(favoriteChannels, key = { "fav-${it.id}" }) { channel ->
                ChannelRow(
                    channel = channel,
                    isCurrent = channel.id == currentChannelId,
                    isPlaying = isPlaying && channel.id == currentChannelId,
                    isFavorite = true,
                    onClick = { onSelect(channel) },
                    onToggleFavorite = { onToggleFavorite(channel) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        groups.forEachIndexed { groupIndex, group ->
            val isFirst = groupIndex == 0 && favoriteChannels.isEmpty()
            item(key = "group-$group") {
                GroupLabel(
                    name = group,
                    topPadding = if (isFirst) 6.dp else 18.dp,
                    modifier = Modifier.animateItem(),
                )
            }
            items(channelsByGroup[group].orEmpty(), key = { "$group-${it.id}" }) { channel ->
                ChannelRow(
                    channel = channel,
                    isCurrent = channel.id == currentChannelId,
                    isPlaying = isPlaying && channel.id == currentChannelId,
                    isFavorite = channel.id in favorites,
                    onClick = { onSelect(channel) },
                    onToggleFavorite = { onToggleFavorite(channel) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(
    name: String,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = topPadding, bottom = 8.dp),
    ) {
        Text(text = name, style = RadionType.GroupLabel)
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            thickness = 1.dp,
            color = RadionColors.Line,
        )
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .then(
            if (isCurrent) {
                Modifier
                    .background(RadionColors.Surface)
                    .border(1.dp, RadionColors.Line, RoundedCornerShape(14.dp))
            } else {
                Modifier
            },
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 13.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = rowModifier,
    ) {
        Text(
            text = channel.freqText,
            style = RadionType.FreqRow.copy(
                color = if (isCurrent) RadionColors.Amber else RadionColors.Muted,
                textAlign = TextAlign.Right,
            ),
            modifier = Modifier.width(52.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = channel.name,
                    style = RadionType.ChannelName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (channel.type == ChannelType.VIDEO) {
                    Text(
                        text = "보이는",
                        style = RadionType.Badge,
                        modifier = Modifier
                            .border(1.dp, RadionColors.AmberDim, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp),
                    )
                }
            }
            Text(
                text = channel.desc,
                style = RadionType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (isCurrent) {
            // 헤더의 취침 타이머 칩과 동일한 알약형 스타일
            Text(
                text = "청취중",
                style = RadionType.Caption.copy(
                    color = if (isPlaying) RadionColors.Amber else RadionColors.Muted,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(RadionColors.Surface)
                    .border(
                        1.dp,
                        if (isPlaying) RadionColors.AmberDim else RadionColors.Line,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center,
        ) {
            StarIcon(
                filled = isFavorite,
                color = if (isFavorite) RadionColors.Amber else RadionColors.StarOff,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
