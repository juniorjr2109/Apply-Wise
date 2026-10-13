package com.webwizards.applywise.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webwizards.applywise.data.remote.LabelValue
import com.webwizards.applywise.domain.MatchType
import com.webwizards.applywise.ui.theme.LocalMatchColors

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

/** Full-screen error with an optional retry and an optional secondary action (e.g. open Settings). */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isNetwork: Boolean = false,
    onRetry: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(if (isNetwork) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline, contentDescription = null,
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (onRetry != null) { Spacer(Modifier.height(20.dp)); Button(onClick = onRetry) { Text("Try again") } }
        if (secondaryLabel != null && onSecondary != null) TextButton(onClick = onSecondary) { Text(secondaryLabel) }
    }
}

@Composable
fun MatchBadge(type: MatchType, modifier: Modifier = Modifier) {
    val colors = LocalMatchColors.current
    val color = when (type) { MatchType.Strong -> colors.strong; MatchType.Good -> colors.good; MatchType.Reach -> colors.reach }
    Surface(color = color.copy(alpha = 0.14f), contentColor = color, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(type.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun StatCard(label: String, value: String, supporting: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Horizontal bar list; bars animate in once. */
@Composable
fun BarList(rows: List<LabelValue>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) {
        Text("No data yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    val max = rows.maxOf { it.value }.coerceAtLeast(1)
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            val progress by animateFloatAsState(if (started) row.value / max.toFloat() else 0f, tween(600), label = "bar")
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${row.label}: ${row.value}" }) {
                Text(row.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(118.dp))
                LinearProgressIndicator(progress = { progress }, strokeCap = StrokeCap.Round, gapSize = 0.dp,
                    drawStopIndicator = {}, modifier = Modifier.weight(1f).height(8.dp))
                Text(row.value.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
            }
        }
    }
}

/** Vertical bar chart for the 14-day trend, drawn on a Canvas (one draw pass, no child layouts per bar). */
@Composable
fun DailyBars(days: List<LabelValue>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (days.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val total = days.sumOf { it.value }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(110.dp).semantics { contentDescription = "$total applications in the last ${days.size} days" }) {
            Canvas(Modifier.fillMaxSize()) {
                if (days.isEmpty()) return@Canvas
                val gap = 4.dp.toPx()
                val barWidth = (size.width - gap * (days.size - 1)) / days.size
                days.forEachIndexed { i, d ->
                    val x = i * (barWidth + gap)
                    drawRoundRect(track, Offset(x, size.height - 2.dp.toPx()), Size(barWidth, 2.dp.toPx()), CornerRadius(2f))
                    val h = (d.value / max.toFloat()) * size.height
                    if (h > 0f) drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(6.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(days.firstOrNull()?.label?.drop(5).orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
