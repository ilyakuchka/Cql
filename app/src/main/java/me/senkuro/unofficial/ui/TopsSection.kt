package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.TopUser

private const val CREDITS = "credits"
private const val EXPERIENCE = "experience"

private val numberFormat get() = java.text.NumberFormat.getIntegerInstance(java.util.Locale("ru"))

/**
 * «Топ пользователей» как на сайте: вкладки «Пополнение» / «Опыт», подпись, «Ты набрал/пополнил», карточки
 * (место, аватар с рамкой, имя со статусом, тег уровня, гильдия или опыт).
 * Данные грузит HomeViewModel заранее, параллельно главной; вторая вкладка — по требованию и кэшируется.
 */
@Composable
fun TopsSection(vm: HomeViewModel, onTasks: () -> Unit, onUser: (String) -> Unit = {}) {
    val account by AppGraph.account.collectAsState()
    val tab = vm.topsTab
    val data = vm.topsData[tab]
    val experience = tab == EXPERIENCE
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Топ пользователей",
            style = HeadingStyle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SegmentTab("Пополнение", tab == CREDITS) { vm.selectTopsTab(CREDITS) }
                SegmentTab("Опыт", experience) { vm.selectTopsTab(EXPERIENCE) }
            }
            Spacer(Modifier.weight(1f))
            if (account != null && experience) {
                Text(
                    "К заданиям",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onTasks).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Text(
            if (experience) "Больше опыта за 24 часа — выше место." else "Больше купленных этерий — выше место.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (account != null && data != null) {
            Text(
                (if (experience) "Ты набрал · " else "Ты пополнил · ") + numberFormat.format(data.viewerValue),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        when {
            data != null -> LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(data.users, key = { _, u -> u.id }) { index, user ->
                    TopCard(rank = index + 1, user = user, experience = experience, onClick = { user.slug?.let(onUser) })
                }
            }
            tab in vm.topsFailed -> Text(
                "Не удалось загрузить · Повторить",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clickable { vm.loadTops(tab, force = true) },
            )
            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = 6, key = { "ph-$it" }) {
                    Box(Modifier.width(128.dp).height(152.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
    }
}

@Composable
private fun SegmentTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun TopCard(rank: Int, user: TopUser, experience: Boolean, onClick: () -> Unit = {}) {
    val accent: Color? = when (rank) {
        1 -> Color(0xFFFFD043)
        2 -> Color(0xFF5177FF)
        3 -> Color(0xFFF1F1F1).copy(alpha = 0.8f)
        else -> null
    }
    Box(
        Modifier
            .width(128.dp)
            .height(152.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .then(
                if (accent != null) Modifier.background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.5f), accent.copy(alpha = 0.05f), accent.copy(alpha = 0.5f))))
                else Modifier,
            )
            .padding(1.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = user.avatar,
                    contentDescription = user.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                )
                if (user.frame != null) {
                    AsyncImage(model = user.frame, contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        user.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (user.status != null) AsyncImage(model = user.status, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                if (user.level != null) LevelTag(user.level)
                if (experience) {
                    Text(
                        (user.experience?.let { numberFormat.format(it) } ?: "0") + " XP",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!user.guild.isNullOrBlank()) {
                    Text(
                        user.guild,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = parseColor(user.guildColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            rank.toString(),
            color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp),
        )
    }
}

private fun parseColor(value: String?): Color? =
    value?.takeIf { it.startsWith("#") }?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/** Цвет тега уровня по ступеням из CSS сайта (lvl__tag--N): шаг 5 уровней, с 65 — градиенты. */
private fun tierBrush(level: Int): Brush {
    val tier = (level / 5) * 5
    fun solid(hex: Long) = Brush.linearGradient(listOf(Color(hex), Color(hex)))
    fun grad(a: Long, b: Long) = Brush.linearGradient(listOf(Color(a), Color(b)))
    return when {
        tier <= 0 -> solid(0xFF6B7280)
        tier == 5 -> solid(0xFF30BA2F)
        tier == 10 -> solid(0xFF5177FF)
        tier == 15 -> solid(0xFFFFB300)
        tier == 20 -> solid(0xFFFF4848)
        tier == 25 -> solid(0xFFB751FF)
        tier == 30 -> solid(0xFFFF81E4)
        tier == 35 -> solid(0xFF33A3FF)
        tier == 40 -> solid(0xFF7751FF)
        tier == 45 -> solid(0xFFF69F42)
        tier == 50 -> solid(0xFF30BA2F)
        tier == 55 -> solid(0xFF5177FF)
        tier == 60 -> solid(0xFFFFB300)
        tier == 65 -> grad(0xFFFF4848, 0xFF33A3FF)
        tier == 70 -> grad(0xFFB751FF, 0xFF7751FF)
        else -> grad(0xFFFF81E4, 0xFFF69F42)
    }
}

@Composable
private fun LevelTag(level: Int) {
    Row(
        Modifier.height(20.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(tierBrush(level)), contentAlignment = Alignment.Center) {
            Text(level.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text("Ур.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, end = 6.dp))
    }
}
