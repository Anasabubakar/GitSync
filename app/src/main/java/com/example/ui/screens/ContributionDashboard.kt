package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.SyncLog
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

data class DailyContribution(
    val dateString: String,      // e.g. "Jun 01"
    val dateKey: String,         // e.g. "2026-06-01"
    val commits: Int,            // simulated baseline
    val syncs: Int,              // actual from logs
    val total: Int = commits + syncs
)

@Composable
fun ContributionDashboardComponent(
    logs: List<SyncLog>,
    modifier: Modifier = Modifier
) {
    // Determine the contribution data over the last 14 days
    val contributionData = remember(logs) {
        calculateDailyContributions(logs)
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var currentViewMode by remember { mutableStateOf(0) } // 0 = Trend Chart, 1 = Heatmap Grid

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("github_contribution_chart_container"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "GitHub Dev Velocity & Contribution Trends",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Visualizing commit history & automated sync activities",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tabs / Toggle Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (currentViewMode == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            1.dp, 
                            if (currentViewMode == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent, 
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { currentViewMode = 0 }
                        .padding(vertical = 8.dp)
                        .testTag("toggle_chart_view_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Recharts Trend Line",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentViewMode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (currentViewMode == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            1.dp, 
                            if (currentViewMode == 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent, 
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { currentViewMode = 1 }
                        .padding(vertical = 8.dp)
                        .testTag("toggle_heatmap_view_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Contribution Box Grid",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentViewMode == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Render active view
            AnimatedContent(
                targetState = currentViewMode,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ChartAnim"
            ) { mode ->
                when (mode) {
                    0 -> {
                        ContributionTrendLineChart(
                            data = contributionData,
                            selectedIndex = selectedIndex,
                            onSelectIndex = { selectedIndex = it }
                        )
                    }
                    1 -> {
                        ContributionHeatmapGrid(logs = logs)
                    }
                }
            }
        }
    }
}

fun calculateDailyContributions(logs: List<SyncLog>): List<DailyContribution> {
    val result = ArrayList<DailyContribution>()
    val cal = Calendar.getInstance()
    
    val logDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val displayFormat = SimpleDateFormat("MMM dd", Locale.US)
    
    val logsByDate = logs.groupBy { 
        logDateFormat.format(Date(it.timestamp))
    }

    // Deterministic seed commits for last 14 days representing constant repository updates
    val seedCommits = intArrayOf(4, 7, 2, 0, 8, 12, 5, 1, 0, 9, 10, 3, 2, 6)

    for (i in 0 until 14) {
        val date = cal.time
        val dateKey = logDateFormat.format(date)
        val dateDisplay = displayFormat.format(date)
        
        val syncCount = logsByDate[dateKey]?.size ?: 0
        val baseCommits = seedCommits[i % seedCommits.size]

        result.add(
            DailyContribution(
                dateString = dateDisplay,
                dateKey = dateKey,
                commits = baseCommits,
                syncs = syncCount
            )
        )
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }

    return result.reversed()
}

@Composable
fun ContributionTrendLineChart(
    data: List<DailyContribution>,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit
) {
    val maxVal = remember(data) {
        (data.maxOfOrNull { it.total } ?: 10).coerceAtLeast(10)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Active selection tooltip pop-up
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selectedIndex != null && selectedIndex < data.size) {
                val item = data[selectedIndex]
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.dateString,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryCyan,
                        fontSize = 11.sp
                    )
                    Box(modifier = Modifier.size(1.dp, 10.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
                    Text(
                        text = "Commits: ${item.commits}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.sp
                    )
                    Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(PrimaryNeon))
                    Text(
                        text = "Portfolio Syncs: ${item.syncs}",
                        color = PrimaryNeon,
                        fontSize = 10.sp
                    )
                }
            } else {
                Text(
                    text = "👇 Tap on a bar chart column to inspect detailed metrics",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Custom Recharts Chart Render
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val surfaceColor = MaterialTheme.colorScheme.surface
        val primaryColor = MaterialTheme.colorScheme.primary

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val width = size.width
                val height = size.height

                val paddingLeft = 35.dp.toPx()
                val paddingRight = 10.dp.toPx()
                val paddingTop = 15.dp.toPx()
                val paddingBottom = 25.dp.toPx()

                val chartWidth = width - paddingLeft - paddingRight
                val chartHeight = height - paddingTop - paddingBottom

                // Draw Horizontal Gridlines (Y axis ticks)
                val ticks = 4
                for (i in 0..ticks) {
                    val y = paddingTop + chartHeight - (chartHeight * i / ticks)
                    drawLine(
                        color = Color(0xFF263238),
                        start = Offset(paddingLeft, y),
                        end = Offset(width - paddingRight, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                if (data.isEmpty()) return@Canvas

                val stepX = chartWidth / (data.size - 1)
                val points = ArrayList<Offset>()

                for (i in data.indices) {
                    val item = data[i]
                    val x = paddingLeft + (i * stepX)
                    val valueRatio = item.total.toFloat() / maxVal
                    val y = paddingTop + chartHeight - (chartHeight * valueRatio)
                    points.add(Offset(x, y))

                    // Draw vertical bars Representing automated contributions
                    val barWidth = (stepX * 0.45f).coerceIn(6.dp.toPx(), 16.dp.toPx())
                    val barHeight = chartHeight * valueRatio
                    val barX = x - (barWidth / 2f)
                    val barY = paddingTop + chartHeight - barHeight

                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                PrimaryNeon.copy(alpha = 0.8f),
                                PrimaryNeon.copy(alpha = 0.15f)
                            )
                        ),
                        topLeft = Offset(barX, barY),
                        size = Size(barWidth, barHeight)
                    )

                    // Draw subtle highlight column indicator if index is selected
                    if (selectedIndex == i) {
                        drawRect(
                            color = onSurfaceColor.copy(alpha = 0.05f),
                            topLeft = Offset(x - stepX / 2f, paddingTop),
                            size = Size(stepX, chartHeight)
                        )
                    }
                }

                // Draw smooth spline connection curve line
                val trendPath = Path()
                trendPath.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val p1 = points[i - 1]
                    val p2 = points[i]
                    val controlX = (p1.x + p2.x) / 2f
                    trendPath.quadraticBezierTo(p1.x, p1.y, controlX, (p1.y + p2.y) / 2f)
                }
                trendPath.lineTo(points.last().x, points.last().y)

                drawPath(
                    path = trendPath,
                    color = SecondaryCyan,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Fill glowing energy gradient representing area
                val fillPath = Path().apply {
                    addPath(trendPath)
                    lineTo(points.last().x, paddingTop + chartHeight)
                    lineTo(points.first().x, paddingTop + chartHeight)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SecondaryCyan.copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                )

                // Render coordinate point circular ticks
                for (i in points.indices) {
                    val p = points[i]
                    drawCircle(
                        color = surfaceColor,
                        radius = 5.dp.toPx(),
                        center = p
                    )
                    drawCircle(
                        color = if (selectedIndex == i) primaryColor else SecondaryCyan,
                        radius = 3.5.dp.toPx(),
                        center = p
                    )
                }
            }

            // Overlay of invisible clickable cells for pixel-perfect hover precision
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 35.dp, end = 10.dp, bottom = 25.dp)
            ) {
                for (index in data.indices) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSelectIndex(if (selectedIndex == index) null else index)
                            }
                    )
                }
            }

            // Y Axis Labels
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(bottom = 25.dp, top = 15.dp)
                    .width(30.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(text = "$maxVal", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                Text(text = "${(maxVal * 0.75).toInt()}", fontSize = 9.sp, color = TextSecondary)
                Text(text = "${(maxVal * 0.50).toInt()}", fontSize = 9.sp, color = TextSecondary)
                Text(text = "${(maxVal * 0.25).toInt()}", fontSize = 9.sp, color = TextSecondary)
                Text(text = "0", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            }

            // X Axis Labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 35.dp, end = 10.dp)
                    .height(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (data.isNotEmpty()) {
                    Text(text = data.first().dateString, fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text(text = data[data.size / 2].dateString, fontSize = 9.sp, color = TextSecondary)
                    Text(text = data.last().dateString, fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ContributionHeatmapGrid(
    logs: List<SyncLog>
) {
    var hoveredDate by remember { mutableStateOf<String?>(null) }
    var hoveredCount by remember { mutableStateOf<Int>(0) }

    val weeks = 14
    val daysInWeek = 7
    val totalDays = weeks * daysInWeek

    val heatmapData = remember(logs) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(totalDays - 1))
        
        val logDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormat = SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.US)
        
        val logsByDate = logs.groupBy { logDateFormat.format(Date(it.timestamp)) }
        val randomSeed = java.util.Random(1337)
        
        val list = ArrayList<HeatmapCell>()
        for (i in 0 until totalDays) {
            val date = cal.time
            val key = logDateFormat.format(date)
            val display = displayFormat.format(date)
            
            val syncCount = logsByDate[key]?.size ?: 0
            val randVal = randomSeed.nextInt(100)
            val simulatedCommits = when {
                randVal < 40 -> 0
                randVal < 70 -> randomSeed.nextInt(3) + 1
                randVal < 90 -> randomSeed.nextInt(4) + 4
                else -> randomSeed.nextInt(6) + 8
            }
            
            list.add(
                HeatmapCell(
                    dateKey = key,
                    displayString = display,
                    commits = simulatedCommits,
                    syncs = syncCount
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hoveredDate != null) {
                Text(
                    text = "$hoveredDate: $hoveredCount contribution${if (hoveredCount == 1) "" else "s"}",
                    fontSize = 11.sp,
                    color = PrimaryNeon,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "💡 Tap any colored grid box below to view exact contribution details",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Spacer(modifier = Modifier.height(13.dp))
                Text(text = "Mon", fontSize = 8.sp, color = TextSecondary, modifier = Modifier.height(12.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Wed", fontSize = 8.sp, color = TextSecondary, modifier = Modifier.height(12.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Fri", fontSize = 8.sp, color = TextSecondary, modifier = Modifier.height(12.dp))
            }

            for (w in 0 until weeks) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val firstDayIdx = w * daysInWeek
                    val label = if (firstDayIdx < heatmapData.size) {
                        val cellDateStr = heatmapData[firstDayIdx].displayString
                        if (cellDateStr.contains(" 01,") || cellDateStr.contains(" 02,") || cellDateStr.contains(" 05,") || w == 0 || w % 4 == 0) {
                            cellDateStr.split(",")[1].trim().split(" ")[0]
                        } else ""
                    } else ""
                    
                    Text(
                        text = label,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.height(11.dp),
                        maxLines = 1
                    )

                    for (d in 0 until daysInWeek) {
                        val cellIdx = w * daysInWeek + d
                        if (cellIdx < heatmapData.size) {
                            val cell = heatmapData[cellIdx]
                            val totalContributions = cell.commits + cell.syncs
                            
                            val cellColor = when {
                                totalContributions == 0 -> Color(0xFFE2E8F0)
                                totalContributions <= 2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                totalContributions <= 5 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                totalContributions <= 8 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(cellColor)
                                    .clickable {
                                        hoveredDate = cell.displayString.split(",")[1].trim()
                                        hoveredCount = totalContributions
                                    }
                                    .testTag("heatmap_cell_day_${cellIdx}")
                            )
                        } else {
                            Box(modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Less", fontSize = 9.sp, color = TextSecondary)
            Spacer(modifier = Modifier.width(4.dp))
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(Color(0xFFE2E8F0)))
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(1.dp)).background(MaterialTheme.colorScheme.primary))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "More", fontSize = 9.sp, color = TextSecondary)
        }
    }
}

data class HeatmapCell(
    val dateKey: String,
    val displayString: String,
    val commits: Int,
    val syncs: Int
)
