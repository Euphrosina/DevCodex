package ai.devcodex.agent.ui.screens

import ai.devcodex.agent.AppViewModel

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TreeMap

private val Mono = FontFamily.Monospace

/** 每日消息量统计（从会话 JSON 聚合） */
data class DailyStats(
    val byDay: Map<Long, Int>,   // key = yyyyMMdd
    val totalMessages: Int,
    val totalSessions: Int,
    val activeDays: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: AppViewModel, onBack: () -> Unit) {
    var stats by remember { mutableStateOf<DailyStats?>(null) }
    LaunchedEffect(Unit) {
        stats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val sessions = vm.sessionRepo.list()
            val byDay = TreeMap<Long, Int>()
            var total = 0
            sessions.forEach { s ->
                s.messages.forEach { m ->
                    total++
                    val c = Calendar.getInstance()
                    c.timeInMillis = if (m.timestamp > 0) m.timestamp else s.updatedAt
                    val key = c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
                    byDay[key] = (byDay[key] ?: 0) + 1
                }
            }
            DailyStats(byDay, total, sessions.size, byDay.count { it.value > 0 })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用统计") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        val st = stats
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (st == null) {
                Text("统计中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            // ===== 汇总卡 =====
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("消息总数", st.totalMessages.toString(), Modifier.weight(1f))
                StatCard("会话数", st.totalSessions.toString(), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("活跃天数", st.activeDays.toString(), Modifier.weight(1f))
                StatCard("今日", todayCount(st).toString(), Modifier.weight(1f))
            }

            // ===== 热力图（最近 16 周，GitHub 风格）=====
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("消息热力图", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "最近 16 周",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    HeatmapGrid(st.byDay)
                }
            }

            // ===== 最近 7 天明细 =====
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("最近 7 天", style = MaterialTheme.typography.titleSmall)
                    val fmt = SimpleDateFormat("MM-dd EEE", Locale.CHINESE)
                    (6 downTo 0).forEach { ago ->
                        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -ago) }
                        val key = dayKey(c)
                        val n = st.byDay[key] ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                fmt.format(c.time),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = Mono,
                                modifier = Modifier.width(84.dp),
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .background(levelColor(n, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), MaterialTheme.colorScheme.primary), RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "$n",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = Mono,
                                modifier = Modifier.width(32.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** GitHub 风格：7 行(周一~周日) × 16 列(周) */
@Composable
private fun HeatmapGrid(byDay: Map<Long, Int>) {
    val primary = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val weeks = 16
    // 对齐周一开头
    val today = Calendar.getInstance()
    val end = today.clone() as Calendar
    val dow = (end.get(Calendar.DAY_OF_WEEK) + 5) % 7  // Mon=0
    end.add(Calendar.DAY_OF_YEAR, 6 - dow)
    val start = (end.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -(weeks * 7 - 1)) }

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0..6) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until weeks) {
                    val c = (start.clone() as Calendar).apply {
                        add(Calendar.DAY_OF_YEAR, col * 7 + row)
                    }
                    val inFuture = c.after(today)
                    val n = if (inFuture) -1 else (byDay[dayKey(c)] ?: 0)
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(
                                when {
                                    n < 0 -> emptyColor.copy(alpha = 0.3f)
                                    else -> levelColor(n, emptyColor, primary)
                                },
                                RoundedCornerShape(3.dp),
                            )
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(0, 3, 8, 20, 50).forEach { n ->
            Box(Modifier.size(10.dp).background(levelColor(n, emptyColor, primary), RoundedCornerShape(2.dp)))
        }
        Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun dayKey(c: Calendar): Long =
    c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)

private fun todayCount(st: DailyStats): Int = st.byDay[dayKey(Calendar.getInstance())] ?: 0

private fun levelColor(n: Int, empty: androidx.compose.ui.graphics.Color, primary: androidx.compose.ui.graphics.Color): androidx.compose.ui.graphics.Color = when {
    n <= 0 -> empty
    n < 3 -> primary.copy(alpha = 0.25f)
    n < 8 -> primary.copy(alpha = 0.45f)
    n < 20 -> primary.copy(alpha = 0.7f)
    n < 50 -> primary.copy(alpha = 0.85f)
    else -> primary
}
