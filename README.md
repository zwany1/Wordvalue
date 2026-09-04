# 言值输入法（高情商 AI 输入法）

一个 Android AI 输入法：在微信复制对方消息 → 点击键盘上的「AI 生成」→ 生成 3 条不同风格（自然 / 温柔 / 幽默）的高情商回复 → 点击回复一键填入微信输入框。

## 核心链路

```
InputMethodService → Clipboard → Qingsheng Engine → API（智谱 GLM 等）→ 3 条回复 → commitText()
```

生成优先级（自动降级）：**云端 API（已配置）→ 规则模板库（本地）**。API 请求失败自动回退并在状态栏提示。

云端配置为隐藏入口（不对外展示）：设置页底部「使用步骤」卡片 **5 秒内连点 7 次**唤出「后台生成配置」对话框，填入 Base URL / API Key / 模型名并启用。密钥仅存本机 SharedPreferences。

## Qingsheng Engine

基于 qingsheng-skill 知识体系的规则引擎 + 知识库 + 工作流，而不是一段超长 Prompt：

```
IntentDetector      意图识别（回复/急速/挽回/自动规划/展示面/顾问）
    ↓
ContextManager     档案与多目标管理（Room 持久化：阶段、事实、时间线）
    ↓
SignalDetector     IOI/IOD 信号密度统计与趋势
StageDetector      七阶段判定（标准枚举 + 置信度 + 证据）
    ↓
StrategyLibrary    按阶段检索策略（邀约三步法/废测/拉扯/话题阶梯/框架/情绪领导）
ExampleRetriever   按阶段+情绪检索 Top3 实战示例（轻量 RAG）
    ↓
SafetyGate         边界信号拦截（明确拒绝 → 体面收尾，停止推进）
    ↓
ReplyGenerator     Prompt 组装 → API 生成 → 解析 A/B/C → 模板兜底
```

七阶段标准枚举（模型只允许输出此枚举）：1 开场破冰 / 2 建立好感 / 3 关系升温 / 4 邀约见面 / 5 约会实战 / 6 亲密升级 / 7 确立关系。强信号（亲昵称呼、约定见面、互表喜欢等）自动提升阶段判定。

键盘快捷模式：**AI 生成** / **换一个**（急速重生成）/ 💔 挽回（冷激活消息）/ 🤖 自动规划（对话树）/ 🧠 顾问（分析建议）。

## 项目结构

```
high-emotion-keyboard/
├── android/                  # Android 工程（Kotlin）
│   └── app/src/main/
│       ├── java/com/qingsheng/ime/
│       │   ├── ime/          # 输入法服务、键盘视图、拼音引擎
│       │   ├── ai/           # API 通道、ReplyGenerator、Prompt 组装
│       │   ├── skill/        # Qingsheng Engine：意图/档案/信号/阶段/策略/安全
│       │   ├── data/         # Room 数据库 + 设置存储
│       │   ├── clipboard/    # 剪贴板读取
│       │   ├── bridge/       # 供 uni-app 原生插件调用的导出 API
│       │   └── home/         # 系统设置页（启用引导）
│       └── assets/skill/     # 知识库 JSON（12 份）
├── skill-data/               # 知识库源文件（与 assets 同步）
└── uniapp/                   # 设置端 UI（uni-app + Vue3）
```

## 知识库（qingsheng-skill 蒸馏）

| 文件 | 来源 | 内容 |
| --- | --- | --- |
| stages.json | stages.md | 七阶段：目标/策略/禁忌/升级信号 |
| signals.json | signals-tools.md | IOI/IOD、强信号强制升阶、边界停止信号 |
| strategies.json | SKILL.md | 九类情绪识别与策略链、话题推进规则 |
| strategies-library.json | advanced-techniques.md | 邀约三步法/废测/服从测试/拉扯/话题阶梯/模糊表白/框架/情绪领导/引领 |
| examples.json | examples-library.md | 实战对话示例（few-shot 检索源） |
| platforms.json | platform-guide.md | 七平台差异化策略 |
| mindset.json | mindset-concepts.md | 心态与元策略 |
| user-context.json | user-context.md | 档案与多目标管理规则 |
| recovery.json | recovery-playbook.md | 冷激活/关系挽回手册 |
| autopilot.json | autopilot-guide.md | 自动规划对话树格式/节奏/暂停边界 |
| profile-audit.json | profile-audit.md | 展示面诊断维度 |
| reply-templates.json | — | 规则模式模板库（9 情绪 × 3 风格） |

## 构建 APK

要求：JDK 17+、Android SDK（compileSdk 34）、Android Gradle Plugin 8.5。

- Android Studio：打开 `android/` 目录直接 Run
- 命令行：在 `android/` 下执行 `gradle assembleDebug`

## 启用输入法

1. 安装 APK 后打开「言值」应用
2. 点击「去系统设置启用输入法」→ 开启「言值输入法」
3. 在微信任意聊天框切换输入法到「言值输入法」
4. 长按对方消息 → 复制 → 点键盘上的「AI 生成」
5. 点击任意一条回复 → 自动填入微信输入框 → 自己点发送

## uni-app 设置端

`uniapp/` 为设置端 UI（uni-app + Vue3），通过 `nativeplugins/QingshengIME` 与主 APK 共享数据。主 APK 自带 SettingsActivity，不依赖 uni-app 即可完成全部配置。

## 隐私

- 剪贴板仅在输入法处于前台焦点时读取，用于生成回复
- 聊天历史与档案仅存本机 Room 数据库，可在设置中关闭
- 云端 API 密钥仅存本机 SharedPreferences；配置后消息内容将发送至所配置的服务商（默认智谱）
