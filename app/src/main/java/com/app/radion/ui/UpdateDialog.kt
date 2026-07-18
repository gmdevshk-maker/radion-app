package com.app.radion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.radion.ui.theme.RadionColors
import com.app.radion.ui.theme.RadionType

/**
 * 업데이트 안내 다이얼로그.
 * [UpdateState.Available]이면 버전 안내 + 버튼, [UpdateState.Downloading]이면 진행률을 보여준다.
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val available = state as? UpdateState.Available
    val downloading = state as? UpdateState.Downloading
    if (available == null && downloading == null) return

    Dialog(
        onDismissRequest = { if (downloading == null) onDismiss() },
        properties = DialogProperties(
            dismissOnClickOutside = downloading == null,
            dismissOnBackPress = downloading == null,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(RadionColors.Surface)
                .border(1.dp, RadionColors.Line, RoundedCornerShape(18.dp))
                .padding(22.dp),
        ) {
            Text(text = "RADION UPDATE", style = RadionType.DialogOverline)
            Spacer(Modifier.height(10.dp))

            if (available != null) {
                Text(text = "새 버전이 있습니다", style = RadionType.DialogTitle)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "현재 $currentVersion  →  최신 ${available.info.version}",
                    style = RadionType.MonoMd,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogButton(text = "나중에", filled = false, onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    DialogButton(text = "업데이트", filled = true, onClick = onUpdate)
                }
            } else if (downloading != null) {
                Text(text = "다운로드 중…", style = RadionType.DialogSubtitle)
                Spacer(Modifier.height(14.dp))
                ProgressBar(progress = downloading.progress)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${(downloading.progress * 100).toInt()}%",
                    style = RadionType.MonoSm,
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(RadionColors.Surface2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(RadionColors.Amber),
        )
    }
}

@Composable
private fun DialogButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = RadionType.Button.copy(
            color = if (filled) RadionColors.PlayIconDark else RadionColors.Muted,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) {
                    Modifier.background(RadionColors.Amber)
                } else {
                    Modifier.border(1.dp, RadionColors.Line, RoundedCornerShape(999.dp))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}
