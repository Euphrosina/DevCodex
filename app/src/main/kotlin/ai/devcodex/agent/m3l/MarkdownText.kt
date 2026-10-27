package ai.devcodex.agent.m3l

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin

/** Android text adapter with explicit styling; no Material theme or controls. */
@Composable
internal fun MarkdownText(
    markdown: String,
    modifier: Modifier,
    linkColor: Color,
    linkifyMask: Int,
    textSelectionColors: TextSelectionColors,
    style: TextStyle,
    isTextSelectable: Boolean,
    afterSetMarkdown: (TextView) -> Unit,
    isDarkTheme: Boolean = (androidx.compose.ui.platform.LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES,
) {
    val context = LocalContext.current
    val linkHandler = LocalMarkdownLinkHandler.current
    val density = LocalDensity.current
    val textSizePx = with(density) { style.fontSize.toPx() }
    val lineHeightPx = with(density) { style.lineHeight.toPx() }
    val markwon = remember(context, linkColor, linkifyMask, linkHandler, isDarkTheme) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create(linkifyMask))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(CoilImagesPlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link -> linkHandler?.invoke(link) }
                }

                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(linkColor.toArgb())
                    builder.headingBreakHeight(0)
                    // GitHub 式代码块：浅色 #F6F8FA / 深色 #0D1117
                    val isDark = isDarkTheme
                    val codeBg = if (isDark) 0xFF0D1117.toInt() else 0xFFF6F8FA.toInt()
                    val codeFg = if (isDark) 0xFFC9D1D9.toInt() else 0xFF24292F.toInt()
                    builder.codeBackgroundColor(codeBg)
                    builder.codeTextColor(codeFg)
                    builder.codeBlockBackgroundColor(codeBg)
                    builder.codeBlockTextColor(codeFg)
                }
            })
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                includeFontPadding = false
            }
        },
        update = { view ->
            view.setTextColor(style.color.toArgb())
            view.setLinkTextColor(linkColor.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            view.setLineSpacing((lineHeightPx - view.paint.fontSpacing).coerceAtLeast(0f), 1f)
            view.highlightColor = textSelectionColors.backgroundColor.toArgb()
            view.setTextIsSelectable(isTextSelectable)
            view.movementMethod = LinkMovementMethod.getInstance()
            if (view.tag != (markdown to markwon)) {
                markwon.setMarkdown(view, markdown)
                view.tag = markdown to markwon
            }
            afterSetMarkdown(view)
        }
    )
}
