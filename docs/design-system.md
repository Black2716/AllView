# AllView 设计规范

> 本文件是 AllView 界面层的唯一约定来源。代码里的令牌在
> `app/src/main/java/com/yg/moduread/ui/Design.kt`（尺度）与 `ui/Theme.kt`（颜色 / 字体 / 形状），
> 两者由 `DesignTokensTest` 钉住（单调递增、透明度区间、48dp 触摸目标、文案格式）。
> 改界面时先看这里，要动数值就改令牌，不要在组件里写裸值。

阅读器只有一件事要做：把本地文件读出来，并且让人愿意读下去。
所以整套视觉的目标不是"好看"，而是 **安静**——内容占九成注意力，界面元素占一成。

---

## 一、三条底层原则

1. **内容优先**。正文区域不出现装饰、卡片、阴影。任何界面元素在滚动时都应该退让（顶栏淡入、底栏低对比）。
2. **一个值只有一个来源**。间距、圆角、字号、透明度都是令牌。同一个数值在两个文件里各写一遍，迟早会不一致——改造前"底栏高度 52dp 散在三处"就是这么来的。
3. **触摸目标不小于 48dp**，文字至少能放大到 1.3 倍而不截断。这两条是硬约束，不因为"看起来更精致"而让步。

---

## 二、颜色

### 2.1 明暗两套基色

浅色与深色是两套完整配色，都从 M3 语义色出发（`primary` / `surface` / `onSurface` / `outline` 等），组件里只引用语义名，不出现色值。

| 角色 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `primary` | `#2F6FEB` | `#7AA2F7` | 主操作、当前状态、进度 |
| `primaryContainer` | `#DCE7FB` | `#1B355E` | 选中底色（标签、目录当前项） |
| `background` / `surface` | `#FCFCFA` | `#0E1013` | 页面底色（略带暖调，纯白刺眼） |
| `surfaceContainerLow` | `#F7F7F3` | `#15181B` | 底栏、卡片 |
| `surfaceContainerHigh` | `#ECECE6` | `#23272C` | 浮层（字号面板、菜单） |
| `onSurface` | `#1A1A1A` | `#E4E4E4` | 正文 |
| `onSurfaceVariant` | 由 M3 推导 | 由 M3 推导 | 次要文字、图标 |
| `outline` | `#DADAD2` | `#2C3035` | 描边 |
| `outlineVariant` | `#E7E7E0` | 由 M3 推导 | 发丝线 |
| `error` | `#B3261E` | `#F2B8B5` | 错误文字 |
| `errorContainer` | 由 M3 推导 | 由 M3 推导 | 错误横幅底 |

深色不是浅色的反相：底色 `#0E1013` 比纯黑亮一点（纯黑 OLED 上滚动会有拖影），正文 `#E4E4E4` 比纯白暗一点（长时间阅读不刺眼）。

### 2.2 纸张主题（阅读区专用）

三种"纸"叠在基色之上，只覆盖背景 / 表面 / 描边 / 主色，**不动正文以外的语义色**（`error` 在任何纸上都得是红的）。

| 纸张 | 浅色背景 | 深色背景 | 意图 |
|---|---|---|---|
| 纸白 PAPER | `#FCFCFA` | `#0E1013` | 默认，等价于不换纸 |
| 米黄 SEPIA | `#F6F1E4` | `#1E1A14` | 长时间阅读，降低蓝光 |
| 夜间 NIGHT | `#1B1A18` | （同上叠加） | 关灯阅读，暖灰而非纯黑 |

M3 的其余颜色槽位保持基色不变，否则每换一次纸就要重算一整套对比度，结果是处处半调子。

### 2.3 状态栏图标

状态栏图标亮暗**按背景亮度判断**，不按"是不是深色模式"：

```kotlin
val lightBars = scheme.background.luminance() > 0.5f
```

原因很具体：浅色模式 + 夜间纸 = 深背景，此时若按 `dark == false` 画深色图标，图标会消失在背景里。

### 2.4 透明度

所有透明度取值集中在 `Alpha`，不允许在组件里写 `0.35f` 这类裸值：

| 令牌 | 值 | 用途 |
|---|---|---|
| `faint` | 0.35 | 不可用状态（如 PDF 无文本层时的搜索图标） |
| `disabled` | 0.38 | 禁用控件（与 M3 的 disabled 一致） |
| `selected` | 0.55 | 选中底色（底栏当前项、标签） |
| `soft` | 0.6 | 分隔线、辅助线 |
| `decorative` | 0.65 | 引用块竖条 |
| `hairline` | 0.7 | 表格行分隔 |
| `progress` | 0.75 | 进度填充、图像上的信息条 |
| `overlay` | 0.9 | 压在幻灯片上的页码胶囊 |
| `scrim` | 0.97 | 全屏看图遮罩 |

---

## 三、字体

### 3.1 字阶

`Type`（`Design.kt`）与 `AppTypography`（`Theme.kt`）是同一套值的两种形态：前者给"直接写 fontSize"的地方用，后者交给 `MaterialTheme`，让 M3 组件（Slider、DropdownMenu）自动跟上。

| 令牌 | 字号 | 用途 |
|---|---|---|
| `Type.label` / `labelMedium` | 11sp | 元信息、徽标、"第 N 块" |
| `Type.caption` / `bodySmall` | 12sp | 顶栏标题、进度百分比、时间 |
| `Type.body` / `bodyMedium` | 13sp | 次要说明、面板正文 |
| `Type.bodyLarge` / `bodyLarge` | 14sp | 界面主文字（"字号"、设置项） |
| `Type.title` / `titleLarge` | 16sp | 面板标题、页首标题（随字号设置缩放） |
| `Type.heading` / `headlineSmall` | 20sp | 章节级强调 |
| `Type.display` / `displaySmall` | 28sp | 首页品牌字 |

**阅读正文字号不在这套字阶里**：它由用户设置决定（13–26sp，默认 17），所有正文块的字号、行高都从 `prefs.fontSizeSp` 推导，标题按倍率放大（H1 1.75× / H2 1.40× / H3 1.18×…）。

### 3.2 行高

- 界面文字行高 = 字号 × 1.45（`bodyLarge` 14/21、`bodyMedium` 13/20）。
- 阅读正文行高 = 字号 × 1.75，段落之间另有 0.7 倍字号的间距——中文长文里"行距比段距小"才读得顺。
- 首页品牌字行高 = 字号 × 1.15（大字号不需要那么多行距）。

### 3.3 字重

只有四档：Regular（400）/ Medium（500）/ SemiBold（600）/ Bold（700）。
标题用 SemiBold，品牌字与 H1 用 Bold，正文一律 Regular。**不用 Light/Thin**——深色背景上细字会糊。

---

## 四、间距

`Sp` 是 8 的倍数阶梯，只有 4dp 一档例外（8 的一半，用于图标与文字之间）：

| 令牌 | 值 | 典型用途 |
|---|---|---|
| `Sp.xs` | 4dp | 图标与文字间隙、胶囊内边距 |
| `Sp.s` | 8dp | 列表项内边距、标签间距 |
| `Sp.m` | 12dp | 卡片内边距、徽标间距 |
| `Sp.l` | 16dp | 面板左右留白、段落间距 |
| `Sp.xl` | 24dp | 面板顶部、区块之间 |
| `Sp.xxl` | 32dp | 首页顶部、空态留白 |

两个正文相关的专用值：

- `ReaderGutter` = 20dp：阅读区左右留白，正文每行 20–24 个汉字。
- `ReaderTopBarHeight` = 56dp / `BottomBarHeight` = 52dp / `ReaderBottomContentInset` = 96dp：阅读器三段固定高度，顶栏叠在内容上所以列表顶部要预留完整高度。

**为什么不用 4 的倍数全铺**：界面里 4dp 的倍数太多会显得碎；8 的倍数给出节奏，4 只用来"微调一个图标的呼吸感"。

---

## 五、圆角与形状

| 令牌 | 值 | 用途 |
|---|---|---|
| `Radius.s` | 6dp | 标签胶囊、小按钮、输入框 |
| `Radius.m` | 12dp | 卡片、面板内容块、徽标 |
| `Radius.l` | 28dp | 底部面板顶部两个圆角 |
| `Radius.pill` | 999dp | 真正的胶囊（页码、开关） |

`Shapes` 是这些值的 `RoundedCornerShape` 形态（`Shapes.card` / `chip` / `pill` / `sheet`），组件里优先用它。
`AppShapes` 把同一套值交给 `MaterialTheme`：`extraSmall`=`small`=6dp、`medium`=`large`=12dp、`extraLarge`=28dp。
这样 M3 的 `ModalBottomSheet`、`Slider`、`DropdownMenu` 与自绘组件圆角一致——改造前 M3 用的是默认 4/8/12/28，与令牌并存，同一屏能看到两种圆角。

只有一处例外：代码块与表格仍用 8dp（`MdRenderer`），因为它嵌在阅读正文里，跟正文的"文章感"走，不跟界面控件走。

---

## 六、层级与阴影

M3 用色调层级（tonal elevation）表达,"浮起来"主要靠 `surfaceContainer*` 的明度差，阴影只在真正浮在内容之上的层用：

| 令牌 | 值 | 用途 |
|---|---|---|
| `Elevation.flat` | 0dp | 页面本体、底栏 |
| `Elevation.card` | 1dp | 卡片（多数时候用描边代替） |
| `Elevation.sheet` | 8dp | 底部面板 |
| `Elevation.overlay` | 12dp | 全屏浮层 |

**优先描边而不是投影**：1dp 的 `outlineVariant` 描边在任何背景上都清晰，投影在浅色纸面上容易糊成一块灰。

层级顺序（从下到上）：页面 → 卡片 / 列表项 → 底栏 → 底部面板 → 全屏浮层。
阅读器的顶栏与底栏**永远在内容之上**，但它们用低对比（`surfaceContainerLow` + 发丝线）而不是阴影来分隔。

---

## 七、图标

全部是 `MiIcons`（`ui/Icons.kt`）里的 Canvas 直绘图形，共 20 个：

| 分组 | 图标 |
|---|---|
| 导航 | `Toc`（目录）、`Back`、`Chevron`、`ChevronDown` |
| 操作 | `Search`、`SearchOff`、`More`、`Close`、`Check`、`Tune` |
| 内容 | `Bookmark`、`File`、`Folder`、`Stack`、`TaskBox` |
| 主题 | `Sun`、`Moon`、`Paper` |
| 字号 | `FontMinus`、`FontPlus`（以及文字标记 `FONT_LABEL = "Aa"`）、`Trash` |

规范：

- 基准尺寸 22dp（`Frame` 里线宽 = 宽度 × 1.8/22，所以任何尺寸下线宽比例一致）。
- 视觉尺寸 18–22dp，点击区一律由外层容器撑到 48dp，**不让图标本身变大**。
- 颜色永远从 `tint` 参数传入，不写死。
- 不引入矢量图库：实测 `painterResource` 方案 +70KB、`ImageVector` 方案 +39KB dex，而 release APK 目标是 1.3MB 量级。

---

## 八、动效

只有四档时长（`Motion`），全部短于 300ms：

| 令牌 | 值 | 用途 |
|---|---|---|
| `fast` | 120ms | 颜色、透明度、按下缩放 |
| `base` | 180ms | 位移、进度条 |
| `slow` | 260ms | 大块内容进场 |

原则：

1. **只动必要的东西**。顶栏滚动时淡入（150ms），字号面板滑出（140/160ms），按下缩放（120ms）。
2. **不用 `AnimatedVisibility` / `Crossfade` / `AnimatedContent`**——它们会把 `androidx.compose.animation` 整包拖进 dex（约 +50KB）。改用 `animateFloatAsState` + `graphicsLayer`，效果一致。
3. 面板进出不加弹性曲线，直线 `tween` 就够；阅读器里出现"弹一下"的动效会打断阅读。

---

## 九、组件

`ui/Components.kt` 是界面层的公共件，新写界面时优先复用：

| 组件 | 说明 |
|---|---|
| `SheetDragHandle` | 面板顶部 36×4dp 拖拽条 |
| `SheetHeader` | 面板标题 + 计数徽标 + 关闭键 |
| `IconAction` | 48dp 正方形图标按钮（必须传 `description`） |
| `SettingCard` | 设置项卡片：标题 + 1dp 描边 + 内容槽 |
| `SectionLabel` | 区块小标题（`Type.label`，`onSurfaceVariant`） |
| `HairlineDivider` | 1dp 发丝线 |
| `ChoiceChip` | 单选胶囊，支持色块与对勾，≥48dp，带 `Role.RadioButton` |
| `PrimaryAction` | 主按钮：主色填充 + 前置图标 + 按下缩放 |
| `SecondaryAction` | 次按钮：描边 |
| `EmptyHint` | 空态：标题 + 说明 + 可选图标 |
| `MetaBadge` | 胶囊小标签（"§3"、"N 项"） |
| `TypeBadge` | 40dp 文件类型徽标（底色 = 类型色 12%） |
| `SearchField` | 搜索输入框：填充 + 描边 + 清除键（不用 M3 的 `OutlinedTextField`） |

**为什么自己写按钮与输入框**：M3 的 `Button` / `OutlinedTextField` 在这个应用里带来两个问题——默认高度 40dp（不到 48dp 触摸目标），描边风格与全站的填充卡片风格冲突。自绘件只有几十行，但能同时满足触摸、圆角、配色三条约束。

---

## 十、无障碍

硬性要求：

1. **触摸目标 ≥ 48dp**：所有可点元素的外层容器至少 `MinTouchTarget`（图标本身可以只有 14–22dp）。标签条的关闭键高度受 48dp 行高约束，因此整条标签的高度也提到 48dp。
2. **图标按钮必须有 `contentDescription`**：`IconAction` 的 `description` 是必填参数。纯装饰图标（正文里的引用竖条等）才允许为空。
3. **选中态用语义表达**：标签用 `Role.Tab + selected`，单选胶囊用 `Role.RadioButton + selected`，列表项用 `Role.Button`。只靠颜色区分选中，读屏用户完全无法感知。
4. **字体放大 1.3 倍不截断**：固定 `height()` 全部改成 `heightIn(min=)`；列表行高跟随文字；标签条高度取 `MinTouchTarget` 而不是 30dp。

---

## 十一、逐屏规范

### 11.1 首页（`HomeScreen`）

未打开文档时显示，替代了改造前的"空态页"。

```
┌─────────────────────────────┐
│ AllView                     │  28sp Bold + 一行副标题
│ 本地文档阅读器 · 无需联网      │
│                             │
│ [ 打开文件 ]                  │  主按钮，全宽，48dp
│                             │
│ 最近打开              3 项    │  区块标题 + 计数
│ ┌─────────────────────────┐ │
│ │[MD] 笔记.md              ×│ │  类型徽标 + 名称 + "格式 · 时间"
│ └─────────────────────────┘ │
│ ...                         │
│ ┌─────────────────────────┐ │
│ │ 能打开                    │ │  支持格式说明
│ │ Markdown、纯文本、PDF…    │ │
│ └─────────────────────────┘ │
└─────────────────────────────┘
```

- 有错误时在副标题下插入 `ErrorBanner`（`errorContainer` 底 + 左侧 3dp 竖条），**不弹窗**——错误不该打断"再选一次文件"的动作。
- 最近列表为空时换成 `EmptyHint`，而不是把标题和计数都留着显示"0 项"。
- 整页可滚动（改造前横屏/分屏会把末尾条目裁掉）。

### 11.2 阅读器（`ReaderScreen`）

```
┌─────────────────────────────┐
│ 笔记.md                32%  │  顶栏 56dp，滚动后淡入
├─────────────────────────────┤
│ 笔记                         │  文档标题（随字号缩放）
│ ──── Markdown · 12,345 字    │  主色短横线 + 元信息
│                             │
│ 正文块…                      │  ReaderGutter = 20dp 左右留白
│                             │
├─────────────────────────────┤
│▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░│  2dp 进度轨道
│  目录   搜索   字号   更多    │  底栏 52dp，4 等分
└─────────────────────────────┘
```

- 进度条**只有一条**，就是底栏顶部那条轨道（改造前顶栏百分比与独立进度线是两套数据源，容易不同步）。
- 顶栏是浮在内容上的，滚动超过一屏才出现；第一节内容因此不需要为它让位（列表 `contentPadding.top = ReaderTopBarHeight`）。
- 底栏活跃项（字号面板打开）用 `primaryContainer` 淡底 + 主色图标，不只换颜色。
- 字号面板从底栏之上滑出，含减小/增大按钮（40dp）、滑块、当前值与关闭键。

### 11.3 标签条（`DocTabs`）

只在多开时出现。单文档不占任何高度，避免"打开一个文件却看到一条只有自己的标签"。

- 每条标签高 48dp、最大宽 200dp，激活用 `primaryContainer` 底 + `onPrimaryContainer` 字。
- 关闭键是标签内的 48×48dp 独立命中区，不与标签本身的点击冲突。
- 整条底部有 1dp 发丝线，与正文区分。

### 11.4 底部面板（目录 / 搜索 / 书签 / 设置）

统一结构：`SheetDragHandle` → `SheetHeader`（标题 + 计数 + 关闭）→ 内容。

- 列表最高 `SheetListMaxHeight` = 420dp，超出内部滚动。
- 四类空态都用 `EmptyHint`：一句标题 + 一句"接下来能做什么"，不用单个"暂无数据"。
- 输入框统一 `SearchField`（填充 + 描边 + 清除键）。

**目录**：层级缩进每级 14dp，最多 4 级；1–2 级左侧有 2dp 竖线；当前章节 `primaryContainer` 底，并带"当前章节"的 `contentDescription`（PDF 会有多页同名标题，所以列表 key 用下标 + 块索引）。

**搜索**：命中片段里关键词加粗 + 主色 12% 底；右侧 `MetaBadge` 显示块序号；超过 200 条时提示只显示前 200 条。

**书签**：左侧 2×28dp 主色竖条标记"这是一处收藏"，摘要两行，右侧删除键 48dp。

**设置**：三张 `SettingCard`（字号 / 外观 / 纸张）。字号卡片显示当前 sp 值与滑块；外观与纸张都用 `ChoiceChip`，纸张额外带 10dp 色块。

### 11.5 幻灯片（`SlideDeckView`）

16:9 卡片，白底 + 发丝描边（不用阴影），页码做成右下角胶囊。左右留白与正文一致（`ReaderGutter`），切换文字/幻灯片时不会横向跳动。

### 11.6 全屏看图（`PdfFullscreenOverlay`）

深色遮罩（`Alpha.scrim`），顶部一条信息条（页码 + 缩放百分比）与一个明确的关闭键。双指缩放，双击复位。

---

## 十二、自查清单

改完一屏，逐条过：

- [ ] 没有任何裸 `dp` / `sp` / `Color(0x…)` / `0.xf`（令牌里除外）
- [ ] 所有可点元素 ≥ 48dp，图标按钮有 `contentDescription`
- [ ] 选中/禁用状态用语义表达，不只靠颜色
- [ ] 浅色、深色、米黄、夜间四种组合都看过一遍
- [ ] 系统字体放大到 1.3 倍，文字不截断、按钮不溢出
- [ ] 空态、加载态、错误态都有文案，不是空白或只有转圈
- [ ] 横屏与分屏下不被裁切（该滚动的能滚）
- [ ] 没有新增依赖；APK 体积量级不变
