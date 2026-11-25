package com.litecal.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.litecal.app.domain.engine.MetabolismEngine
import com.litecal.app.domain.model.ActivityLevel
import com.litecal.app.domain.model.Gender
import com.litecal.app.domain.model.UserProfile
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    initialProfile: UserProfile,
    onComplete: (UserProfile) -> Unit
) {
    var gender by remember { mutableStateOf(initialProfile.gender) }
    var ageText by remember { mutableStateOf(initialProfile.age.toString()) }
    var heightText by remember { mutableStateOf(initialProfile.heightCm.roundToInt().toString()) }
    var startWeightText by remember { mutableStateOf(initialProfile.startWeightKg.toString()) }
    var targetWeightText by remember { mutableStateOf(initialProfile.targetWeightKg.toString()) }
    var activityLevel by remember { mutableStateOf(initialProfile.activityLevel) }
    var weeklyLossRate by remember { mutableFloatStateOf(initialProfile.weeklyLossRateKg) }
    var weekendReserve by remember { mutableStateOf(initialProfile.weekendReserveEnabled) }

    val draftProfile = UserProfile(
        onboardingCompleted = true,
        gender = gender,
        age = ageText.toIntOrNull()?.coerceIn(14, 90) ?: 28,
        heightCm = heightText.toFloatOrNull()?.coerceIn(130f, 220f) ?: 172f,
        startWeightKg = startWeightText.toFloatOrNull()?.coerceIn(35f, 200f) ?: 72f,
        targetWeightKg = targetWeightText.toFloatOrNull()?.coerceIn(35f, 180f) ?: 64f,
        activityLevel = activityLevel,
        weeklyLossRateKg = weeklyLossRate,
        weekendReserveEnabled = weekendReserve,
        weekendDailySaveKcal = 120
    )

    val bmr = MetabolismEngine.calculateBmr(draftProfile, draftProfile.startWeightKg)
    val tdee = MetabolismEngine.calculateInitialTdee(draftProfile, draftProfile.startWeightKg)
    val proteinTarget = MetabolismEngine.calculateProteinTargetG(draftProfile)
    val previewBank = MetabolismEngine.computeWeeklyCalorieBank(
        today = LocalDate.of(2026, 11, 23), // 周一基准
        profile = draftProfile,
        estimatedTdee = tdee,
        bmr = bmr,
        weekIntakes = emptyMap(),
        incompleteDates = emptySet()
    )
    val weekendTarget = previewBank.days.first { it.dayLabel == "周六" }.plannedKcal

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "欢迎使用 轻刻 LiteCal",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "纯本地隐私存储 · 热量与蛋白质双核追踪 · 自适应真实代谢校准",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 实时科学预算预览卡
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "初始科学预算预览（记录满 8 天后开启自适应校准）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PreviewMetric("工作日可吃", "${previewBank.todayPlannedBase} kcal")
                        PreviewMetric("周末可吃", "$weekendTarget kcal")
                        PreviewMetric("蛋白质目标", "${proteinTarget} g/天")
                    }
                    Text(
                        text = "基础代谢 BMR $bmr kcal · 估算总消耗 TDEE $tdee kcal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 性别与年龄
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Gender.entries.forEach { g ->
                    FilterChip(
                        selected = gender == g,
                        onClick = { gender = g },
                        label = { Text("性别：${g.label}") }
                    )
                }
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it.filter(Char::isDigit).take(2) },
                    label = { Text("年龄") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // 身高、当前体重、目标体重
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("身高 (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = startWeightText,
                    onValueChange = { startWeightText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("当前体重 (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = targetWeightText,
                    onValueChange = { targetWeightText = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("目标体重 (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // 日常活动量
            Text("日常活动强度", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActivityLevel.entries.forEach { level ->
                    FilterChip(
                        selected = activityLevel == level,
                        onClick = { activityLevel = level },
                        label = { Text(level.title) }
                    )
                }
            }
            Text(
                text = activityLevel.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 期望每周减脂速率
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("期望每周减脂速度", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${String.format("%.2f", weeklyLossRate)} kg / 周 (日缺口 ~${MetabolismEngine.calculateDailyDeficitTarget(weeklyLossRate)} kcal)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = weeklyLossRate,
                    onValueChange = { weeklyLossRate = (it * 20).roundToInt() / 20f },
                    valueRange = 0.25f..0.85f
                )
                Text(
                    text = "推荐每周减 0.4 ~ 0.6 kg，不易掉肌肉也不容易暴食反弹",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 周热量银行：周末聚餐预留
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("开启「周热量银行 · 周末聚餐预留」", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "周一至周五每天微存 120 kcal，周六周日每天多享 +300 kcal 聚餐弹性额度",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = weekendReserve,
                        onCheckedChange = { weekendReserve = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = { onComplete(draftProfile) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("开启科学减脂账本", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PreviewMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
