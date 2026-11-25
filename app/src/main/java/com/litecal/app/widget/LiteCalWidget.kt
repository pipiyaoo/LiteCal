package com.litecal.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.litecal.app.LiteCalApplication
import com.litecal.app.MainActivity
import com.litecal.app.data.repository.WidgetSnapshot
import java.time.LocalDate
import kotlin.math.abs

object LiteCalWidgetUpdater {
    suspend fun updateAll(context: Context) {
        LiteCalWidget().updateAll(context)
    }
}

class LiteCalWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as LiteCalApplication
        val snapshot = runCatching {
            app.repository.computeTodaySnapshotOnce(LocalDate.now())
        }.getOrElse {
            WidgetSnapshot(
                remainingKcal = 1800,
                consumedKcal = 0,
                targetKcal = 1800,
                proteinConsumedG = 0,
                proteinTargetG = 105,
                waterMl = 0,
                waterTargetMl = 2000
            )
        }

        provideContent {
            GlanceTheme {
                WidgetCardContent(context = context, snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun WidgetCardContent(context: Context, snapshot: WidgetSnapshot) {
    val isOver = snapshot.remainingKcal < 0
    val bgColor = ColorProvider(Color(0xFF0F172A))
    val mintColor = ColorProvider(if (isOver) Color(0xFFFB7185) else Color(0xFF2DD4BF))
    val textWhite = ColorProvider(Color(0xFFF8FAFC))
    val subText = ColorProvider(Color(0xFF94A3B8))
    val pillBg = ColorProvider(Color(0xFF1E293B))

    val quickAddIntent = Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_QUICK_ADD
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val aiScanIntent = Intent(context, MainActivity::class.java).apply {
        action = MainActivity.ACTION_AI_SCAN
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openHomeIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(bgColor)
            .clickable(actionStartActivity(openHomeIntent))
            .padding(14.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：剩余大卡 + 蛋白质与饮水进度
            Column(
                modifier = GlanceModifier.defaultWeight()
            ) {
                Text(
                    text = if (isOver) "今日已超支" else "今日还可吃",
                    style = TextStyle(color = subText, fontSize = 12.sp)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${abs(snapshot.remainingKcal)}",
                        style = TextStyle(
                            color = mintColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "kcal",
                        style = TextStyle(color = subText, fontSize = 12.sp)
                    )
                }
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "蛋白质 ${snapshot.proteinConsumedG}/${snapshot.proteinTargetG}g · 饮水 ${snapshot.waterMl}ml",
                    style = TextStyle(color = textWhite, fontSize = 12.sp)
                )
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // 右侧：3 个高频快捷触点
            Column(
                horizontalAlignment = Alignment.End
            ) {
                WidgetActionPill(
                    label = "+ 记一餐",
                    bg = ColorProvider(Color(0xFF0D9488)),
                    fg = textWhite,
                    modifier = GlanceModifier.clickable(actionStartActivity(quickAddIntent))
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                WidgetActionPill(
                    label = "✨ AI识餐",
                    bg = pillBg,
                    fg = textWhite,
                    modifier = GlanceModifier.clickable(actionStartActivity(aiScanIntent))
                )
                Spacer(modifier = GlanceModifier.height(6.dp))
                WidgetActionPill(
                    label = "💧 +250ml",
                    bg = pillBg,
                    fg = ColorProvider(Color(0xFF38BDF8)),
                    modifier = GlanceModifier.clickable(actionRunCallback<AddWaterActionCallback>())
                )
            }
        }
    }
}

@Composable
private fun WidgetActionPill(
    label: String,
    bg: ColorProvider,
    fg: ColorProvider,
    modifier: GlanceModifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .cornerRadius(10.dp)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = fg,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

class AddWaterActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val app = context.applicationContext as LiteCalApplication
        app.repository.addWater(LocalDate.now(), 250)
    }
}
