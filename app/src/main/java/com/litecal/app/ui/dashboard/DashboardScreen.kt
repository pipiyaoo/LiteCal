package com.litecal.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.litecal.app.data.local.ExerciseLogEntity
import com.litecal.app.data.local.MealLogEntity
import com.litecal.app.domain.model.MealType
import com.litecal.app.ui.DashboardUiState
import com.litecal.app.ui.theme.ProteinAccent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onBackToToday: () -> Unit,
    onOpenQuickAdd: (MealType, Int) -> Unit,
    onCopyYesterdayMeal: (MealType) -> Unit,
    onDeleteMealLog: (MealLogEntity) -> Unit,
    onLogExercise: (String, Float) -> Unit,
    onDeleteExercise: (ExerciseLogEntity) -> Unit,
    onRecordWeight: (Float) -> Unit,
    onAddWater: (Int) -> Unit,
    onToggleIncompleteDay: (Boolean) -> Unit,
    onNavigateToTrends: () -> Unit
) {
    var showWeightDialog by remember { mutableStateOf(false) }
    var showExerciseDialog by remember { mutableStateOf(false) }
    var showMacros by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DateHeaderBar(
                selectedDate = state.selectedDate,
                isIncompleteDay = state.isIncompleteDay,
                showMacros = showMacros,
                onPrevDay = onPrevDay,
                onNextDay = onNextDay,
                onBackToToday = onBackToToday,
                onToggleIncompleteDay = { onToggleIncompleteDay(!state.isIncompleteDay) },
                onToggleMacros = { showMacros = !showMacros }
            )
        }

        if (state.isIncompleteDay) {
            item {
                IncompleteDayBanner(
                    onRestore = { onToggleIncompleteDay(false) }
                )
            }
        }

        item {
            DualCoreHeroCard(
                state = state,
                showMacros = showMacros,
                onToggleMacros = { showMacros = !showMacros },
                onNavigateToTrends = onNavigateToTrends
            )
        }

        item {
            WeightAndWaterStrip(
                state = state,
                onWeightClick = { showWeightDialog = true },
                onAddWater = onAddWater
            )
        }

        item {
            UnifiedMealsCard(
                mealLogsByType = state.mealLogsByType,
                onAddClick = { mealType -> onOpenQuickAdd(mealType, 0) },
                onCopyYesterday = onCopyYesterdayMeal,
                onDeleteLog = onDeleteMealLog
            )
        }

        item {
            ExerciseBurnCard(
                logs = state.exerciseLogs,
                totalBurnKcal = state.todayExerciseBurnKcal,
                onAddClick = { showExerciseDialog = true },
                onDeleteLog = onDeleteExercise
            )
        }
    }

    if (showWeightDialog) {
        WeightInputDialog(
            initialWeight = state.selectedDateWeightKg ?: state.latestWeightKg ?: state.profile.startWeightKg,
            onDismiss = { showWeightDialog = false },
            onConfirm = { w ->
                onRecordWeight(w)
                showWeightDialog = false
            }
        )
    }

    if (showExerciseDialog) {
        ExerciseInputDialog(
            onDismiss = { showExerciseDialog = false },
            onConfirm = { name, burn ->
                onLogExercise(name, burn)
                showExerciseDialog = false
            }
        )
    }
}

@Composable
private fun DateHeaderBar(
    selectedDate: LocalDate,
    isIncompleteDay: Boolean,
    showMacros: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onBackToToday: () -> Unit,
    onToggleIncompleteDay: () -> Unit,
    onToggleMacros: () -> Unit
) {
    val today = LocalDate.now()
    val isToday = selectedDate == today
    val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val weekLabel = dayNames[selectedDate.dayOfWeek.value - 1]
    val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("MM月dd日"))
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevDay, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一天")
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isToday) "$dateStr · 今天" else "$dateStr $weekLabel",
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNextDay, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "后一天")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多选项",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text("今日未记全")
                        },
                        trailingIcon = {
                            if (isIncompleteDay) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleIncompleteDay()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("碳水/脂肪") },
                        trailingIcon = {
                            if (showMacros) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleMacros()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IncompleteDayBanner(onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "今日未记全 · 不计入代谢",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "恢复",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onRestore() }
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DualCoreHeroCard(
    state: DashboardUiState,
    showMacros: Boolean,
    onToggleMacros: () -> Unit,
    onNavigateToTrends: () -> Unit
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 左侧剩余大卡圆环（点按可切换显示碳水/脂肪）
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .clickable { onToggleMacros() }
                ) {
                    Canvas(modifier = Modifier.size(96.dp)) {
                        val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
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

                // 右侧：摄入概览 + 蛋白质进度条 + 周热量银行紧凑胶囊
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已吃 ${state.consumedKcal} / ${state.dynamicTargetKcal}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.todayExerciseBurnKcal > 0) {
                            Text(
                                text = "含运动 +${state.todayExerciseBurnKcal}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "蛋白质",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ProteinAccent
                            )
                            Text(
                                text = "${state.proteinConsumedG}/${state.proteinTargetG}g",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ProteinAccent
                            )
                        }
                        LinearProgressIndicator(
                            progress = { proteinRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = ProteinAccent,
                            trackColor = ProteinAccent.copy(alpha = 0.16f)
                        )
                    }

                    state.weeklyBank?.let { bank ->
                        val balance = bank.bankBalanceBeforeToday
                        val pillLabel = when {
                            balance > 0 -> "本周 +$balance ›"
                            balance < 0 -> "本周 $balance ›"
                            else -> if (state.profile.weekendReserveEnabled) {
                                "周末预留 ›"
                            } else {
                                "周银行 ›"
                            }
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = pillLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { onNavigateToTrends() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showMacros) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "碳水 ${state.carbsConsumedG} g",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "脂肪 ${state.fatConsumedG} g",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightAndWaterStrip(
    state: DashboardUiState,
    onWeightClick: () -> Unit,
    onAddWater: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：晨重
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onWeightClick() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MonitorWeight,
                    contentDescription = "空腹晨重",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    val weightText = state.selectedDateWeightKg?.let { "${String.format("%.1f", it)} kg" }
                        ?: "记晨重"
                    Text(
                        text = weightText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val emaNote = state.selectedDateEmaKg?.let {
                        "均值 ${String.format("%.1f", it)}"
                    } ?: "点按记录"
                    Text(
                        text = emaNote,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 右侧：饮水
            Row(
                modifier = Modifier
                    .weight(1.15f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "饮水",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                    Column {
                        Text(
                            text = "${state.waterMl}/${state.profile.waterTargetMl} ml",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "饮水",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "+250",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { onAddWater(250) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun UnifiedMealsCard(
    mealLogsByType: Map<MealType, List<MealLogEntity>>,
    onAddClick: (MealType) -> Unit,
    onCopyYesterday: (MealType) -> Unit,
    onDeleteLog: (MealLogEntity) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MealType.entries.forEachIndexed { idx, mealType ->
                if (idx > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                val logs = mealLogsByType[mealType].orEmpty()
                MealSectionRow(
                    mealType = mealType,
                    logs = logs,
                    onAddClick = { onAddClick(mealType) },
                    onCopyYesterday = { onCopyYesterday(mealType) },
                    onDeleteLog = onDeleteLog
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealSectionRow(
    mealType: MealType,
    logs: List<MealLogEntity>,
    onAddClick: () -> Unit,
    onCopyYesterday: () -> Unit,
    onDeleteLog: (MealLogEntity) -> Unit
) {
    val mealKcal = logs.sumOf { it.totalKcal.toDouble() }.roundToInt()
    val mealProt = logs.sumOf { it.proteinG.toDouble() }.roundToInt()
    var mealMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (logs.isEmpty()) Modifier.clickable { onAddClick() } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${mealType.emoji} ${mealType.label}",
                    style = MaterialTheme.typography.titleMedium
                )
                if (logs.isNotEmpty()) {
                    Text(
                        text = "$mealKcal kcal · 蛋白${mealProt}g",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(
                        onClick = { mealMenuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "${mealType.label}更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = mealMenuExpanded,
                        onDismissRequest = { mealMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("复制昨日") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                mealMenuExpanded = false
                                onCopyYesterday()
                            }
                        )
                    }
                }

                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加${mealType.label}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (logs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            logs.forEach { item ->
                key(item.id) {
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onDeleteLog(item)
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "左滑删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = item.foodName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.portionDesc} · 蛋白 ${String.format("%.1f", item.proteinG)}g",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${item.totalKcal.roundToInt()} kcal",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseBurnCard(
    logs: List<ExerciseLogEntity>,
    totalBurnKcal: Int,
    onAddClick: () -> Unit,
    onDeleteLog: (ExerciseLogEntity) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (logs.isEmpty()) Modifier.clickable { onAddClick() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🏃 运动消耗",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (totalBurnKcal > 0) {
                        Text(
                            text = "+$totalBurnKcal 大卡",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加运动消耗",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                logs.forEach { item ->
                    key(item.id) {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    onDeleteLog(item)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "左滑删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Text(
                                    text = "+${item.burnKcal.roundToInt()} 大卡",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
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
private fun ExerciseInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Float) -> Unit
) {
    var burnText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    val burnVal = burnText.toFloatOrNull() ?: 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录运动消耗") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = burnText,
                    onValueChange = { burnText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("消耗热量 (大卡)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("运动项目（选填）") },
                    placeholder = { Text("如：力量训练、跑步") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (burnVal > 0f) onConfirm(nameText, burnVal) },
                enabled = burnVal > 0f
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
private fun WeightInputDialog(
    initialWeight: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var weightInput by remember { mutableStateOf(String.format("%.1f", initialWeight)) }
    val validWeight = weightInput.toFloatOrNull()?.takeIf { it in 30f..250f }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录体重") },
        text = {
            OutlinedTextField(
                value = weightInput,
                onValueChange = { weightInput = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("体重 (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { validWeight?.let(onConfirm) },
                enabled = validWeight != null
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
