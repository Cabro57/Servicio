<#
=====================================================================
 build-and-package-all.ps1 — Tek komutla: derle + tüm paketleri üret
=====================================================================
 IntelliJ'deki TEK "Build & Package (Windows + Linux)" run configuration'ı
 bu script'i çalıştırır. Sırayla yapar:
   1. mvn clean package (JBR 21 ile) + Windows app-image (WiX gerekmez)
   2. Windows .msi installer (WiX Toolset v3 gerekir) — build'i tekrarlamaz (-SkipBuild)
   3. Windows .exe installer (WiX Toolset v3 gerekir) — build'i tekrarlamaz (-SkipBuild)
   4. Linux .deb (WSL üzerindeki "Ubuntu" dağıtımı gerekir) — best effort,
      WSL/Ubuntu yoksa bu adım atlanır, script BAŞARISIZ SAYILMAZ.

 Bir adım başarısız olursa script durmaz, diğer adımlara devam eder;
 en sonda özet gösterilir ve herhangi bir adım başarısızsa exit code 1 döner.

 Kullanım:
   .\packaging\build-and-package-all.ps1                    # hepsini yap
   .\packaging\build-and-package-all.ps1 -SkipLinux          # yalnızca Windows
   .\packaging\build-and-package-all.ps1 -SkipBuild          # mvn'i atla (mevcut target/ kullan)
   .\packaging\build-and-package-all.ps1 -SkipWindowsInstallers  # yalnızca app-image, .msi/.exe atla
=====================================================================
#>
param(
    [switch]$SkipBuild,
    [switch]$SkipLinux,
    [switch]$SkipWindowsInstallers
)

try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "OK  $m"  -ForegroundColor Green }
function Warn($m) { Write-Host "!!  $m"  -ForegroundColor Yellow }
function Err($m)  { Write-Host "HATA $m" -ForegroundColor Red }

$results = [ordered]@{}

function Run-Step {
    param([string]$Name, [scriptblock]$Action)
    Info "Adım: $Name"
    $global:LASTEXITCODE = 0
    try {
        & $Action
        if ($LASTEXITCODE -ne 0 -and $null -ne $LASTEXITCODE) {
            throw "exit code $LASTEXITCODE"
        }
        $results[$Name] = "OK"
        Ok "$Name tamamlandı."
    } catch {
        $results[$Name] = "BAŞARISIZ: $($_.Exception.Message)"
        Err "$Name başarısız: $($_.Exception.Message)"
    }
}

$buildArg = if ($SkipBuild) { "-SkipBuild" } else { "" }

# ── 1) Derleme + Windows app-image ──────────────────────────────────────
Run-Step "1) mvn clean package + Windows app-image" {
    if ($SkipBuild) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\jpackage-windows.ps1" -Type app-image -SkipBuild
    } else {
        & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\jpackage-windows.ps1" -Type app-image
    }
}

if (-not $SkipWindowsInstallers) {
    # ── 2) Windows .msi ──────────────────────────────────────────────────
    Run-Step "2) Windows .msi installer" {
        & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\jpackage-windows.ps1" -Type msi -SkipBuild
    }

    # ── 3) Windows .exe ──────────────────────────────────────────────────
    Run-Step "3) Windows .exe installer" {
        & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\jpackage-windows.ps1" -Type exe -SkipBuild
    }
} else {
    Info "-SkipWindowsInstallers verildi, .msi/.exe atlandı."
}

# ── 4) Linux .deb (WSL) — best effort ───────────────────────────────────
if (-not $SkipLinux) {
    $wslHasUbuntu = $false
    try {
        $distros = & wsl -l -q 2>$null
        if ($distros -match "Ubuntu") { $wslHasUbuntu = $true }
    } catch {}

    if ($wslHasUbuntu) {
        Run-Step "4) Linux .deb (WSL Ubuntu)" {
            & cmd.exe /c "$PSScriptRoot\ide\package-linux-deb.cmd"
        }
    } else {
        Warn "WSL üzerinde 'Ubuntu' dağıtımı bulunamadı, Linux paketleme atlandı. Kurulum: wsl --install -d Ubuntu"
        $results["4) Linux .deb (WSL Ubuntu)"] = "ATLANDI (WSL Ubuntu yok)"
    }
} else {
    Info "-SkipLinux verildi, Linux paketleme atlandı."
    $results["4) Linux .deb (WSL Ubuntu)"] = "ATLANDI"
}

# ── Özet ─────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "===================== ÖZET =====================" -ForegroundColor Cyan
$hasFailure = $false
foreach ($key in $results.Keys) {
    $status = $results[$key]
    if ($status -like "BAŞARISIZ*") {
        Write-Host "  [X] $key — $status" -ForegroundColor Red
        $hasFailure = $true
    } elseif ($status -like "ATLANDI*") {
        Write-Host "  [-] $key — $status" -ForegroundColor Yellow
    } else {
        Write-Host "  [OK] $key" -ForegroundColor Green
    }
}
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "Çıktılar: $root\packaging\dist\" -ForegroundColor Cyan

if ($hasFailure) { exit 1 } else { exit 0 }
