# DevCodex

**Android 原生 AI Agent 应用 —— 真·工具执行，不是聊天壳。**

[中文](#中文) | [English](#english) | [日本語](#日本語)

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.x-7F52FF">
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4">
  <img alt="API" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-yellow">
</p>

---

## 中文

DevCodex 是运行在你 Android 设备上的**智能体（Agent）**：模型不只是聊天——它调用**真实工具**读写文件、执行终端命令、跑 Linux 沙盒、连 SSH、抓网页，把任务真正干完。

### 核心能力

**🤖 多模型接入**
- OpenAI / Anthropic / Google / DeepSeek / 智谱 / 通义 / 月之暗面 / OpenRouter / Ollama 等，自定义 OpenAI 兼容端点随意加
- 45+ 内置品牌矢量图标（LobeHub icons）
- 会话级模型切换 + 上下文窗口档位（默认/256k/512k/1M）
- 六档推理滑块：Faster（Low/Med/High）↔ Smarter（Xhigh/Max/Ultra）

**🛠 真 Agent 工具集（27+）**
- 文件系统：读写/搜索/目录树/压缩
- Web：URL 抓取（M3L 渲染）、图片保存
- Linux 沙盒：**PRoot 运行 Ubuntu 26.10**（内置 rootfs，免 root，`apt` 装包，工作区挂载）
- 执行通道自动选择：**Shizuku（ADB 权限）> Root > Termux（RUN_COMMAND 原生）> AXS > Linux 沙盒→toybox 自动降级**
- SSH 隧道（JSch）：远程执行 + 端口转发
- 应用管理：安装/卸载/启动、通知、剪贴板、截图（Shizuku）

**🔌 扩展机制（5 层）**
| 层 | 说明 |
|---|---|
| MCP 服务器 | Streamable/SSE 双协议，工具自动注入 |
| HTTP 插件 | JSON 定义工具（API→工具），全屏开发教程 |
| 自定义工具 | shell 模板 `{{参数}}` |
| 技能 | Claude 同款 SKILL.md 技能包 |
| Shell/SSH | 应用内 Linux + 远程服务器 |

**🧠 Agent 工程细节**
- 失败记忆：自动压缩时保留 ERROR 工具结果，不重复踩坑
- 三档工具审批：FULL 全自动 / ASSISTED 高危才审 / REQUEST 每步审
- 流式 SSE + 工具并行调用 + 中断/重试
- 自动上下文压缩（档位感知）

**📊 使用统计**：日/周/月/年/累计 Token、费用估算、缓存命中率、连续天数、活跃日历（热力图）、模型排行、工具调用次数

**🎨 界面**：MD3 动态取色、AMOLED、**内置 6 字体**（Google Sans Flex / Anthropic Serif / Oplus Serif / JetBrains Mono…）、VT100 内嵌终端、思考过程折叠、M3L 富文本（Markdown+LaTeX+代码高亮）

### 安装

从 [Releases](../../releases) 下载 `DevCodex-*.apk`（arm64-v8a），Android 8.0+。

> Termux 通道需在 Termux 中开启：`echo "allow-external-apps=true" >> ~/.termux/termux.properties`

### 构建

```bash
git clone https://github.com/Moirania/DevCodex.git
cd DevCodex
./gradlew assembleRelease  # 需 Android SDK + JDK 21
```

### 致谢

- [LobeHub icons](https://icons.lobehub.com)（MIT）—— 品牌矢量图标
- [OpenMinis](https://github.com/openminis) —— VT 终端模拟器内核
- [rikkahub](https://github.com/rikkahub/rikkahub) / [rikkahub-agent-pure](https://github.com/wuyhong715/rikkahub-agent-pure) 与 [taixu](https://github.com/wkbin/taixu) —— Agent 设计参考
- [Shizuku](https://github.com/RikkaApps/Shizuku) —— 特权通道
- [proot](https://proot-me.github.io/) —— 用户态 Linux

---

## English

**DevCodex — a native Android AI agent that actually runs tools, not a chat shell.**

The model doesn't just talk — it invokes **real tools**: reads/writes files, executes terminal commands, spins up a Linux sandbox, opens SSH tunnels, fetches web pages, and gets the job done.

### Highlights

**🤖 Multi-Provider** — OpenAI / Anthropic / Google / DeepSeek / Zhipu / Qwen / Moonshot / OpenRouter / Ollama, plus arbitrary OpenAI-compatible endpoints. 45+ built-in brand logos (LobeHub). Per-session model & context window tiers (default/256k/512k/1M). Six-step reasoning slider: Faster (Low/Med/High) ↔ Smarter (Xhigh/Max/Ultra).

**🛠 Real Tooling (27+)**
- Filesystem: read/write/search/tree/archive
- Web: URL fetch (rendered), image save
- Linux sandbox: **PRoot running Ubuntu 26.10** (bundled rootfs, no root needed, `apt` install, workspace bind-mount)
- Auto channel selection: **Shizuku (ADB) > Root > Termux (native RUN_COMMAND) > AXS > sandbox→toybox fallback**
- SSH tunnels (JSch): remote exec + port forwarding
- App management, notifications, clipboard, screenshots (via Shizuku)

**🔌 5 Extension Layers** — MCP servers (Streamable/SSE), HTTP plugins (JSON-defined tools), custom shell tools, Claude-style SKILL.md skills, shell/SSH layer.

**🧠 Agent Engineering** — failure memory survives context compaction (ERROR tool results retained); three-tier approval (FULL / ASSISTED / REQUEST); streaming SSE with parallel tool calls; interrupt & retry.

**📊 Analytics** — daily/weekly/monthly/yearly/all-time tokens, cost estimate, cache hit rate, streak days, activity heatmap, per-model ranking, tool-call counts.

**🎨 UI** — MD3 dynamic color, AMOLED, **6 bundled fonts** (Google Sans Flex / Anthropic Serif / Oplus Serif / JetBrains Mono...), embedded VT100 terminal, collapsible thinking, M3L rich text (Markdown + LaTeX + code highlighting).

### Install

Grab `DevCodex-*.apk` (arm64-v8a) from [Releases](../../releases). Android 8.0+.

> Termux channel: enable `allow-external-apps=true` in `~/.termux/termux.properties`.

### Build

```bash
git clone https://github.com/Moirania/DevCodex.git
cd DevCodex
./gradlew assembleRelease  # Android SDK + JDK 21
```

### Acknowledgements

[LobeHub icons](https://icons.lobehub.com) (MIT) · [OpenMinis](https://github.com/openminis) VT core · [rikkahub](https://github.com/rikkahub/rikkahub) / [rikkahub-agent-pure](https://github.com/wuyhong715/rikkahub-agent-pure) & [taixu](https://github.com/wkbin/taixu) — agent design references · [Shizuku](https://github.com/RikkaApps/Shizuku) · [proot](https://proot-me.github.io/)

---

## 日本語

**DevCodex — Android ネイティブの AI エージェント。チャットシェルではなく、実際にツールを実行します。**

モデルは話すだけでなく、**本物のツール**を呼び出します：ファイルの読み書き、ターミナルコマンドの実行、Linux サンドボックスの起動、SSH トンネル、Web ページの取得——タスクを本当に完了させます。

### 主な機能

**🤖 マルチプロバイダー** — OpenAI / Anthropic / Google / DeepSeek / 智譜 / Qwen / Moonshot / OpenRouter / Ollama、任意の OpenAI 互換エンドポイント。45 以上のブランドロゴ内蔵（LobeHub）。セッション単位のモデル切替とコンテキストウィンドウ（デフォルト/256k/512k/1M）。6 段階の推論スライダー：Faster（Low/Med/High）↔ Smarter（Xhigh/Max/Ultra）。

**🛠 本物のツールセット（27 以上）**
- ファイルシステム：読み書き／検索／ツリー／圧縮
- Web：URL 取得（レンダリング済み）、画像保存
- Linux サンドボックス：**PRoot で Ubuntu 26.10 を実行**（rootfs 同梱、root 不要、`apt` でパッケージ導入、ワークスペース バインドマウント）
- 実行チャネル自動選択：**Shizuku（ADB 権限）> Root > Termux（RUN_COMMAND ネイティブ）> AXS > サンドボックス→toybox 自動フォールバック**
- SSH トンネル（JSch）：リモート実行 + ポートフォワーディング
- アプリ管理、通知、クリップボード、スクリーンショット（Shizuku 経由）

**🔌 5 層の拡張機構** — MCP サーバー（Streamable/SSE）、HTTP プラグイン（JSON 定義ツール）、カスタムシェルツール、Claude 方式 SKILL.md スキル、シェル／SSH 層。

**🧠 エージェント設計** — 失敗メモリはコンテキスト圧縮後も保持（ERROR ツール結果を保存、同じミスを繰り返さない）；3 段階承認（FULL／ASSISTED／REQUEST）；ストリーミング SSE + 並列ツール呼び出し；中断・リトライ対応。

**📊 統計** — 日／週／月／年／累計トークン、費用推定、キャッシュヒット率、連続日数、アクティビティ ヒートマップ、モデル別ランキング、ツール呼び出し回数。

**🎨 UI** — MD3 ダイナミックカラー、AMOLED、**6 種内蔵フォント**（Google Sans Flex／Anthropic Serif／Oplus Serif／JetBrains Mono など）、内蔵 VT100 ターミナル、思考プロセス折りたたみ、M3L リッチテキスト（Markdown + LaTeX + コードハイライト）。

### インストール

[Releases](../../releases) から `DevCodex-*.apk`（arm64-v8a）をダウンロード。Android 8.0 以上。

> Termux チャネルを使用するには `~/.termux/termux.properties` で `allow-external-apps=true` を有効化してください。

### ビルド

```bash
git clone https://github.com/Moirania/DevCodex.git
cd DevCodex
./gradlew assembleRelease  # Android SDK + JDK 21
```

### 謝辞

[LobeHub icons](https://icons.lobehub.com)（MIT）· [OpenMinis](https://github.com/openminis) VT コア · [rikkahub](https://github.com/rikkahub/rikkahub) / [rikkahub-agent-pure](https://github.com/wuyhong715/rikkahub-agent-pure) と [taixu](https://github.com/wkbin/taixu) — エージェント設計参考 · [Shizuku](https://github.com/RikkaApps/Shizuku) · [proot](https://proot-me.github.io/)

---

## License

MIT © 2026 Moirania
