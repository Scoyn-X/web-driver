# =============================================================================
# 网盘系统 完备压力测试 —— 一键运行全部场景与梯度
# =============================================================================
# 用法：
#   ./run-comprehensive.ps1                    # 全部运行（约 45-60 分钟）
#   ./run-comprehensive.ps1 -Quick             # 快速模式（约 15 分钟，缩减并发档位）
#   ./run-comprehensive.ps1 -SkipRamping       # 跳过阶梯加压场景
#
# 前置条件：
#   1. k6 已安装（winget install k6）
#   2. 后端 8989 + MySQL + MinIO 已启动
#   3. 前端不需要运行（k6 直连后端 API）
# =============================================================================

param(
  [switch] $Quick,
  [switch] $SkipRamping
)

$ErrorActionPreference = "Continue"
$k6 = "C:\Program Files\k6\k6.exe"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path

# ----------------------------- 目录准备 -----------------------------
$resultsDir = "$here/results/comprehensive"
New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm"

function Write-Banner($text) {
  Write-Host ("=" * 72) -ForegroundColor Cyan
  Write-Host "  $text" -ForegroundColor Cyan
  Write-Host ("=" * 72) -ForegroundColor Cyan
}

function Invoke-K6($description, $outputName, $scriptFile, $envVars = @{}) {
  Write-Host ">>> $description" -ForegroundColor Yellow
  $outPath = "$resultsDir/$outputName.json"

  $saved = @{}
  foreach ($k in $envVars.Keys) {
    $saved[$k] = [Environment]::GetEnvironmentVariable($k, "Process")
    [Environment]::SetEnvironmentVariable($k, $envVars[$k], "Process")
  }

  try {
    & $k6 run --summary-export="$outPath" "$here/$scriptFile" 2>&1 | ForEach-Object {
      if ($_ -match "running|load|http_req_duration|http_req_failed|checks|http_reqs|vus_max|iterations" -or $_ -match "✓|✗|avg=|p\(95\)" -or $_ -match "█") {
        Write-Host $_ -ForegroundColor DarkGray
      }
    }
    if (Test-Path $outPath) {
      return Get-Content $outPath -Raw | ConvertFrom-Json
    }
  } catch {
    Write-Host "  FAILED: $_" -ForegroundColor Red
    return $null
  } finally {
    foreach ($k in $saved.Keys) {
      [Environment]::SetEnvironmentVariable($k, $saved[$k], "Process")
    }
  }
  return $null
}

function Extract-Metrics($json) {
  if (-not $json) { return $null }
  $m = $json.metrics
  return [PSCustomObject]@{
    ReqCount    = [int]$m.http_reqs.count
    ReqRate     = [math]::Round($m.http_reqs.rate, 1)
    Avg         = [math]::Round($m.http_req_duration.avg, 1)
    Med         = [math]::Round($m.http_req_duration.med, 1)
    P95         = [math]::Round($m.http_req_duration.'p(95)', 1)
    P99         = [math]::Round($m.http_req_duration.'p(99)', 1)
    Max         = [math]::Round($m.http_req_duration.max, 1)
    FailRate    = [math]::Round($m.http_req_failed.value * 100, 2)
    Iterations  = [int]$m.iterations.count
  }
}

# =============================================================================
# Phase 1: 多梯度恒定并发 —— 读接口
# =============================================================================
Write-Banner "Phase 1/6: 多梯度恒定并发 — 文件列表"

$fileListGradients = if ($Quick) { @(10, 50) } else { @(10, 50, 100, 200) }
$fileListResults = @{}
foreach ($v in $fileListGradients) {
  $r = Invoke-K6 "文件列表 VUS=$v DURATION=60s" "gradient-filelist-v$v" "scenarios-file-list.js" @{
    VUS = "$v"; DURATION = "60s"
  }
  $fileListResults["$v"] = Extract-Metrics $r
  Write-Host ""
}

Write-Banner "Phase 2/6: 多梯度恒定并发 — 文件下载"
$dlGradients = if ($Quick) { @(10, 50) } else { @(10, 50, 100) }
$dlResults = @{}
foreach ($v in $dlGradients) {
  $r = Invoke-K6 "文件下载 VUS=$v DURATION=60s" "gradient-download-v$v" "scenarios-file-download.js" @{
    VUS = "$v"; DURATION = "60s"
  }
  $dlResults["$v"] = Extract-Metrics $r
  Write-Host ""
}

Write-Banner "Phase 3/6: 多梯度恒定并发 — 分享详情（公开接口）"
$sdGradients = if ($Quick) { @(10, 50) } else { @(10, 50, 200, 500) }
$sdResults = @{}
foreach ($v in $sdGradients) {
  $r = Invoke-K6 "分享详情 VUS=$v DURATION=60s" "gradient-sharedetail-v$v" "scenarios-share-detail.js" @{
    VUS = "$v"; DURATION = "60s"
  }
  $sdResults["$v"] = Extract-Metrics $r
  Write-Host ""
}

Write-Banner "Phase 4/6: 多梯度恒定并发 — 创建分享"
$scGradients = if ($Quick) { @(10, 50) } else { @(10, 50, 100) }
$scResults = @{}
foreach ($v in $scGradients) {
  $r = Invoke-K6 "创建分享 VUS=$v DURATION=60s" "gradient-sharecreate-v$v" "scenarios-share-create.js" @{
    VUS = "$v"; DURATION = "60s"
  }
  $scResults["$v"] = Extract-Metrics $r
  Write-Host ""
}

# =============================================================================
# Phase 5: 不同文件大小上传对比
# =============================================================================
Write-Banner "Phase 5/6: 不同文件大小上传对比"

$uploadSizes = if ($Quick) { @(1, 100) } else { @(1, 100, 1024, 5120) }  # KB
$uploadSizeResults = @{}
foreach ($sz in $uploadSizes) {
  $label = if ($sz -ge 1024) { "$([math]::Round($sz/1024,1))MB" } else { "${sz}KB" }
  $r = Invoke-K6 "上传 ${label} VUS=20 DURATION=30s" "upload-size-${sz}kb" "scenarios-file-upload-sizes.js" @{
    VUS = "20"; DURATION = "30s"; SIZE_KB = "$sz"
  }
  $uploadSizeResults["$sz"] = Extract-Metrics $r
  Write-Host ""
}

# =============================================================================
# Phase 6: 团队空间接口
# =============================================================================
Write-Banner "Phase 6/6: 团队空间压力测试"

$teamFileListResults = @{}
$teamGradients = if ($Quick) { @(20) } else { @(20, 50) }
foreach ($v in $teamGradients) {
  $r = Invoke-K6 "团队文件列表 VUS=$v DURATION=60s" "team-filelist-v$v" "scenarios-team-file-list.js" @{
    VUS = "$v"; DURATION = "60s"
  }
  $teamFileListResults["$v"] = Extract-Metrics $r
  Write-Host ""
}

$teamUploadResults = @{}
foreach ($v in $teamGradients) {
  $r = Invoke-K6 "团队文件上传 VUS=$v DURATION=60s" "team-upload-v$v" "scenarios-team-file-upload.js" @{
    VUS = "$v"; DURATION = "60s"
  }
  $teamUploadResults["$v"] = Extract-Metrics $r
  Write-Host ""
}

# =============================================================================
# 阶梯加压找饱和点（不跳过的情况下）
# =============================================================================
if (-not $SkipRamping -and -not $Quick) {
  Write-Banner "阶梯加压：文件列表 (MAX_VUS=300)"
  $rampFileList = Invoke-K6 "文件列表 阶梯加压 MAX_VUS=300" "ramp-filelist" "scenarios-file-list-ramping.js" @{
    MAX_VUS = "300"
  }
  Write-Host ""

  Write-Banner "阶梯加压：文件下载 (MAX_VUS=200)"
  $rampDownload = Invoke-K6 "文件下载 阶梯加压 MAX_VUS=200" "ramp-download" "scenarios-file-download-ramping.js" @{
    MAX_VUS = "200"
  }
  Write-Host ""

  Write-Banner "阶梯加压：分享详情 (MAX_VUS=800)"
  $rampShareDetail = Invoke-K6 "分享详情 阶梯加压 MAX_VUS=800" "ramp-sharedetail" "scenarios-share-detail-ramping.js" @{
    MAX_VUS = "800"
  }
  Write-Host ""
}

# =============================================================================
# 混合工作负载
# =============================================================================
Write-Banner "混合工作负载 (VUS=50 DURATION=120s)"
$mixedResult = Invoke-K6 "混合工作负载 VUS=50 DURATION=120s" "mixed-workload" "scenarios-mixed-workload.js" @{
  VUS = "50"; DURATION = "120s"
}
Write-Host ""

# =============================================================================
# 汇总输出
# =============================================================================
Write-Banner "完备压力测试 全部完成"

Write-Host ""
Write-Host "===== 多梯度汇总 =====" -ForegroundColor Green
Write-Host ("{0,10} {1,8} {2,10} {3,10} {4,10} {5,10} {6,10} {7,8}" -f "场景","VUS","吞吐量","avg","P95","P99","max","失败%")
Write-Host ("-" * 76)

foreach ($v in $fileListGradients) {
  $m = $fileListResults["$v"]
  if ($m) {
    Write-Host ("{0,10} {1,8} {2,8}r/s {3,8}ms {4,8}ms {5,8}ms {6,8}ms {7,7}%" -f "文件列表",$v,$m.ReqRate,$m.Avg,$m.P95,$m.P99,$m.Max,$m.FailRate)
  }
}
foreach ($v in $dlGradients) {
  $m = $dlResults["$v"]
  if ($m) {
    Write-Host ("{0,10} {1,8} {2,8}r/s {3,8}ms {4,8}ms {5,8}ms {6,8}ms {7,7}%" -f "文件下载",$v,$m.ReqRate,$m.Avg,$m.P95,$m.P99,$m.Max,$m.FailRate)
  }
}
foreach ($v in $sdGradients) {
  $m = $sdResults["$v"]
  if ($m) {
    Write-Host ("{0,10} {1,8} {2,8}r/s {3,8}ms {4,8}ms {5,8}ms {6,8}ms {7,7}%" -f "分享详情",$v,$m.ReqRate,$m.Avg,$m.P95,$m.P99,$m.Max,$m.FailRate)
  }
}
foreach ($v in $scGradients) {
  $m = $scResults["$v"]
  if ($m) {
    Write-Host ("{0,10} {1,8} {2,8}r/s {3,8}ms {4,8}ms {5,8}ms {6,8}ms {7,7}%" -f "创建分享",$v,$m.ReqRate,$m.Avg,$m.P95,$m.P99,$m.Max,$m.FailRate)
  }
}

Write-Host ""
Write-Host "===== 文件大小对比 =====" -ForegroundColor Green
Write-Host ("{0,12} {1,8} {2,10} {3,10} {4,10} {5,10} {6,8}" -f "文件大小","VUS","avg","P95","P99","max","失败%")
Write-Host ("-" * 68)
foreach ($sz in $uploadSizes) {
  $m = $uploadSizeResults["$sz"]
  if ($m) {
    $label = if ($sz -ge 1024) { "$([math]::Round($sz/1024,1))MB" } else { "${sz}KB" }
    Write-Host ("{0,12} {1,8} {2,8}ms {3,8}ms {4,8}ms {5,8}ms {6,7}%" -f $label,20,$m.Avg,$m.P95,$m.P99,$m.Max,$m.FailRate)
  }
}

Write-Host ""
Write-Host "===== 团队空间 =====" -ForegroundColor Green
Write-Host ("{0,12} {1,8} {2,10} {3,10} {4,10} {5,10} {6,8}" -f "场景","VUS","avg","P95","P99","max","失败%")
Write-Host ("-" * 68)
foreach ($v in $teamGradients) {
  $m = $teamFileListResults["$v"]
  if ($m) {
    Write-Host ("{0,12} {1,8} {2,8}ms {3,8}ms {4,8}ms {5,8}ms {6,7}%" -f "团队列表",$v,$m.Avg,$m.P95,$m.P99,$m.Max,$m.FailRate)
  }
}
foreach ($v in $teamGradients) {
  $m = $teamUploadResults["$v"]
  if ($m) {
    Write-Host ("{0,12} {1,8} {2,8}ms {3,8}ms {4,8}ms {5,8}ms {6,7}%" -f "团队上传",$v,$m.Avg,$m.P95,$m.P99,$m.Max,$m.FailRate)
  }
}

$mm = Extract-Metrics $mixedResult
if ($mm) {
  Write-Host ""
  Write-Host "===== 混合工作负载 =====" -ForegroundColor Green
  Write-Host ("{0,12} {1,8} {2,10} {3,10} {4,10} {5,10} {6,8}" -f "场景","VUS","avg","P95","P99","max","失败%")
  Write-Host ("-" * 68)
  Write-Host ("{0,12} {1,8} {2,8}ms {3,8}ms {4,8}ms {5,8}ms {6,7}%" -f "混合负载",50,$mm.Avg,$mm.P95,$mm.P99,$mm.Max,$mm.FailRate)
}

Write-Host ""
Write-Host "全部结果导出到: $resultsDir" -ForegroundColor Green
