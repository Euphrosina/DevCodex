package ai.devcodex.agent.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.CalendarContract
import ai.devcodex.agent.agent.ToolDef
import ai.devcodex.agent.util.JsonArg
import ai.devcodex.agent.shell.ShellAccess
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

    private fun tr(id: Int, vararg args: Any): String =
        ai.devcodex.agent.i18n.AppLocale.string(id, *args)

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
        description = tr(ai.devcodex.agent.R.string.ext_web_search_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("query", obj { put("type", "string") }) })
            put("required", req("query"))
        },
    ) { args ->
        val q = JsonArg.string(args, "query") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "query")
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
            results.ifBlank { tr(ai.devcodex.agent.R.string.ext_web_no_result) }
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_err_fmt, it.message ?: "") }
    }

    // ============ APK 构建（Termux 工具链：aapt2+javac+d8+apksigner） ============
    private val apkBuild: ToolDef = ToolDef(
        name = "apk_build",
        risk = ToolDef.Risk.MEDIUM,
        description = tr(ai.devcodex.agent.R.string.ext_apk_build_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("project_dir", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.ext_apk_project_dir)) })
                put("keystore", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.ext_apk_keystore)) })
            })
            put("required", req("project_dir"))
        },
    ) { args ->
        val dir = ai.devcodex.agent.util.JsonArg.string(args, "project_dir") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "project_dir")
        val ks = ai.devcodex.agent.util.JsonArg.string(args, "keystore") ?: ""
        val cmd = buildString {
            append("cd \"\$HOME/workspace/").append(dir.trim('/')).append("\" && ")
            append("(command -v aapt2 >/dev/null 2>&1 || pkg install -y aapt openjdk-17 d8-android-tools apksigner >/dev/null 2>&1 || true) && ")
            append("mkdir -p build && aapt2 compile --dir res -o build/res.zip 2>/dev/null; ")
            append("aapt2 link -o build/base.apk --manifest AndroidManifest.xml build/res.zip 2>/dev/null || aapt2 link -o build/base.apk --manifest AndroidManifest.xml; ")
            append("find java -name '*.java' > build/sources.txt 2>/dev/null || find . -name '*.java' > build/sources.txt; ")
            append("javac -classpath build/base.apk -d build/classes @build/sources.txt 2>&1 | head -30; ")
            append("(cd build/classes && d8 \$(find . -name '*.class') --output ../dexout 2>/dev/null) && ")
            append("cp build/dexout/*.dex build/ 2>/dev/null; (cd build && zip -j base.apk classes*.dex dexout/*.dex -q 2>/dev/null || zip -j base.apk *.dex -q); ")
            if (ks.isNotBlank()) append("apksigner sign --ks \"").append(ks).append("\" --out output.apk base.apk && echo APK_SIGNED; ")
            else append("(keytool -genkeypair -keystore build/debug.ks -storepass debugpass -keypass debugpass -alias debug -dname CN=DevCodex -keyalg RSA -validity 10000 2>/dev/null; apksigner sign --ks build/debug.ks --pass pass:debugpass --key-pass pass:debugpass --out output.apk base.apk) && echo APK_SIGNED; ")
            append("ls -la output.apk 2>/dev/null || echo BUILD_FAILED_CHECK_LOG")
        }
        ai.devcodex.agent.shell.ShellAccess.execSandbox(cmd).take(2500)
    }

    // ============ native 构建（Termux clang：.so/.ko） ============
    private val nativeBuild: ToolDef = ToolDef(
        name = "native_build",
        risk = ToolDef.Risk.MEDIUM,
        description = tr(ai.devcodex.agent.R.string.ext_native_build_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("src_dir", obj { put("type", "string") })
                put("out", obj { put("type", "string") })
                put("type", obj { put("type", "string") })
                put("kernel_dir", obj { put("type", "string") })
            })
            put("required", req("src_dir"))
        },
    ) { args ->
        val dir = ai.devcodex.agent.util.JsonArg.string(args, "src_dir") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "src_dir")
        val out = ai.devcodex.agent.util.JsonArg.string(args, "out") ?: "out"
        val type = ai.devcodex.agent.util.JsonArg.string(args, "type") ?: "so"
        val kdir = ai.devcodex.agent.util.JsonArg.string(args, "kernel_dir") ?: ""
        val cmd = when (type) {
            "ko" -> if (kdir.isBlank()) tr(ai.devcodex.agent.R.string.ext_ko_need_kernel_dir)
            else "cd \"\$HOME/workspace/" + dir.trim('/') + "\" && clang -target aarch64-linux-android -I\"" + kdir + "/include\" -I\"" + kdir + "/arch/arm64/include\" -DKERNEL -c *.c -o ../" + out + ".o && ld -r -o ../" + out + ".ko ../" + out + ".o && echo KO_OK"
            else -> "cd \"\$HOME/workspace/" + dir.trim('/') + "\" && clang -target aarch64-linux-android21 -shared -fPIC *.c -o ../" + out + ".so && echo SO_OK"
        }
        ai.devcodex.agent.shell.ShellAccess.execSandbox(cmd).take(2000)
    }

    // ============ JVM 跨平台构建（javac+jar） ============
    private val jarBuild: ToolDef = ToolDef(
        name = "jar_build",
        risk = ToolDef.Risk.LOW,
        description = tr(ai.devcodex.agent.R.string.ext_jar_build_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("src_dir", obj { put("type", "string") })
                put("out", obj { put("type", "string") })
                put("main", obj { put("type", "string") })
            })
            put("required", req("src_dir"))
        },
    ) { args ->
        val dir = ai.devcodex.agent.util.JsonArg.string(args, "src_dir") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "src_dir")
        val out = ai.devcodex.agent.util.JsonArg.string(args, "out") ?: "app.jar"
        val main = ai.devcodex.agent.util.JsonArg.string(args, "main") ?: ""
        val fixed = if (main.isBlank())
            "cd \"\$HOME/workspace/" + dir.trim('/') + "\" && find . -name '*.java' > srcs.txt && javac -d classes @srcs.txt && (cd classes && jar cf ../../" + out + " .) && ls -la ../../" + out + " && echo JAR_OK"
        else
            "cd \"\$HOME/workspace/" + dir.trim('/') + "\" && find . -name '*.java' > srcs.txt && javac -d classes @srcs.txt && jar cfe ../../" + out + " " + main + " -C classes . && ls -la ../../" + out + " && echo JAR_OK"
        ai.devcodex.agent.shell.ShellAccess.execSandbox(fixed).take(2000)
    }


    // ============ 2. 浏览器渲染（WebView 离屏执行 JS 取文本） ============
    val browserRender = ToolDef(
        name = "browser_render",
        description = tr(ai.devcodex.agent.R.string.ext_browser_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("url", obj { put("type", "string") })
                put("timeout_sec", obj { put("type", "integer") })
                put("screenshot", obj { put("type", "boolean") })
            })
            put("required", req("url"))
        },
        risk = ToolDef.Risk.LOW,
    ) { args ->
        val url = JsonArg.string(args, "url") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "url")
        val timeout = (JsonArg.int(args, "timeout_sec") ?: 12) * 1000L
        val wantShot = args.contains("screenshot") && args.contains("true")
        val latch = CountDownLatch(1)
        var text = ""
        var title = ""
        var shotPath = ""
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
                            if (wantShot) {
                                runCatching {
                                    view?.measure(
                                        android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                                        android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                                    )
                                    view?.layout(0, 0, view?.measuredWidth ?: 1080, view?.measuredHeight ?: 1000)
                                    val b = android.graphics.Bitmap.createBitmap(view?.measuredWidth ?: 1080, view?.measuredHeight ?: 1000, android.graphics.Bitmap.Config.ARGB_8888)
                                    view?.draw(android.graphics.Canvas(b))
                                    val f = java.io.File(ai.devcodex.agent.tools.BuiltinTools.appContext().filesDir, "workspace/shot_" + System.currentTimeMillis() + ".png")
                                    f.parentFile?.mkdirs()
                                    f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
                                    shotPath = f.absolutePath
                                }
                            }
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
        val base = text.ifBlank { tr(ai.devcodex.agent.R.string.ext_browser_timeout) }.let { "[$title]\n$it" }
        if (shotPath.isNotBlank()) base + tr(ai.devcodex.agent.R.string.ext_shot_saved_fmt, shotPath) else base
    }

    // ============ 3. 图像信息 ============
    val imageInfo = ToolDef(
        name = "image_info",
        description = tr(ai.devcodex.agent.R.string.ext_image_info_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("path", obj { put("type", "string") }) })
            put("required", req("path"))
        },
    ) { args ->
        val p = JsonArg.string(args, "path") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "path")
        val f = File(resolveW(p))
        if (!f.isFile) return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_file_not_found_fmt, p)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, opts)
        tr(ai.devcodex.agent.R.string.ext_image_info_fmt, f.name, opts.outWidth, opts.outHeight, opts.outMimeType ?: "?", f.length() / 1024, f.absolutePath)
    }

    // ============ 4. 图像转换（缩放/压缩/webp/png/jpg） ============
    val imageConvert = ToolDef(
        name = "image_convert",
        description = tr(ai.devcodex.agent.R.string.ext_image_convert_desc),
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
        val p = JsonArg.string(args, "path") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "path")
        val src = File(resolveW(p))
        if (!src.isFile) return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_file_not_found_fmt, p)
        val maxW = JsonArg.int(args, "max_width") ?: 0
        val quality = JsonArg.int(args, "quality") ?: 85
        val fmt = JsonArg.string(args, "format")?.lowercase() ?: "webp"
        runCatching {
            val bmp = BitmapFactory.decodeFile(src.absolutePath) ?: error(tr(ai.devcodex.agent.R.string.ext_decode_failed))
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
            tr(ai.devcodex.agent.R.string.ext_image_convert_ok_fmt, outFile.absolutePath, scaled.width, scaled.height, outFile.length() / 1024)
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_err_fmt, it.message ?: "") }
    }

    // ============ 5. 管道命令（Termux 原生，支持 | 管道链） ============
    val pipelineRun = ToolDef(
        name = "pipeline_run",
        description = tr(ai.devcodex.agent.R.string.ext_pipeline_desc),
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
        val cmd = JsonArg.string(args, "command") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "command")
        val to = (JsonArg.int(args, "timeout_sec") ?: 30).toLong()
        ai.devcodex.agent.shell.ShellAccess.linuxExec(cmd).take(30000)
    }

    // ============ 6. 后台进程列表（同 uid） ============
    val bgList = ToolDef(
        name = "bg_list",
        description = tr(ai.devcodex.agent.R.string.ext_bg_list_desc),
        parameters = obj { put("type", "object"); put("properties", obj { }) },
    ) {
        runCatching {
            File("/proc").listFiles()?.filter { it.name.toIntOrNull() != null }
                ?.mapNotNull { pidDir ->
                    val comm = runCatching { File(pidDir, "comm").readText().trim() }.getOrNull()
                    val cmd = runCatching { File(pidDir, "cmdline").readText().replace('\u0000', ' ').trim() }.getOrNull()
                    if (comm != null) "$pidDir.name $comm ${cmd?.take(80) ?: ""}" else null
                }?.joinToString("\n") ?: tr(ai.devcodex.agent.R.string.ext_empty)
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_err_fmt, it.message ?: "") }.take(10000)
    }

    // ============ 7. 后台进程终止 ============
    val bgKill = ToolDef(
        name = "bg_kill",
        description = tr(ai.devcodex.agent.R.string.ext_bg_kill_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("pid", obj { put("type", "integer") }) })
            put("required", req("pid"))
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val pid = JsonArg.int(args, "pid") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "pid")
        runCatching {
            val p = ProcessBuilder("kill", "-9", pid.toString()).start()
            p.waitFor()
            "OK: kill $pid"
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_bg_kill_err_fmt, it.message ?: "") }
    }

    // ============ 8. 日历事件读取 ============
    val calendarList = ToolDef(
        name = "calendar_list",
        description = tr(ai.devcodex.agent.R.string.ext_cal_list_desc),
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
                        .append(c.getString(0) ?: tr(ai.devcodex.agent.R.string.ext_no_title))
                        .append("（${c.getString(3) ?: "?"}）")
                        .append(c.getString(1)?.let { "\n  $it" } ?: "").append("\n")
                }
            }
            out.toString().ifBlank { tr(ai.devcodex.agent.R.string.ext_cal_empty) }
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_cal_list_err_fmt, it.message ?: "") }
    }

    // ============ 9. 日历事件创建 ============
    val calendarAdd = ToolDef(
        name = "calendar_add",
        description = tr(ai.devcodex.agent.R.string.ext_cal_add_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("title", obj { put("type", "string") })
                put("begin_ms", obj { put("type", "integer") })
                put("begin_rfc", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.ext_cal_begin_example)) })
                put("duration_min", obj { put("type", "integer") })
                put("description", obj { put("type", "string") })
            })
            put("required", req("title"))
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val title = JsonArg.string(args, "title") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "title")
        val begin = JsonArg.int(args, "begin_ms")?.let { it.toLong() }
            ?: JsonArg.string(args, "begin_rfc")?.let { s ->
                runCatching { java.time.LocalDateTime.parse(s).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
            }
            ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "begin_ms / begin_rfc")
        val dur = JsonArg.int(args, "duration_min") ?: 60
        runCatching {
            val cal = ctx().contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID), null, null, null
            )?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: error(tr(ai.devcodex.agent.R.string.ext_cal_no_account))
            val cv = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cal)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, JsonArg.string(args, "description") ?: "DevCodex AI")
                put(CalendarContract.Events.DTSTART, begin)
                put(CalendarContract.Events.DTEND, begin + dur * 60_000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }
            ctx().contentResolver.insert(CalendarContract.Events.CONTENT_URI, cv)
            tr(ai.devcodex.agent.R.string.ext_cal_add_ok_fmt, title)
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_cal_add_err_fmt, it.message ?: "") }
    }

    // ============ 10. 剪贴板写入 ============
    val clipboardWrite = ToolDef(
        name = "clipboard_write",
        description = tr(ai.devcodex.agent.R.string.ext_clip_write_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("text", obj { put("type", "string") }) })
            put("required", req("text"))
        },
    ) { args ->
        val text = JsonArg.string(args, "text") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "text")
        runCatching {
            val cm = ctx().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("DevCodex", text))
            tr(ai.devcodex.agent.R.string.ext_clip_write_ok_fmt, text.length)
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_err_fmt, it.message ?: "") }
    }

    // ============ 11. 发通知 ============
    val notifyPost = ToolDef(
        name = "notify_post",
        description = tr(ai.devcodex.agent.R.string.ext_notify_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("title", obj { put("type", "string") })
                put("text", obj { put("type", "string") })
            })
            put("required", req("title"))
        },
    ) { args ->
        val title = JsonArg.string(args, "title") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "title")
        val text = JsonArg.string(args, "text") ?: ""
        runCatching {
            val nm = ctx().getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val ch = android.app.NotificationChannel("devcodex_tools", tr(ai.devcodex.agent.R.string.ext_notify_channel), android.app.NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
            val n = android.app.Notification.Builder(ctx(), "devcodex_tools")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(text)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 100000).toInt(), n)
            tr(ai.devcodex.agent.R.string.ext_notify_ok)
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_notify_err_fmt, it.message ?: "") }
    }

    // ============ ccco 式设备工具 ============
    val alarmSet = ToolDef(
        name = "alarm_set",
        description = tr(ai.devcodex.agent.R.string.ext_alarm_desc),
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
        val hour = JsonArg.int(args, "hour") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "hour")
        val minute = JsonArg.int(args, "minute") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "minute")
        val label = JsonArg.string(args, "label") ?: "DevCodex"
        runCatching {
            val i = android.content.Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx().startActivity(i)
            tr(ai.devcodex.agent.R.string.ext_alarm_ok_fmt, hour, minute, label)
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_alarm_err_fmt, it.message ?: "") }
    }

    val shareContent = ToolDef(
        name = "share_content",
        description = tr(ai.devcodex.agent.R.string.ext_share_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("text", obj { put("type", "string") })
                put("title", obj { put("type", "string") })
            })
            put("required", req("text"))
        },
    ) { args ->
        val text = JsonArg.string(args, "text") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "text")
        runCatching {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx().startActivity(android.content.Intent.createChooser(i, JsonArg.string(args, "title") ?: tr(ai.devcodex.agent.R.string.ext_share_title)))
            tr(ai.devcodex.agent.R.string.ext_share_ok)
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_err_fmt, it.message ?: "") }
    }

    val calendarOpen = ToolDef(
        name = "calendar_open",
        description = tr(ai.devcodex.agent.R.string.ext_cal_open_desc),
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
            tr(ai.devcodex.agent.R.string.ext_cal_open_ok)
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_err_fmt, it.message ?: "") }
    }

    // ============ 沙盒扩展工具（自动装包） ============
    val sandboxPkgInstall = ToolDef(
        name = "sandbox_pkg_install",
        description = tr(ai.devcodex.agent.R.string.ext_pkg_install_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("packages", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.ext_pkg_list_desc)) })
            })
            put("required", req("packages"))
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val pkgs = JsonArg.string(args, "packages") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "packages")
        ShellAccess.execSandbox("(command -v apt-get >/dev/null 2>&1 && DEBIAN_FRONTEND=noninteractive apt-get install -y $pkgs || pkg install -y $pkgs) 2>&1 | tail -20").take(8000)
    }

    val pythonRun = ToolDef(
        name = "python_run",
        description = tr(ai.devcodex.agent.R.string.ext_python_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("code", obj { put("type", "string") })
                put("stdin", obj { put("type", "string") })
            })
            put("required", req("code"))
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val code = JsonArg.string(args, "code") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "code")
        val script = java.io.File(ctx().cacheDir, "devcodex_py.py")
        script.writeText(code)
        val hasPy = ShellAccess.execSandbox("command -v python3")
        if (!hasPy.contains("python3")) {
            ShellAccess.execSandbox("(command -v apt-get >/dev/null 2>&1 && DEBIAN_FRONTEND=noninteractive apt-get install -y python3 || pkg install -y python) 2>&1 | tail -3")
        }
        ShellAccess.execSandbox("python3 ${script.absolutePath} 2>&1").take(20000)
    }

    val nodeRun = ToolDef(
        name = "node_run",
        description = tr(ai.devcodex.agent.R.string.ext_node_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("code", obj { put("type", "string") })
            })
            put("required", req("code"))
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val code = JsonArg.string(args, "code") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "code")
        val script = java.io.File(ctx().cacheDir, "devcodex_js.js")
        script.writeText(code)
        val hasNode = ShellAccess.execSandbox("command -v node")
        if (!hasNode.contains("node")) {
            ShellAccess.execSandbox("(command -v apt-get >/dev/null 2>&1 && DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs || pkg install -y nodejs) 2>&1 | tail -3")
        }
        ShellAccess.execSandbox("node ${script.absolutePath} 2>&1").take(20000)
    }

    // ============ Zafiro 式设备工具 ============
    val deviceInfo = ToolDef(
        name = "device_info",
        description = tr(ai.devcodex.agent.R.string.ext_device_info_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("detail", obj { put("type", "string") }) })
        },
    ) { _ ->
        val am = ctx().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val bat = ctx().registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra("level", -1) ?: -1
        val scale = bat?.getIntExtra("scale", 100) ?: 100
        val dm = ctx().resources.displayMetrics
        buildString {
            appendLine(tr(ai.devcodex.agent.R.string.ext_dev_model_fmt, android.os.Build.MODEL, android.os.Build.BRAND))
            appendLine(tr(ai.devcodex.agent.R.string.ext_dev_system_fmt, android.os.Build.VERSION.RELEASE, android.os.Build.VERSION.SDK_INT))
            appendLine(tr(ai.devcodex.agent.R.string.ext_dev_screen_fmt, dm.widthPixels, dm.heightPixels, dm.densityDpi))
            appendLine(tr(ai.devcodex.agent.R.string.ext_dev_memory_fmt, mi.totalMem / 1048576, mi.availMem / 1048576))
            appendLine(tr(ai.devcodex.agent.R.string.ext_dev_battery_fmt, if (level >= 0) "${level * 100 / scale}%" else tr(ai.devcodex.agent.R.string.ext_unknown)))
            append(tr(ai.devcodex.agent.R.string.ext_dev_storage_fmt, android.os.Environment.getDataDirectory().usableSpace / 1048576))
        }
    }

    val vibrate = ToolDef(
        name = "vibrate",
        description = tr(ai.devcodex.agent.R.string.ext_vibrate_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("ms", obj { put("type", "integer") }) })
        },
    ) { args ->
        val ms = ai.devcodex.agent.util.JsonArg.int(args, "ms") ?: 300
        runCatching {
            val v = ctx().getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                v.vibrate(android.os.VibrationEffect.createOneShot(ms.coerceIn(50, 5000).toLong(), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else @Suppress("DEPRECATION") v.vibrate(ms.coerceIn(50, 5000).toLong())
            tr(ai.devcodex.agent.R.string.ext_vibrate_ok_fmt, ms)
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_err_fmt, it.message ?: "") }
    }

    val openUrl = ToolDef(
        name = "open_url",
        description = tr(ai.devcodex.agent.R.string.ext_open_url_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("url", obj { put("type", "string") }) })
        },
    ) { args ->
        val url = ai.devcodex.agent.util.JsonArg.string(args, "url") ?: return@ToolDef tr(ai.devcodex.agent.R.string.ext_err_missing_fmt, "url")
        runCatching {
            ctx().startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            tr(ai.devcodex.agent.R.string.ext_open_url_ok_fmt, url)
        }.getOrElse { tr(ai.devcodex.agent.R.string.ext_err_fmt, it.message ?: "") }
    }

    // ============ Zafiro 精华设备工具（二批） ============
    val screenshotTake = ToolDef(
        name = "screenshot_take",
        description = tr(ai.devcodex.agent.R.string.ext_screenshot_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("name", obj { put("type", "string") }) })
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val name = ai.devcodex.agent.util.JsonArg.string(args, "name") ?: ("shot_" + System.currentTimeMillis())
        val out = java.io.File(ctx().filesDir, "workspace/" + name + ".png")
        out.parentFile.mkdirs()
        val r = ShellAccess.exec("screencap -p " + out.absolutePath + " && echo OK || echo FAIL")
        if (out.exists() && out.length() > 1000) tr(ai.devcodex.agent.R.string.ext_screenshot_ok_fmt, name, out.length() / 1024)
        else tr(ai.devcodex.agent.R.string.ext_screenshot_err_fmt, r.take(100))
    }

    val clipboardGet = ToolDef(
        name = "clipboard_get",
        description = tr(ai.devcodex.agent.R.string.ext_clip_get_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("dummy", obj { put("type", "string") }) })
        },
    ) { _ ->
        runCatching {
            val cm = ctx().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (t.isBlank()) tr(ai.devcodex.agent.R.string.ext_clip_empty) else t.take(5000)
        }.getOrElse { "ERROR: " + it.message }
    }

    val wifiState = ToolDef(
        name = "wifi_state",
        description = tr(ai.devcodex.agent.R.string.ext_wifi_desc),
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("dummy", obj { put("type", "string") }) })
        },
    ) { _ ->
        runCatching {
            val wm = ctx().applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val info = wm.connectionInfo
            val ssid = info.ssid.removePrefix("\"").removeSuffix("\"")
            buildString {
                append("WiFi: ")
                append(if (ssid.isBlank() || ssid == "<unknown ssid>") tr(ai.devcodex.agent.R.string.ext_wifi_disconnected) else ssid)
                append(" / ").append(tr(ai.devcodex.agent.R.string.ext_wifi_signal)).append(" ")
                append(android.net.wifi.WifiManager.calculateSignalLevel(info.rssi, 100))
                append("/100 / IP ")
                append(if (info.ipAddress != 0) android.text.format.Formatter.formatIpAddress(info.ipAddress) else tr(ai.devcodex.agent.R.string.ext_none))
            }
        }.getOrElse { "ERROR: " + it.message }
    }

    val all = listOf(
        webSearch, browserRender, imageInfo, imageConvert,
        pipelineRun, bgList, bgKill, calendarList, calendarAdd,
        clipboardWrite, notifyPost, alarmSet, shareContent, calendarOpen,
        sandboxPkgInstall, pythonRun, nodeRun, apkBuild, nativeBuild, jarBuild, vibrate, openUrl,
        screenshotTake, clipboardGet, wifiState,
    )
}
