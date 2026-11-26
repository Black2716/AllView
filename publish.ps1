# 一键发布（Windows PowerShell 版）
# 用法：
#   $env:GITHUB_TOKEN="ghp_xxx"
#   ./publish.ps1
$ErrorActionPreference = "Stop"
if (-not $env:GITHUB_TOKEN) { throw "请先设置 GITHUB_TOKEN" }
$repo = if ($env:REPO_NAME) { $env:REPO_NAME } else { "AllView" }
$api  = "https://api.github.com"
$h = @{ Authorization = "Bearer $($env:GITHUB_TOKEN)"; Accept = "application/vnd.github+json" }

$owner = (Invoke-RestMethod -Uri "$api/user" -Headers $h).login
"==> 账号: $owner  仓库: $repo"

try {
  Invoke-RestMethod -Uri "$api/user/repos" -Method Post -Headers $h -Body (@{
    name = $repo; private = $false; description = "AllView · 轻量级多格式阅读器"
  } | ConvertTo-Json) | Out-Null
  "==> 仓库已创建"
} catch {
  if ($_.Exception.Response.StatusCode.value__ -eq 422) { "==> 仓库已存在，继续推送" } else { throw }
}

$url = "https://x-access-token:$($env:GITHUB_TOKEN)@github.com/$owner/$repo.git"
git remote remove origin 2>$null
git remote add origin "https://github.com/$owner/$repo.git"
git -c http.postBuffer=524288000 push $url main:main
"==> 已推送 main"

# 版本号唯一来源：app/build.gradle.kts 的 versionName。
# 不要写死 —— 之前写死 1.0.0，导致 Release 说明与实际包不一致。
$VERSION = (Select-String -Path "app/build.gradle.kts" -Pattern 'versionName\s*=\s*"([^"]+)"').Matches.Groups[1].Value
if (-not $VERSION) { throw "无法从 app/build.gradle.kts 读出 versionName" }
$TAGV = "v$VERSION"
Write-Host "==> 版本: $VERSION（来自 app/build.gradle.kts）"

$apk = "dist/AllView-$VERSION-release.apk"
if (Test-Path $apk) {
  git tag -f $TAGV | Out-Null
  git -c http.postBuffer=524288000 push -f $url $TAGV | Out-Null
  $rel = Invoke-RestMethod -Uri "$api/repos/$owner/$repo/releases" -Method Post -Headers $h -Body (@{
    tag_name = $TAGV; name = "AllView $VERSION"; body = "AllView $VERSION。下载 AllView-$VERSION-release.apk 直接安装（minSdk 24）。"
  } | ConvertTo-Json)
  $up = "https://uploads.github.com/repos/$owner/$repo/releases/$($rel.id)/assets?name=AllView-$VERSION-release.apk"
  Invoke-RestMethod -Uri $up -Method Post -Headers @{ Authorization = "Bearer $($env:GITHUB_TOKEN)"; "Content-Type" = "application/vnd.android.package-archive" } -InFile $apk | Out-Null
  "==> APK 已上传到 Release $TAGV"
} else { "!! 未找到 $apk" }

"仓库: https://github.com/$owner/$repo"
"分支: main"
