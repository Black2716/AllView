# 更新日志

版本号唯一来源是 `app/build.gradle.kts` 的 `versionName`。
每次发版：改 `versionCode` + `versionName` → 提交 → 打 `v<版本>` tag → 推送 tag。

## 1.3.0

界面全面重塑，**功能与数据零变更**：格式支持、解析结果、交互路径、存储键、`applicationId` 全部与 1.2.1 一致。

设计约定与逐屏规范见 [docs/design-system.md](docs/design-system.md)。

### 设计系统

- 颜色、字阶、形状交给 `MaterialTheme`：新增 `AppTypography`（12 档）与 `AppShapes`，
  M3 组件的圆角与字阶和自绘组件统一（改造前是两套并存）
- 令牌补齐：`Radius.l`、`Type.heading`、`Elevation`、`Motion`、`Hairline`、`SheetListMaxHeight`，
  透明度收成 9 档语义令牌（`faint` / `disabled` / `selected` / `soft` / `decorative` / `hairline` / `progress` / `overlay` / `scrim`），
  组件里不再出现裸浮点数
- 纸张主题补齐：米黄与夜间两套现在覆盖 `primary` / `primaryContainer` / `outline` 系，
  深色模式不再在暖纸底上露出冷蓝
- 状态栏图标按背景亮度反色，修掉"浅色模式 + 夜间纸时图标看不见"

### 首页（新）

- `HomeScreen` 取代原空态页：品牌头 + 主操作 + 最近打开卡片列表 + 格式说明，整页可滚动
- 最近文件卡片带类型徽标（MD / PDF / PPT / XLS…）、格式名与相对时间，删除键 48dp
- 打开失败改为页内错误横幅（`errorContainer`），不再挤占主按钮位置

### 阅读器

- 进度线合并为一条：底栏顶部 2dp 轨道，带 `tween` 过渡；顶栏百分比与它同源
- `ReaderTopBarHeight` (56dp) / `BottomBarHeight` (52dp) / `ReaderBottomContentInset` (96dp) 三个常量统一各处的隐式契约
- 页首元信息新增格式名与字数（"PDF · 24 页" / "Markdown · 12,345 字"）
- 底栏活跃项加 `primaryContainer` 底色，字号浮层加 `A-` / `A+`（40dp）与当前值
- 加载态加文案（"正在解析文档"）

### 面板

- `AppSheet` 显式指定形状、容器色、遮罩透明度与拖拽条
- 四类面板统一结构：`SheetDragHandle` → `SheetHeader`（标题 + 计数 + 关闭）→ 内容
- 目录：行高 ≥ 48dp、当前项 `primaryContainer` 底 + "当前章节"语义、缩进改用令牌
- 搜索：命中片段关键词高亮（最多 20 处）、块号徽标、四类空态各有文案；输入框换成 `SearchField`
- 书签：卡片化、删除键 48dp 且带描述、左侧主色标记条
- 设置：三张 `SettingCard`（字号 / 外观 / 纸张），外观与纸张用带对勾的 `ChoiceChip`

### 标签条与浮层

- 标签高 30dp → 48dp，关闭键独立 48×48dp 命中区，激活态换 `primaryContainer`，加 `Role.Tab` 语义
- 幻灯片：留白与正文对齐、卡片改描边、页码做成胶囊
- PDF 全屏看图：顶部信息条 + 明确的关闭键

### 图标

- 自绘图标 6 → 20 个（`Back`/`Chevron`/`Check`/`Tune`/`Bookmark`/`File`/`Folder`/`Sun`/`Moon`/`Paper`/`FontMinus`/`FontPlus`/`Trash`/`Stack`/`SearchOff` 等），仍是 `Canvas` 直绘
- `IconAction` 把 `contentDescription` 变成必填参数

### 内务

- 测试 249 → **258**（新增 `DesignTokensTest`：令牌单调性、透明度区间、48dp 触摸目标、相对时间与字数文案、格式名覆盖）
- 新增 [docs/design-system.md](docs/design-system.md)

## 1.2.1

结构重构与 UI 缺陷修复，**对外行为零变更**：接口、配置键、输入输出、边界与异常行为全部与 1.2.0 一致。

### 结构（不改行为）

- `ReaderViewModel` 435 → 347 行：编解码、进度、偏好、会话 uri、错误文案、加载态映射各自独立成文件
- `ReaderScreen` 720 → 339 行：只留滚动编排，浮层与底栏拆到 `ReaderChrome`，空态拆到 `EmptyView`
- 4 处 `ModalBottomSheet` 样板收敛为 `AppSheet`；2 份测试用 `FakePrefs` 合并为一份
- 新增 44 条回归测试，钉住重构前行为；原有 198 条一条未改

### 修复（UI）

- 大图按屏幕宽度采样解码，4000×3000 照片不再整图入堆（原约 48 MB，滚动即可能 OOM）
- 标签关闭 ×、最近文件 ×、字号浮层 × 三处命中区放大到 48dp，并补 `contentDescription`
- 空态页支持滚动，横屏/分屏不再裁掉"最近打开"末尾条目
- 切换标签时目录高亮首帧即落在正确位置
- 底栏高度从三处硬编码收敛为 `BottomBarHeight` / `ReaderBottomContentInset`

### 内务

- 测试 242 → **249**（新增 7 条覆盖采样算法）
- 分析报告：[docs/refactor-report.md](docs/refactor-report.md)

## 1.2.0

PPTX 渲染链路与 PDF 解析重做。

### PPTX

- 表格：单元格按列宽折行（中文 1em / 英文 0.55em 估宽），长文本不再溢出到相邻单元格
- 公式：改为 OMML → LaTeX → 复用已有的 LaTeX 语法树渲染，替换原先手绘 SVG 的方案
- 数学字符按码点遍历：U+1D400 以上是代理对，逐 `Char` 读取只能拿到半个
- 新增柱状图渲染，识别 `graphicFrame` 里的 `c:chart` 引用
- 角标固定排在右上/右下，超长脚本缩字号而不是居中

### PDF

- 加载从 43 秒降到约 0.1 秒：去掉 `/Parent` 链里的全量正则、`estimatePages` 的重复全文转换、跨行非贪婪回溯
- 目录按 `/First` + `/Next` 树序遍历，修正三处结构误判：`/Dest` 在 `/Title` 之前、根对象需从 Catalog 取引用、对象号要跳过 generation 号
- 标题解码先撤销 PDF 转义再按 UTF-16BE 解，消除混排乱码
- 目录标题不再插入正文，改由 `outline` 单独承载
- 修正列表项与正文块的索引差一格（目录跳转、恢复进度、高亮三处）
- 同页多条目时高亮该页首个条目

### 其他

- 移除已被 `LatexSvg` 取代的 `OmmlRenderer.kt`
- 大体积测试样本移出版本控制（`sample/regress/*.pdf|docx|pptx`），本地保留即可，测试对缺失样本容错

## 1.1.0

- 多文件多开（标签页）与 SAF 回退
- UI 重做：纸张主题（纸白 / 米黄 / 夜间）、目录抽屉、搜索面板、字号浮层

## 1.0.0

- 首个版本：本地 Markdown 阅读器，支持标题、段落、列表、引用、代码块、表格、链接、图片
- 原生 LaTeX 公式（`$$…$$` 块级、`$…$` 行内）
- 目录、全文搜索、代码高亮、最近文件、外部打开
