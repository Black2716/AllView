# 手册一：Git Bash 终端操作（AllView）

> 适用仓库：<https://github.com/Black2716/AllView>  
> 项目目录：`<本地项目目录路径>`（下文示例用 `E:/code/project/vibe/AllView`，换成你自己的路径即可）  
> 终端：Windows「Git Bash」（开始菜单搜索 Git Bash；不要用 cmd 或 PowerShell 跑本文的 `export` 语法）

排序原则：**越日常越靠前**。第 1–4 章是每天都会用的，第 5–6 章是发版才用，第 7–8 章是应急；第 9 章是本仓库特有坑，**务必先读一遍**。

---

## 0. 一次性准备（只做一次）

### 0.1 进入项目目录并确认身份

```bash
cd /e/code/project/vibe/AllView      # 注意：Git Bash 里盘符写成 /e/ 而不是 E:/
git status -sb
```

期望输出（干净仓库）：

```
## main...origin/main
```

如果报 `fatal: not a git repository`，说明 `cd` 错了目录，回到含 `settings.gradle.kts` 的那一层。

### 0.2 确认提交者身份（只需一次，全局）

```bash
git config --global user.name  "Black2716"
git config --global user.email "<你的 GitHub 邮箱>"
git config --global init.defaultBranch main
```

验证：`git config --global --list | head`。没配的话提交会失败并提示 `Please tell me who you are`。

### 0.3 配置凭据（二选一，见第 9.3 节）

```bash
gh auth login          # 推荐：浏览器授权，token 由 gh 自己保管
```

**本机没有装 gh**，用 winget 一行装好（已确认源里有 `GitHub.cli`）：

```bash
winget install --id GitHub.cli -e
# 装完关掉当前 Git Bash 重开一个，让 PATH 生效
gh --version
gh auth login          # 选 GitHub.com → HTTPS → Login with a web browser
gh auth status         # 确认已登录，并检查 scope 里有没有 workflow
```

或使用 PAT（个人访问令牌）。本机这份 git 只随包了 `git-credential-wincred`（**没有** `manager`），所以用它：

```bash
git config --global credential.helper wincred
git push               # 首次弹窗：用户名填 Black2716，密码位置粘贴 PAT
```

验证凭据已进 Windows 凭据管理器：

```bash
printf 'protocol=https\nhost=github.com\n\n' | git credential fill | sed 's/password=.*/password=***/'
```

### 0.4 确认远程与分支跟踪

```bash
git remote -v
git status -sb
```

期望：

```
origin  https://github.com/Black2716/AllView.git (fetch)
origin  https://github.com/Black2716/AllView.git (push)
## main...origin/main
```

`## main...origin/main` **没有** `[ahead N]` 也没有 `[behind N]`，表示本地与远端完全一致。

---

## 1. 查看状态与差异（每天第一条命令）

### 1.1 `git status -sb` —— 我改了什么

**用途**：看哪些文件被改、哪些已暂存、领先/落后远端多少。

```bash
git status -sb
```

典型输出：

```
## main...origin/main [ahead 1]
 M app/src/main/java/com/yg/moduread/ui/MdRenderer.kt
M  README.md
?? docs/new-shot.png
```

读法（左侧第一位是暂存区，第二位是工作区）：

| 标记 | 含义 |
|---|---|
| ` M` | 工作区改过、未暂存 |
| `M ` | 已 `git add`、等提交 |
| `MM` | 暂存后又在工作区改了一次 |
| `??` | 未跟踪的新文件（不会被提交，除非 `git add`） |
| ` D` / `D ` | 工作区删除 / 已暂存删除 |
| `A ` | 新增且已暂存 |

### 1.2 `git diff` —— 具体改了哪几行

```bash
git diff                       # 工作区 vs 暂存区（还没 add 的改动）
git diff --staged              # 暂存区 vs 上次提交（马上要提交的改动）
git diff --stat                # 只看每个文件增删行数
git diff README.md             # 只看单个文件
```

在 Git Bash 里翻页用 `↑`/`↓`，退出按 `q`。

### 1.3 `git log` —— 我做过哪些提交

```bash
git log --oneline -5
git log --oneline --graph --decorate -10
git show 47f2587 --stat        # 某次提交改了什么文件
```

---

## 2. 暂存与提交

### 2.1 暂存

```bash
git add README.md                        # 指定文件
git add app/src/main/java/com/yg/moduread/ui/   # 整个目录
git add -A                               # 全部改动（含删除、含新文件）
git add -p                               # 逐块挑选（同一个文件里只想提交一部分时用）
```

`git add -p` 交互键：`y` 要这块、`n` 不要、`s` 拆得更细、`q` 退出。

### 2.2 提交

```bash
git commit -m "修复表格列宽在 4 列时被挤压的问题"
```

期望输出：

```
[main 1a2b3c4] 修复表格列宽在 4 列时被挤压的问题
 1 file changed, 12 insertions(+), 3 deletions(-)
```

多行提交信息（推荐：标题 + 空行 + 条目）：

```bash
git commit -F - <<'EOF'
渲染：表格列宽按内容加权

- 4 列以内按字符数分配宽度，不再横向滚动
- 超过 4 列退回固定列宽 + 横向滚动
- 补一条单测覆盖 4 列表格
EOF
```

> 中文提交信息不需要额外配置，Git 默认 UTF-8。若 `git log` 里中文显示成 `\344\270\255`，执行 `git config --global core.quotepath false`。

### 2.3 提交前的自检（推荐养成的习惯）

```bash
./gradlew :app:testDebugUnitTest      # 单测过没过
git status -sb                        # 还有没有该加的没加
git diff --staged --stat              # 这次到底提交了哪些文件
```

---

## 3. 推送到 GitHub

### 3.1 标准推送

```bash
git push
```

首次（或换了新分支）需要建立跟踪：

```bash
git push -u origin main
```

期望输出：

```
To https://github.com/Black2716/AllView.git
   47f2587..1a2b3c4  main -> main
```

### 3.2 典型报错与处理

**A. 远端有新提交，被拒**

```
! [rejected]        main -> main (fetch first)
error: failed to push some refs to 'https://github.com/Black2716/AllView.git'
```

处理：先同步再推（详见第 4 章）。

```bash
git pull --rebase
git push
```

**B. 连不上 GitHub（本仓库最常见）**

```
fatal: unable to access 'https://github.com/Black2716/AllView.git/':
Failed to connect to github.com:443 after 21051 ms: Could not connect to server
```

这是 DNS 污染，不是你的代码问题。处理见 **9.1 节**。

**C. 要用户名密码 / 提示 terminal prompts disabled**

```
fatal: could not read Username for 'https://github.com': terminal prompts disabled
```

说明没配凭据。处理见 **9.3 节**（`gh auth login` 或 PAT）。

**D. 推 workflow 文件被拒（本仓库特有）**

```
! [remote rejected] main -> main (refusing to allow an OAuth App to create or update
workflow `.github/workflows/android.yml` without `workflow` scope)
```

处理见 **9.2 节**。

---

## 4. 拉取同步

### 4.1 只拉不改历史（推荐）

```bash
git pull --rebase
```

期望：

```
Already up to date.
```

或

```
Successfully rebased and updated refs/heads/main.
```

### 4.2 先看再拉（更稳，推荐在不确定时用）

```bash
git fetch origin
git log --oneline origin/main -3        # 远端最新三条
git log --oneline main..origin/main     # 远端比我多哪些（behind）
git log --oneline origin/main..main     # 我比远端多哪些（ahead）
git pull --rebase
```

### 4.3 设一次 `pull.rebase` 省事

```bash
git config --global pull.rebase true
```

之后 `git pull` 默认就是 rebase，历史更干净。

---

## 5. 打 tag 并推送（发版必用）

### 5.1 创建附注 tag

```bash
git tag -a v1.2.0 -m "AllView 1.2.0：修复表格列宽"
```

> 已发布过的 tag（如 `v1.0.0`、`v1.1.0`、`v1.2.0`）不要动；版本号只在「要发一个可安装包」时递增。

### 5.2 推送 tag

```bash
git push origin v1.2.0
```

期望：

```
To https://github.com/Black2716/AllView.git
 * [new tag]         v1.2.0 -> v1.2.0
```

**注意**：`git push` 不会顺带推 tag，必须显式推。

### 5.3 查看 / 删除 tag

```bash
git tag                          # 本地所有 tag
git ls-remote --tags origin      # 远端所有 tag
git tag -d v1.2.0                          # 删本地
git push origin --delete v1.2.0            # 删远端
```

---

## 6. 构建 APK 并上传到 Release

### 6.1 先构建，再放到 `dist/`

```bash
./gradlew :app:assembleRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/AllView-1.2.0-release.apk
```

`dist/` 与 `*.apk` 都在 `.gitignore` 里，**APK 永远不会进版本历史**，只挂 Release。用 `git status` 确认它没出现在变更列表里。

### 6.2 方式 A：用 `gh`（推荐，最省事）

```bash
# 新版本一次性建 Release + 传资产
gh release create v1.2.0 "dist/AllView-1.2.0-release.apk" \
  --title "AllView 1.2.0" \
  --notes "修复 4 列表格列宽；补充单测。"

# 给已存在的 Release 追加/替换资产
gh release upload v1.2.0 "dist/AllView-1.2.0-release.apk" --clobber
```

升级 gh 后可自动生成更新说明：`gh release create v1.2.0 --generate-notes dist/AllView-1.2.0-release.apk`。

### 6.3 方式 B：用 `curl`（没装 gh 时）

```bash
export GH_TOKEN=ghp_xxxxxxxxxxxxxxxx     # PAT，需要 repo + workflow 权限
VERSION=1.2.0
REPO=Black2716/AllView

# 1) 建 Release（tag 必须已推送）
curl -s -X POST \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -d "{\"tag_name\":\"v$VERSION\",\"name\":\"AllView $VERSION\",\"body\":\"修复 4 列表格列宽\"}" \
  "https://api.github.com/repos/$REPO/releases" \
  -o /tmp/rel.json -w "create %{http_code}\n"

# 2) 取出 release id
RELEASE_ID=$(grep -o '"id": *[0-9]*' /tmp/rel.json | head -1 | grep -o '[0-9]*')
echo "release_id=$RELEASE_ID"

# 3) 上传资产
curl -s -X POST \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"dist/AllView-$VERSION-release.apk" \
  "https://uploads.github.com/repos/$REPO/releases/$RELEASE_ID/assets?name=AllView-$VERSION-release.apk" \
  -o /tmp/asset.json -w "upload %{http_code}\n"

# 4) 取下载直链
grep -o '"browser_download_url":"[^"]*"' /tmp/asset.json

unset GH_TOKEN     # 用完就清掉，别留在会话里
```

### 6.4 上传的典型报错

| 现象 | 原因 | 处理 |
|---|---|---|
| `create 404` | tag 还没推到远端 | 先 `git push origin v1.2.0` |
| `create 422` | 该 tag 已有 Release | 改用 `gh release upload ... --clobber`，或先删 Release |
| `upload 422` | 同名资产已存在 | 加 `--clobber`（gh）或先 `DELETE .../assets/<id>` |
| `create 401` | token 错/过期/没权限 | 重新 `gh auth login` 或换 PAT |
| `upload 201` 但下载 404 | 资产还没转存完 | 等 5–10 秒再访问 |

---

## 7. 撤销：改错了怎么退回去

### 7.1 丢弃工作区改动（没提交的，**不可恢复**）

```bash
git restore README.md                  # 单个文件
git restore .                          # 全部（危险）
git restore --source=HEAD~1 README.md  # 恢复到上一次提交的版本
```

### 7.2 取消暂存（改动还在，只是从暂存区拿出来）

```bash
git restore --staged README.md
```

### 7.3 撤销最近一次提交（改动保留）

```bash
git reset --soft HEAD~1     # 提交没了，改动回到暂存区
git reset HEAD~1            # 提交没了，改动回到工作区（默认 mixed）
git reset --hard HEAD~1     # 提交和改动都没了（危险，先确认 git log）
```

### 7.4 改提交信息 / 补漏文件

```bash
git commit --amend -m "新的提交信息"        # 只改信息
git add forgotten.kt
git commit --amend --no-edit                # 把漏掉的文件并进上一次提交
```

> **已经 push 过的提交不要 amend**，会与远端分叉；确实要改就用 `git push --force-with-lease`（比 `--force` 安全，远端被别人改过会拒绝）。

### 7.5 救命命令：`reflog`

```bash
git reflog -10
git reset --hard <reflog 里的 hash>
```

`reset --hard` 或 rebase 搞砸后，`reflog` 通常能把提交找回来。

---

## 8. 简单冲突处理

### 8.1 什么时候会冲突

`git pull --rebase` 时本地和远端改了**同一文件的同一区域**。输出：

```
CONFLICT (content): Merge conflict in README.md
error: could not apply 1a2b3c4... 修复表格列宽
```

### 8.2 解决步骤

```bash
git status -sb          # 列出 both modified 的文件
# 用编辑器打开冲突文件，找到冲突标记：
#   <<<<<<< HEAD         ← 本地（你刚才 rebase 的提交）
#   =======
#   >>>>>>> 1a2b3c4      ← 对方/上游
```

改完后：

```bash
git add README.md
git rebase --continue        # rebase 场景
# 或 git commit               # merge 场景
```

放弃这次 rebase / merge，回到冲突前：

```bash
git rebase --abort
git merge --abort
```

### 8.3 二进制文件冲突（本仓库 `sample/pic.png`、`docs/*.png`）

Git 没法合并图片，直接选一边：

```bash
git checkout --ours   sample/pic.png     # 保留本地版本
git checkout --theirs sample/pic.png     # 保留远端版本
git add sample/pic.png
```

---

## 9. 本仓库特有注意事项（必读）

### 9.1 `.git/config` 里的三行网络配置 —— 别删它（它本来就不会被提交）

本机 `.git/config` 里有这样一段（是本机绕过 DNS 污染的设置）：

```ini
[http]
	curloptResolve = github.com:443:20.205.243.166
	version = HTTP/1.1
	postBuffer = 524288000
```

| 配置 | 作用 |
|---|---|
| `curloptResolve` | 把 `github.com` 强制解析到固定 IP，绕开被污染的 DNS（`57.155.223.240` 不可达） |
| `http.version=HTTP/1.1` | 该链路上 HTTP/2 会握手失败，降级后稳定 |
| `postBuffer=512M` | 避免大提交报 `RPC failed; HTTP 413` |

**为什么不用管“提交”**：`.git/config` 是本地文件，**不在版本控制里**，`git status` 永远看不到它，也不会被推上去。所以不存在“不小心提交”的问题；要做的是**别删它**。

**这个 IP 失效时怎么办**（症状：又出现第 3.2 节 B 的 21 秒超时）：

```bash
# 1) 看当前固定的 IP 还是不是原来那个
git config --local --get http.curloptResolve

# 2) 逐条试探候选 IP（把能返回 200 的那个记下来）
for ip in 20.205.243.166 140.82.113.3 20.27.177.113; do
  printf '%s -> ' "$ip"
  curl -s -o /dev/null -w '%{http_code}\n' --max-time 10 --resolve github.com:443:$ip https://github.com/
done

# 3) 换成能通的那个
git config --local http.curloptResolve github.com:443:<能通的IP>

# 4) 或者干脆摘掉这行，让系统 DNS 自己解析（DNS 干净时更省事）
git config --local --unset http.curloptResolve
git push
```

> 判据就看 `git push` 能不能过：`Failed to connect ... after 21000 ms` 说明 IP 不通，换一个；`could not read Username` 说明网络已通、缺的是凭据（见 9.3）。

**只对哪个仓库生效**：`--local` 只作用于本仓库；如果你想全局（所有仓库），改用 `git config --global http.curloptResolve ...`。

### 9.2 推 workflow 文件需要 `workflow` scope

仓库里有 `.github/workflows/android.yml`，GitHub 规定 **OAuth App / PAT 必须带 `workflow` 权限**才能推送该路径，否则报：

```
! [remote rejected] main -> main (refusing to allow an OAuth App to create or update
workflow `.github/workflows/android.yml` without `workflow` scope)
```

处理：

| 凭据类型 | 需要的权限 |
|---|---|
| PAT（classic） | 勾 **`repo`** + **`workflow`** |
| PAT（fine-grained） | Repository permissions 里 **Contents: Read and write** + **Workflows: Read and write** |
| `gh auth login` | 选 HTTPS + 浏览器登录，gh 默认带 `workflow`；若之前授权过旧范围，执行 `gh auth refresh -s repo,workflow`，再 `gh auth status` 核对 |

改完权限后重新 `git push` 即可，本地不用任何改动。

### 9.3 凭据只配一次，别写进仓库或 `.git/config`

**推荐**：

```bash
gh auth login                 # 浏览器授权，token 存在 Windows 凭据管理器
gh auth status                # 确认已登录、看 scope 里有没有 workflow
```

**或者**让 git 用 Git 自带的 Windows 凭据助手存 PAT（本机只有 `wincred`，没有 `manager`）：

```bash
git config --global credential.helper wincred
```

**绝对不要**做这几件事：

```bash
# ✗ 把 token 拼进 remote URL（会永久留在 .git/config，还会被日志打印出来）
git remote set-url origin https://ghp_xxx@github.com/Black2716/AllView.git

# ✗ 写进 README / 脚本 / 任何会被提交的文件
# ✗ 写进 gradle.properties、local.properties
```

一次性临时授权（比如脚本里）用环境变量，用完就清：

```bash
export GH_TOKEN=ghp_xxx
# ... curl 上传 ...
unset GH_TOKEN
```

**如果 token 不小心泄露**：立刻去 GitHub → Settings → Developer settings → Personal access tokens 删除该 token；OAuth App 则在 Settings → Applications 里 Revoke。

### 9.4 哪些东西本来就不入库（别惊讶它们不出现）

| 路径 | 原因 |
|---|---|
| `local.properties` | 里面有本机 `sdk.dir` 绝对路径 |
| `build/`、`app/build/`、`.gradle/` | 构建产物 |
| `dist/`、`*.apk` | APK 只走 Release |
| `*.jks`、`*.keystore`、`signing.properties` | 签名材料，永不入库 |
| `.idea/`、`*.iml` | IDE 配置 |

改完代码后在 `git status` 里看不到 `dist/` 或 `app/build/`，是**正常**的，不要 `git add -f` 强行加。

---

## 10. 最常用 10 条速查

| # | 命令 | 干什么 |
|---|---|---|
| 1 | `git status -sb` | 看改动与领先/落后状态 |
| 2 | `git diff` | 看具体改了哪几行 |
| 3 | `git add -A` | 暂存全部改动 |
| 4 | `git commit -m "说明"` | 提交 |
| 5 | `git push` | 推到 GitHub |
| 6 | `git pull --rebase` | 同步远端 |
| 7 | `git log --oneline -5` | 看最近提交 |
| 8 | `git restore <文件>` | 丢弃未提交改动 |
| 9 | `git push origin v1.2.0` | 推 tag（发版） |
| 10 | `gh release upload v1.2.0 <apk> --clobber` | 换 Release 里的 APK |

---

## 11. 「改完代码 → 发布新版本」完整流程

```bash
# ── ① 进目录，确认起点干净 ────────────────────────
cd /e/code/project/vibe/AllView
git status -sb                    # 期望 ## main...origin/main
git pull --rebase                 # 先同步，避免推不上去

# ── ② 改代码 ... （此处是你的工作） ────────────────

# ── ③ 本地验证 ───────────────────────────────────
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease

# ── ④ 提交 ───────────────────────────────────────
git status -sb                    # 确认该加的都加了、dist/ 没混进来
git add -A
git commit -m "渲染：表格列宽按内容加权"

# ── ⑤ 推送 ───────────────────────────────────────
git push

# ── ⑥ 打 tag 并推 tag ────────────────────────────
VERSION=1.2.0
git tag -a v$VERSION -m "AllView $VERSION"
git push origin v$VERSION

# ── ⑦ 准备 APK（不进版本历史） ───────────────────
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/AllView-$VERSION-release.apk

# ── ⑧ 上传 Release ───────────────────────────────
gh release create v$VERSION "dist/AllView-$VERSION-release.apk" \
  --title "AllView $VERSION" --notes "本次改动摘要"

# ── ⑨ 验收 ───────────────────────────────────────
curl -sIL "https://github.com/Black2716/AllView/releases/download/v$VERSION/AllView-$VERSION-release.apk" \
  | grep -iE 'HTTP/|content-length'
# 期望：HTTP/1.1 200 OK 且 content-length 与 dist 里文件大小一致
```

> 只想提交代码、不发版？做到 ⑤ 就结束——**tag 和 Release 只在要发可安装包时才需要**。
