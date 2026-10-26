package ai.devcodex.agent.theme

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.toFontFamily
import java.io.File

/** 自定义字体仓（rikkahub 式）：用户导入 ttf/otf 到 filesDir/fonts，动态加载 */
object CustomFonts {
    fun dir(ctx: Context): File = File(ctx.filesDir, "fonts").apply { mkdirs() }

    fun list(ctx: Context): List<String> = dir(ctx).listFiles { f -> f.extension in listOf("ttf", "otf") }?.map { it.nameWithoutExtension } ?: emptyList()

    /** 导入字体文件（返回字体名，失败 null） */
    fun import(ctx: Context, src: File): String? = runCatching {
        val dest = File(dir(ctx), src.name)
        src.copyTo(dest, overwrite = true)
        dest.nameWithoutExtension
    }.getOrNull()

    fun family(ctx: Context, name: String): FontFamily? {
        val f = dir(ctx).listFiles()?.firstOrNull { it.nameWithoutExtension == name && it.extension in listOf("ttf", "otf") } ?: return null
        return runCatching { FontFamily(Font(f, weight = androidx.compose.ui.text.font.FontWeight.Normal)) }.getOrNull()
    }
}
