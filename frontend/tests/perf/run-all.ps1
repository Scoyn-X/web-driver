# 一键运行全部 k6 压测场景（Windows PowerShell）
# 用法：
#   ./run-all.ps1                 # 默认 VUS=20 DURATION=60s
#   $env:VUS=50; $env:DURATION="90s"; ./run-all.ps1
#
# 需先安装 k6（https://grafana.com/docs/k6/latest/set-up/install-k6/）
# 并确保后端(8989) + MySQL + MinIO 已启动。

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
New-Item -ItemType Directory -Force -Path "$here/results" | Out-Null

$scenarios = @(
  "scenarios-file-list",
  "scenarios-file-upload",
  "scenarios-file-download",
  "scenarios-share-create",
  "scenarios-share-detail"
)

foreach ($s in $scenarios) {
  Write-Host "==== 运行 $s ====" -ForegroundColor Cyan
  k6 run --summary-export="$here/results/$s.json" "$here/$s.js"
}

Write-Host "全部场景完成，汇总结果见 results/*.json" -ForegroundColor Green
