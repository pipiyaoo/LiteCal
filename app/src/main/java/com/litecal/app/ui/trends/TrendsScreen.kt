package com.litecal.app.ui.trends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.litecal.app.domain.model.AdaptiveTdeeResult
import com.litecal.app.domain.model.WeeklyBankState
import com.litecal.app.ui.DashboardUiState
import com.litecal.app.ui.WeightTrendPoint
import com.litecal.app.ui.theme.AmberSecondaryLight
import kotlin.math.abs
import kotlin.math.max

@Composable
fun TrendsScreen(
    state: DashboardUiState,
    onRecordBodyShape: (Float?, Float?) -> Unit
) {
    var showShapeDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "趋势",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            WeightTrendCard(
                points = state.weightSeries,
                startWeight = state.profile.startWeightKg,
                targetWeight = state.profile.targetWeightKg,
                latestEma = state.latestEmaKg
            )
        }

        state.weeklyBank?.let { bank ->
            item {
                WeeklyIntakeCard(bank = bank)
            }
        }

        state.tdeeResult?.let { tdee ->
            item {
                DailyBurnCard(
                    tdee = tdee,
                    todayExerciseBurnKcal = state.todayExerciseBurnKcal
                )
            }
        }

        item {
            BodyShapeCard(
                history = state.bodyMetricsHistory,
                onOpenRecordDialog = { showShapeDialog = true }
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showShapeDialog) {
        val latestWithShape = state.bodyMetricsHistory.firstOrNull { it.waistCm != null || it.bodyFatPct != null }
        BodyShapeDialog(
            initialWaist = latestWithShape?.waistCm,
            initialBodyFat = latestWithShape?.bodyFatPct,
            onDismiss = { showShapeDialog = false },
            onConfirm = { waist, fat ->
                onRecordBodyShape(waist, fat)
                showShapeDialog = false
            }
        )
    }
}

@Composable
private fun WeightTrendCard(
    points: List<WeightTrendPoint>,
    startWeight: Float,
    targetWeight: Float,
    latestEma: Float?
) {
    val currentDisplay = latestEma ?: startWeight
    val diffToGoal = (currentDisplay - targetWeight).coerceAtLeast(0f)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("体重变化", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCol("起始", "${String.format("%.1f", startWeight)} kg")
                MetricCol("近期走势", "${String.format("%.1f", currentDisplay)} kg", highlight = true)
                MetricCol("还需减", "${String.format("%.1f", diffToGoal)} kg")
            }

            if (points.size < 2) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    Text(
                        text = "再记 1 天体重就能看到走势图",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val primaryColor = MaterialTheme.colorScheme.primary
                val dotColor = AmberSecondaryLight
                val gridColor = MaterialTheme.colorScheme.surfaceVariant

                val allValues = points.flatMap { listOfNotNull(it.rawWeightKg, it.emaWeightKg) }
                val minW = (allValues.minOrNull() ?: 60f) - 0.6f
                val maxW = max((allValues.maxOrNull() ?: 75f) + 0.6f, minW + 1.5f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                        .padding(vertical = 6.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / (points.size - 1).coerceAtLeast(1)

                    fun mapY(weight: Float): Float {
                        val normalized = (weight - minW) / (maxW - minW)
                        return h - (normalized * h)
                    }

                    listOf(0.15f, 0.5f, 0.85f).forEach { frac ->
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, h * frac),
                            end = Offset(w, h * frac),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }

                    val emaPath = Path()
                    points.forEachIndexed { idx, pt ->
                        val x = idx * stepX
                        val y = mapY(pt.emaWeightKg)
                        if (idx == 0) emaPath.moveTo(x, y) else emaPath.lineTo(x, y)
                    }
                    drawPath(
                        path = emaPath,
                        color = primaryColor,
                        style = Stroke(
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    points.forEachIndexed { idx, pt ->
                        pt.rawWeightKg?.let { rawW ->
                            val x = idx * stepX
                            val y = mapY(rawW)
                            drawCircle(
                                color = dotColor.copy(alpha = 0.85f),
                                radius = 4.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "● 每天称重",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberSecondaryLight
                    )
                    Text(
                        text = "━ 真实走势",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyIntakeCard(bank: WeeklyBankState) {
    val balance = bank.bankBalanceBeforeToday
    val balanceText = when {
        balance > 0 -> "少吃了 $balance 大卡"
        balance < 0 -> "多吃了 ${abs(balance)} 大卡"
        else -> "节奏正好"
    }
    val weekConsumed = bank.days.sumOf { it.intakeKcal }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("本周摄入", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = balanceText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCol("今天可吃", "${bank.todayDynamicTarget} 大卡", highlight = true)
                MetricCol("本周已吃", "$weekConsumed 大卡")
                MetricCol("别低于", "${bank.safetyFloorKcal} 大卡")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                bank.days.forEach { d ->
                    val ratio = if (d.plannedKcal > 0) {
                        (d.intakeKcal.toFloat() / d.plannedKcal.toFloat()).coerceIn(0f, 1.2f)
                    } else 0f
                    val barColor = when {
                        d.isIncomplete -> MaterialTheme.colorScheme.outline
                        d.intakeKcal > d.plannedKcal -> MaterialTheme.colorScheme.error
                        d.isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomCenter,
                            modifier = Modifier
                                .width(28.dp)
                                .height(50.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((50f * (ratio / 1.2f)).dp)
                                    .background(barColor)
                            )
                        }
                        Text(
                            text = d.dayLabel.removePrefix("周"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (d.isToday) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = if (d.intakeKcal > 0) "${d.intakeKcal}" else "${d.plannedKcal}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyBurnCard(
    tdee: AdaptiveTdeeResult,
    todayExerciseBurnKcal: Int
) {
    val totalTodayBurn = tdee.estimatedTdee + todayExerciseBurnKcal
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("每天大约消耗", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (tdee.isCalibrated) "已按体重校准" else "记满8天更准 (${tdee.validDaysCount}/${tdee.minRequiredDays})",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (tdee.isCalibrated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCol("今日总消耗", "$totalTodayBurn 大卡", highlight = true)
                MetricCol("日常消耗", "${tdee.estimatedTdee} 大卡")
                if (todayExerciseBurnKcal > 0) {
                    MetricCol("今日运动", "+$todayExerciseBurnKcal 大卡", highlight = true)
                } else {
                    MetricCol("基础消耗", "${tdee.bmr} 大卡")
                }
            }

            if (!tdee.isCalibrated) {
                val ratio = (tdee.validDaysCount.toFloat() / tdee.minRequiredDays.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )
            } else {
                val deltaStr = tdee.weightDelta14dKg?.let { String.format("%+.1f kg", it) } ?: "--"
                Text(
                    text = "近两周日均吃 ${tdee.avgIntake14d ?: "--"} 大卡 · 体重 $deltaStr",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BodyShapeCard(
    history: List<com.litecal.app.data.local.BodyMetricEntity>,
    onOpenRecordDialog: () -> Unit
) {
    val shapeLogs = history.filter { it.waistCm != null || it.bodyFatPct != null }.take(5)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("腰围与体脂", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onOpenRecordDialog) {
                    Text("记录")
                }
            }

            if (shapeLogs.isEmpty()) {
                Text(
                    text = "暂无记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                shapeLogs.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.dateIso, style = MaterialTheme.typography.bodyMedium)
                        val waistStr = item.waistCm?.let { "腰围 ${String.format("%.1f", it)} cm" } ?: ""
                        val fatStr = item.bodyFatPct?.let { "体脂 ${String.format("%.1f", it)}%" } ?: ""
                        Text(
                            text = listOf(waistStr, fatStr).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BodyShapeDialog(
    initialWaist: Float?,
    initialBodyFat: Float?,
    onDismiss: () -> Unit,
    onConfirm: (Float?, Float?) -> Unit
) {
    var waistText by remember { mutableStateOf(initialWaist?.let { String.format("%.1f", it) }.orEmpty()) }
    var fatText by remember { mutableStateOf(initialBodyFat?.let { String.format("%.1f", it) }.orEmpty()) }
    val validWaist = waistText.toFloatOrNull()?.takeIf { it in 40f..180f }
    val validFat = fatText.toFloatOrNull()?.takeIf { it in 4f..65f }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录腰围 / 体脂") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = waistText,
                    onValueChange = { waistText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("腰围 (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fatText,
                    onValueChange = { fatText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("体脂率 (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validWaist != null || validFat != null) onConfirm(validWaist, validFat)
                },
                enabled = validWaist != null || validFat != null
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
