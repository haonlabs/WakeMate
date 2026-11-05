package id.haonlabs.wakemate.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.ui.theme.Motion
import id.haonlabs.wakemate.ui.theme.SignalRed
import id.haonlabs.wakemate.ui.theme.Wake
import id.haonlabs.wakemate.ui.theme.hardShadow
import id.haonlabs.wakemate.ui.theme.pulsing
import id.haonlabs.wakemate.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

private val BorderWidth = 2.5.dp

/** Garis tepi tebal gaya poster; warnanya tinta (terang) / krem (gelap). */
@Composable
fun posterBorder(width: Dp = BorderWidth) = BorderStroke(width, Wake.colors.border)

/** Bayangan keras dengan warna border; gambar di belakang elemen. */
@Composable
fun Modifier.posterShadow(shape: Shape, offset: Dp = 4.dp): Modifier = hardShadow(shape, Wake.colors.shadow, offset)

/** Label seksi huruf kapital, opsional dengan keterangan di kanan. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface) }
    }
}

/**
 * Pilihan tunggal 2–3 opsi dalam satu pil (arah perjalanan, moda). Semua pilihan selalu terlihat;
 * warna segmen terpilih berpindah dengan halus.
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
            .border2(CircleShape, Wake.colors.border)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            val fill by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, label = "segmen")
            val content by animateColorAsState(
                if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                label = "segmen teks",
            )
            Surface(
                onClick = { onSelect(index) },
                shape = CircleShape,
                color = fill,
                contentColor = content,
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
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Chip pil untuk filter dan pilihan ambang; yang terpilih terisi "inverse" dan memantul sekali. */
@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainer, label = "chip")
    val content by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, label = "chip teks")
    val scale by animateFloatAsState(if (selected) 1f else 0.97f, Motion.Pop, label = "chip skala")
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = fill,
        contentColor = content,
        border = posterBorder(),
        modifier = modifier
            .heightIn(min = 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { this.selected = selected },
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge)
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

/** Tombol bulat 48dp untuk bar atas, termasuk yang mengambang di atas peta. */
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
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        border = posterBorder(),
        modifier = modifier.size(size).posterShadow(CircleShape, 3.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = contentDescription, tint = tint)
        }
    }
}

/** Ikon di dalam blok berwarna (kartu bus, tujuan terakhir). [tint] = warna blok; ikon selalu tinta. */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    val shape = RoundedCornerShape(size / 3)
    Box(
        modifier.size(size).background(tint, shape).border2(shape, Wake.colors.border),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Wake.colors.onColor, modifier = Modifier.size(size / 2))
    }
}

/** "● ALARM AKTIF" dan sejenisnya: pil terisi, titiknya bernapas. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondary) {
    Row(
        modifier.clip(CircleShape).background(color).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).pulsing(amount = 0.5f, millis = 900).background(Wake.colors.onColor, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Wake.colors.onColor)
    }
}

/** "● 36 bus" — penanda bahwa angka di sebelahnya datang langsung dari feed; titik berdenyut. */
@Composable
fun LivePill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = Wake.colors.live,
    content: Color = Wake.colors.onColor,
) {
    Row(
        modifier.clip(CircleShape).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).pulsing(amount = 0.6f, millis = 1000).background(content, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp), color = content)
    }
}

/**
 * Bar alarm yang menempel di atas navigasi selama alarm berjalan, di layar mana pun — status
 * jaring pengaman tidak boleh hilang hanya karena user menggulir. Saat berbunyi, muncul tombol
 * matikan dan ikonnya berdenyut.
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
    val shape = RoundedCornerShape(22.dp)
    val onColor = Wake.colors.onColor
    Surface(
        onClick = onClick,
        shape = shape,
        color = scheme.secondary,
        contentColor = onColor,
        border = posterBorder(),
        modifier = modifier.fillMaxWidth().posterShadow(shape).animateContentSize(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).then(if (ringing) Modifier.pulsing(0.12f, 500) else Modifier).background(onColor, CircleShape),
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
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (ringing) {
                Surface(onClick = onStop, shape = CircleShape, color = onColor, contentColor = scheme.secondary) {
                    Text("Matikan", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            } else {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Buka")
            }
        }
    }
}

enum class TimelineStatus { DEFAULT, DESTINATION, PASSED, BUS_HERE }

/**
 * Satu halte/stasiun di garis waktu rute. Garis memakai warna rute (data GTFS); tujuan jadi blok
 * amber penuh; bus yang sedang di halte itu digambar langsung di garis.
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
    val border = Wake.colors.border
    val line = if (passed) scheme.outline.copy(alpha = 0.5f) else lineColor
    val onDest = scheme.onSecondaryContainer
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(32.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(6.dp).weight(1f).background(if (isFirst) Color.Transparent else line))
                    Box(Modifier.width(6.dp).weight(1f).background(if (isLast) Color.Transparent else line))
                }
                when (status) {
                    TimelineStatus.BUS_HERE -> Box(
                        Modifier.size(30.dp).background(lineColor, RoundedCornerShape(10.dp)).border2(RoundedCornerShape(10.dp), border),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_mode_tj), contentDescription = "Bus di sini", tint = Wake.colors.onColor, modifier = Modifier.size(16.dp))
                    }
                    TimelineStatus.DESTINATION -> Box(
                        Modifier.size(22.dp).background(scheme.primary, CircleShape).border2(CircleShape, border)
                    )
                    else -> Box(
                        Modifier.size(16.dp).background(if (passed) scheme.surfaceVariant else scheme.surfaceContainer, CircleShape).border2(CircleShape, border)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = if (destination) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    color = when {
                        passed -> scheme.outline
                        destination -> onDest
                        else -> scheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (destination) Text("TURUN DI SINI", style = MaterialTheme.typography.labelMedium, color = onDest)
            }
            if (eta != null || detail != null) {
                Column(horizontalAlignment = Alignment.End) {
                    eta?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                passed -> scheme.outline
                                destination -> onDest
                                else -> scheme.onSurface
                            },
                            textAlign = TextAlign.End,
                        )
                    }
                    detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (destination) onDest else scheme.outline) }
                }
            }
        }
    }
    val shape = RoundedCornerShape(18.dp)
    // Baris tujuan membesar dengan spring saat muncul/berpindah, dan punya border + bayangan sendiri.
    val rowModifier = if (destination) {
        modifier.fillMaxWidth().posterShadow(shape, 3.dp)
    } else modifier.fillMaxWidth()
    val color = if (destination) scheme.secondaryContainer else Color.Transparent
    val border2 = if (destination) posterBorder() else null
    if (onClick != null) Surface(onClick = onClick, shape = shape, color = color, border = border2, modifier = rowModifier) { content() }
    else Surface(shape = shape, color = color, border = border2, modifier = rowModifier) { content() }
}

private fun Modifier.border2(shape: Shape, color: Color) = border(BorderWidth, color, shape)

/** Kartu bus besar (80dp) — aman diketuk di bus yang bergoyang. Yang disorot terisi mint. */
@Composable
fun BusCard(
    busNo: String,
    info: String,
    distance: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val fill by animateColorAsState(if (highlighted) scheme.primaryContainer else scheme.surfaceContainer, label = "kartu bus")
    val content = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface
    Surface(
        onClick = onClick,
        shape = shape,
        color = fill,
        contentColor = content,
        border = posterBorder(if (highlighted) 3.5.dp else BorderWidth),
        modifier = modifier.fillMaxWidth().posterShadow(shape, if (highlighted) 5.dp else 3.dp),
    ) {
        Row(
            Modifier.padding(16.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconBadge(R.drawable.ic_mode_tj, size = 48.dp, tint = SignalRed)
            Column(Modifier.weight(1f)) {
                Text(busNo, style = MaterialTheme.typography.titleMedium)
                Text(info, style = MaterialTheme.typography.bodySmall, color = if (highlighted) content else scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            distance?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                    Text("darimu", style = MaterialTheme.typography.labelSmall, color = if (highlighted) content else scheme.outline)
                }
            }
        }
    }
}

/**
 * Kartu hitung mundur untuk perjalanan aktif (live maupun GPS): angka besar yang terbaca sekilas,
 * bar segmen yang terisi satu per satu, dan titik tempat alarm akan berbunyi.
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
    val shape = RoundedCornerShape(28.dp)
    Surface(
        shape = shape,
        color = scheme.surfaceContainer,
        border = posterBorder(),
        modifier = modifier.fillMaxWidth().posterShadow(shape, 5.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Angka berguling ke atas saat berganti supaya terbaca sebagai hitungan mundur, bukan kedipan.
                AnimatedContent(
                    targetState = count,
                    transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                    label = "hitungan",
                ) { value -> Text(value, style = MaterialTheme.typography.displayLarge) }
                Text(unit, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (segments > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(segments) { i ->
                        val target = when {
                            i < done -> Wake.colors.live
                            i == wakeAt -> Wake.colors.alarm
                            else -> scheme.surfaceVariant
                        }
                        val color by animateColorAsState(target, label = "segmen")
                        val reveal = remember { Animatable(0f) }
                        val reduced = rememberReducedMotion()
                        LaunchedEffect(Unit) {
                            if (reduced) reveal.snapTo(1f) else {
                                delay(250L + i * 70L)
                                reveal.animateTo(1f, Motion.Settle)
                            }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(14.dp)
                                .graphicsLayer { alpha = reveal.value.coerceIn(0f, 1f); scaleY = 0.4f + 0.6f * reveal.value }
                                .clip(CircleShape)
                                .background(color)
                                .border2(CircleShape, Wake.colors.border),
                        )
                    }
                }
            }
            legend?.let {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(Wake.colors.alarm, CircleShape).border2(CircleShape, Wake.colors.border))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
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
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(container).border2(shape, Wake.colors.border).padding(contentPadding),
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

/** Tinggi minimum semua tombol besar — aman diketuk dengan jempol saat berjalan atau di bus. */
private val ButtonMinHeight = 58.dp

/**
 * Tombol aksi utama gaya poster: blok warna penuh lebar, border tebal, bayangan keras. Warna
 * diisi pemanggil: alarm = `secondary`/`onSecondary`, netral = `surfaceContainer`/`onSurface`,
 * batal/berbahaya = `error`/`onError`.
 */
@Composable
fun PosterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = container,
        contentColor = content,
        border = posterBorder(),
        modifier = modifier.fillMaxWidth().heightIn(min = ButtonMinHeight).posterShadow(shape, 4.dp),
    ) {
        Box(
            Modifier.defaultMinSize(minHeight = ButtonMinHeight).padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

/** Bidang cari gaya poster: kartu sel, border tebal, bayangan keras; silang muncul saat terisi. */
@Composable
fun PosterSearchField(
    query: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(28.dp)
    val border = Wake.colors.border
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        enabled = enabled,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = { onChange("") }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Hapus pencarian") }
            }
        },
        singleLine = true,
        shape = shape,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = border,
            unfocusedBorderColor = border,
            disabledBorderColor = border,
        ),
        modifier = modifier.fillMaxWidth().posterShadow(shape),
    )
}
