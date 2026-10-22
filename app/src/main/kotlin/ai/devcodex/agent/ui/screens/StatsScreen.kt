package ai.devcodex.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TreeMap

private val Mono = FontFamily.Monospace

/** 聚合数据（从会话 JSON 全量统计） */
data class FullStats(
    val byDay: Map<Long, DayUsage>,
    val byModel: Map<String, ModelUsage>,
    val totalRequests: Int,
    val totalSessions: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedTokens: Long,
    val peakDayTokens: Long,
    val curStreak: Int,
    val maxStreak: Int,
    val toolCalls: Int = 0,
) {
    val totalTokens get() = inputTokens + outputTokens
    val cacheHitRate: Int get() = if (inputTokens > 0) (cachedTokens * 100 / inputTokens).toInt() else 0
    val freshInput get() = inputTokens - cachedTokens
}

data class DayUsage(
    val messages: Int = 0,
    val input: Long = 0,
    val output: Long = 0,
    val cached: Long = 0,
    val requests: Int = 0,
)

data class ModelUsage(
    val input: Long = 0,
    val output: Long = 0,
    val requests: Int = 0,
)

/** 常见模型粗估价格（美元/百万 token：输入, 输出） */
private fun modelPrice(model: String): Pair<Double, Double>? = when {
    model.contains("gpt-4o", true) -> 2.5 to 10.0
    model.contains("gpt-4.1", true) -> 2.0 to 8.0
    model.contains("gpt-5", true) || Regex("(?i)\\bo[34]\\b").containsMatchIn(model) -> 1.25 to 10.0
    model.contains("claude-opus", true) -> 15.0 to 75.0
    model.contains("claude-sonnet", true) -> 3.0 to 15.0
    model.contains("claude-3-5-haiku", true) || model.contains("haiku", true) -> 0.8 to 4.0
    model.contains("deepseek-chat", true) || model.contains("deepseek-v3", true) -> 0.27 to 1.1
    model.contains("deepseek", true) -> 0.55 to 2.19
    model.contains("gemini-2.5-pro", true) -> 1.25 to 10.0
    model.contains("gemini", true) -> 0.075 to 0.3
    model.contains("glm-4", true) || model.contains("glm-5", true) -> 0.6 to 2.2
    model.contains("qwen", true) -> 0.4 to 1.2
    model.contains("kimi", true) -> 0.6 to 2.5
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: ai.devcodex.agent.AppViewModel, onBack: () -> Unit) {
    var stats by remember { mutableStateOf<FullStats?>(null) }
    var period by remember { mutableStateOf(4) }  // 0日 1周 2月 3年 4累计

    LaunchedEffect(Unit) {
        stats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val sessions = vm.sessionRepo.list()
            val byDay = TreeMap<Long, DayUsage>()
            val byModel = HashMap<String, ModelUsage>()
            var total = 0
            sessions.forEach { s ->
                s.messages.forEach { m ->
                    if (m.inputTokens + m.outputTokens == 0L && m.role != "user") return@forEach
                    total++
                    val c = Calendar.getInstance()
                    c.timeInMillis = if (m.timestamp > 0) m.timestamp else s.updatedAt
                    val key = c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
                    val day = byDay.getOrPut(key) { DayUsage() }
                    byDay[key] = day.copy(
                        messages = day.messages + 1,
                        input = day.input + m.inputTokens,
                        output = day.output + m.outputTokens,
                        cached = day.cached + m.cachedTokens,
                        requests = day.requests + if (m.outputTokens > 0) 1 else 0,
                    )
                    val mu = byModel.getOrPut(m.model ?: "未知") { ModelUsage() }
                    byModel[m.model ?: "未知"] = mu.copy(
                        input = mu.input + m.inputTokens,
                        output = mu.output + m.outputTokens,
                        requests = mu.requests + if (m.outputTokens > 0) 1 else 0,
                    )
                }
            }
            // 连续天数
            val days = byDay.keys.sortedDescending()
            var cur = 0
            val today = Calendar.getInstance()
            val tKey = today.get(Calendar.YEAR) * 10000L + (today.get(Calendar.MONTH) + 1) * 100 + today.get(Calendar.DAY_OF_MONTH)
            if (days.isNotEmpty()) {
                var probe = tKey
                if (days[0] == probe || days[0] == prevDayKey(tKey)) {
                    if (days[0] == prevDayKey(tKey)) probe = tKey
                    while (byDay.containsKey(probe)) { cur++; probe = prevDayKey(probe) }
                }
            }
            var maxS = 0; var run = 0; var prev = -1L
            days.sorted().forEach { d ->
                run = if (prev > 0 && d == nextDayKey(prev)) run + 1 else 1
                if (run > maxS) maxS = run
                prev = d
            }
            var inTok = 0L; var outTok = 0L; var cacheTok = 0L; var tools = 0
            byDay.values.forEach { inTok += it.input; outTok += it.output; cacheTok += it.cached }
            var peak = 0L
            byDay.values.forEach { val t = it.input + it.output; if (t > peak) peak = t }
            sessions.forEach { s -> s.messages.forEach { m -> tools += m.toolCalls.size + if (m.role == "tool") 1 else 0 } }
            FullStats(byDay, byModel, total, sessions.size, inTok, outTok, cacheTok, peak, cur, maxS, tools)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        val st = stats
        if (st == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        // 周期过滤
        val periodDays = when (period) { 0 -> 1; 1 -> 7; 2 -> 30; 3 -> 365; else -> -1 }
        val filtered = if (periodDays < 0) st.byDay else {
            val cal = Calendar.getInstance()
            st.byDay.filter { (k, _) ->
                cal.timeInMillis = System.currentTimeMillis()
                cal.add(Calendar.DAY_OF_YEAR, -periodDays)
                val from = cal.get(Calendar.YEAR) * 10000L + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
                k >= from
            }
        }
        val fIn = filtered.values.sumOf { it.input }
        val fOut = filtered.values.sumOf { it.output }
        val fCached = filtered.values.sumOf { it.cached }
        val fReq = filtered.values.sumOf { it.requests }
        val fCost = filtered.entries.sumOf { (k, _) -> 0.0 } + run {
            // 按模型估算（周期内粗算：模型总额 × 周期占比）
            val ratio = if (st.totalTokens > 0) (fIn + fOut).toDouble() / st.totalTokens else 0.0
            st.byModel.entries.mapNotNull { (model, u) ->
                val price = modelPrice(model) ?: return@mapNotNull null
                (u.input / 1e6 * price.first + u.output / 1e6 * price.second) * ratio
            }.sum()
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 周期选择
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("每日", "每周", "每月", "每年", "累计").forEachIndexed { i, label ->
                    FilterChip(selected = period == i, onClick = { period = i }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                }
            }
            // 总览卡
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Token 使用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmtTok(fIn + fOut), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("  tokens", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatSmall("请求", "$fReq")
                        StatSmall("工具调用", "${st.toolCalls}")
                        StatSmall("缓存命中", if (fIn > 0) "${fCached * 100 / fIn}%" else "-")
                    }
                    if (fCost > 0) {
                        Text(
                            "周期费用 ≈ ${"%.2f".format(fCost)}（按公开价目估算）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            // 缓存节省 + 输出
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                    Text("缓存节省", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtTok(st.cachedTokens), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF9E9E24))
                } }
                Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                    Text("累计输出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtTok(st.outputTokens), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFFEA8600))
                } }
            }
            // 连续/峰值
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                    Text("当前连续", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${st.curStreak} 天", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } }
                Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                    Text("最长连续", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${st.maxStreak} 天", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                } }
                Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
                    Text("单日峰值", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmtTok(st.peakDayTokens), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                } }
            }
            // Token 构成（堆叠条）
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Token 构成", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.padding(6.dp))
                    val totalF = (fIn + fOut).coerceAtLeast(1)
                    val pFresh = (st.freshInputOf(fIn, fCached) * 100 / totalF).toInt().coerceIn(0, 100)
                    val pCache = (fCached * 100 / totalF).toInt().coerceIn(0, 100)
                    val pOut = (fOut * 100 / totalF).toInt().coerceIn(0, 100)
                    Row(Modifier.fillMaxWidth().height(14.dp).clip0()) {
                        Box(Modifier.weight(pFresh.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Color(0xFF4285F4)))
                        Box(Modifier.weight(pCache.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Color(0xFF9E9E24)))
                        Box(Modifier.weight(pOut.coerceAtLeast(1).toFloat()).fillMaxHeight().background(Color(0xFFEA8600)))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        LegendDot(Color(0xFF4285F4), "未缓存输入 ${fmtTok(fIn - fCached)}")
                        LegendDot(Color(0xFF9E9E24), "缓存读取 ${fmtTok(fCached)}")
                        LegendDot(Color(0xFFEA8600), "输出 ${fmtTok(fOut)}")
                    }
                }
            }
            // 最近 7 天统计
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -6)
                    val last7 = (0..6).map { d ->
                        val c = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis }
                        c.add(Calendar.DAY_OF_YEAR, d)
                        val key = c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
                        st.byDay[key]?.let { (it.input + it.output).toInt() } ?: 0
                    }
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.Bottom) {
                        last7.forEachIndexed { i, v ->
                            val maxV = (last7.max() ?: 1).coerceAtLeast(1)
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.62f)
                                        .height((v.toFloat() / maxV * 34).coerceAtLeast(3f).dp)
                                        .background(
                                            if (v > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f + 0.6f * v / maxV)
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(4.dp)
                                        )
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    listOf("一", "二", "三", "四", "五", "六", "日")[(cal.get(Calendar.DAY_OF_WEEK) + 5 + i) % 7],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("最近 7 天 ${(last7.sum() / 1000)}k tokens · 活跃 ${last7.count { it > 0 }} 天", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 活跃日历（近 14 周）
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("消息热力图", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        Text("近 14 周", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.padding(6.dp))
                    val empty = MaterialTheme.colorScheme.surfaceVariant
                    val primary = MaterialTheme.colorScheme.primary
                    // 列式日历：每列一周（周一至周日行），等宽
                    val weeks = 14
                    val today = Calendar.getInstance()
                    val start = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -(weeks * 7))
                        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (w in 0 until weeks) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (d in 0..6) {
                                    val c = Calendar.getInstance().apply { timeInMillis = start.timeInMillis }
                                    c.add(Calendar.DAY_OF_YEAR, w * 7 + d)
                                    if (c.timeInMillis > today.timeInMillis) {
                                        Box(Modifier.size(16.dp))
                                    } else {
                                        val key = c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
                                        val msgs = st.byDay[key]?.messages ?: 0
                                        val color = when {
                                            msgs <= 0 -> empty
                                            msgs < 5 -> primary.copy(alpha = 0.25f)
                                            msgs < 20 -> primary.copy(alpha = 0.5f)
                                            msgs < 50 -> primary.copy(alpha = 0.75f)
                                            else -> primary
                                        }
                                        Box(Modifier.size(16.dp).background(color, RoundedCornerShape(4.dp)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // 模型累计排行
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("模型累计", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.padding(6.dp))
                    val models = st.byModel.entries.sortedByDescending { it.value.input + it.value.output }.take(10)
                    val maxTok = (models.firstOrNull()?.let { it.value.input + it.value.output } ?: 1L).coerceAtLeast(1)
                    models.forEach { (model, u) ->
                        val tok = u.input + u.output
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(model.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall, fontFamily = Mono, maxLines = 1, modifier = Modifier.weight(1f))
                                Text("${fmtTok(tok)} · ${u.requests}次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth(tok.toFloat() / maxTok)
                                    .height(6.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
            // 设备与应用信息
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("设备信息", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.padding(4.dp))
                    val sp = vm.getPreferences()
                    val launches = sp.getInt("launch_count", 0) + 1
                    sp.edit().putInt("launch_count", launches).apply()
                    InfoRow("应用版本", "v${ai.devcodex.agent.BuildConfig.VERSION_NAME} (${ai.devcodex.agent.BuildConfig.VERSION_CODE})")
                    InfoRow("启动次数", "$launches 次")
                    InfoRow("会话总数", "${st.totalSessions} 个")
                    InfoRow("设备", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    InfoRow("系统", "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    InfoRow("架构", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "-")
                }
            }
            Spacer(Modifier.padding(20.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = Mono)
    }
}

private fun FullStats.freshInputOf(fIn: Long, fCached: Long) = (fIn - fCached).coerceAtLeast(0)
private fun Modifier.clip0(): Modifier = this

@Composable
private fun StatSmall(label: String, value: String) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

private fun fmtTok(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1e6)
    n >= 1_000 -> "%.1fk".format(n / 1e3)
    else -> "$n"
}

private fun prevDayKey(key: Long): Long {
    val c = Calendar.getInstance()
    c.set(key.toString().substring(0, 4).toInt(), key.toString().substring(4, 6).toInt() - 1, key.toString().substring(6, 8).toInt())
    c.add(Calendar.DAY_OF_YEAR, -1)
    return c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

private fun nextDayKey(key: Long): Long {
    val c = Calendar.getInstance()
    c.set(key.toString().substring(0, 4).toInt(), key.toString().substring(4, 6).toInt() - 1, key.toString().substring(6, 8).toInt())
    c.add(Calendar.DAY_OF_YEAR, 1)
    return c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}
