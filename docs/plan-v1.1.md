# 墨读 ModuRead 功能扩充方案（v1.1 规划）

> **实施状态（2026-11-21）**：档位一全部 5 项 + 档位二的搜索、代码高亮已完成并实测；
> 单测 17 → **51**，release 1.03 → **1.18 MB**，**0 新增第三方依赖**。
> 未做：书签摘录、脚注（需要新依赖）。
>
> 基线：`main` @ 72af948，release APK **1.03 MB**（1,078,219 字节），单测 **17/17**（8 公式 + 9 解析），
> 源码 14 个 Kotlin 文件 / 2,349 行，依赖仅 Compose BOM + 4 个 androidx + commonmark(2 个 jar) + junit。

---

## 一、现状盘点

### 1.1 已经有的

| 能力 | 实现位置 | 完成度 |
|---|---|---|
| SAF 打开本地 .md，UTF-8 读取 | `DocRepository.kt`（42 行） | 完整 |
| Markdown 解析（9 类块） | `MdParser.kt`（277 行） | 标题/段落/引用/两类列表/代码块/GFM 表格/图片/分隔线 |
| LaTeX 公式（块级 + 行内） | `Latex.kt`（461 行）+ `MathView.kt`（274 行） | 分式/上下标/大运算符/定界符/根号/重音/箭头 |
| 渲染 | `MdRenderer.kt`（460 行） | 9 个 `is MdBlock.*` 分支，全部原生 Compose |
| 字号 13–26sp、浅/深/跟随系统 | `ReaderViewModel.kt` + `Theme.kt` | 即时生效 |
| 本地图片（相对路径/`file://`/`content://`） | `MdImageResolver.kt`（104 行） | 含 MediaStore 兜底 |
| 续读上次文件 | `ReaderViewModel.kt:41` | 只记住**最后一个文件** |
| 两本操作手册 | `docs/manual-*.md` | Git Bash / 侧边栏 Git |

### 1.2 明显缺的 —— 「太单一」具体缺在五类

下面每一条都有仓库内实证，不是猜的。

**A. 无法在长文里定位（最痛）**

`sample/sample.md` 有 **1,299 行、115 个标题**，而 `ReaderScreen.kt` 只有一条 `LazyColumn` + 上下滑。没有目录、没有搜索、没有跳转。
更具体的证据：书稿开头第 11–21 行是**目录，11 个 `](#锚点)` 链接**——现在点它们**没有任何反应**（我在 `MdRenderer.kt` 里明确写了 `if (!annotation.item.startsWith("#"))` 才开浏览器，锚点被静默吞掉）。

**B. 阅读状态不保存**

`ReaderViewModel` 的 SharedPreferences 只有三个键：`font_size`、`theme_mode`、`last_uri`。
**滚动位置没有存**——读到第 8 章，切出去看个消息再回来，回到第一页。也没有书签。

**C. 打开文件的路只有一条**

`AndroidManifest.xml` 只有 `MAIN`/`LAUNCHER`，**没有 `VIEW` intent-filter**。所以：

- 文件管理器里长按 .md →「打开方式」里没有墨读
- 微信/QQ 收到 .md 存到下载目录后，必须手动进 App 再用 SAF 翻目录

且每次切文件都要重新走一遍 SAF；没有「最近文件」。

**D. 内容表达有几处硬伤**

| 现象 | 证据 |
|---|---|
| **任务列表不复渲染** | 书稿 1109–1113 行有 5 个 `- [ ]`，现在原样显示成 `- [ ] xxx` 文本 |
| **代码块无高亮** | `CodeView` 只有等宽字体 + 语言标签，正文单色。技术书里代码占比很高 |
| **脚注不认** | 未引入 commonmark-ext-footnotes，`[^1]` 会当普通文字 |
| 表格 >4 列横向滚动 | 已有，但长链路目录仍靠手滑 |

**E. 读完之后带不走**

没有选中即复制整段、没有「分享这一段」、没有笔记/摘录落盘。你提到「整理自己的笔记」，这条直接相关。

---

## 二、功能清单（三档）

### 档位一 · 低成本高收益

判据：≤ 150 行新代码、不新增依赖、不改 `MdBlock` 密封类结构（或只做加法）、单文件可独立验证。

#### 1. 阅读进度记忆（含滚动位置）

| 项 | 内容 |
|---|---|
| 解决什么 | B：切出去回来丢掉阅读位置；重装/重启后回到第一页 |
| 实现要点 | `ReaderPrefs` 增加 `lastBlockIndex`、`lastOffset`；`ReaderViewModel` 在 `onStop`/节流 1s 时写 SharedPreferences（键 `progress:<uri>`）；`LaunchedEffect(doc.docUri)` 在解析完成后 `listState.scrollToItem(saved)`。**只存整数键，不建数据库** |
| 涉及模块 | `ReaderState.kt`、`ReaderViewModel.kt`、`ReaderScreen.kt` |
| 工作量 | ≈ 60 行 / 半天 |
| 风险 | 低。SharedPreferences 无 IO 线程问题（`apply()` 异步）；键用 uri 的 `hashCode` 避免超长 key |

#### 2. 最近文件列表

| 项 | 内容 |
|---|---|
| 解决什么 | C 的一半：每次都要重新 SAF 翻目录 |
| 实现要点 | 用 `contentResolver.takePersistableUriPermission` 已有的授权，把最近 8 个 `(uri, 显示名, 最后打开时间)` 存成 SharedPreferences 里的一段 JSON（手写序列化，不引 Gson）；空态页列出，点击直接 `vm.open(uri)` |
| 涉及模块 | `ReaderViewModel.kt`（存储）、`ReaderScreen.kt` 的 `EmptyView`（列表 UI），新文件 `data/RecentFiles.kt` |
| 工作量 | ≈ 90 行 / 半天 |
| 风险 | 低。注意持久化授权上限（系统限制约 128 个），只留 8 个不触顶 |

#### 3. 目录（TOC）抽屉 + 标题跳转 + 锚点链接

| 项 | 内容 |
|---|---|
| 解决什么 | A 的核心：115 个标题只能靠滑 |
| 实现要点 | ① 从 AST 抽 `List<TocEntry(level, text, blockIndex)>`（已有 `MdBlock.Heading`，直接遍历）；② `ModalBottomSheet`（material3 自带，无新依赖）按 level 缩进；③ 点条目 → `listState.animateScrollToItem(itemIndex)`；④ 实现 GitHub 风格 slug（小写、空格转 `-`、去标点、**保留中文**），建 `slug → blockIndex` 映射，让书稿里那 11 个 `](#...)` 真正能跳 |
| 涉及模块 | `md/MdToc.kt`（新）、`ui/TocSheet.kt`（新）、`MdRenderer.kt`（锚点点击回调）、`ReaderScreen.kt` |
| 工作量 | ≈ 150 行 / 1 天 |
| 风险 | **中等，有一个真实坑**：现在是 `body.chunked(40)` 喂给 `itemsIndexed`，**块索引 ≠ 列表项索引**。要么改成一块一项（需要实测 1,299 行文档的滚动性能），要么保留分块并预先算好 `blockIndex → chunkIndex`。建议先做前者并实测，这是后面搜索/书签的共同前置 |

#### 4. 打开 .md 的 intent-filter

| 项 | 内容 |
|---|---|
| 解决什么 | C 的另一半：从文件管理器/其他 App 直接「用墨读打开」 |
| 实现要点 | Manifest 加 `VIEW` + `android:mimeType="text/markdown"`、`text/x-markdown`、`text/plain` + `pathPattern=".*\\.md"`；`MainActivity` 读 `intent.data` 转 `vm.open()`；补 `onNewIntent` 处理已有实例 |
| 涉及模块 | `AndroidManifest.xml`、`MainActivity.kt` |
| 工作量 | ≈ 35 行 / 2 小时 |
| 风险 | 低。`text/plain` 会让它出现在所有 txt 的打开方式里，介意就只留 markdown 两个 |

#### 5. 任务列表渲染（只读）

| 项 | 内容 |
|---|---|
| 解决什么 | D：书稿 5 个 `- [ ]` 现在显示成字面量 |
| 实现要点 | `MdBlock.ListBlock` 的 item 里检测首段是否以 `[ ]`/`[x]` 开头，剥掉后设 `checked: Boolean?`；渲染时把 `•` 换成 `☐`/`☑`（用 Compose `Icon` 画方框更好看）。**先只读**，不做点击写回 |
| 涉及模块 | `MdModel.kt`（加字段）、`MdParser.kt`、`MdRenderer.kt` |
| 工作量 | ≈ 70 行 / 半天 |
| 风险 | 中低。**必须同步更新 3 处穷尽 `when`**（`MdRenderer.kt:263`、`:417`，`MdParser.kt:266`），否则编译失败——好在编译器会强制，漏不掉。点击勾选要写回文件（SAF 写权限），属范围扩张，本档不做 |

**档位一小计**：≈ 405 行，**+15~25 KB dex**（R8 后），0 新依赖，0 新权限。

---

### 档位二 · 中等投入

#### 6. 全文搜索（定位 + 高亮 + 上/下一处）

| 项 | 内容 |
|---|---|
| 解决什么 | A：知道关键词但不知道在哪一章 |
| 实现要点 | 解析完成后把每个块 `plainText()` 拼成大字符串 + 偏移表（块索引 ↔ 字符区间）；`indexOf` 扫描得命中列表；结果列表点击跳转；命中块渲染时用 `SpanStyle(background)` 高亮 |
| 涉及模块 | `md/SearchIndex.kt`（新）、`ui/SearchSheet.kt`（新）、`MdRenderer.kt`（高亮参数） |
| 工作量 | ≈ 220 行 / 1.5 天 |
| 风险 | 中。① 依赖第 3 项的块索引扁平化；② 高亮参数会贯穿 `MdBlockView` 签名，注意别破坏现有 9 个分支；③ 大文档索引内存（1,299 行约 60 KB 文本，可接受） |

#### 7. 代码块语法高亮（手写 tokenizer）

| 项 | 内容 |
|---|---|
| 解决什么 | D：技术书代码是主要信息载体，单色很费眼 |
| 实现要点 | 不做通用高亮引擎。按 `CodeBlock.language` 分派到 kotlin/python/bash/json/其它 五个手写 tokenizer（关键词表 + 字符串/注释/数字状态机），产出 `List<Span>` 后拼 `AnnotatedString`；未知语言原样输出 |
| 涉及模块 | `md/CodeHighlighter.kt`（新，约 150 行含关键词表）、`MdRenderer.kt` 的 `CodeView` |
| 工作量 | ≈ 300 行 / 2 天 |
| 风险 | **中高，最容易失控的一项**。关键词表是纯数据，会撑 dex；建议每个语言 ≤ 60 个关键词、只做 4 种语言；深浅色各一套配色放在 `Theme.kt` |

#### 8. 书签 / 段落摘录

| 项 | 内容 |
|---|---|
| 解决什么 | E：「整理自己的笔记」的最低配版本 |
| 实现要点 | 长按段落 → 弹「加书签 / 复制本段 / 分享」；书签存 `(uri, blockIndex, 前 30 字摘要, 时间)` 到 SharedPreferences JSON；侧栏列出，点击跳转 |
| 涉及模块 | `data/Bookmarks.kt`（新）、`MdRenderer.kt`（`combinedClickable`）、`ui/BookmarkSheet.kt`（新） |
| 工作量 | ≈ 200 行 / 1.5 天 |
| 风险 | 中。书签锚点靠 `blockIndex`，**文档改版后会错位**——存摘要做二次校验，找不到就提示「原文档已变更」 |

#### 9. 脚注支持

| 项 | 内容 |
|---|---|
| 解决什么 | D：`[^1]` 现在当普通文字 |
| 实现要点 | 引 `commonmark-ext-footnotes`（约 15 KB jar），解析后把脚注块落到文末；正文上标数字可点，点击跳文末 |
| 涉及模块 | `MdParser.kt`（多挂一个 extension）、`MdModel.kt`（`MdBlock.Footnote`） |
| 工作量 | ≈ 100 行 / 半天 |
| 风险 | 低，但**首次引入新依赖**，与「尽量不引第三方」的取向冲突。书稿里 0 个脚注，性价比最低，建议排到最后 |

---

### 档位三 · 长期方向

| # | 功能 | 为什么放长期 |
|---|---|---|
| 10 | **书库**（多书、封面、分类、阅读统计） | 需要持久化层；用 Room 会 +400 KB 以上，建议先手写 JSON 索引。等第 2 项（最近文件）跑一阵再升级 |
| 11 | **笔记导出**（选中 → 摘录 → 导出 .md/.txt，走 SAF `ACTION_CREATE_DOCUMENT`） | 依赖第 8 项的书签数据结构；导出格式要和你的书稿风格对齐 |
| 12 | **EPUB / HTML 支持** | 需新解析栈，体积风险大，和「1 MB 阅读器」的定位冲突；建议永远不做，或单开一个 flavor |
| 13 | **云同步 / 账号** | 与现有定位明确冲突（零网络权限），不做 |
| 14 | **Markdown 编辑** | 与「只读」定位冲突，不做 |

---

## 三、优先级排序与推荐

### 3.1 排序

| 序 | 功能 | 收益 | 成本 | 依赖 | 结论 |
|---|---|---|---|---|---|
| **1** | **进度记忆** | 每天 3 次以上 | 半天 | 无 | **先做** |
| **2** | **目录 + 锚点跳转** | 每天 3 次以上 | 1 天 | 需块索引扁平化 | **先做** |
| 3 | 最近文件 | 每天 1–2 次 | 半天 | 无 | 紧随其后 |
| 4 | intent-filter | 每次从外部打开 | 2 小时 | 无 | 顺手做 |
| 5 | 任务列表 | 少数文档 | 半天 | 无 | 可延后 |
| 6 | 全文搜索 | 每周几次 | 1.5 天 | 依赖 #2 | 第二批 |
| 7 | 代码高亮 | 技术书高频 | 2 天 | 无 | 第二批（注意别失控） |
| 8 | 书签摘录 | 每周几次 | 1.5 天 | 依赖 #2 | 第二批 |
| 9 | 脚注 | 你的书稿用不到 | 半天 | 引入依赖 | 押后 |

### 3.2 为什么先做这两项

1. **它们把「能读」变成「能长期用」**。现在最大的摩擦不是渲染质量——渲染已经够用——而是**每次打开都要重新找文件、重新滚到上次的位置**。这两项直接消掉这两个摩擦。
2. **零依赖、零权限、零体积风险**。合计 ≈ 210 行，估算 +8~12 KB，完全不威胁 1.03 MB 的定位。
3. **它们是后续功能的地基**。搜索要高亮定位、书签要段落锚点，都依赖「块索引扁平化」这一步。先做 #2 就顺手把这个地基打好了。

### 3.3 可执行的第一步

**Step 0（前置，0.5 天）：扁平化块列表**

```
ReaderScreen.kt
  body.chunked(40) + itemsIndexed(chunks)     ← 现在
        ↓
  itemsIndexed(body) { i, block -> ... }      ← 目标
      key = { i, block -> blockKey(i, block) }
```

验收标准：

- [ ] `sample/sample.md`（1,299 行）滚动帧率无可感知下降（用 `adb shell dumpsys gfxinfo` 看 P90 帧时间）
- [ ] 原地 17 个单测全绿
- [ ] 加一个 `blockKey` 的稳定 key，避免滚动时重组抖动

> 如果扁平化后性能真的退步（1,299 行 × 每块一个 item 是有可能的），改为保留分块但**预先算好 `blockIndex → chunkIndex` 映射表**，后续 TOC/搜索照用，不用改方案。

**Step 1（0.5 天）：进度记忆**

```
1. ReaderPrefs 增 lastBlockIndex / lastOffset
2. ReaderViewModel: 解析完成后读 progress:<uriHash> → 暴露给 UI
3. ReaderScreen: LaunchedEffect(doc.docUri) { listState.scrollToItem(saved) }
4. 节流保存：listState.firstVisibleItemIndex 变化后 1s debounce 写入
```

验收标准：

- [ ] 读到中段 → 杀进程 → 重开 → 自动打开同一文档并回到原位置
- [ ] 换一本书 → 各自记住各自的位置（键按 uri 分）
- [ ] 17 个单测全绿（这一步不碰解析器，应该零影响）

**Step 2（1 天）：目录 + 锚点**

```
1. md/MdToc.kt：
     data class TocEntry(level: Int, text: String, blockIndex: Int, slug: String)
     fun buildToc(blocks: List<MdBlock>): List<TocEntry>
     fun githubSlug(text: String): String          // 小写、空格→-、去标点、保留中文
2. ui/TocSheet.kt：ModalBottomSheet + LazyColumn，level 缩进，点 → onJump(blockIndex)
3. MdRenderer：链接点击时若 url 以 # 开头，回调 onAnchor(slug) 而不是 no-op
4. ReaderViewModel 持有 slug → blockIndex 映射
```

验收标准：

- [ ] 打开书稿，抽屉列出 115 个标题，层级正确
- [ ] 点「第 4 章」直接跳到该章开头
- [ ] 点书稿第 11 行那个「走进红外小目标检测」目录项，跳到第 0 章（这是现有 11 个锚点的实测）
- [ ] 新增 `MdTocTest`：slug 生成对中文/标点/重复标题的用例；**原有 17 个仍全绿**

---

## 四、约束核对

| 约束 | 方案如何满足 |
|---|---|
| 尽量不引第三方 | 档位一**零新增依赖**；档位二只有脚注会引 15 KB 扩展，且已标注为可押后 |
| 包体积敏感（1.03 MB） | 档位一估 +8~25 KB；档位二上限约 +40 KB（高亮配色与关键词表是主要增长）；整体仍 < 1.15 MB |
| 17 个单测必须全绿 | 档位一里唯一会碰解析器的是「任务列表」，是**加法式**改动（新增可选字段），不改现有分支语义；其余改动都在 UI 层。每个 Step 的验收都写了「17 个全绿」 |
| Compose 实现 | 全部用 material3 已有组件（`ModalBottomSheet`、`LazyColumn`、`Icon`），无新 UI 库 |
| 公式体系不动 | `Latex.kt` / `MathView.kt` 不参与本次改动；`sample/math.md` 继续作为回归样例 |

---

## 五、明确不做

- Markdown 编辑、账号、云同步、在线内容抓取（延续现有定位）
- 通用语法高亮引擎（只做 4 种语言的手写 tokenizer）
- Room / Hilt / 导航框架 / 图片加载库（体积换不来等值收益）
- EPUB / HTML / PDF 阅读（定位冲突）
