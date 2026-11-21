# 一键发布（Windows PowerShell 版）
# 用法：
#   $env:GITHUB_TOKEN="ghp_xxx"
#   ./publish.ps1
$ErrorActionPreference = "Stop"
if (-not $env:GITHUB_TOKEN) { throw "请先设置 GITHUB_TOKEN" }
$repo = if ($env:REPO_NAME) { $env:REPO_NAME } else { "ModuRead" }
$api  = "https://api.github.com"
$h = @{ Authorization = "Bearer $($env:GITHUB_TOKEN)"; Accept = "application/vnd.github+json" }

$owner = (Invoke-RestMethod -Uri "$api/user" -Headers $h).login
"==> 账号: $owner  仓库: $repo"

try {
  Invoke-RestMethod -Uri "$api/user/repos" -Method Post -Headers $h -Body (@{
    name = $repo; private = $false; description = "墨读 ModuRead · 轻量级 Android Markdown 阅读器"
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

$apk = "dist/ModuRead-1.0.0-release.apk"
if (Test-Path $apk) {
  git tag -f v1.0.0 | Out-Null
  git -c http.postBuffer=524288000 push -f $url v1.0.0 | Out-Null
  $rel = Invoke-RestMethod -Uri "$api/repos/$owner/$repo/releases" -Method Post -Headers $h -Body (@{
    tag_name = "v1.0.0"; name = "墨读 1.0.0"; body = "首个版本：本地 Markdown 阅读器。下载 APK 直接安装（minSdk 24）。"
  } | ConvertTo-Json)
  $up = "https://uploads.github.com/repos/$owner/$repo/releases/$($rel.id)/assets?name=ModuRead-1.0.0-release.apk"
  Invoke-RestMethod -Uri $up -Method Post -Headers @{ Authorization = "Bearer $($env:GITHUB_TOKEN)"; "Content-Type" = "application/vnd.android.package-archive" } -InFile $apk | Out-Null
  "==> APK 已上传到 Release v1.0.0"
} else { "!! 未找到 $apk" }

"仓库: https://github.com/$owner/$repo"
"分支: main"
