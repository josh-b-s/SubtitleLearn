package com.example.subtitlelearn.screens.stats

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.subtitlelearn.DEBUG
import com.example.subtitlelearn.dictionary.Dictionary
import com.example.subtitlelearn.srs.KnownWordsStore
import com.example.subtitlelearn.srs.SeedDataHelper
import com.example.subtitlelearn.srs.SrsStore
import java.time.LocalDate

private enum class StatsPeriod(val label: String, val days: Int) {
    WEEK("Last 7 days", 7),
    MONTH("Last month", 30),
    ALL("All time", 365)
}


@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dict = Dictionary.currentFile
    var period by remember { mutableStateOf(StatsPeriod.MONTH) }

    // Compute stats
    val totalTracked = remember(dict) { SrsStore.allTracked(context, dict).size }
    val knownCount = remember(dict) { KnownWordsStore.allKnown(context).size }
    val streak = remember(dict) { SrsStore.currentStreak(context, dict) }
    val totalReviews = remember(dict, period) { SrsStore.totalReviews(context, dict) }
    val retention = remember(dict, period) { SrsStore.retentionRate(context, dict, period.days) }
    val heatmapData = remember(dict) { SrsStore.reviewsPerDay(context, dict, 90) }
    val weeklyData = remember(dict) { SrsStore.wordsLearnedPerWeek(context, dict, 12) }


    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Debug seed bar ────────────────────────────────────────────────────
        if (DEBUG) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { SeedDataHelper.seed(context);},
                    modifier = Modifier.weight(1f)
                ) { Text("Seed test data", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = { SeedDataHelper.clear(context);},
                    modifier = Modifier.weight(1f)
                ) { Text("Clear seed", fontSize = 11.sp) }
            }
            Spacer(Modifier.height(8.dp))
        }


        // ── Header ────────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Progress",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    dict,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Streak badge
            Surface(
                color = if (streak > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("🔥", fontSize = 16.sp)
                    Text(
                        "$streak day${if (streak != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (streak > 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Period filter ─────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p },
                    label = { Text(p.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // ── Key numbers ───────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("Words in deck", totalTracked.toString(), Modifier.weight(1f))
            StatCard("Reviews done", totalReviews.toString(), Modifier.weight(1f))
            StatCard(
                "Retention",
                if (retention > 0) "${(retention * 100).toInt()}%" else "—",
                Modifier.weight(1f),
                accent = when {
                    retention >= 0.85f -> Color(0xFF2E7D32)
                    retention >= 0.70f -> Color(0xFFE65100)
                    retention > 0 -> Color(0xFFB00020)
                    else -> null
                }
            )
        }

        // ── Dictionary progress ───────────────────────────────────────────────
        SectionCard(title = "Dictionary coverage") {
            val progressFraction = if (totalTracked > 0)
                knownCount.toFloat() / totalTracked.coerceAtLeast(1) else 0f
            val animProgress by animateFloatAsState(
                targetValue = progressFraction,
                animationSpec = tween(800),
                label = "coverage"
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$knownCount known / $totalTracked tracked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${(progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceDim)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2E7D32))
                )
            }
        }

        // ── Weekly words learned ──────────────────────────────────────────────

        SectionCard(title = "Words learned per week") {
            WeeklyLineChart(weeklyData)

        }

        // ── Review heatmap ────────────────────────────────────────────────────
        SectionCard(title = "Review activity (90 days)") {
            ReviewHeatmap(heatmapData)
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun WeeklyLineChart(data: List<Pair<Long, Int>>) {
    if (data.size < 2) {
        Box(Modifier
            .fillMaxWidth()
            .height(160.dp), contentAlignment = Alignment.Center) {
            Text(
                "Not enough data yet", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Build cumulative series — like ELO, each point is the running total
    val cumulative = mutableListOf<Pair<Long, Int>>()
    var running = 0
    data.sortedBy { it.first }.forEach { (epochDay, count) ->
        running += count
        cumulative.add(epochDay to running)
    }

    val primary = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val maxVal = cumulative.maxOf { it.second }.toFloat()
    val minVal = 0f
    val range = (maxVal - minVal).coerceAtLeast(1f)
    val current = cumulative.last().second
    val previous = cumulative.dropLast(1).last().second
    val delta = current - previous

    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(data) {
        progress = 0f
        animate(0f, 1f, animationSpec = tween(1200, easing = EaseOutCubic)) { v, _ -> progress = v }
    }

    Column {
        // ── Top row: current value + delta ────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    current.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
                Text(
                    "total words learned", style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
            }
            // Delta badge — like ELO gain/loss this period
            if (delta != 0) {
                Surface(
                    color = if (delta > 0) Color(0xFF2E7D32).copy(alpha = 0.15f)
                    else Color(0xFFB00020).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${if (delta > 0) "+" else ""}$delta this period",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (delta > 0) Color(0xFF2E7D32) else Color(0xFFB00020)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Chart canvas ──────────────────────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .drawWithCache {
                    val padL = 48.dp.toPx()  // left pad for Y labels
                    val padB = 24.dp.toPx()  // bottom pad for X labels
                    val padR = 8.dp.toPx()
                    val padT = 8.dp.toPx()

                    val chartW = size.width - padL - padR
                    val chartH = size.height - padT - padB

                    fun xOf(i: Int) = padL + i.toFloat() / (cumulative.size - 1) * chartW
                    fun yOf(v: Int) = padT + chartH - ((v - minVal) / range) * chartH

                    // Build smooth path
                    val pts = cumulative.mapIndexed { i, (_, v) -> Offset(xOf(i), yOf(v)) }
                    val linePath = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) {
                            val prev = pts[i - 1];
                            val curr = pts[i]
                            val cx = (prev.x + curr.x) / 2
                            cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                        }
                    }

                    // Fill path (area under curve)
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(pts.last().x, padT + chartH)
                        lineTo(pts.first().x, padT + chartH)
                        close()
                    }

                    // Clip width for animation
                    val clipW = padL + chartW * progress

                    onDrawBehind {
                        // Horizontal grid lines (4 levels)
                        val gridSteps = 4
                        for (s in 0..gridSteps) {
                            val v = minVal + range * s / gridSteps
                            val y = padT + chartH - (v / range) * chartH
                            drawLine(
                                gridColor,
                                Offset(padL, y),
                                Offset(size.width - padR, y),
                                1.dp.toPx()
                            )
                            // Y label
                            drawContext.canvas.nativeCanvas.drawText(
                                v.toInt().toString(),
                                padL - 6.dp.toPx(),
                                y + 4.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.parseColor("#80FFFFFF")
                                    textSize = 9.dp.toPx()
                                    textAlign = android.graphics.Paint.Align.RIGHT
                                }
                            )
                        }

                        // Clip to animated width
                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(
                            androidx.compose.ui.geometry.Rect(0f, 0f, clipW, size.height)
                        )

                        // Fill
                        drawPath(
                            fillPath,
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    primary.copy(alpha = 0.3f),
                                    primary.copy(alpha = 0.02f)
                                ),
                                startY = padT,
                                endY = padT + chartH
                            )
                        )

                        // Line
                        drawPath(
                            linePath,
                            primary,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        drawContext.canvas.restore()

                        // Endpoint dot + horizontal dashed reference
                        if (progress >= 1f) {
                            val lastPt = pts.last()
                            drawCircle(primary, 5.dp.toPx(), lastPt)
                            drawCircle(primary.copy(alpha = 0.25f), 10.dp.toPx(), lastPt)

                            // Dashed horizontal line at current value
                            var x = padL
                            while (x < lastPt.x - 4.dp.toPx()) {
                                drawLine(
                                    primary.copy(alpha = 0.3f),
                                    Offset(x, lastPt.y),
                                    Offset((x + 6.dp.toPx()).coerceAtMost(lastPt.x), lastPt.y),
                                    1.dp.toPx()
                                )
                                x += 10.dp.toPx()
                            }
                        }
                    }
                }
        )

        // X-axis labels
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(cumulative.first(), cumulative[cumulative.size / 2], cumulative.last())
                .forEach { (epochDay, _) ->
                    val d = LocalDate.ofEpochDay(epochDay)
                    Text(
                        "${d.monthValue}/${d.dayOfMonth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
        }
    }
}

@Composable
private fun ReviewHeatmap(data: List<SrsStore.DailyCount>) {
    val today = LocalDate.now().toEpochDay()
    val days = 90
    val start = today - days + 1

    val countByDay = data.associate { it.epochDay to it.count }
    val maxCount = (countByDay.values.maxOrNull() ?: 1).coerceAtLeast(1)

    // Build 7-row grid (Mon–Sun) × 13 columns (~90 days)
    val cols = (days + 6) / 7
    val todayDow = LocalDate.now().dayOfWeek.value - 1 // 0=Mon

    val activeColor = Color(0xFF1565C0)
    val emptyColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until 7) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until cols) {
                    val offset = col * 7 + row
                    val epochDay = start + offset
                    if (epochDay > today) {
                        Box(Modifier.size(10.dp)) // empty placeholder
                    } else {
                        val count = countByDay[epochDay] ?: 0
                        val alpha =
                            if (count == 0) 0f else 0.25f + 0.75f * (count.toFloat() / maxCount)
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (count == 0) emptyColor
                                    else activeColor.copy(alpha = alpha)
                                )
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Text(
        "Each cell = 1 day. Darker = more reviews.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}