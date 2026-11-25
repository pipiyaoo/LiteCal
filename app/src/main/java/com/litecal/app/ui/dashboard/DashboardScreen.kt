package com.litecal.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.litecal.app.data.local.MealLogEntity
import com.litecal.app.domain.model.MealType
import com.litecal.app.domain.model.WeeklyBankState
import com.litecal.app.ui.DashboardUiState
import com.litecal.app.ui.theme.ProteinAccent
import com.litecal.app.ui.theme.WaterAccent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onBackToToday: () -> Unit,
    onOpenQuickAdd: (MealType, Int) -> Unit,
    onCopyYesterdayMeal: (MealType) -> Unit,
    onSaveMealAsCombo: (MealType, String) -> Unit,
    onDeleteMealLog: (MealLogEntity) -> Unit,
    onRecordWeight: (Float) -> Unit,
    onAddWater: (Int) -> Unit,
    onToggleIncompleteDay: (Boolean) -> Unit
) {
    var showWeightDialog by remember { mutableStateOf(false) }
    var saveComboForMeal by remember { mutableStateOf<MealType?>(null) }
    var showWeekBankDetail by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        onOpenQuickAdd(
                            MealType.defaultForHour(java.time.LocalTime.now().hour),
                            1 // 直接打开 AI 识餐 Tab
                        )
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI 拍照或一句话估算"
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        onOpenQuickAdd(
                            MealType.defaultForHour(java.time.LocalTime.now().hour),
                            0
                        )
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("记饮食") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DateHeaderBar(
                    selectedDate = state.selectedDate,
                    onPrevDay = onPrevDay,
                    onNextDay = onNextDay,
                    onBackToToday = onBackToToday
                )
            }

            item {
                DualCoreHeroCard(
                    state = state,
                    showWeekBankDetail = showWeekBankDetail,
                    onToggleBankDetail = { showWeekBankDetail = !showWeekBankDetail }
                )
            }

            item {
                WeightAndWaterRow(
                    state = state,
                    onWeightClick = { showWeightDialog = true },
                    onAddWater = onAddWater
                )
            }

            MealType.entries.forEach { mealType ->
                val logs = state.mealLogsByType[mealType].orEmpty()
                item(key = mealType.name) {
                    MealGroupCard(
                        mealType = mealType,
                        logs = logs,
                        onAddClick = { onOpenQuickAdd(mealType, 0) },
                        onCopyYesterday = { onCopyYesterdayMeal(mealType) },
                        onSaveAsCombo = { saveComboForMeal = mealType },
                        onDeleteLog = onDeleteMealLog
                    )
                }
            }

            item {
                IncompleteDayGuardCard(
                    isIncomplete = state.isIncompleteDay,
                    onToggle = onToggleIncompleteDay
                )
            }

            item {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }
    }

    if (showWeightDialog) {
        WeightInputDialog(
            initialWeight = state.selectedDateWeightKg ?: state.latestWeightKg ?: state.profile.startWeightKg,
            selectedDate = state.selectedDate,
            onDismiss = { showWeightDialog = false },
            onConfirm = { w ->
                onRecordWeight(w)
                showWeightDialog = false
            }
        )
    }

    saveComboForMeal?.let { mealType ->
        SaveComboDialog(
            mealType = mealType,
            onDismiss = { saveComboForMeal = null },
            onConfirm = { name ->
                onSaveMealAsCombo(mealType, name)
                saveComboForMeal = null
            }
        )
    }
}

@Composable
private fun DateHeaderBar(
    selectedDate: LocalDate,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onBackToToday: () -> Unit
) {
    val today = LocalDate.now()
    val isToday = selectedDate == today
    val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val weekLabel = dayNames[selectedDate.dayOfWeek.value - 1]
    val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("MM月dd日"))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevDay) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一天")
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isToday) "$dateStr · 今天 ($weekLabel)" else "$dateStr ($weekLabel)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (!isToday) {
                AssistChip(
                    onClick = onBackToToday,
                    label = { Text("回今天") }
                )
            }
        }

        IconButton(onClick = onNextDay) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "后一天")
        }
    }
}

@Composable
private fun DualCoreHeroCard(
    state: DashboardUiState,
    showWeekBankDetail: Boolean,
    onToggleBankDetail: () -> Unit
) {
    val progressRaw = if (state.dynamicTargetKcal > 0) {
        (state.consumedKcal.toFloat() / state.dynamicTargetKcal.toFloat()).coerceIn(0f, 1.25f)
    } else 0f
    val animatedSweep by animateFloatAsState(
        targetValue = (progressRaw.coerceAtMost(1f)) * 360f,
        label = "kcalRing"
    )
    val isOver = state.remainingKcal < 0
    val ringColor = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    val proteinRatio = if (state.proteinTargetG > 0) {
        (state.proteinConsumedG.toFloat() / state.proteinTargetG.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 左侧剩余大卡圆环
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(124.dp)
                ) {
                    Canvas(modifier = Modifier.size(112.dp)) {
                        val stroke = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = stroke
                        )
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = animatedSweep,
                            useCenter = false,
                            style = stroke
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isOver) "超支" else "还可吃",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${abs(state.remainingKcal)}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "kcal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 右侧蛋白质核心达标 + 预算明细
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "已摄入 ${state.consumedKcal}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "额度 ${state.dynamicTargetKcal} kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 蛋白质进度核心条
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "蛋白质保肌目标",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ProteinAccent
                            )
                            Text(
                                text = "${state.proteinConsumedG} / ${state.proteinTargetG} g",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ProteinAccent
                            )
                        }
                        LinearProgressIndicator(
                            progress = { proteinRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = ProteinAccent,
                            trackColor = ProteinAccent.copy(alpha = 0.16f)
                        )
                    }

                    // 次要折叠展示碳水与脂肪
                    Text(
                        text = "碳水 ${state.carbsConsumedG}g · 脂肪 ${state.fatConsumedG}g",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 周热量银行胶囊条
            state.weeklyBank?.let { bank ->
                val balance = bank.bankBalanceBeforeToday
                val balanceLabel = when {
                    balance > 0 -> "周热量银行已攒 +$balance kcal"
                    balance < 0 -> "周银行待平摊 $balance kcal（已温和分摊）"
                    else -> if (state.profile.weekendReserveEnabled) {
                        "周热量银行：周末聚餐弹性预留生效中"
                    } else {
                        "周热量银行：本周预算均衡执行中"
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                        .clickable { onToggleBankDetail() }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🏦 $balanceLabel",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (showWeekBankDetail) "收起" else "看7天",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(visible = showWeekBankDetail) {
                        WeekBankMiniChart(bank = bank)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBankMiniChart(bank: WeeklyBankState) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
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
                            .width(26.dp)
                            .height(46.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((46f * (ratio / 1.2f)).dp)
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

@Composable
private fun WeightAndWaterRow(
    state: DashboardUiState,
    onWeightClick: () -> Unit,
    onAddWater: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 晨重卡片
        Card(
            onClick = onWeightClick,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MonitorWeight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("空腹晨重", style = MaterialTheme.typography.labelMedium)
                }

                val weightText = state.selectedDateWeightKg?.let { "${String.format("%.1f", it)} kg" }
                    ?: "点击记录"
                Text(
                    text = weightText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                val emaNote = state.selectedDateEmaKg?.let {
                    "7日平滑 ${String.format("%.1f", it)} kg"
                } ?: "记录后自动计算平滑趋势"
                Text(
                    text = emaNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 快捷饮水卡片
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = WaterAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("饮水打卡", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        text = "+250ml",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = WaterAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(WaterAccent.copy(alpha = 0.14f))
                            .clickable { onAddWater(250) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "${state.waterMl} ml",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                val waterProgress = (state.waterMl.toFloat() / state.profile.waterTargetMl.coerceAtLeast(500)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { waterProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = WaterAccent,
                    trackColor = WaterAccent.copy(alpha = 0.16f)
                )
            }
        }
    }
}

@Composable
private fun MealGroupCard(
    mealType: MealType,
    logs: List<MealLogEntity>,
    onAddClick: () -> Unit,
    onCopyYesterday: () -> Unit,
    onSaveAsCombo: () -> Unit,
    onDeleteLog: (MealLogEntity) -> Unit
) {
    val mealKcal = logs.sumOf { it.totalKcal.toDouble() }.toInt()
    val mealProt = logs.sumOf { it.proteinG.toDouble() }.toInt()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${mealType.emoji} ${mealType.label}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (logs.isNotEmpty()) {
                        Text(
                            text = "$mealKcal kcal · 蛋白质 ${mealProt}g",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logs.isEmpty()) {
                        TextButton(onClick = onCopyYesterday) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("复制昨日", style = MaterialTheme.typography.labelMedium)
                        }
                    } else if (logs.size >= 2) {
                        IconButton(onClick = onSaveAsCombo) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "存为套餐模板",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    FilledTonalIconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加${mealType.label}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (logs.isEmpty()) {
                Text(
                    text = "暂未记录，点右上角 + 秒选常吃模板或 AI 识餐",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                logs.forEachIndexed { idx, item ->
                    if (idx > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = item.foodName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${item.portionDesc} · 蛋白 ${String.format("%.1f", item.proteinG)}g",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${item.totalKcal.toInt()} kcal",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { onDeleteLog(item) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncompleteDayGuardCard(
    isIncomplete: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("今日饮食未记全（防算法污染保护）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = "若外出聚餐忘记或漏记，打开此开关可避免拉偏真实代谢 TDEE 与周银行结余",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isIncomplete, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun WeightInputDialog(
    initialWeight: Float,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var weightInput by remember { mutableStateOf(String.format("%.1f", initialWeight)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录晨重 ($selectedDate)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "建议每天早上起床排空后称重。单日涨跌多为水分，请以 7 日平滑趋势为准。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("体重 (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = weightInput.toFloatOrNull()
                    if (v != null && v in 30f..250f) onConfirm(v)
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

@Composable
private fun SaveComboDialog(
    mealType: MealType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("我的常吃${mealType.label}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存为常吃套餐模板") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("套餐名称（下次可一键整餐入账）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) }
            ) {
                Text("保存模板")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
