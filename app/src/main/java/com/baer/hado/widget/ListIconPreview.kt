package com.baer.hado.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ListIconPreview(
    resolvedIcon: ListIconManager.ResolvedIcon?,
    size: Dp,
    modifier: Modifier = Modifier,
    emojiColor: Color = LocalContentColor.current,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    fallbackEmoji: String? = null
) {
    when (resolvedIcon?.type) {
        ListIconManager.IconType.IMAGE -> {
            val bitmap = remember(resolvedIcon.value) { ListIconManager.loadBitmap(resolvedIcon.value) }
            if (bitmap != null) {
                Surface(
                    modifier = modifier.size(size),
                    shape = CircleShape,
                    color = backgroundColor
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                EmojiIcon(
                    emoji = fallbackEmoji,
                    size = size,
                    modifier = modifier,
                    emojiColor = emojiColor
                )
            }
        }
        ListIconManager.IconType.MDI -> {
            MdiIcon(
                mdiIcon = resolvedIcon.value,
                fallbackEmoji = resolvedIcon.fallbackEmoji ?: fallbackEmoji,
                size = size,
                modifier = modifier,
                tint = emojiColor
            )
        }
        ListIconManager.IconType.EMOJI -> {
            EmojiIcon(
                emoji = resolvedIcon.value,
                size = size,
                modifier = modifier,
                emojiColor = emojiColor
            )
        }
        null -> {
            EmojiIcon(
                emoji = fallbackEmoji,
                size = size,
                modifier = modifier,
                emojiColor = emojiColor
            )
        }
    }
}

@Composable
private fun MdiIcon(
    mdiIcon: String,
    fallbackEmoji: String?,
    size: Dp,
    modifier: Modifier,
    tint: Color
) {
    val context = LocalContext.current
    val sizePx = remember(size) { size.value.toInt().coerceAtLeast(1) }
    val bitmap = remember(mdiIcon, sizePx, tint) {
        ListIconManager.renderMdiBitmap(
            context = context,
            mdiIcon = mdiIcon,
            sizePx = (sizePx * context.resources.displayMetrics.density).toInt().coerceAtLeast(1),
            tintColor = tint.toArgb()
        )
    }

    if (bitmap != null) {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        EmojiIcon(
            emoji = fallbackEmoji,
            size = size,
            modifier = modifier,
            emojiColor = tint
        )
    }
}

@Composable
private fun EmojiIcon(
    emoji: String?,
    size: Dp,
    modifier: Modifier,
    emojiColor: Color
) {
    if (emoji == null) return

    val fontSize = (size.value * 0.72f).sp
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            color = emojiColor,
            fontSize = fontSize,
            lineHeight = fontSize,
            textAlign = TextAlign.Center
        )
    }
}