package id.haonlabs.wakemate.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.haonlabs.wakemate.R

/** Label seksi huruf kapital, opsional dengan keterangan teal di kanan. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    }
}

/**
 * Pilihan tunggal 2–3 opsi dalam satu pil (arah perjalanan, moda). Menggantikan chip yang bisa
 * terpotong di tepi layar: semua pilihan selalu terlihat.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icons: List<Int>? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(container)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            Surface(
                onClick = { onSelect(index) },
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .semantics { role = Role.Tab; this.selected = on },
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icons?.getOrNull(index)?.let {
                        Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(18.dp))
                        Box(Modifier.width(6.dp))
                    }
                    Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Chip berbentuk pil untuk filter dan pilihan ambang; yang terpilih terisi teal. */
@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.heightIn(min = 40.dp).semantics { this.selected = selected },
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipChoices(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> PillChip(label(option), option == selected, onClick = { onSelect(option) }) }
    }
}

/** Tombol bulat 48dp untuk bar atas yang mengambang di atas peta. */
@Composable
fun CircleIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    size: Dp = 48.dp,
) {
    Surface(onClick = onClick, shape = CircleShape, color = container, shadowElevation = 2.dp, modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = contentDescription, tint = tint)
        }
    }
}

/** Ikon di dalam lingkaran berwarna lembut (kartu bus, tujuan terakhir). */
@Composable
fun IconBadge(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier.size(size).background(tint.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size / 2))
    }
}

/** "● ALARM AKTIF" dan sejenisnya. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondary) {
    Row(
        modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** "● 36 bus" — penanda bahwa angka di sebelahnya datang langsung dari feed. */
@Composable
fun LivePill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Bar alarm yang menempel di atas navigasi selama alarm berjalan, di layar mana pun — status
 * jaring pengaman tidak boleh hilang hanya karena user menggulir. Saat berbunyi, bar berubah
 * amber penuh dengan tombol matikan.
 */
@Composable
fun AlarmBar(
    title: String,
    subtitle: String,
    ringing: Boolean,
    onClick: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (ringing) scheme.secondary else scheme.surfaceContainerHighest,
        contentColor = if (ringing) scheme.onSecondary else scheme.onSurface,
        border = if (ringing) null else BorderStroke(1.5.dp, scheme.secondary),
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(if (ringing) scheme.onSecondary else scheme.secondary.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (ringing) R.drawable.ic_alarm else R.drawable.ic_bell),
                    contentDescription = null,
                    tint = scheme.secondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ringing) scheme.onSecondary else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ringing) {
                Surface(onClick = onStop, shape = CircleShape, color = scheme.onSecondary, contentColor = scheme.secondary) {
                    Text("Matikan", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            } else {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Buka", tint = scheme.onSurfaceVariant)
            }
        }
    }
}

enum class TimelineStatus { DEFAULT, DESTINATION, PASSED, BUS_HERE }

/**
 * Satu halte/stasiun di garis waktu rute. Garis memakai warna rute (data GTFS); tujuan disorot
 * amber; bus yang sedang di halte itu digambar langsung di garis.
 */
@Composable
fun TimelineRow(
    name: String,
    status: TimelineStatus,
    lineColor: Color,
    modifier: Modifier = Modifier,
    eta: String? = null,
    detail: String? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val passed = status == TimelineStatus.PASSED
    val destination = status == TimelineStatus.DESTINATION
    val line = if (passed) scheme.outline.copy(alpha = 0.5f) else lineColor
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(32.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(4.dp).weight(1f).background(if (isFirst) Color.Transparent else line))
                    Box(Modifier.width(4.dp).weight(1f).background(if (isLast) Color.Transparent else line))
                }
                when (status) {
                    TimelineStatus.BUS_HERE -> Box(
                        Modifier.size(28.dp).background(lineColor, CircleShape).padding(2.dp).background(lineColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_mode_tj), contentDescription = "Bus di sini", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    TimelineStatus.DESTINATION -> Box(
                        Modifier.size(20.dp).background(scheme.background, CircleShape).padding(3.dp).background(scheme.secondary, CircleShape)
                    )
                    else -> Box(
                        Modifier.size(14.dp).background(line, CircleShape).padding(3.dp).background(scheme.background, CircleShape)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = if (destination) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    color = when {
                        passed -> scheme.outline
                        destination -> scheme.secondary
                        else -> scheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (destination) Text("TURUN DI SINI", style = MaterialTheme.typography.labelMedium, color = scheme.secondary)
            }
            if (eta != null || detail != null) {
                Column(horizontalAlignment = Alignment.End) {
                    eta?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleSmall,
                            color = when {
                                passed -> scheme.outline
                                destination -> scheme.secondary
                                else -> scheme.primary
                            },
                            textAlign = TextAlign.End,
                        )
                    }
                    detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = scheme.outline) }
                }
            }
        }
    }
    val shape = RoundedCornerShape(14.dp)
    val color = if (destination) scheme.secondaryContainer else Color.Transparent
    if (onClick != null) Surface(onClick = onClick, shape = shape, color = color, modifier = modifier.fillMaxWidth()) { content() }
    else Surface(shape = shape, color = color, modifier = modifier.fillMaxWidth()) { content() }
}

/** Kartu bus besar (80dp) — aman diketuk di bus yang bergoyang. */
@Composable
fun BusCard(
    busNo: String,
    info: String,
    distance: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconBadge(R.drawable.ic_mode_tj, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(busNo, style = MaterialTheme.typography.titleMedium)
                Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            distance?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("darimu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/**
 * Kartu hitung mundur untuk perjalanan aktif (live maupun GPS): angka besar yang terbaca sekilas,
 * bar progres, dan titik tempat alarm akan berbunyi.
 */
@Composable
fun CountdownCard(
    eyebrow: String,
    title: String,
    count: String,
    unit: String,
    modifier: Modifier = Modifier,
    segments: Int = 0,
    done: Int = 0,
    wakeAt: Int? = null,
    legend: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = scheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Digeser ke atas saat berganti supaya terbaca sebagai hitungan mundur, bukan kedipan.
                AnimatedContent(
                    targetState = count,
                    transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                    label = "hitungan",
                ) { value -> Text(value, style = MaterialTheme.typography.displayMedium, color = scheme.primary) }
                Text(unit, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            }
            if (segments > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(segments) { i ->
                        val color = when {
                            i < done -> scheme.primary
                            i == wakeAt -> scheme.secondary
                            else -> scheme.surfaceContainerHighest
                        }
                        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(color))
                    }
                }
            }
            legend?.let {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_alarm), contentDescription = null, tint = scheme.secondary, modifier = Modifier.size(16.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.secondary)
                }
            }
        }
    }
}

/** Panel informasi kecil berlatar permukaan (status sinyal, info bus, penjelasan ambang). */
@Composable
fun InfoRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(container).padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
