package ai.devcodex.agent.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.CalendarContract
import ai.devcodex.agent.agent.ToolDef
import ai.devcodex.agent.util.JsonArg
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File
import java.net.URL
import java.net.URLEncoder

/**
 * 拓展工具集（11 个）：
 * web_search / browser_render / image_info / image_convert / pipeline_run /
 * bg_list / bg_kill / calendar_list / calendar_add / clipboard_write / notify_post
 * 日历与通知需要运行时权限（WRITE_CALENDAR / POST_NOTIFICATIONS）
 */
object ExtraTools {

    private fun ctx(): Context = BuiltinTools.appContext()

    private fun obj(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.buildJsonObject(block)

    private fun req(vararg names: String) = kotlinx.serialization.json.buildJsonArray {
        names.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
    }

    private fun resolveW(path: String): String {
        val p = path.removePrefix("./")
        return if (p.startsWith("/")) p else java.io.File(BuiltinTools.workspaceRoot(), p).absolutePath
    }

    // ============ 1. Web 搜索（DuckDuckGo HTML 免 key） ============
    val webSearch = ToolDef(
        name = "web_search",
        description = "联网搜索。参数 query(必填)。返回标题/URL/摘要（DuckDuckGo）。回答时事、新知识、版本号等需要联网信息时的首选工具",
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("query", obj { put("type", "string") }) })
            put("required", req("query"))
        },
    ) { args ->
        val q = JsonArg.string(args, "query") ?: return@ToolDef "ERROR: 缺少 query"
        runCatching {
            val enc = URLEncoder.encode(q, "UTF-8")
            val html = URL("https://html.duckduckgo.com/html/?q=$enc").openStream()
                .bufferedReader().use { it.readText() }.take(400_000)
            val results = Regex("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>")
                .findAll(html).take(8).mapIndexed { i, m ->
                    val href = m.groupValues[1].replace(Regex("^//duckduckgo\\.com/l/\\?uddg="), "")
                        .let { java.net.URLDecoder.decode(it.substringBefore("&rut="), "UTF-8") }
                    "【${i + 1}】${m.groupValues[2].replace(Regex("<[^>]+>"), "")}\n  $href"
                }.joinToString("\n")
            results.ifBlank { "（无结果，换关键词试试）" }
        }.getOrElse { "ERROR: ${it.message}" }
    }

    // ============ 2. 浏览器渲染（WebView 离屏执行 JS 取文本） ============
    val browserRender = ToolDef(
        name = "browser_render",
        description = "浏览器渲染网页（执行 JS 后提取正文文本）。参数 url(必填)、timeout_sec(可选默认12)。适合 SPA/动态页面（http_request 拿不到内容时用这个）",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("url", obj { put("type", "string") })
                put("timeout_sec", obj { put("type", "integer") })
            })
            put("required", req("url"))
        },
        risk = ToolDef.Risk.LOW,
    ) { args ->
        val url = JsonArg.string(args, "url") ?: return@ToolDef "ERROR: 缺少 url"
        val timeout = (JsonArg.int(args, "timeout_sec") ?: 12) * 1000L
        val latch = CountDownLatch(1)
        var text = ""
        var title = ""
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                val wv = android.webkit.WebView(ctx())
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = true
                wv.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, t: String?) {
                        view?.evaluateJavascript(
                            "(function(){return document.title+'\\n\\n'+document.body.innerText.substring(0,20000)})()"
                        ) { r ->
                            text = r?.trim('"')?.replace("\\n", "\n") ?: ""
                            title = t ?: ""
                            latch.countDown()
                            view?.destroy()
                        }
                    }
                }
                wv.loadUrl(url)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (latch.count > 0L) latch.countDown()
                }, timeout)
            }
        }
        latch.await(timeout + 3000L, TimeUnit.MILLISECONDS)
        text.ifBlank { "ERROR: 页面加载超时或无文本" }.let { "[$title]\n$it" }
    }

    // ============ 3. 图像信息 ============
    val imageInfo = ToolDef(
        name = "image_info",
        description = "读取图片信息（尺寸/格式/大小）。参数 path(必填，工作区或沙箱外绝对路径)",
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("path", obj { put("type", "string") }) })
            put("required", req("path"))
        },
    ) { args ->
        val p = JsonArg.string(args, "path") ?: return@ToolDef "ERROR: 缺少 path"
        val f = File(resolveW(p))
        if (!f.isFile) return@ToolDef "ERROR: 文件不存在 $p"
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, opts)
        "文件: ${f.name}\n尺寸: ${opts.outWidth}x${opts.outHeight}\n格式: ${opts.outMimeType ?: "?"}\n大小: ${f.length() / 1024} KB\n路径: ${f.absolutePath}"
    }

    // ============ 4. 图像转换（缩放/压缩/webp/png/jpg） ============
    val imageConvert = ToolDef(
        name = "image_convert",
        description = "图像处理：缩放/压缩/转格式。参数 path(必填)、max_width(可选)、quality(可选1-100)、format(可选 webp/png/jpeg，默认 webp)。输出到工作区 converted/",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string") })
                put("max_width", obj { put("type", "integer") })
                put("quality", obj { put("type", "integer") })
                put("format", obj { put("type", "string") })
            })
            put("required", req("path"))
        },
    ) { args ->
        val p = JsonArg.string(args, "path") ?: return@ToolDef "ERROR: 缺少 path"
        val src = File(resolveW(p))
        if (!src.isFile) return@ToolDef "ERROR: 文件不存在 $p"
        val maxW = JsonArg.int(args, "max_width") ?: 0
        val quality = JsonArg.int(args, "quality") ?: 85
        val fmt = JsonArg.string(args, "format")?.lowercase() ?: "webp"
        runCatching {
            val bmp = BitmapFactory.decodeFile(src.absolutePath) ?: error("解码失败")
            val scaled = if (maxW > 0 && bmp.width > maxW) {
                Bitmap.createScaledBitmap(bmp, maxW, maxW * bmp.height / bmp.width, true)
            } else bmp
            val outDir = File(BuiltinTools.workspaceRoot(), "converted").apply { mkdirs() }
            val outFile = File(outDir, src.nameWithoutExtension + "." + fmt)
            val compressFmt = when (fmt) {
                "png" -> Bitmap.CompressFormat.PNG
                "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
                else -> Bitmap.CompressFormat.WEBP
            }
            outFile.outputStream().use { scaled.compress(compressFmt, quality, it) }
            "OK: ${outFile.absolutePath}（${scaled.width}x${scaled.height}，${outFile.length() / 1024} KB）"
        }.getOrElse { "ERROR: ${it.message}" }
    }

    // ============ 5. 管道命令（Termux 原生，支持 | 管道链） ============
    val pipelineRun = ToolDef(
        name = "pipeline_run",
        description = "在 Termux 原生环境跑管道链式命令（如 ls -la | grep .kt | wc -l）。参数 command(必填)、timeout_sec(默认30)",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("command", obj { put("type", "string") })
                put("timeout_sec", obj { put("type", "integer") })
            })
            put("required", req("command"))
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val cmd = JsonArg.string(args, "command") ?: return@ToolDef "ERROR: 缺少 command"
        val to = (JsonArg.int(args, "timeout_sec") ?: 30).toLong()
        ai.devcodex.agent.shell.ShellAccess.linuxExec(cmd).take(30000)
    }

    // ============ 6. 后台进程列表（同 uid） ============
    val bgList = ToolDef(
        name = "bg_list",
        description = "列出本应用后台子进程（pid/名称/命令），无参数",
        parameters = obj { put("type", "object"); put("properties", obj { }) },
    ) {
        runCatching {
            File("/proc").listFiles()?.filter { it.name.toIntOrNull() != null }
                ?.mapNotNull { pidDir ->
                    val comm = runCatching { File(pidDir, "comm").readText().trim() }.getOrNull()
                    val cmd = runCatching { File(pidDir, "cmdline").readText().replace('\u0000', ' ').trim() }.getOrNull()
                    if (comm != null) "$pidDir.name $comm ${cmd?.take(80) ?: ""}" else null
                }?.joinToString("\n") ?: "(空)"
        }.getOrElse { "ERROR: ${it.message}" }.take(10000)
    }

    // ============ 7. 后台进程终止 ============
    val bgKill = ToolDef(
        name = "bg_kill",
        description = "终止本应用的后台进程。参数 pid(必填)。先 bg_list 查看",
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("pid", obj { put("type", "integer") }) })
            put("required", req("pid"))
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val pid = JsonArg.int(args, "pid") ?: return@ToolDef "ERROR: 缺少 pid"
        runCatching {
            val p = ProcessBuilder("kill", "-9", pid.toString()).start()
            p.waitFor()
            "OK: kill $pid"
        }.getOrElse { "ERROR: ${it.message}（toybox 通道用 pipeline_run kill）" }
    }

    // ============ 8. 日历事件读取 ============
    val calendarList = ToolDef(
        name = "calendar_list",
        description = "读取系统日历最近/未来日程。参数 days(可选默认14)。需要日历读取权限",
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("days", obj { put("type", "integer") }) })
        },
    ) { args ->
        val days = JsonArg.int(args, "days") ?: 14
        runCatching {
            val start = System.currentTimeMillis()
            val end = start + days * 86400_000L
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(start.toString()).appendPath(end.toString()).build()
            val out = StringBuilder()
            ctx().contentResolver.query(uri, arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            ), null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && out.length < 15000) {
                    out.append("[${java.text.SimpleDateFormat("MM-dd HH:mm").format(java.util.Date(c.getLong(2)))}] ")
                        .append(c.getString(0) ?: "(无标题)")
                        .append("（${c.getString(3) ?: "?"}）")
                        .append(c.getString(1)?.let { "\n  $it" } ?: "").append("\n")
                }
            }
            out.toString().ifBlank { "（该时段无日程）" }
        }.getOrElse { "ERROR: ${it.message}（需日历权限：设置→应用→权限）" }
    }

    // ============ 9. 日历事件创建 ============
    val calendarAdd = ToolDef(
        name = "calendar_add",
        description = "添加日历日程。参数 title(必填)、begin_ms(必填,毫秒时间戳)或 begin_rfc(ISO时间)、duration_min(默认60)、description(可选)。需要日历写入权限",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("title", obj { put("type", "string") })
                put("begin_ms", obj { put("type", "integer") })
                put("begin_rfc", obj { put("type", "string"); put("description", "如 2026-10-22T09:00") })
                put("duration_min", obj { put("type", "integer") })
                put("description", obj { put("type", "string") })
            })
            put("required", req("title"))
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val title = JsonArg.string(args, "title") ?: return@ToolDef "ERROR: 缺少 title"
        val begin = JsonArg.int(args, "begin_ms")?.let { it.toLong() }
            ?: JsonArg.string(args, "begin_rfc")?.let { s ->
                runCatching { java.time.LocalDateTime.parse(s).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            }
            ?: return@ToolDef "ERROR: 缺少 begin_ms 或 begin_rfc"
        val dur = JsonArg.int(args, "duration_min") ?: 60
        runCatching {
            val cal = ctx().contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID), null, null, null
            )?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: error("无可用日历账户（先在系统日历 app 登录账户）")
            val cv = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cal)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, JsonArg.string(args, "description") ?: "DevCodex AI")
                put(CalendarContract.Events.DTSTART, begin)
                put(CalendarContract.Events.DTEND, begin + dur * 60_000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }
            ctx().contentResolver.insert(CalendarContract.Events.CONTENT_URI, cv)
            "OK: 日程已添加「$title」"
        }.getOrElse { "ERROR: ${it.message}（需日历写入权限）" }
    }

    // ============ 10. 剪贴板写入 ============
    val clipboardWrite = ToolDef(
        name = "clipboard_write",
        description = "写剪贴板。参数 text(必填)。给用户准备可直接粘贴的内容时用",
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("text", obj { put("type", "string") }) })
            put("required", req("text"))
        },
    ) { args ->
        val text = JsonArg.string(args, "text") ?: return@ToolDef "ERROR: 缺少 text"
        runCatching {
            val cm = ctx().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("DevCodex", text))
            "OK: 已复制 ${text.length} 字符"
        }.getOrElse { "ERROR: ${it.message}" }
    }

    // ============ 11. 发通知 ============
    val notifyPost = ToolDef(
        name = "notify_post",
        description = "发送系统通知。参数 title(必填)、text(可选)。任务完成提醒",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("title", obj { put("type", "string") })
                put("text", obj { put("type", "string") })
            })
            put("required", req("title"))
        },
    ) { args ->
        val title = JsonArg.string(args, "title") ?: return@ToolDef "ERROR: 缺少 title"
        val text = JsonArg.string(args, "text") ?: ""
        runCatching {
            val nm = ctx().getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val ch = android.app.NotificationChannel("devcodex_tools", "AI 工具通知", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
            val n = android.app.Notification.Builder(ctx(), "devcodex_tools")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(text)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
            "OK: 通知已发送"
        }.getOrElse { "ERROR: ${it.message}（Android 13+ 需通知权限）" }
    }

    // ============ ccco 式设备工具 ============
    val alarmSet = ToolDef(
        name = "alarm_set",
        description = "设置系统闹钟（跳系统闹钟预填）。参数 hour(0-23 必填) minute(0-59 必填) label(可选)",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("hour", obj { put("type", "integer") })
                put("minute", obj { put("type", "integer") })
                put("label", obj { put("type", "string") })
            })
            put("required", req("hour", "minute"))
        },
    ) { args ->
        val hour = JsonArg.int(args, "hour") ?: return@ToolDef "ERROR: 缺少 hour"
        val minute = JsonArg.int(args, "minute") ?: return@ToolDef "ERROR: 缺少 minute"
        val label = JsonArg.string(args, "label") ?: "DevCodex"
        runCatching {
            val i = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx().startActivity(i)
            "OK: 闹钟 $hour:$minute $label 已预填，请在系统界面保存"
        }.getOrElse { "ERROR: ${it.message}（需系统闹钟应用）" }
    }

    val shareContent = ToolDef(
        name = "share_content",
        description = "系统分享文本（弹分享面板）。参数 text(必填) title(可选)",
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("text", obj { put("type", "string") })
                put("title", obj { put("type", "string") })
            })
            put("required", req("text"))
        },
    ) { args ->
        val text = JsonArg.string(args, "text") ?: return@ToolDef "ERROR: 缺少 text"
        runCatching {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx().startActivity(android.content.Intent.createChooser(i, JsonArg.string(args, "title") ?: "分享"))
            "OK: 分享面板已弹出"
        }.getOrElse { "ERROR: ${it.message}" }
    }

    val calendarOpen = ToolDef(
        name = "calendar_open",
        description = "打开系统日历应用（ccco 式设备工具）",
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("date", obj { put("type", "string") }) })
        },
    ) { _ ->
        runCatching {
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("content://com.android.calendar/time/")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx().startActivity(i)
            "OK: 日历已打开"
        }.getOrElse { "ERROR: ${it.message}" }
    }

    val all = listOf(
        webSearch, browserRender, imageInfo, imageConvert,
        pipelineRun, bgList, bgKill, calendarList, calendarAdd,
        clipboardWrite, notifyPost, alarmSet, shareContent, calendarOpen,
    )
}
