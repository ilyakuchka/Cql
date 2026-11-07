package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/**
 * Аватар с рамкой как на сайте (AvatarFrame.vue): рамка лежит поверх аватара по центру и крупнее его
 * (размеры на сайте 42→50, 80→94, 130→155, то есть ×1.19). gif/анимированный webp играют сами (Coil + coil-gif).
 */
@Composable
fun AvatarWithFrame(avatar: String?, frame: String?, size: Dp, modifier: Modifier = Modifier, avatarVideo: String? = null) {
    val box = if (frame != null) size * 1.19f else size
    Box(modifier.size(box), contentAlignment = Alignment.Center) {
        val avatarModifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        val still: @Composable () -> Unit = { AsyncImage(avatar, null, contentScale = ContentScale.Crop, modifier = avatarModifier) }
        if (avatarVideo != null) LoopVideo(avatarVideo, avatarModifier, fallback = still) else still()
        if (frame != null) AsyncImage(frame, null, contentScale = ContentScale.Fit, modifier = Modifier.size(box))
    }
}
