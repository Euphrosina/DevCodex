package ai.devcodex.agent.tools

import ai.devcodex.agent.agent.ToolDef
import ai.devcodex.agent.data.PluginManifest
import ai.devcodex.agent.data.PluginTool
import ai.devcodex.agent.plugin.PluginExecutor
import ai.devcodex.agent.service.DcAccessibilityService
import ai.devcodex.agent.shell.ShellAccess
import ai.devcodex.agent.shell.SshClient
import android.content.Context
import android.os.Build
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** 内置工具集 + 插件工具装配 */
object BuiltinTools {

    private lateinit var appContext: Context
    fun init(ctx: Context) { appContext = ctx.applicationContext }
    fun appContext(): Context = appContext

    private fun tr(id: Int, vararg args: Any): String =
        ai.devcodex.agent.i18n.AppLocale.string(id, *args)

    /** 应用内页面导航回调（ChatScreen 注册：AI 可打开终端/设置等页面） */
    @Volatile
    var navigateCallback: ((String) -> Unit)? = null

    private fun obj(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): kotlinx.serialization.json.JsonObject = buildJsonObject(block)

    // ============ 内存文件读写工具 ============
    val fsRead = ToolDef(
        name = "fs_read",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_fs_read_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_abs_file_path)) })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("path")) })
        },
    ) { args ->
        val path = ai.devcodex.agent.util.JsonArg.string(args, "path") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_path_param)
        val resolved = resolvePath(path)
        ShellAccess.readFile(resolved).take(20000)
    }

    val fsWrite = ToolDef(
        name = "fs_write",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_fs_write_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("path", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_target_abs_path)) })
                put("content", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_write_content)) })
            })
            put("required", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("path")); add(kotlinx.serialization.json.JsonPrimitive("content"))
            })
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val path = ai.devcodex.agent.util.JsonArg.string(args, "path") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_path)
        val content = ai.devcodex.agent.util.JsonArg.string(args, "content") ?: ""
        val resolved = resolvePath(path)
        // 工作区内或沙箱内 → 免审批直写；其他路径走 ShellAccess（需授权/审批）
        if (resolved.startsWith(appContext.filesDir.canonicalPath)) {
            runCatching {
                java.io.File(resolved).parentFile?.mkdirs()
                java.io.File(resolved).writeText(content)
            }.fold(onSuccess = { tr(ai.devcodex.agent.R.string.bt_fs_write_ok_fmt, java.io.File(resolved).relativeTo(java.io.File(appContext.filesDir, "workspace")).path) },
                   onFailure = { tr(ai.devcodex.agent.R.string.bt_err_fmt, it.message ?: "") })
        } else ShellAccess.writeFile(resolved, content)
    }

    val fsList = ToolDef(
        name = "fs_list",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_fs_list_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("path", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_dir_abs_path)) }) })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("path")) })
        },
    ) { args ->
        val path = ai.devcodex.agent.util.JsonArg.string(args, "path") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_path)
        ShellAccess.listDir(resolvePath(path)).take(8000)
    }

    val fsDelete = ToolDef(
        name = "fs_delete",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_fs_delete_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("path", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_delete_abs_path)) }) })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("path")) })
        },
        risk = ToolDef.Risk.HIGH,
    ) { args ->
        val path = ai.devcodex.agent.util.JsonArg.string(args, "path") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_path)
        ShellAccess.delete(path)
    }

    // ============ Shell ============
    val appOpen = ToolDef(
        name = "app_open",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_app_open_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("page", obj { put("type", "string"); put("enum", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("terminal"))
                    add(kotlinx.serialization.json.JsonPrimitive("settings"))
                    add(kotlinx.serialization.json.JsonPrimitive("stats"))
                    add(kotlinx.serialization.json.JsonPrimitive("plugins"))
                    add(kotlinx.serialization.json.JsonPrimitive("providers"))
                }) })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("page")) })
        },
        risk = ToolDef.Risk.LOW,
    ) { args ->
        val page = ai.devcodex.agent.util.JsonArg.string(args, "page") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_page)
        val valid = listOf("terminal", "settings", "stats", "plugins", "providers")
        if (page !in valid) return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_bad_page_fmt, valid.joinToString("/"))
        navigateCallback?.invoke(page) ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_ui_not_ready)
        tr(ai.devcodex.agent.R.string.bt_app_open_ok_fmt, page)
    }

    val sshExec = ToolDef(
        name = "ssh_exec",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_ssh_exec_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("command", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_remote_cmd)) })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("command")) })
        },
        risk = ToolDef.Risk.HIGH,
    ) { args ->
        val cmd = ai.devcodex.agent.util.JsonArg.string(args, "command") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_command)
        SshClient.exec(cmd)
    }

    val sshForward = ToolDef(
        name = "ssh_forward",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_ssh_forward_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("local_port", obj { put("type", "integer") })
                put("remote_host", obj { put("type", "string") })
                put("remote_port", obj { put("type", "integer") })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("local_port")); add(kotlinx.serialization.json.JsonPrimitive("remote_host")); add(kotlinx.serialization.json.JsonPrimitive("remote_port")) })
        },
        risk = ToolDef.Risk.LOW,
    ) { args ->
        val lp = ai.devcodex.agent.util.JsonArg.int(args, "local_port") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_local_port)
        val rh = ai.devcodex.agent.util.JsonArg.string(args, "remote_host") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_remote_host)
        val rp = ai.devcodex.agent.util.JsonArg.int(args, "remote_port") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_remote_port)
        SshClient.forward(lp, rh, rp)
    }

    val linuxExec = ToolDef(
        name = "linux_exec", risk = ToolDef.Risk.HIGH,
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_linux_exec_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("command", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_shell_cmd)) })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("command")) })
        },
    ) { args ->
        val cmd = ai.devcodex.agent.util.JsonArg.string(args, "command") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_command)
        ShellAccess.linuxExec(cmd).take(30000)
    }

    val shellExec = ToolDef(
        name = "shell_exec",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_shell_exec_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("command", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_cmd)) })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("command")) })
        },
        risk = ToolDef.Risk.HIGH,
    ) { args ->
        val cmd = ai.devcodex.agent.util.JsonArg.string(args, "command") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_command)
        ShellAccess.exec(cmd).take(20000)
    }

    // ============ HTTP ============
    val httpRequest = ToolDef(
        name = "http_request",
        risk = ToolDef.Risk.MEDIUM,
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_http_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("method", obj { put("type", "string"); put("enum", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.JsonPrimitive("GET")); add(kotlinx.serialization.json.JsonPrimitive("POST")); add(kotlinx.serialization.json.JsonPrimitive("PUT")); add(kotlinx.serialization.json.JsonPrimitive("DELETE"))
                }) })
                put("url", obj { put("type", "string") })
                put("headers", obj { put("type", "object"); put("description", tr(ai.devcodex.agent.R.string.bt_param_headers)) })
                put("body", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_body)) })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("url")) })
        },
    ) { args ->
        ai.devcodex.agent.plugin.PluginExecutor.executeRaw(args)
    }

    // ============ 记忆 ============
    private val MEMORY_KEY_REGEX = Regex("[A-Za-z0-9._一-龥-]{1,64}")
    private fun isSafeMemoryKey(key: String): Boolean = key.matches(MEMORY_KEY_REGEX) && key != "." && key != ".."

    val memorySave = ToolDef(
        name = "memory_save",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_mem_save_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("key", obj { put("type", "string") })
                put("value", obj { put("type", "string") })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("key")); add(kotlinx.serialization.json.JsonPrimitive("value")) })
        },
    ) { args ->
        val key = ai.devcodex.agent.util.JsonArg.string(args, "key") ?: "general"
        val value = ai.devcodex.agent.util.JsonArg.string(args, "value") ?: ""
        // 路径穿越防护：键只允许安全文件名字符
        if (!isSafeMemoryKey(key)) return@ToolDef tr(ai.devcodex.agent.R.string.bt_mem_err_bad_key)
        if (value.length > 200_000) return@ToolDef tr(ai.devcodex.agent.R.string.bt_mem_err_too_long)
        val dir = File(appContext.filesDir, "memory").apply { mkdirs() }
        File(dir, "$key.md").writeText(value)
        tr(ai.devcodex.agent.R.string.bt_mem_saved_fmt, key)
    }

    val memoryRead = ToolDef(
        name = "memory_read",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_mem_read_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("key", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_key_read)) })
            })
        },
    ) { args ->
        val dir = File(appContext.filesDir, "memory")
        val key = ai.devcodex.agent.util.JsonArg.string(args, "key")
        if (key.isNullOrBlank()) {
            dir.listFiles()?.joinToString("\n") { it.nameWithoutExtension } ?: tr(ai.devcodex.agent.R.string.bt_mem_empty)
        } else if (!isSafeMemoryKey(key)) {
            tr(ai.devcodex.agent.R.string.bt_mem_err_bad_key)
        } else {
            File(dir, "$key.md").takeIf { it.exists() }?.readText()?.take(8000) ?: tr(ai.devcodex.agent.R.string.bt_mem_missing_fmt, key)
        }
    }

    // ============ 设备 ============
    val appList = ToolDef(
        name = "app_list",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_app_list_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("keyword", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_keyword)) })
            })
        },
    ) { args ->
        val kw = ai.devcodex.agent.util.JsonArg.string(args, "keyword")?.lowercase()
        val pm = appContext.packageManager
        pm.getInstalledPackages(0)
            .mapNotNull { pi -> (pi.applicationInfo?.loadLabel(pm)?.toString() ?: "") to pi.packageName }
            .filter { kw.isNullOrBlank() || it.second.contains(kw, true) || it.first.lowercase().contains(kw) }
            .take(300)
            .joinToString("\n") { "${it.second} (${it.first})" }
    }

    val deviceInfo = ToolDef(
        name = "device_info",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_device_info_desc,
        parameters = obj { put("type", "object"); put("properties", obj {}) },
    ) { _ ->
        buildString {
            appendLine(tr(ai.devcodex.agent.R.string.bt_dev_model_fmt, Build.MANUFACTURER, Build.MODEL))
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine(tr(ai.devcodex.agent.R.string.bt_dev_shell_fmt, ShellAccess.availableMode()))
            appendLine(tr(ai.devcodex.agent.R.string.bt_dev_a11y_fmt, if (DcAccessibilityService.isEnabled()) tr(ai.devcodex.agent.R.string.bt_a11y_enabled) else tr(ai.devcodex.agent.R.string.bt_a11y_disabled)))
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
        }
    }

    // ============ UI 自动化（无障碍） ============
    val uiSnapshot = ToolDef(
        name = "ui_snapshot",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_ui_snapshot_desc,
        parameters = obj { put("type", "object"); put("properties", obj {}) },
    ) { _ -> DcAccessibilityService.snapshot() }

    val uiTap = ToolDef(
        name = "ui_tap",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_ui_tap_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("x", obj { put("type", "integer") })
                put("y", obj { put("type", "integer") })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("x")); add(kotlinx.serialization.json.JsonPrimitive("y")) })
        },
        risk = ToolDef.Risk.MEDIUM,
    ) { args ->
        val x = ai.devcodex.agent.util.JsonArg.int(args, "x") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_x)
        val y = ai.devcodex.agent.util.JsonArg.int(args, "y") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_y)
        if (DcAccessibilityService.tap(x.toFloat(), y.toFloat())) tr(ai.devcodex.agent.R.string.bt_ui_tapped_fmt, x, y) else tr(ai.devcodex.agent.R.string.bt_err_a11y_disabled)
    }

    val uiBack = ToolDef(
        name = "ui_back", description = "", descriptionRes = ai.devcodex.agent.R.string.bt_ui_back_desc,
        parameters = obj { put("type", "object"); put("properties", obj {}) },
    ) { _ -> if (DcAccessibilityService.back()) "OK" else tr(ai.devcodex.agent.R.string.bt_err_not_enabled) }

    val uiHome = ToolDef(
        name = "ui_home", description = "", descriptionRes = ai.devcodex.agent.R.string.bt_ui_home_desc,
        parameters = obj { put("type", "object"); put("properties", obj {}) },
    ) { _ -> if (DcAccessibilityService.home()) "OK" else tr(ai.devcodex.agent.R.string.bt_err_not_enabled) }

    val uiScroll = ToolDef(
        name = "ui_scroll", description = "", descriptionRes = ai.devcodex.agent.R.string.bt_ui_scroll_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("down", obj { put("type", "boolean"); put("description", tr(ai.devcodex.agent.R.string.bt_param_scroll_dir)) }) })
        },
    ) { args ->
        val down = ai.devcodex.agent.util.JsonArg.bool(args, "down") ?: true
        if (DcAccessibilityService.scroll(down)) "OK" else tr(ai.devcodex.agent.R.string.bt_err_not_enabled)
    }

    val notify = ToolDef(
        name = "notify",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_notify_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj {
                put("title", obj { put("type", "string") })
                put("text", obj { put("type", "string") })
            })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("title")) })
        },
    ) { args ->
        val title = ai.devcodex.agent.util.JsonArg.string(args, "title") ?: "DevCodex"
        val text = ai.devcodex.agent.util.JsonArg.string(args, "text") ?: ""
        ai.devcodex.agent.util.Notify.post(appContext, title, text)
        tr(ai.devcodex.agent.R.string.bt_notify_ok)
    }

    /** 相对路径 → 工作区绝对路径（尊重自定义工作区）；绝对路径原样返回 */
    private fun resolvePath(path: String): String {
        if (path.startsWith("/")) return path
        val ws = workspaceRoot()
        return java.io.File(ws, path.removePrefix("./")).canonicalPath
    }

    /** 当前工作区根（自定义目录或应用私有） */
    fun workspaceRoot(): java.io.File {
        val custom = ai.devcodex.agent.data.WorkspaceConfig.dir
        val ws = if (custom.isNotBlank()) java.io.File(custom) else java.io.File(appContext.filesDir, "workspace")
        ws.mkdirs()
        return ws
    }

    // ============ 技能读取 ============
    val skillRead = ToolDef(
        name = "skill_read",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_skill_read_desc,
        parameters = obj {
            put("type", "object")
            put("properties", obj { put("name", obj { put("type", "string"); put("description", tr(ai.devcodex.agent.R.string.bt_param_skill_name)) }) })
            put("required", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("name")) })
        },
    ) { args ->
        val name = ai.devcodex.agent.util.JsonArg.string(args, "name") ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_err_missing_name)
        val repo = ai.devcodex.agent.data.SkillRepo(appContext)
        val sk = repo.list().find { it.name.equals(name, true) && it.enabled }
            ?: return@ToolDef tr(ai.devcodex.agent.R.string.bt_skill_not_found_fmt, name)
        tr(ai.devcodex.agent.R.string.bt_skill_body_fmt, sk.name, sk.content.take(20000))
    }

    val skillList = ToolDef(
        name = "skill_list",
        description = "",
        descriptionRes = ai.devcodex.agent.R.string.bt_skill_list_desc,
        parameters = obj { put("type", "object"); put("properties", obj {}) },
    ) { _ ->
        val repo = ai.devcodex.agent.data.SkillRepo(appContext)
        val all = repo.list().filter { it.enabled }
        if (all.isEmpty()) tr(ai.devcodex.agent.R.string.bt_skill_none) else all.joinToString("\n") { "${it.name}: ${it.description.take(100)}" }
    }

    val all: List<ToolDef> = listOf(
        fsRead, fsWrite, fsList, fsDelete, shellExec, httpRequest, appOpen, linuxExec, sshExec, sshForward,
        memorySave, memoryRead, appList, deviceInfo,
        uiSnapshot, uiTap, uiBack, uiHome, uiScroll, notify,
        skillRead, skillList,
    )

    /** 装配插件工具 */
    fun pluginTools(manifests: List<PluginManifest>): List<ToolDef> =
        manifests.filter { it.enabled }.flatMap { m ->
            m.tools.map { t: PluginTool -> PluginExecutor.toToolDef(t) }
        }
}
