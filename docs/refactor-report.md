# AllView 逆向分析与重构报告

> 项目：`E:/code/project/vibe/AllView`（原 ModuReader，包名 `com.yg.moduread` 保持不变）
> 目标：在不改变任何对外行为的前提下完成结构解耦、消除重复。
> 验收基线：重构前 `./gradle :app:testDebugUnitTest` = **198 tests / 26 suites / 0 failures**；
> 重构后同命令 = **264 tests / 36 suites / 0 failures**（新增 66 条全部是"钉住旧行为"的回归测试）。
> 三批依次为：结构重构（+44）、UI 缺陷修复（+7）、界面重塑（+15）。
> 其中第一批结构重构新增 44 条，第二批 UI 缺陷修复新增 7 条 `ImageSamplingTest`。

---

## 一、逆向梳理（重构基线）

### 1.1 模块划分

| 层 | 包 | 职责 | 规模 |
|---|---|---|---|
| 入口 | `MainActivity` | 只做三件事：`enableEdgeToEdge`、把 `ACTION_VIEW` 的 uri 存进 `vm.pendingExternalUri`、`setContent { AppTheme { ReaderRoot(vm) } }` | 41 行 |
| 状态 | `ui/ReaderViewModel` + `ui/ReaderState` + `ui/DocSession` | 多开会话、加载编排、偏好、书签、跳转 | 435 → 347 行 |
| 界面 | `ui/*` | ReaderRoot（装配 + 权限 + SAF）→ ReaderScreen（滚动编排）→ MdRenderer（块渲染）；4 个底部面板；Toc/Search/Bookmark/Settings | ~3400 行 |
| 解析 | `md/*` | MdModel（AST）、MdParser（commonmark + GFM）、MdToc、SearchIndex、Latex、CodeHighlighter | ~1300 行 |
| 格式 | `format/*` | DocFormat（格式判定）、TextAdapters、ContainerAdapters（EPUB/DOCX/ODT）、XlsxAdapter、PptxAdapter + PptxSvg、PdfAdapter、OmmlToLatex、ChartSvg | ~4300 行 |
| 数据 | `data/*` | DocRepository（IO 入口）、RecentFiles、Bookmarks、MdDocResolver、MdImageResolver、ExternalOpen、PdfPageSizes | ~700 行 |

### 1.2 入口与关键调用链

```
MainActivity.onCreate
  └─ ExternalOpen.uriOf(intent)          // 只接受 ACTION_VIEW + scheme content/file
  └─ setContent → ReaderRoot(vm)
       └─ LaunchedEffect(Unit) → vm.restoreLastIfNeeded()
            ├─ pendingExternalUri != null → open(uri)            // 外部打开优先
            ├─ prefs["session_uris"] 非空 → 逐个 open(silent,newTab) + pendingActiveUri
            └─ prefs["last_uri"] → open(silent,true)

vm.open(uri, silent, newTab)
  ├─ 已开过 && !newTab → 切 _activeId + 写 active_uri
  └─ openInNewSession
       ├─ 立即插入 DocumentState(loading=true) 会话（界面先出骨架）
       ├─ takePersistableUriPermission(READ)
       ├─ DocRepository.load(context, uri)            // Dispatchers.IO
       │    ├─ DocFormat.detect(name, mime)
       │    ├─ openInputStream().readBytes()
       │    ├─ parseByFormat(...)  → ParsedDoc(blocks, plainText)
       │    ├─ PDF：PdfAdapter.inspect → parse
       │    ├─ PPTX：PptxAdapter.parse + PptxAdapter.toDeck（逐页 SVG）
       │    └─ 解析异常 → 降级 TextAdapters.txt(safeDecode(raw))
       ├─ ProgressStore.read(uri)                     // 恢复位置
       ├─ buildDocumentState(loaded, index, offset)    // 目录/锚点/搜索索引
       ├─ 写 last_uri、RecentFiles.remember
       └─ persistSessionUris()
```

界面回调反向链：

```
ReaderScreen.LazyColumn
  ├─ snapshotFlow{firstVisibleItemIndex}          → onVisibleBlock → vm.saveSessionVisibleBlock（目录高亮，即时）
  ├─ snapshotFlow{index to offset}.debounce(1000) → onSaveProgress  → vm.saveProgress（落盘，节流）
  ├─ LaunchedEffect(doc.docUri)                   → listState.scrollToItem(restoreIndex+1, restoreOffset)
  └─ LaunchedEffect(jumpTarget)                   → scrollToItem(target+1) + onVisibleBlock + onJumpConsumed
```

### 1.3 数据流

单向下行：`SharedPreferences / 文件系统 → DocRepository → DocumentState → Composable`；
单向上行：`用户手势 → 回调 → ViewModel → SharedPreferences`。
没有任何双向绑定，没有全局单例状态，这也是可以安全做纯结构重构的前提。

### 1.4 外部依赖

- 运行时：仅 AndroidX + Compose Material3 + `org.commonmark:commonmark:0.22.0` + GFM tables 扩展。
- 无网络、无数据库、无 Gson、无第三方 UI 库。所有持久化都是 `SharedPreferences` 里的一两个字符串。
- 单测：JUnit4 + 手写 `FakePrefs` 内存替身（无 Robolectric）。

### 1.5 配置来源

| 键 | 类型 | 写入点 | 语义 |
|---|---|---|---|
| `font_size` | Float | `setFontSize` | 17f 默认，13f..26f |
| `theme_mode` | String | `setThemeMode` | SYSTEM/LIGHT/DARK |
| `reading_theme` | String | `setReadingTheme` | PAPER/SEPIA/NIGHT |
| `last_uri` | String | `openInNewSession` | 老版本单文档续读 |
| `session_uris` | String | `persistSessionUris` | `\n` 分隔的多开列表 |
| `active_uri` | String | `switchTo`/`open` | 上次活动标签 |
| `progress_index_<hash>` | Int | `saveProgress` | 上次可见块 |
| `progress_offset_<hash>` | Int | `saveProgress` | 上次像素偏移 |
| `recent_files` | String | `RecentFiles.write` | `\u0001`/`\u0002` 分隔，上限 8 |
| `bookmarks` | String | `Bookmarks.write` | `\u0003`/`\u0004` 分隔，上限 200 |

**这些键名与分隔符全部逐字保留** —— 改动任何一个都等于让已装用户的阅读位置、最近文件、书签全部失效。

---

## 二、重构改动清单

### 2.1 新增文件（8 个，全部是"搬家 + 单一职责"）

| 文件 | 行数 | 从哪来 | 为什么 |
|---|---|---|---|
| `data/PrefsCodec.kt` | 49 | RecentFiles.write/list + Bookmarks.write/all 的切分拼装 | 同一套"split 记录 / split 字段 / joinToString 写回"写了两遍，只差分隔符。改一处 bug 要改两遍。收敛后编解码只有一处实现，分隔符仍由调用方传入（不能统一，历史数据用的是两对） |
| `data/OpenError.kt` | 31 | `ReaderViewModel.friendlyError` | 纯字符串映射，却因写在 ViewModel 私有方法里无法单测。抽出后是可直接断言的函数，7 条用例钉住每个分支与判定顺序 |
| `data/ProgressStore.kt` | 39 | `saveProgress/readProgress/progressKey` | 键名拼接、hashCode 取键、非负收敛三件事与界面生命周期无关。键格式保持 `progress_index_<uri.hashCode()>` |
| `ui/ReaderPrefsStore.kt` | 47 | `_readerPrefs` 初始化 + 三个 setter 里的 prefs 写入 | "读一次、写三处"，键名分散在 ViewModel 的 companion 里。放 ui 包（它读写的 `ReaderPrefs` 是界面模型，放 data 会让 data 反向依赖 ui 枚举） |
| `data/SessionUris.kt` | 23 | `persistSessionUris` + `restoreLastIfNeeded` 的编解码 | `\n` 拼接/拆分出现两次，且承担"老版本 last_uri 与新版本列表共存"的兼容职责。抽成两个纯函数后兼容规则有唯一实现处 |
| `ui/AppSheet.kt` | 30 | TocSheet/SearchSheet/BookmarkSheet/SettingsSheet 各写一遍的 `@OptIn + rememberModalBottomSheetState(true) + ModalBottomSheet` | 4 份逐字重复的样板 = "面板怎么出现"有 4 个定义处。新增面板时漏掉 `skipPartiallyExpanded` 就会出现半屏态 |
| `ui/ReaderChrome.kt` | 303 | ReaderScreen 的 DocTitle/TopBar/ReaderBottomBar/BarIcon/FontGlyph/FontPanel/LoadingView | ReaderScreen 720 行里一半是"滚动编排"（含 5 处偏移量 +1 的历史 bug 注释），一半是"长什么样"。两者改动原因完全不同：调图标间距不该动 scrollToItem 的偏移计算 |
| `ui/EmptyView.kt` | 154 | ReaderScreen 的 EmptyView/RecentRow/relativeTime | 空态页只依赖 `RecentFile` + 3 个回调，与阅读器滚动编排无关 |
| `ui/DocumentStateMappers.kt` | 72 | `openInNewSession` 里的 25 行 `DocumentState(...)` 构造 + 失败分支的 `copy` | 打开流程原本 90 行，"取授权/读文件/写偏好/刷书签"之间夹着纯构造。抽出后映射规则可单测（PDF outline 优先、blockIndex 用真实页码、anchors 由最终 toc 生成） |
| `ui/LinkRouter.kt` | 50 | ReaderRoot 的 `onLink` 三层 when | 路由策略（锚点 → 兄弟文档 → SAF 回退 → 外部浏览器）藏在 UI 装配代码里。抽出后 ReaderRoot 只声明"去哪"，判定顺序集中一处 |
| `md/MdBlockKey.kt` | 41 | ReaderRoot 文件末尾的 `MdBlock.keyHint()` / `List<MdBlock>.displayTitle()` | 操作的完全是 `MdBlock` 模型，与 Compose 无关，却挂在 ui 包文件末尾（靠"同包可见"这种隐式约定被发现）。移到 md 包后可脱离 Compose 断言 |

### 2.2 修改文件

| 文件 | 变化 | 为什么 | 行为影响 |
|---|---|---|---|
| `ui/ReaderViewModel.kt` | 435 → 347 行 | 偏好/进度/会话 uri/错误文案/状态映射全部委托给上面 6 个单元；`switchTo`/`closeSession` 里重复两次的 `first{...}`/`getOrNull(...)` 合并为一次取值 | 无。每个委托方法的参数、返回值、写入键名逐字一致 |
| `ui/ReaderScreen.kt` | 720 → 339 行 | 外观部件与空态页移出，只保留滚动编排 | 无。`contentPadding`、`+1` 偏移、debounce 1000ms、`keyHint` 组合全部原样 |
| `ui/ReaderRoot.kt` | 247 → 204 行 | `onLink` 改调 `dispatchLink`；两个顶层扩展移出 | 无。三分支判定顺序、SAF 回退条件、`pendingDocLink` 赋值一致 |
| `data/RecentFiles.kt` | 57 行（内部改写） | 读写走 `PrefsCodec`，只保留"去重 + 置顶 + 上限 8" | 无。分隔符 `\u0001`/`\u0002`、字段顺序、`parts.size < 3` 丢弃规则不变 |
| `data/Bookmarks.kt` | 74 → 72 行 | 同上，走 `PrefsCodec` | 无。`\u0003`/`\u0004`、`f.size < 4`、`EXCERPT_LEN=40`、`LIMIT=200` 不变 |
| `ui/TocSheet.kt` `ui/SearchSheet.kt` `ui/BookmarkSheet.kt` `ui/SettingsSheet.kt` | 各减 5 行 | 改用 `AppSheet` 外壳 | 无。`skipPartiallyExpanded = true` 语义相同 |

### 2.3 新增测试（7 个 suite，44 条）

| 文件 | 条数 | 钉住什么 |
|---|---|---|
| `FakePrefs.kt` | — | 支持 String/Int/Float/Long 的内存替身（原有两个测试各内联了一份，现在共用） |
| `PrefsCodecTest.kt` | 5 | 缺键/空串读空、往返保边界、空表写空串、两对分隔符不串 |
| `ProgressStoreTest.kt` | 5 | 缺省为 0、往返、按 uri 隔离、负值收敛、**键前缀固定** |
| `ReaderPrefsStoreTest.kt` | 5 | 默认值、**键名固定**、往返、非法枚举回落、存储层不做 clamp |
| `SessionUrisTest.kt` | 5 | `\n` 编码、保序、空行丢弃、尾换行不产生空 tab、**键名固定** |
| `OpenErrorTest.kt` | 7 | 四条文案分支 + 判定顺序 + 首行截断 + 120 字截断 |
| `MdBlockKeyTest.kt` | 8 | 各块类型 key 前缀逐字一致、24/16 字截断、displayTitle 优先 H1/H2 与两种回退 |
| `DocumentStateMappersTest.kt` | 9 | 目录/锚点指向同一块、恢复位置透传、loading 清零、PDF outline 优先、level 收敛 1..6、失败只补标题 |

---

## 三、验证功能未变

### 3.1 自动化验证（主要手段）

```bash
cd /e/code/project/vibe/AllView
JAVA_HOME='D:\jdk17' /d/gradle/gradle-8.7/bin/gradle :app:testDebugUnitTest --offline --console=plain
```

| 时点 | suites | tests | failures | errors |
|---|---|---|---|---|
| 重构前（基线） | 26 | **198** | 0 | 0 |
| 第一批（数据层抽取）后 | 26 | 198 | 0 | 0 |
| 第二批（UI 拆分）后 | 26 | 198 | 0 | 0 |
| 第三批（路由/映射抽取）后 | 32 | 233 | 0 | 0 |
| 最终（含映射测试） | 33 | **242** | 0 | 0 |
| 第二批（UI 缺陷修复，结构重构未回退） | 34 | **249** | 0 | 0 |

关键点：**原有 198 条一条未改、一条未删、全部通过**。它们覆盖的正是本次改动最多的区域：

- `RecentFilesTest`（6）/ `BookmarksTest`（8）→ 直接验证 `PrefsCodec` 改写后的序列化与淘汰规则
- `FormatRegressionsTest`（11）→ 验证 `DocRepository.sanitizeTitle` 等未受影响
- `MdTocTest`（7）/ `SearchIndexTest`（7）→ 验证目录与锚点生成未变
- `DocSessionTest`（7）→ 验证会话数据类未变
- `PptxRealTest`/`PptxColorTest`/`SampleFormatsTest` → 验证解析链路未被牵连

### 3.2 构建验证

```bash
gradle :app:assembleDebug --offline     # BUILD SUCCESSFUL，app-debug.apk 8.4 MB
```

编译告警与重构前**完全一致**（9 条，全部在 `DocRepository`/`PdfAdapter`/`PptxSvg`/`Latex`/`Icons` 等未改动文件里），新增文件零告警。

### 3.3 手动回归路径（真机/模拟器）

按用户可见行为逐项走一遍，重点在"重构动了的地方"：

| # | 操作 | 期望（与重构前一致） |
|---|---|---|
| 1 | 冷启动，无历史记录 | 空态页：`AllView` 30sp + "打开本地文件开始阅读" + "选择文件"胶囊；无"最近打开"区 |
| 2 | 点"选择文件"选 `.md` | 先出居中转圈；解析完显示页首大标题 + 正文；**底栏 4 个图标：目录/搜索/字号/更多** |
| 3 | 滚动正文 | 顶栏淡入，右侧百分比随滚动增长；底部 2dp 进度线同步增长 |
| 4 | 点"目录" | 底部面板滑出（**不做半屏态**）；标题"目录 N 个标题"；输入框可过滤；列表 2 条以上 |
| 5 | 点目录项 | 面板关闭，跳到对应块；**不偏移一页**（这是 `+1` 的历史 bug 点） |
| 6 | 点"搜索"输入 ≥2 字 | 显示"N 处命中"或"没有找到「x」"；点命中项跳转 |
| 7 | PDF 或无文字层文档 | 搜索入口**置灰**（alpha 0.35） |
| 8 | 点"字号" | 浮层从底栏上滑出（Aa + 滑杆 + 数值 + ×）；拖动即时生效；关闭后字号保持 |
| 9 | 点"更多" | 下拉锚定在该按钮**正上方**（不贴屏幕最左）；三项：换一个文件/书签/阅读设置 |
| 10 | 长按正文段落 | 加书签成功；书签面板显示"<摘要> / 第 N 块 + ×" |
| 11 | 点书签项 | 摘要一致才跳转；文档改版后摘要对不上则不跳（面板保持打开） |
| 12 | 阅读设置改主题/纸张 | 立即生效；杀进程重启后保持 |
| 13 | 字号拉到两端 | 被夹在 13..26；重启后保持 |
| 14 | 打开第二个文件 | 出现顶部标签条；切换标签时各自滚动位置独立 |
| 15 | 关掉活动标签 | 自动切到相邻标签（下标 `coerceAtMost(lastIndex)` 语义） |
| 16 | 杀进程重启 | 恢复上次多开列表 + **上次活动的那个标签** |
| 17 | 删除一个已打开的文件后重启 | 该标签被静默丢弃，不产生"未命名"空标签 |
| 18 | 从文件管理器用 AllView 打开 | 优先于"续读上次文件" |
| 19 | 点 `#锚点` 链接 | 跳到对应标题 |
| 20 | 点 `.md` 相对路径链接 | 同目录能读则直接打开；读不到则弹 SAF，选完即打开 |
| 21 | 点 `https://` 链接 | 交给系统浏览器 |
| 22 | 打开损坏/无权限文件 | 标签保留，显示对应文案（未授权/文件找不到/没有读取权限/打开失败，四选一） |
| 23 | 打开 `.doc` | 显示"Office 97-2003 旧格式"提示与另存为建议 |
| 24 | 打开不支持格式 | 显示"AllView 暂不支持 .xxx 格式。"（**注意：这句在源码里是内插失效的写法，输出的是字面量，重构保留了这个行为**） |
| 25 | 打开 PPTX | 按 16:9 逐页显示，进度分母是页数而非块数 |

### 3.4 一处刻意保留的"疑似 bug"

`DocRepository.parseByFormat` 的 `UNSUPPORTED` 分支里，提示语写在三引号字符串中却带着 `" + shown + "`，
Kotlin 不会对它做内插，用户实际看到的是字面量 `AllView 暂不支持 " + shown + " 格式。`。

按"不擅自改变行为"的要求，本次**原样保留**。若要修，是独立的一行改动（把 `"` 换成 `$shown`），
建议单独提交并在 CHANGELOG 里标注为修复。

---

## 四、每处改动对优化目标的追溯

| 优化目标 | 对应改动 |
|---|---|
| **结构解耦** | ReaderViewModel 拆出 PrefsCodec/ProgressStore/ReaderPrefsStore/SessionUris/OpenError/DocumentStateMappers；ReaderScreen 拆出 ReaderChrome/EmptyView；ReaderRoot 拆出 LinkRouter；两个 md 扩展移回 md 包 |
| **消除重复** | 4 处 ModalBottomSheet 样板 → AppSheet；2 份记录编解码 → PrefsCodec；2 份 `FakePrefs` → 共用；2 处 `first{...}`/`getOrNull` 重复取值 → 一次 |
| **可读性** | ReaderScreen 720→339 行只留编排；ReaderViewModel 435→347 行只留会话编排；每个抽取文件顶部写明"为什么抽"与"不能动的常量" |
| **可测性** | 无法单测的 4 段逻辑（错误文案、映射、key 规则、存储编解码）变为纯函数，新增 44 条用例 |
| **性能** | 第一批（结构重构）未动性能 —— 没有可安全证明的收益点，不为"优化"引入风险；第二批补了两处可度量的：大图按目标宽度采样解码、行内样式按内容记忆化 |

---

## 五、第二批：UI 层缺陷修复与一致性收口

这一批不在"结构重构"的范围内，是从同一份逆向结论里读出来的**真实缺陷**（触摸区、OOM、隐式尺寸契约）与令牌散落。原则与第一批一致：对外行为保持一致。

### 5.1 A1 大图整图解码（`ui/MdRenderer.kt`）

**现象**：图片块的 `produceState` 里直接 `BitmapFactory.decodeStream`，无 `inSampleSize`。一张 4000×3000 的手机照片按 ARGB_8888 入堆约 **48 MB**，滚到该块即可能 OOM。

**改法**：
1. 新增 `data/ImageSampling.kt` —— 纯函数，无 Android 依赖：`DEFAULT_MAX_PIXELS = 4_000_000`、`MAX_SAMPLE = 64`、`sampleSizeFor(width, height, targetWidth, maxPixels)`、`pixelsAfter(width, height, sample)`。
2. `MdRenderer` 改为两遍解码：先 `inJustDecodeBounds` 取 `outWidth/outHeight`，算出 `inSampleSize` 后再 `decode`。
3. 目标宽度取**屏幕宽度**（`LocalConfiguration.screenWidthDp` → `LocalDensity` 换算成像素）并作为 `produceState` 的 key，屏幕旋转会重新解码到新尺寸。

**影响**：小图（小于屏幕宽）`sampleSizeFor` 返回 1，解码结果与原实现逐像素相同；大图上限 400 万像素，视觉上在 1x 屏已无差别。新增 7 条用例覆盖：不放大、2 的幂对齐、像素上限、targetWidth 为 0/负数、MAX_SAMPLE 夹取、`pixelsAfter` 与实际解码面积一致。

### 5.2 A2 底栏高度是三处隐式契约（`ui/ReaderChrome.kt` + `ui/ReaderScreen.kt`）

**现象**：`52.dp` 出现在三个地方 —— 底栏自身高度、进度线的 `padding(bottom = 52.dp + inset)`、字号浮层的同类 padding；正文底部留白则是另一处独立的 `96.dp`。改底栏高度必漏改。

**改法**：`ReaderChrome.kt` 导出 `internal val BottomBarHeight = 52.dp` 与 `internal val ReaderBottomContentInset = 96.dp`；底栏外层 `Column` 用 `height(BottomBarHeight + bottomPadding)`；`ReaderScreen` 的 `contentPadding.bottom` 与两处浮层 padding 全部引用这两个常量。

**影响**：数值逐字相同，界面像素不变；后续调高度只改一处。

### 5.3 A3 触摸区小于项目自定规范（3 处）

项目自己在 `Design.kt` 定了 `MinTouchTarget = 48.dp`，但三处关闭按钮远小于它：

| 位置 | 原命中区 | 现命中区 | 无障碍 |
|---|---|---|---|
| `ui/DocTabs.kt` 标签关闭 × | ≈20×16 dp | 48×30 dp（受标签条 30dp 高度约束，不再加高以免撑高标签条） | 新增 `contentDescription = "关闭 " + label` |
| `ui/EmptyView.kt` 最近文件 × | ≈26×24 dp | 48×48 dp | 新增 "从最近打开中移除" |
| `ui/ReaderChrome.kt` 字号浮层关闭 | 32×32 dp | 48×48 dp（同排 M3 Slider 本已 48dp 高，面板总高不变） | 新增 "关闭字号面板" |

**影响**：**这是本批唯一有意扩大可视/可点范围的地方** —— 三处只放宽命中矩形，图标尺寸、位置、颜色与点击后的行为逐字未变；标签条高度、字号浮层高度均保持不变。

### 5.4 A4 空态页不可滚动（`ui/EmptyView.kt`）

**现象**：`recent.forEach` 堆进 `Column`，横屏或分屏下"标题 + 按钮 + 最多 8 条最近文件"约 500dp，超出部分被裁切且无法滚动到。

**改法**：外层 `Column` 加 `verticalScroll(rememberScrollState())`。

**影响**：竖屏正常尺寸下内容本来就不溢出，滚动条不出现，视觉一致；只有原来会被裁切的情形现在能看到全部。

### 5.5 B5 设计令牌写了没被用（`ui/Design.kt` 及调用点）

**现象**：`ReaderGutter = 20.dp` 定义了却仍在两处硬写 `20.dp`；`Sp`/`Type` 部分未收口；圆角 `Radius.s` 与 `Shapes.chip` 两处定义同一件事；透明度散落裸浮点 `0.35/0.6/0.75`（同一种淡灰在不同文件写成 0.3/0.35/0.4，暗色主题下尤其容易花）。

**改法**：新增 `object Alpha { faint = 0.35f; soft = 0.6f; progress = 0.75f }`，替换 `ReaderChrome` 的 `BarIcon` dimmed 分支、分隔线、`ReaderScreen` 进度条填充；`ReaderScreen` 的 `start/end` 改用 `ReaderGutter`；`DocTabs` 的 `RoundedCornerShape(Radius.s)` 改用 `Shapes.chip`；`MdRenderer` 图片占位块的 `13.sp`/`12.dp` 改用 `Type.body`/`Sp.m`。

**影响**：全部是等值替换。唯一保留原值的是图片占位块 `RoundedCornerShape(8.dp)` —— 8dp 不在现有三档圆角里，换任何一档都是视觉变更，故不动。

### 5.6 B6 行内样式每次重组重建（`ui/MdRenderer.kt`）

**现象**：`buildInline` 在 `InlineText` 组合内直接调用，每次重组都重建 `AnnotatedString`（含链接/代码片段的 SpanStyle 分配），滚动时每秒数十次。

**改法**：拆两层 —— `@Composable fun buildInline(inlines)` 用 `remember(inlines, linkColor, codeBg, codeColor)` 缓存；原实现保留为 `internal fun buildInline(inlines, linkColor, codeBg, codeColor): AnnotatedString` 供测试与缓存体调用。

**影响**：输出字符串逐字相同；参数三元组不变时复用同一实例。

### 5.7 C 组零散收口

| 位置 | 改动 | 为什么 |
|---|---|---|
| `ui/ReaderScreen.kt` | 安全区只取一次 `WindowInsets.safeDrawing.asPaddingValues()`，`bottomInsets` 复用 | 原来同一函数里连调两次，取值必然相同，属重复计算 |
| `ui/ReaderRoot.kt` | `currentIndex` 改为 `remember(activeId) { mutableStateOf(sessions.firstOrNull { it.id == activeId }?.visibleBlock ?: 0) }` | 原为 `mutableStateOf(0)`，切换文档时目录高亮会先闪一帧顶部。取值表达式与 `LaunchedEffect(activeId)` 内完全一致，只是把"先错一帧再改对"变成"一开始就对" |
| `ui/DocTabs.kt` | `30.dp`→`TabChipHeight`、`160.dp`→`TabChipMaxWidth`；`padding(start = Sp.s, end = 2.dp)` → `padding(start = Sp.s)` | 两个魔数各出现两次；尾部 2dp 已被扩大后的关闭按钮命中区吸收 |

### 5.8 一处刻意未修（需真机确认，不在本批）

`MdRenderer` 里手写的 `Final` 阶段 tap 判定与后接的 `detectTapGestures(onLongPress)` 互不知情 —— 长按一段含链接的文字，**可能同时加书签并跳转**。判断成立与否取决于 Compose 手势竞技场的实际优先级，静态阅读无法确证。按"可能影响行为的改动先单独标注"的要求，**本批不动**，留作单独复现后修复。

### 5.9 本批验证

```bash
cd /e/code/project/vibe/AllView
JAVA_HOME='D:\jdk17' /d/gradle/gradle-8.7/bin/gradle :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
```

| 项 | 结果 |
|---|---|
| tests / suites / failures | **249 / 34 / 0** |
| 第一批的 242 条 | 全部通过，一条未改未删 |
| `assembleDebug` | BUILD SUCCESSFUL，APK 正常产出 |
| 裸透明度残留复查 | 仅剩 `MathView` 的尺寸比例系数与 `PdfPageSlot` 的 `LinkedHashMap` loadFactor，均非颜色，故不动 |

手动回归仍按 §3.3 的 25 条路径执行；本批额外盯 4 点：① 打开一张手机原图尺寸的图片，滚动不崩、显示清晰；② 标签条关闭 × / 最近文件 × / 字号浮层 × 三处点按手感变宽，图标位置不变；③ 横屏打开空态页可滚动到"最近打开"最后一条；④ 切换标签时目录高亮从第一帧起就在正确位置。

## 六、第三批：界面全面重塑（1.3.0 / 1.3.1）

前两批解决的是"结构"与"缺陷"，界面本身没动过。这一批只改视觉与体验层：
**不新增功能、不改交互路径、不改解析结果、不改存储键、不改 `applicationId`**。

### 6.1 诊断：界面为什么像"上个年代"

逐屏读完全部 45 个 main 源文件后，问题归成 12 类（每条都定位到文件与行号，完整清单见 §6.2 的对照表）：

| # | 现象 | 根因 |
|---|---|---|
| 1 | 同一屏里两种圆角、两套字阶 | `Design.kt` 有 `Sp`/`Radius`/`Type` 令牌，但 `MaterialTheme` 用的是默认 `Shapes`(4/8/12/28dp) 与默认 `Typography`，两套并行 |
| 2 | 裸值密集 | `EmptyView` 一处就写了 28/32/10/22/30/24/12/10/8/6dp + 30/14/15/12/11sp；`TocSheet` 20/8/12/14/2/1/9dp；`DocTitle` 18/44/3/2dp |
| 3 | 触摸区普遍不足 48dp | 标签条 30dp、目录行 36dp、主题 chip 36dp、书签删除键 30dp、最近文件行 40dp |
| 4 | 没有按压/选中反馈 | `BarIcon` 只换 tint；`ThemeChip`/`PaperChip` 只换底色；四个列表行都是裸 `clickable` |
| 5 | 三态（空/加载/错误）不一致 | `LoadingView` 只有转圈无文案；三处面板各写一套空态 Box+Text，内边距 26/24/24dp 不一 |
| 6 | 输入框风格割裂 | 目录与搜索用 M3 默认描边 `OutlinedTextField`，其余界面是填充卡片风格 |
| 7 | 纸感主题露冷蓝 | `withPaper` 只覆盖 `background`/`surface` 系，`primaryContainer`/`secondary`/`tertiary` 在米黄/夜间下仍是蓝色 |
| 8 | 状态栏图标踩空 | 用 `!dark` 判断图标颜色 → "浅色模式 + 夜间纸"时深底配深图标，看不见 |
| 9 | 无障碍几乎为零 | 只有 4 处 `contentDescription`；Slider、主题 chip、目录行、命中行、删除键全缺；无 `stateDescription` |
| 10 | 动效只有三处 alpha | 顶栏、内容、字号浮层；无过渡、无指示器、无按下反馈 |
| 11 | 固定高度遇字体放大必截断 | `TabChipHeight = 30.dp`、chip 36dp 都是硬约束 |
| 12 | 没有主列表页 | 未打开文档时直接是空态页（品牌 + 按钮 + 最近 8 条），没有"首页"这一层 |

### 6.2 设计系统（把令牌变成唯一来源）

- `ui/Design.kt` 补齐：`Radius.l`(28dp)、`Type.heading`(20sp)、`Elevation`(flat/card/sheet/overlay)、
  `Motion`(fast 120 / base 180 / slow 260)、`Hairline`(1dp)、`SheetListMaxHeight`(420dp)，
  透明度收成 9 档语义令牌（`faint` / `disabled` / `selected` / `soft` / `decorative` / `hairline` / `progress` / `overlay` / `scrim`）
- `ui/Theme.kt` 新增 `AppTypography`（12 档，含行高与字距）与 `AppShapes`，`AppTheme` 三个参数一起给 `MaterialTheme` ——
  M3 的 `Slider`/`DropdownMenu`/`ModalBottomSheet` 从此与自绘组件同圆角同字阶
- `withPaper` 补齐米黄/夜间两套的 `primary`/`primaryContainer`/`outline` 系；
  状态栏图标改按 `scheme.background.luminance() > 0.5f` 反色
- `ui/Icons.kt` 从 6 个图标扩到 20 个（Back/Chevron/Check/Tune/Bookmark/File/Folder/Sun/Moon/Paper/FontMinus/FontPlus/Trash/Stack/SearchOff…），
  全部 Canvas 直绘 —— 实测引 `painterResource` 会 +70KB、引 `ImageVector` +39KB，与"体积量级不变"的要求冲突
- `ui/Components.kt` 新建 14 个公共件：`SheetDragHandle` / `SheetHeader` / `IconAction`(48dp) /
  `SettingCard` / `SectionLabel` / `HairlineDivider` / `ChoiceChip`(≥48dp + `Role.RadioButton`) /
  `PrimaryAction` / `SecondaryAction` / `EmptyHint` / `MetaBadge` / `TypeBadge` / `SearchField`
- 设计规范落成文档：[docs/design-system.md](design-system.md)（12 章：颜色 / 字阶 / 间距 / 形状 / 层级 / 图标 / 动效 / 组件 / 无障碍 / 逐屏线框 / 自查清单）

### 6.3 逐屏改动

| 屏 | 改了什么 | 为什么 |
|---|---|---|
| 首页（新 `ui/HomeScreen.kt`，删除 `ui/EmptyView.kt`） | 品牌头 + 主操作 + 最近打开卡片列表（类型徽标 + 格式名 + 相对时间 + 48dp 删除键）+ 格式说明；整页可滚动；打开失败改为页内错误横幅（`errorContainer` + 左侧竖条） | 原来只有"品牌 + 按钮 + 裸列表"，横屏/分屏时溢出且无法滚动；错误与主按钮抢位置 |
| 阅读器（`ui/ReaderScreen.kt` + `ui/ReaderChrome.kt`） | 进度线合并为底栏顶部一条 2dp 轨道（带 `tween`）；`ReaderTopBarHeight`/`BottomBarHeight`/`ReaderBottomContentInset` 三个常量收口；页首元信息加格式名与字数；底栏活跃项加 `primaryContainer` 底；字号浮层加 `A-`/`A+` 与当前值；加载态加文案 | 原来 52dp 是三处手写常量、进度线画在内容层、页首只有标题没有文档信息 |
| 标签条（`ui/DocTabs.kt`） | 高度 30→48dp、宽度上限 160→200dp、选中项 `primaryContainer`、`Role.Tab` + `selected` 语义、关闭键 48×48dp、底部发丝线与内容分隔 | 30dp 硬高度在系统字体放大后必然截断；关闭键命中区约 20×16dp |
| 四类面板（目录 / 搜索 / 书签 / 设置） | 统一 `SheetDragHandle` → `SheetHeader`（标题 + 计数 + 关闭）→ 内容；输入框换成填充式 `SearchField`；空态统一 `EmptyHint`；行高统一 ≥48dp；设置页三张重复卡片抽成 `SettingCard` | 四处各写一套标题行与空态，内边距与字号互不相同 |
| 搜索面板 | 命中片段围绕每处命中取窗口 + 关键词淡色高亮 + `§N` 徽标 | 原窗口从块首取 60 字，同段多次命中时几行片段完全一样，无法判断该点哪条（1.3.1 修复） |
| 幻灯片 / PDF 全屏（`ui/SlideDeckView.kt` / `ui/PdfFullscreenOverlay.kt`） | 卡片改 `Shapes.card` + 发丝描边；页码改胶囊；全屏顶部信息条改单行 + 右侧 48dp 关闭键 | 原来页码是裸文字叠在图上，深色版式下会读不出来 |

### 6.4 收尾修复（1.3.1）

| 缺陷 | 位置 | 修法 |
|---|---|---|
| 单文档时章节名与状态栏时间重叠（截图实锤） | `ui/ReaderScreen.kt` / `ui/ReaderChrome.kt` / `ui/ReaderRoot.kt` | 新增 `reserveStatusBar` 参数，按它算 `topInset` 并加到 `contentPadding.top` 与 `TopBar`；多开时标签条已吃过一次状态栏，传 `false` 防双份留白 |
| 首页底部残留 4 个无效图标 | `ui/ReaderScreen.kt` | `hasContent = blocks.isNotEmpty() \|\| slides != null` 门控顶栏、底栏、字号浮层 |
| 搜索片段重复 | `md/SearchIndex.kt` / `ui/SearchSheet.kt` | `SearchHit` 加 `contextOffset`/`contextClippedEnd`，窗口改为围绕命中各取两侧 40 字 |
| Release 编译告警 | `ui/Icons.kt` | 删掉 `Frame` 从未使用的 `tint` 形参 |

### 6.5 本批验证

```bash
cd /e/code/project/vibe/AllView
JAVA_HOME='D:\jdk17' /d/gradle/gradle-8.7/bin/gradle :app:testDebugUnitTest :app:assembleRelease --offline --console=plain
```

| 项 | 结果 |
|---|---|
| tests / suites / failures | **264 / 36 / 0** |
| 前两批的 249 条 | 全部通过，一条未改未删 |
| 新增 15 条 | `DesignTokensTest` 9 条（间距/圆角/字阶单调、透明度范围、触摸目标 48dp、相对时间分档、字数千分位、格式名全覆盖）+ `SearchSnippetTest` 6 条 |
| `assembleRelease` | BUILD SUCCESSFUL，APK **1,384,871 字节**（1.2.1 为 1,398,387，同量级略小） |
| Release 编译告警 | 0 |
| 模拟器逐屏走查 | 首页 / 阅读器 / 目录 / 字号浮层 / 设置 / 搜索 / Excel / PPTX / PDF / PDF 全屏，覆盖浅色、深色、米黄、夜间四套，截图见 `docs/shot-*.png` |

仍有一处按"可能影响行为先标注"的原则保留：`ui/MdRenderer.kt` 里 `SelectionContainer` 与 `detectTapGestures(onLongPress)` 共存，
长按正文会先弹系统复制菜单，`onLongPressBlock` 难以直接触发（模拟器复现确认）。这是既有行为，本批未改。


