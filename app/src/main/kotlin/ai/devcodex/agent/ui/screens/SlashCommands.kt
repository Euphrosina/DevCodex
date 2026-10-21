package ai.devcodex.agent.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 斜杠命令（opencode/openclaude 式）：
 * /new /clear /compact /model /stats /export /copy /think /skills /help
 */
object SlashCommands {

    data class Def(val name: String, val desc: String)

    val definitions = listOf(
        Def("/new", "新建会话"),
        Def("/clear", "清空当前会话消息"),
        Def("/compact", "压缩上下文（保留摘要）"),
        Def("/model", "打开模型选择器"),
        Def("/stats", "查看使用统计"),
        Def("/export", "导出会话为 Markdown 到工作区"),
        Def("/copy", "复制 AI 最后一条回复"),
        Def("/think", "设置推理等级：/think high"),
        Def("/ctx", "设置上下文档位：/ctx 1m"),
        Def("/skills", "列出已安装技能"),
        Def("/help", "显示命令帮助"),
    )

    /** 执行命令；返回 true=已处理（不发送 LLM） */
    fun execute(
        vm: ai.devcodex.agent.AppViewModel,
        raw: String,
        openModelPicker: () -> Unit,
        openStats: () -> Unit,
        context: () -> Context,
    ): Boolean {
        val parts = raw.trim().removePrefix("/").split(Regex("\\s+"), limit = 2)
        val cmd = parts.getOrNull(0)?.lowercase() ?: return false
        val arg = parts.getOrNull(1)?.trim() ?: ""
        return when (cmd) {
            "new" -> { vm.openSession(null); true }
            "clear" -> { vm.clearMessages(); true }
            "compact" -> { vm.compactNow(); true }
            "model" -> { openModelPicker(); true }
            "stats" -> { openStats(); true }
            "export" -> { vm.exportSession(); true }
            "copy" -> {
                val last = vm.activeSession.value?.messages?.lastOrNull { it.role == "assistant" && it.content.isNotBlank() }
                if (last != null) {
                    val cm = context().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("DevCodex", last.content))
                    true
                } else true
            }
            "think" -> {
                if (arg.isNotBlank()) vm.saveSettings { it.copy(reasoningEffort = arg.lowercase()) }
                true
            }
            "ctx" -> {
                val v = when (arg.lowercase()) {
                    "128k", "128" -> 131072
                    "256k", "256" -> 262144
                    "512k", "512" -> 524288
                    "1m" -> 1048576
                    "2m" -> 2097152
                    "default", "默认", "" -> 0
                    else -> arg.toIntOrNull() ?: 0
                }
                vm.setSessionContext(v)
                true
            }
            "skills" -> {
                val repo = ai.devcodex.agent.data.SkillRepo(context())
                val list = repo.list()
                vm.appendLocalNotice("已安装技能（${list.size}）：\n" + list.joinToString("\n") { "· /${it.name} — ${it.description}" })
                true
            }
            "help" -> {
                vm.appendLocalNotice(buildString {
                    append("斜杠命令：\n")
                    definitions.forEach { append("${it.name.padEnd(10)} ${it.desc}\n") }
                    append("\n技能也可用 /技能名 触发（详见技能页）")
                })
                true
            }
            else -> {
                // 技能触发：/skillname → 提示 AI 读取该技能
                val skills = runCatching { ai.devcodex.agent.data.SkillRepo(context()).list() }.getOrDefault(emptyList())
                val hit = skills.firstOrNull { it.name.equals(cmd, true) }
                if (hit != null) {
                    vm.send("（调用技能 ${hit.name}：先 skill_read 了解用法再执行任务）")
                    true
                } else false
            }
        }
    }
}
