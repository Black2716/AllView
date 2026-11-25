#!/usr/bin/env bash
# 一键发布：创建 GitHub 仓库 -> push main -> 上传 Release APK
# 用法：
#   export GITHUB_TOKEN=ghp_xxx        # 需要 repo 权限（classic）或 Contents+Administration（fine-grained）
#   export GITHUB_OWNER=Black2716      # 可选，默认取 token 所属账号
#   ./publish.sh
set -euo pipefail

: "${GITHUB_TOKEN:?请先设置 GITHUB_TOKEN 环境变量}"
REPO_NAME="${REPO_NAME:-ModuRead}"
PRIVATE="${PRIVATE:-false}"
API="https://api.github.com"

# 版本号唯一来源：app/build.gradle.kts 的 versionName。
# 不要在这里写死 —— 之前写死 1.0.0，导致 release 说明与实际包不一致。
VERSION="$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
: "${VERSION:?无法从 app/build.gradle.kts 读出 versionName}"
APK="dist/AllView-${VERSION}-release.apk"
TAG="v${VERSION}"
echo "==> 版本: ${VERSION}（来自 app/build.gradle.kts）"

OWNER="${GITHUB_OWNER:-$(curl -sS -H "Authorization: Bearer $GITHUB_TOKEN" "$API/user" | sed -n 's/.*"login": *"\([^"]*\)".*/\1/p' | head -1)}"
echo "==> 账号: $OWNER  仓库: $REPO_NAME  private=$PRIVATE"

# 1. 创建仓库（已存在则跳过）
code=$(curl -sS -o /tmp/gh_repo.json -w '%{http_code}' -X POST "$API/user/repos" \
  -H "Authorization: Bearer $GITHUB_TOKEN" -H 'Accept: application/vnd.github+json' \
  -d "{\"name\":\"$REPO_NAME\",\"private\":$PRIVATE,\"description\":\"AllView · 轻量级多格式阅读器\",\"has_issues\":true,\"has_wiki\":false}")
case "$code" in
  201) echo "==> 仓库已创建" ;;
  422) echo "==> 仓库已存在，继续推送" ;;
  *)   echo "!! 创建仓库失败 HTTP $code"; cat /tmp/gh_repo.json; exit 1 ;;
esac

# 2. 配置远程并推送 main
URL="https://x-access-token:$GITHUB_TOKEN@github.com/$OWNER/$REPO_NAME.git"
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/$OWNER/$REPO_NAME.git"
git -c http.postBuffer=524288000 push "$URL" main:main
git branch --set-upstream-to=origin/main main 2>/dev/null || true
echo "==> 已推送 main"

# 3. 打 tag 并创建 Release，上传 APK（APK 不进版本历史）
if [ -f "$APK" ]; then
  git tag -f "$TAG" >/dev/null 2>&1 || true
  git -c http.postBuffer=524288000 push -f "$URL" "$TAG" >/dev/null 2>&1 || true

  RELEASE_ID=$(curl -sS -X POST "$API/repos/$OWNER/$REPO_NAME/releases" \
    -H "Authorization: Bearer $GITHUB_TOKEN" -H 'Accept: application/vnd.github+json' \
    -d "{\"tag_name\":\"$TAG\",\"name\":\"AllView $VERSION\",\"body\":\"AllView $VERSION。下载 AllView-$VERSION-release.apk 直接安装（minSdk 24）。\"}" \
    | sed -n 's/.*"id": *\([0-9]*\).*/\1/p' | head -1)

  if [ -n "${RELEASE_ID:-}" ]; then
    curl -sS -X POST "https://uploads.github.com/repos/$OWNER/$REPO_NAME/releases/$RELEASE_ID/assets?name=AllView-$VERSION-release.apk" \
      -H "Authorization: Bearer $GITHUB_TOKEN" -H 'Content-Type: application/vnd.android.package-archive' \
      --data-binary @"$APK" -o /tmp/gh_asset.json
    echo "==> APK 已上传到 Release $TAG"
  else
    echo "!! Release 创建失败，跳过 APK 上传"
  fi
else
  echo "!! 未找到 $APK，跳过 Release（可先 ./gradlew :app:assembleRelease）"
fi

echo
echo "仓库: https://github.com/$OWNER/$REPO_NAME"
echo "分支: main"
