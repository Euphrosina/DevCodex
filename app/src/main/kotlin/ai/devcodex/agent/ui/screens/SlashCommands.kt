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

    fun definitions(context: Context): List<Def> = listOf(
        Def("/new", context.getString(ai.devcodex.agent.R.string.slash_new)),
        Def("/clear", context.getString(ai.devcodex.agent.R.string.slash_clear)),
        Def("/compact", context.getString(ai.devcodex.agent.R.string.slash_compact)),
        Def("/model", context.getString(ai.devcodex.agent.R.string.slash_model)),
        Def("/stats", context.getString(ai.devcodex.agent.R.string.slash_stats)),
        Def("/export", context.getString(ai.devcodex.agent.R.string.slash_export)),
        Def("/copy", context.getString(ai.devcodex.agent.R.string.slash_copy)),
        Def("/think", context.getString(ai.devcodex.agent.R.string.slash_think)),
        Def("/ctx", context.getString(ai.devcodex.agent.R.string.slash_ctx)),
        Def("/skills", context.getString(ai.devcodex.agent.R.string.slash_skills)),
        Def("/help", context.getString(ai.devcodex.agent.R.string.slash_help)),
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
                val ctx = context()
                val repo = ai.devcodex.agent.data.SkillRepo(ctx)
                val list = repo.list()
                vm.appendLocalNotice(ctx.getString(ai.devcodex.agent.R.string.slash_skills_header_fmt, list.size) + "\n" + list.joinToString("\n") { "· /${it.name} — ${it.description}" })
                true
            }
            "help" -> {
                val ctx = context()
                val defs = definitions(ctx)
                vm.appendLocalNotice(buildString {
                    append(ctx.getString(ai.devcodex.agent.R.string.slash_help_header))
                    append("\n")
                    defs.forEach { append("${it.name.padEnd(10)} ${it.desc}\n") }
                    append("\n")
                    append(ctx.getString(ai.devcodex.agent.R.string.slash_help_footer))
                })
                true
            }
            else -> {
                // 技能触发：/skillname → 提示 AI 读取该技能
                val skills = runCatching { ai.devcodex.agent.data.SkillRepo(context()).list() }.getOrDefault(emptyList())
                val hit = skills.firstOrNull { it.name.equals(cmd, true) }
                if (hit != null) {
                    vm.send(context().getString(ai.devcodex.agent.R.string.slash_invoke_fmt, hit.name))
                    true
                } else false
            }
        }
    }
}
