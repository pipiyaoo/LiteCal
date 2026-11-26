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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "趋势与真实代谢复盘",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "用 7 日平滑趋势过滤水分涨跌，用真实能量守恒校准每日消耗",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            WeightEmaChartCard(
                points = state.weightSeries,
                startWeight = state.profile.startWeightKg,
                targetWeight = state.profile.targetWeightKg,
                latestEma = state.latestEmaKg
            )
        }

        state.weeklyBank?.let { bank ->
            item {
                WeeklyCalorieBankCard(bank = bank)
            }
        }

        state.tdeeResult?.let { tdee ->
            item {
                AdaptiveTdeeCard(tdee = tdee)
            }
        }

        item {
            BodyShapeCard(
                history = state.bodyMetricsHistory,
                onOpenRecordDialog = { showShapeDialog = true }
            )
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
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
private fun WeightEmaChartCard(
    points: List<WeightTrendPoint>,
    startWeight: Float,
    targetWeight: Float,
    latestEma: Float?
) {
    val currentDisplay = latestEma ?: startWeight
    val diffToGoal = (currentDisplay - targetWeight).coerceAtLeast(0f)

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCol("初始体重", "${String.format("%.1f", startWeight)} kg")
                MetricCol("当前7日平滑", "${String.format("%.1f", currentDisplay)} kg", highlight = true)
                MetricCol("距目标体重", "${String.format("%.1f", diffToGoal)} kg")
            }

            if (points.size < 2) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    Text(
                        text = "连续记录 2 天以上晨重后，这里将绘制「原始晨重散点 + 7日平滑曲线」",
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
                        .height(185.dp)
                        .padding(vertical = 8.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val stepX = w / (points.size - 1).coerceAtLeast(1)

                    fun mapY(weight: Float): Float {
                        val normalized = (weight - minW) / (maxW - minW)
                        return h - (normalized * h)
                    }

                    // 3 条水平参考虚线
                    listOf(0.15f, 0.5f, 0.85f).forEach { frac ->
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, h * frac),
                            end = Offset(w, h * frac),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }

                    // 绘制 7日 EMA 平滑主趋势线
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

                    // 绘制原始每日晨重散点
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
                        text = "● 橙点：每日原始晨重（含水分波动）",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberSecondaryLight
                    )
                    Text(
                        text = "━ 绿线：7日 EMA 真实趋势",
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
private fun WeeklyCalorieBankCard(bank: WeeklyBankState) {
    val balance = bank.bankBalanceBeforeToday
    val balanceText = when {
        balance > 0 -> "本周结余 +$balance kcal"
        balance < 0 -> "待分摊 $balance kcal"
        else -> "预算均衡"
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏦 周热量银行（周一至周日）", style = MaterialTheme.typography.titleMedium)
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
                MetricCol("本周总预算", "${bank.weeklyTotalBudget} kcal")
                MetricCol("今日动态额度", "${bank.todayDynamicTarget} kcal", highlight = true)
                MetricCol("安全保底下限", "${bank.safetyFloorKcal} kcal")
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
                                .height(54.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((54f * (ratio / 1.2f)).dp)
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
private fun AdaptiveTdeeCard(tdee: AdaptiveTdeeResult) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔥 自适应真实代谢校准", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (tdee.isCalibrated) "已动态激活" else "数据预热中 (${tdee.validDaysCount}/${tdee.minRequiredDays}天)",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (tdee.isCalibrated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCol("当前生效 TDEE", "${tdee.estimatedTdee} kcal", highlight = true)
                MetricCol("公式静态估算", "${tdee.formulaTdee} kcal")
                MetricCol("基础代谢 BMR", "${tdee.bmr} kcal")
            }

            if (!tdee.isCalibrated) {
                val ratio = (tdee.validDaysCount.toFloat() / tdee.minRequiredDays.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = "再积累 ${(tdee.minRequiredDays - tdee.validDaysCount).coerceAtLeast(1)} 个完整饮食记录日并保持晨重打卡，系统将根据「摄入热量 + 平滑体重变化」自动校准你的真实代谢。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val deltaStr = tdee.weightDelta14dKg?.let { String.format("%+.2f kg", it) } ?: "--"
                Text(
                    text = "近14天有效日均摄入 ${tdee.avgIntake14d ?: "--"} kcal · 平滑体重变化 $deltaStr → 已为你自动消除估算与穿戴设备偏差。",
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("📏 腰围与体脂率低频记录", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "每周测 1 次即可，平台期腰围缩小说明正在掉纯脂肪",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onOpenRecordDialog) {
                    Text("记一次")
                }
            }

            if (shapeLogs.isEmpty()) {
                Text(
                    text = "暂无围度记录，建议每周固定一天早晨软尺平脐量一次腰围。",
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录今日腰围 / 体脂率") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = waistText,
                    onValueChange = { waistText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("腰围 (cm，选填)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fatText,
                    onValueChange = { fatText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("体脂率 (% ，选填)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = waistText.toFloatOrNull()?.takeIf { it in 40f..180f }
                    val f = fatText.toFloatOrNull()?.takeIf { it in 4f..65f }
                    if (w != null || f != null) onConfirm(w, f)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
