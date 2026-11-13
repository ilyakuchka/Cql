package me.senkuro.unofficial.ui

import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.senkuro.unofficial.data.AppGraph

/**
 * Режим стримера: размывает личные данные того, кто вошёл в приложение (ник, аватар, уровень, очки, кредиты).
 * Данные других пользователей не трогаются.
 *
 * Действует, только если в настройках включён переключатель и пользователь авторизован
 * (см. [AppGraph.streamerActive]); от темы не зависит.
 */
@Composable
fun streamerActive(): Boolean = AppGraph.streamerActive.collectAsState().value

/** Это данные текущего пользователя? Сравнение по slug или по id аккаунта. */
@Composable
fun isMe(slug: String? = null, userId: String? = null): Boolean {
    val account by AppGraph.account.collectAsState()
    val a = account ?: return false
    return (!slug.isNullOrBlank() && slug == a.slug) || (!userId.isNullOrBlank() && userId == a.userId)
}

/**
 * Размывает содержимое, когда включён режим стримера и [hide] = true (по умолчанию — всегда).
 * [radius] подбирайте под размер: для текста хватает 8–10 dp, для аватара — примерно треть его стороны.
 *
 * До Android 12 (API 31) системного размытия в Compose нет, поэтому там содержимое не рисуется вовсе, а на его месте
 * показывается полупрозрачная заглушка: личные данные не просвечивают ни на одной версии системы.
 */
@Composable
fun Modifier.streamerBlur(hide: Boolean = true, radius: Dp = 10.dp): Modifier {
    if (!hide || !streamerActive()) return this
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return blur(radius, BlurredEdgeTreatment.Unbounded)
    val mask = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    return drawWithContent {
        drawRoundRect(mask, cornerRadius = CornerRadius(size.minDimension / 2f))
    }
}

/** Подпись из нескольких частей, где размывается только значение: «Эстерии ▒▒▒ · Коины ▒▒▒». */
@Composable
fun StreamerStat(
    label: String,
    value: String,
    hide: Boolean,
    style: TextStyle = TextStyle.Default,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style, color = color, fontSize = fontSize, maxLines = 1)
        Text(value, style = style, color = color, fontSize = fontSize, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.streamerBlur(hide, 6.dp))
    }
}
