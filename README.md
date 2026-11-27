# AllView · 轻量级多格式阅读器

一个只做「读」的多格式阅读器：打开本地文件，专注排版，冷启动直达。
支持 Markdown、纯文本/日志、CSV/JSON/XML/YAML/INI、字幕、RTF、HTML、EPUB、Word、Excel、PowerPoint、PDF 与源码，全部本地解析，
不联网、不要账号、没有广告。

[![Android Build](https://github.com/Black2716/AllView/actions/workflows/android.yml/badge.svg)](https://github.com/Black2716/AllView/actions/workflows/android.yml)

| 项 | 值 |
|---|---|
| 应用名 | **AllView** |
| 包名 | `com.yg.moduread` |
| 技术栈 | Kotlin + Jetpack Compose（Material 3） |
| minSdk / targetSdk / compileSdk | 24 / 34 / 34 |
| 解析内核 | commonmark-java 0.22.0 + GFM Tables |
| 当前版本 | **1.3.0**（versionCode 4） |
| release APK 实测体积 | **1.33 MB**（1,398,387 字节；R8 + 资源压缩，含 PPTX 表格/公式/图表渲染与 PDF 目录修复） |
| debug APK 实测体积 | 7.8 MB（不裁剪，仅用于调试） |
| 冷启动（Pixel 后端 API 34 模拟器，host GPU） | **302–505 ms**（`Displayed` 打点） |
| 9.2 万字文档打开到首屏可读 | 约 3.4 s（含 `uiautomator` 轮询开销） |
| 单测 | **258/258**（原有 198 条全部保留并通过；44 条覆盖编解码、偏好/进度存储、会话 uri、错误文案、块 key、加载态映射，7 条覆盖大图采样解码，9 条覆盖设计令牌与展示文案 —— 详见 [docs/refactor-report.md](docs/refactor-report.md)） |
| 设计规范 | [docs/design-system.md](docs/design-system.md)（颜色 / 字阶 / 间距 / 圆角 / 图标 / 动效 / 无障碍 / 逐屏规范） |

## 一、界面截图

| 深色模式 | 表格 + 图片 |
|---|---|
| ![深色模式](docs/01-dark.png) | ![表格与图片](docs/02-table-image.png) |

| 首启空态 | 浅色长文 |
|---|---|
| ![首启空态](docs/03-empty.png) | ![浅色长文](docs/04-book-light.png) |

| 浅色 · 图标化底栏 | 深色 |
|---|---|
| ![浅色界面](docs/08-ui-light.png) | ![深色界面](docs/06-ui-dark.png) |

![字号浮层](docs/07-font-panel.png)

| 标题层级（H2 通丝线） | 代码块与表格 |
|---|---|
| ![标题层级](docs/09-typography.png) | ![代码块与表格](docs/10-code-table.png) |

| 米黄纸张 | 夜间纸张 |
|---|---|
| ![米黄](docs/11-sepia.png) | ![夜间](docs/12-night.png) |

![目录跟随当前章节](docs/13-toc-follow.png)

### PPTX 渲染（1.2.0 新增）

| 表格、配色与图表 | 公式与表格 |
|---|---|
| ![PPTX 表格](docs/14-pptx-table.png) | ![PPTX 公式](docs/15-pptx-math.png) |

表格按真实列宽折行，长文本不溢出；公式走 OMML → LaTeX → 语法树渲染；
深色版式自动反色，柱状图按 `c:barChart` 数据绘制。

![LaTeX 公式渲染](docs/05-math.png)

分式、上下标、求最值的上下限、定界符、根号、重音、希腊字母与关系符均为原生排版，不走 WebView。

截图位于 [`docs/`](docs/)，用仓库相对路径引用（`![说明](docs/文件名.png)`），无需外部图床。

## 二、工程目录结构

```
AllView/
├── settings.gradle.kts
├── build.gradle.kts                    # AGP 8.5.2 / Kotlin 1.9.24
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties        # Gradle 8.7
├── local.properties                     # sdk.dir 指向本机 SDK（不入库）
├── docs/                                # 真机/模拟器实测截图
│   ├── 01-dark.png                      # 深色模式
│   ├── 02-table-image.png               # 表格 + 图片
│   ├── 03-empty.png                     # 首启空态
│   ├── 04-book-light.png                # 浅色模式长文
│   ├── 05-math.png                      # LaTeX 公式渲染
│   ├── manual-1-git-bash.md             # Git Bash 终端操作手册
│   └── manual-2-sidebar-git.md          # 侧边栏 Git 操作手册
├── sample/
│   ├── sample.md                        # 9.2 万字真实长文（验收用）
│   ├── demo.md                          # 全元素覆盖的最小样例
│   ├── math.md                          # 公式语法回归样例
│   └── pic.png                          # 配合 demo.md 的本地图片
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro               # R8 保留 Compose CompositionLocal
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── values/strings.xml          # app_name = AllView
        │   │   ├── values/themes.xml           # 启动窗口底色（浅）
        │   │   ├── values-night/themes.xml     # 启动窗口底色（深）
        │   │   ├── drawable/ic_launcher_*.xml  # 自适应图标
        │   │   ├── mipmap-anydpi-v26/          # Android 8+ 自适应图标
        │   │   └── mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png
        │   └── java/com/yg/moduread/
        │       ├── MainActivity.kt             # 单 Activity，无导航框架
        │       ├── md/
        │       │   ├── MdModel.kt              # 只读 AST：MdBlock / MdInline
        │       │   ├── MdParser.kt             # commonmark -> AST 一次性转换
        │       │   ├── MdToc.kt                # 目录抽取 + GitHub slug
        │       │   ├── SearchIndex.kt          # 全文搜索索引（偏移表 + 二分）
        │       │   └── CodeHighlighter.kt      # 4 种语言的轻量词法高亮
        │       ├── data/
        │       │   ├── DocRepository.kt        # SAF Uri -> UTF-8 -> AST
        │       │   ├── ExternalOpen.kt         # VIEW intent 解析（可单测）
        │       │   ├── RecentFiles.kt          # 最近 8 本，无数据库
        │       │   └── MdImageResolver.kt      # 相对路径图片定位（本地）
        │       └── ui/
        │           ├── Design.kt               # 设计令牌：间距/圆角/字阶/透明度/动效
        │           ├── Theme.kt                # 明暗双主题 + 纸张主题 + M3 字体与形状
        │           ├── Components.kt           # 公共件：卡片/胶囊/按钮/输入框/空态
        │           ├── Icons.kt                # 20 个 Canvas 直绘图标（不引矢量库）
        │           ├── ReaderState.kt          # DocumentState / ReaderPrefs
        │           ├── ReaderViewModel.kt      # 状态 + 偏好持久化
        │           ├── ReaderRoot.kt           # SAF 选择器 + 页面装配
        │           ├── HomeScreen.kt           # 首页：品牌 + 主操作 + 最近文件
        │           ├── ReaderScreen.kt         # LazyColumn 阅读主界面
        │           ├── ReaderChrome.kt         # 顶栏 / 底栏 / 字号浮层 / 加载态
        │           ├── DocTabs.kt              # 多文档标签条
        │           ├── MdRenderer.kt           # 块级渲染器（全部元素）
        │           ├── AppSheet.kt             # 底部面板统一外壳
        │           ├── TocSheet.kt             # 目录面板（ModalBottomSheet）
        │           ├── SearchSheet.kt          # 搜索面板（命中列表 + 高亮 + 跳转）
        │           ├── BookmarkSheet.kt        # 书签面板
        │           └── SettingsSheet.kt        # 字号 / 外观 / 纸张面板
        └── test/java/com/yg/moduread/
            ├── MdParserTest.kt                 # 9 个解析单测（含真实长文）
            ├── LatexTest.kt                    # 8 个公式单测
            ├── MdTocTest.kt                    # 7 个目录单测（含书稿 11 锚点真值）
            ├── RecentFilesTest.kt              # 6 个最近文件单测
            ├── ExternalOpenTest.kt             # 6 个外部打开判据单测
            ├── DesignTokensTest.kt             # 9 个设计令牌 / 展示文案单测
            └── ……共 35 个测试类、258 条用例
```

## 一·五、界面与设计系统

1.3.0 把界面重做了一遍：不换功能，只把"能读"变成"好读"。
完整约定见 [docs/design-system.md](docs/design-system.md)，这里只列看得见的变化。

### 双套设计系统合并

改造前 `Design.kt` 里有 `Sp` / `Radius` / `Type` 令牌，而 `MaterialTheme` 用的是默认 `Shapes`（4/8/12/28dp）与默认为 `Typography`，
同一个界面里并存两种圆角、两套字阶。现在 `AppShapes` 与 `AppTypography` 把令牌交给 M3，
`Slider` / `DropdownMenu` / `ModalBottomSheet` 与自绘组件圆角字阶完全一致。

### 首页取代空态

未打开文档时不再是"一行标题 + 一个按钮"，而是完整首页：品牌头、主操作按钮、
最近打开卡片列表（类型徽标 + 格式 + 相对时间 + 删除键）、可打开格式说明。错误也改成页内横幅，不弹窗打断。

### 阅读器

- 进度条只保留一条（底栏顶部 2dp 轨道），改造前顶栏百分比与独立进度线是两套数据源。
- 顶栏高度、底栏高度、正文底部留白收成三个常量；改造前 52dp 散在三处。
- 底栏四项有选中底色与图标变色，不再只换颜色；字号浮层加 `A-` / `A+` 微调键。
- 页首元信息补上格式与字数（"Markdown · 12,345 字"）。

### 面板

目录 / 搜索 / 书签 / 设置四类面板统一为：拖拽条 → 标题 + 计数 + 关闭键 → 内容。
搜索命中片段做关键词高亮并按块号标注；目录当前项带语义标记；
纸张/外观改用带对勾的 `ChoiceChip` 并补 `Role.RadioButton`。

### 触摸与无障碍

所有可点元素 ≥ 48dp（改造前标签关闭键 30×20dp、书签删除键约 30dp、标签条整体 30dp 高），
图标按钮全部带 `contentDescription`，固定行高改 `heightIn(min=)` 以支持系统字体放大 1.3 倍。

### 图标

自绘图标从 6 个补到 20 个（返回、勾选、书签、文件、文件夹、日/月、纸张、字号增减、删除、堆叠等），
仍是 `Canvas` 直绘，不引入矢量库——release 体积量级不变。

## 二·五、支持的文件格式

| 类别 | 扩展名 | 说明 |
|---|---|---|
| Markdown | `.md` `.markdown` | 完整支持，见下节渲染表 |
| 纯文本 | `.txt` `.log` | 自动识别编码；日志按行保留 |
| 结构化 | `.json` `.xml` `.yaml` `.ini` `.csv` `.tsv` | 缩进 / 分节 / 表格化呈现 |
| 字幕 | `.srt` `.vtt` | 按时间轴分块 |
| Office 容器 | `.docx` `.xlsx` `.pptx` `.odt` | ZIP + XML 解析；PPTX 整页 16:9 渲染 |
| 电子书 | `.epub` | 按章节顺序展开 |
| 网页 | `.html` `.htm` | 转块显示，不执行脚本 |
| PDF | `.pdf` | 逐页渲染 + `/Outlines` 目录树 |
| 源码 | `.kt` `.py` `.js` `.java` `.sh` 等 | 语法高亮 |
| 不支持 | `.doc` `.ppt` 等旧版二进制 | 给出明确提示，不做乱码降级 |

## 三、渲染支持

| Markdown 元素 | 状态 | 渲染方式 |
|---|---|---|
| ATX / Setext 标题（1–6 级） | 支持 | 字号按级别缩放，加粗，段前留白分级 |
| 段落 | 支持 | 行高 1.75，中文长文排版 |
| 有序列表 | 支持 | 按 `start` 递增编号，支持紧凑模式 |
| 无序列表 | 支持 | `•` 项目符号，支持多层嵌套 |
| 引用块 | 支持 | 左侧主题色竖线 + 缩进，支持嵌套引用 |
| 代码块（fenced / indented） | 支持 | 等宽字体、卡片底色、语言标签、横向滚动、可选中复制 |
| 表格（GFM） | 支持 | 表头加粗、列对齐、按内容加权分配列宽；> 4 列时横向滚动 |
| 链接 | 支持 | 主题色下划线，点击调系统浏览器（页内 `#锚点` 静默） |
| 图片 | 支持 | 块级等比铺满 + 图注；解析相对路径 / `file://` / `content://` |
| 行内代码 / 粗体 / 斜体 | 支持 | `AnnotatedString` 拼装 |
| 分隔线 | 支持 | 1dp 细线 |
| **全文搜索** | 支持 | 命中列表带块号与上下文片段，点击跳转；大小写不敏感，单字查询被拒 |
| **代码高亮** | 支持 | 手写 tokenizer：kotlin/python/bash/json；未知语言原样输出 |
| **外部打开** | 支持 | 文件管理器「用 AllView 打开」；只声明 `text/markdown`，不抢 `.txt` |
| **最近文件** | 支持 | 空态页列最近 8 本，可单独移除；纯 SharedPreferences，无数据库 |
| **目录抽屉** | 支持 | 按级别缩进，点击跳转；打开时自动定位并高亮当前章节 |
| **纸张主题** | 支持 | 纸白 / 米黄 / 夜间，与深色模式正交，只调底色不动配色 |
| **文档内锚点** | 支持 | `[文本](#slug)` 按 GitHub slug 规则解析后跳转 |
| **阅读进度记忆** | 支持 | 按文档分别记住滚动位置，杀进程重开回到原处 |
| **文档内锚点** `[文本](#slug)` | 支持 | 按 GitHub slug 规则解析后跳转 |
| **阅读进度记忆** | 支持 | 按文档分别记住滚动位置，杀进程重开回到原处 |
| **块级公式** `$$…$$` | 支持 | 原生排版：分式（自适应分数线）、上下标、求和/求积/最值的上下限、定界符、根号、重音、箭头 |
| **行内公式** `$…$` | 支持 | 能线性化的转 Unicode 融入正文（σ、μₜ、x²）；含分式等结构降级为等宽原文 |
| HTML 块 / 行内 HTML | 部分 | 按纯文本显示（不执行 HTML，无 WebView） |
| 任务列表 / 脚注 / 删除线 | 未启用 | 按普通文本渲染 |

## 四、编译与安装

环境：JDK 17、Android SDK（platform 34 + build-tools 34.0.0）、Gradle 8.7（wrapper 自动下载）。

```bash
git clone https://github.com/Black2716/AllView.git
cd AllView
```

仓库不含 `local.properties`（里面有本机 SDK 的绝对路径），克隆后需要自己生成一份，或直接设置环境变量 `ANDROID_HOME`。

```bash
# 1. 指向本机 SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 2. 编译（任选）
./gradlew :app:assembleDebug      # 调试包 app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease    # 正式包 app/build/outputs/apk/release/app-release.apk（已用 debug 证书签名，可直接装）

# 3. 安装
./gradlew :app:installDebug
# 或
adb install -r app/build/outputs/apk/release/app-release.apk

# 4. 跑解析单测
./gradlew :app:testDebugUnitTest
```

Windows 用 `gradlew.bat`；本地无 Gradle 时 wrapper 会自行下载 8.7。

## 版本与发版

版本号的**唯一来源**是 `app/build.gradle.kts`:

```kotlin
versionCode = 4
versionName = "1.3.0"
```

发版流程（三处会自动对齐，无需手改文档里的版本串）：

1. 改 `versionCode`（整数递增）与 `versionName`
2. 在 [CHANGELOG.md](CHANGELOG.md) 顶部加一节
3. 提交后打 tag 并推送：`git tag -a v1.3.0 -m "..." && git push origin v1.3.0`

推送 `v*` tag 会触发 [`.github/workflows/release.yml`](.github/workflows/release.yml)：
跑单测 → 构建 APK → 重命名为 `AllView-<版本>.apk` → 发布到 GitHub Release。

`publish.sh` / `publish.ps1` 也从 `build.gradle.kts` 读取版本号，不再硬编码。

## 五、验收步骤（已在模拟器实测通过）

```bash
adb push sample/sample.md /sdcard/Download/
adb push sample/demo.md   /sdcard/Download/
adb push sample/pic.png   /sdcard/Download/
```

1. 启动应用，首屏为首页：「AllView / 本地文档阅读器 · 无需联网 / 打开文件」，下方是最近打开列表与可打开格式说明。
2. 点「打开文件」→ 系统 SAF → 选择 `sample.md`，顶部大标题 + 正文同步出现。
3. 按元素检查（`demo.md` 覆盖全部元素）：标题、段落、嵌套有序/无序列表、引用块（含嵌套引用）、代码块（语言标签 `python`、横向滚动）、4 列表格、图片、链接、分隔线。
4. 点底栏「字号」→ 浮层里的 `A-` / `A+`：字号 13–26sp 逐级变化，正文立即重排，不重新解析。
5. 「设置」→ 滑杆调字号；「外观」切换 跟随系统 / 浅色 / 深色，立即生效且状态栏图标反色。
6. 杀进程重开：自动续读上次文件（SAF 持久化授权）；重装后首次启动为空态。
7. 若文档含本地图片，首次打开会申请 `READ_MEDIA_IMAGES`（Android 13+）/ 存储权限；拒绝时图片降级为占位条，正文不受影响。

## 六、包体与依赖（实测）

| 依赖 | 作用 | release 内实际占用 |
|---|---|---|
| `androidx.compose`（BOM 2024.06.00：ui / foundation / material3） | 全部 UI | classes.dex 主体 |
| `androidx.core:core-ktx:1.13.1` | 兼容层与 WindowInsets | 同上 |
| `androidx.activity:activity-compose:1.9.0` | Activity + SAF 结果回调 | 同上 |
| `androidx.lifecycle:lifecycle-{runtime,viewmodel}-compose:2.8.2` | 生命周期与状态收集 | 同上 |
| `org.commonmark:commonmark:0.22.0` | Markdown 解析 | 约 82 KB（dex 内 org.commonmark 全部类） |
| `org.commonmark:commonmark-ext-gfm-tables:0.22.0` | GFM 表格 | 合并在上述统计中 |
| 自研 `Latex.kt` + `MathView.kt` | LaTeX 子集解析与排版 | 约 60 KB（无第三方公式库，不引入 KaTeX/MathJax/WebView） |
| `junit:junit:4.13.2` | 单测（不打进包） | 0 |

图标同理：试过 `res/drawable` + `painterResource`（+70 KB，会把 Compose 矢量解析引擎整包拖进来）和内存 `ImageVector`（+39 KB，引擎仍在），最终改用 `Canvas` 直绘（+14 KB）。

未引入：图片加载库（Coil/Glide，省 0.6–1.2 MB）、导航库、DI 框架、WebView、Splash 库、公式渲染库、图标库（KaTeX/MathJax 走 WebView 会让包体涨 2 MB+ 并引入 JS 引擎）。

实测产物：

```
app-debug.apk     7.79 MB   （不混淆，仅调试用）
app-release.apk   1.33 MB   （R8 + shrinkResources；1.2.0 相比上一版增量主要来自 PPTX 渲染链路：表格折行、OMML→LaTeX 公式、柱状图，以及 PDF 目录树解析）
```

## 七、性能设计

- **解析只做一次**：文件读入即转成不可变 AST 并缓存在 ViewModel，主题/字号切换只触发重组。
- **首帧不等 IO**：续读上次文件放在首帧后的 `LaunchedEffect`，冷启动路径上没有任何磁盘读取与解析。
- **懒加载布局**：`LazyColumn` + 40 块分组，9.2 万字文档也只测量可见分组。
- **图片按需解码**：`produceState` + `BitmapFactory`，在 IO 线程解码，离开屏幕即释放。
- **启动无白屏**：`values/themes.xml` 与 Compose 主题同底色，Android 12+ 自适应图标 + `enableEdgeToEdge`。
- 实测冷启动 `Displayed` 打点 302 ms / 505 ms（同环境系统「设置」为 737 ms / 12 s 级）。

> 说明：模拟器首次冷启动会受到 dexopt/JIT 影响；真机上应更低。

## 八、边界与明确不做

- 不做 Markdown 编辑、账号登录、云同步、在线内容抓取。
- 不做网络图片：`http(s)` 图片显示占位提示（保持零网络权限）。
- HTML 不执行，避免引入 WebView 的体积与安全面。
- release 使用 debug 证书签名以便直接安装；上架时替换为自有签名配置即可。
