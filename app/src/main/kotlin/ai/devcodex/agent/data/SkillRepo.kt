package ai.devcodex.agent.data

import android.content.Context
import java.io.File

/**
 * 技能库：agentskills.io 标准（SKILL.md = frontmatter(name/description) + markdown 正文）
 * 安装来源：本地文件 / URL / 内置示例
 * 注入方式：description 常驻系统提示词，正文按需由 AI 调 skill_read 读取（渐进披露）
 */
class SkillRepo(private val context: Context) {

    data class Skill(
        val name: String,
        val description: String,
        val content: String,
        val enabled: Boolean = true,
        val dir: File,
    )

    val root: File get() = File(context.filesDir, "skills").apply { mkdirs() }

    fun list(): List<Skill> =
        root.listFiles { f -> f.isDirectory }?.mapNotNull { dir ->
            val f = File(dir, "SKILL.md")
            if (!f.exists()) return@mapNotNull null
            parse(f.readText(), dir)
        }?.sortedBy { it.name } ?: emptyList()

    private fun parse(text: String, dir: File): Skill? {
        val normalized = text.replace("\r\n", "\n").replace("\uFEFF", "")
        val m = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n?(.*)$", RegexOption.DOT_MATCHES_ALL).find(normalized) ?: return null
        val fm = m.groupValues[1]
        val name = Regex("^name:\\s*(.+)$", RegexOption.MULTILINE).find(fm)?.groupValues?.get(1)?.trim() ?: dir.name
        val desc = Regex("^description:\\s*(.+)$", RegexOption.MULTILINE).find(fm)?.groupValues?.get(1)?.trim() ?: ""
        val flag = File(dir, ".disabled")
        return Skill(name, desc, m.groupValues[2], !flag.exists(), dir)
    }

    /** 从 SKILL.md 全文安装（name 冲突覆盖） */
    fun install(text: String): Result<Skill> = runCatching {
        val m = Regex("^---\\s*\\n(.*?)\\n---", RegexOption.DOT_MATCHES_ALL).find(text.trim())
            ?: throw IllegalArgumentException("缺少 frontmatter（需要 --- name/description --- 开头）")
        val fm = m.groupValues[1]
        val name = Regex("^name:\\s*(.+)$", RegexOption.MULTILINE).find(fm)?.groupValues?.get(1)?.trim()
            ?: throw IllegalArgumentException("frontmatter 缺少 name")
        val dir = File(root, name.replace(Regex("[^\\w\\u4e00-\\u9fa5.-]"), "_"))
        dir.mkdirs()
        File(dir, "SKILL.md").writeText(text.trim())
        parse(File(dir, "SKILL.md").readText(), dir) ?: throw IllegalStateException("解析失败")
    }

    fun toggle(name: String) {
        list().find { it.name == name }?.let { sk ->
            val flag = File(sk.dir, ".disabled")
            if (sk.enabled) flag.writeText("off") else flag.delete()
        }
    }

    fun delete(name: String) {
        list().find { it.name == name }?.dir?.deleteRecursively()
    }
}
