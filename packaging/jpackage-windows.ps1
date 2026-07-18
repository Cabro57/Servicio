<#
=====================================================================
 jpackage-windows.ps1 — Servicio için Windows native paket üretimi
=====================================================================
 Ne yapar:
   1. (gerekirse) mvn clean package çalıştırır → target/servicio.jar + target/libs
   2. jpackage için temiz bir girdi klasörü hazırlar (packaging/app-input)
   3. jpackage ile native çıktı üretir:
        -Type app-image (varsayılan) → WiX GEREKMEZ, taşınabilir klasör (Servicio.exe + JVM gömülü)
        -Type exe / msi             → WiX Toolset v3 GEREKİR (bkz. aşağıdaki not)

 WiX Toolset kurulumu (yalnızca -Type exe / msi için gerekli):
   winget install --id WiXToolset.WiXToolset -e
   (Yönetici PowerShell gerektirir; NetFx3 Windows Feature'ı kurar.)

 Kullanım:
   .\packaging\jpackage-windows.ps1                  # app-image üretir (WiX gerekmez)
   .\packaging\jpackage-windows.ps1 -Type exe         # tek dosya .exe kurulum paketi
   .\packaging\jpackage-windows.ps1 -Type msi         # .msi kurulum paketi
   .\packaging\jpackage-windows.ps1 -SkipBuild        # mvn package'ı atla, mevcut target/'ı kullan
   .\packaging\jpackage-windows.ps1 -JdkHome "C:\...\jbr-21.0.10"

 Çıktı: packaging\dist\ altında.
   - app-image  → packaging\dist\Servicio\Servicio.exe (kurulum GEREKTİRMEZ,
                   klasörü olduğu gibi kopyalayıp Servicio.exe'ye çift tıklayarak
                   çalıştırabilirsiniz — JVM içine gömülü, ayrıca Java kurulumu gerekmez)
   - exe        → packaging\dist\Servicio-<versiyon>.exe (GERÇEK KURULUM SİHİRBAZI —
                   çift tıklayınca "İleri/İleri/Kur" adımlarıyla Program Files'a kurar,
                   Başlat Menüsü kısayolu + masaüstü kısayolu oluşturur,
                   "Program Ekle/Kaldır"da görünür ve oradan kaldırılabilir)
   - msi        → packaging\dist\Servicio-<versiyon>.msi (exe ile aynı, kurumsal/
                   toplu dağıtım senaryoları için — msiexec ile sessiz kurulum da yapılabilir)
=====================================================================
#>
param(
    [ValidateSet("app-image", "exe", "msi")]
    [string]$Type = "app-image",
    [switch]$SkipBuild,
    [string]$JdkHome = ""
)

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "OK  $m"  -ForegroundColor Green }
function Fail($m) { Write-Host "HATA $m" -ForegroundColor Red; exit 1 }

# ── 1) JDK 21 (jpackage için) seç ──────────────────────────────────────
function Resolve-Jdk {
    param([string]$Explicit)
    if ($Explicit) {
        if (Test-Path "$Explicit\bin\jpackage.exe") { return $Explicit }
        Fail "-JdkHome geçersiz (jpackage.exe yok): $Explicit"
    }
    $jdksRoot = "$env:USERPROFILE\.jdks"
    $candidates = @("$jdksRoot\jbr-21.0.10")
    $candidates += (Get-ChildItem $jdksRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^jbr-21' } | Sort-Object Name -Descending | Select-Object -Expand FullName)
    $candidates += (Get-ChildItem $jdksRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '21' } | Select-Object -Expand FullName)
    foreach ($c in $candidates) {
        if ($c -and (Test-Path "$c\bin\jpackage.exe")) { return $c }
    }
    Fail "JDK 21 (jpackage.exe ile) bulunamadı. -JdkHome ile belirtin."
}
$jdk = Resolve-Jdk $JdkHome
Info "JDK: $jdk"

# ── 1b) WiX Toolset'i PATH'e ekle (yalnızca -Type exe/msi için gerekli) ─
# winget kurulumu bazen Machine PATH'i güncellemiyor; burada bu process'in
# PATH'ine ekliyoruz (kalıcı sistem değişikliği YAPMIYOR, yalnızca bu script
# çalışırken geçerli).
function Resolve-WixBin {
    $candidates = @(
        "${env:ProgramFiles(x86)}\WiX Toolset v3.14\bin",
        "${env:ProgramFiles(x86)}\WiX Toolset v3.11\bin",
        "$env:ProgramFiles\WiX Toolset v3.14\bin"
    )
    $candidates += (Get-ChildItem "${env:ProgramFiles(x86)}" -Directory -Filter "WiX Toolset*" -ErrorAction SilentlyContinue |
        ForEach-Object { "$($_.FullName)\bin" })
    foreach ($c in $candidates) {
        if ($c -and (Test-Path "$c\candle.exe")) { return $c }
    }
    return $null
}
if ($Type -ne "app-image") {
    $wixBin = Resolve-WixBin
    if ($wixBin) {
        $env:PATH = "$env:PATH;$wixBin"
        Info "WiX Toolset bulundu, PATH'e eklendi: $wixBin"
    } else {
        Info "UYARI: WiX Toolset bulunamadı. -Type $Type başarısız olabilir. Kurulum: winget install --id WiXToolset.WiXToolset -e (yönetici olarak)."
    }
}

# ── 2) Sürüm ve versiyon.pom'dan oku ───────────────────────────────────
[xml]$pom = Get-Content "$root\pom.xml" -Encoding UTF8
$rawVersion = $pom.project.version
if (-not $rawVersion) { Fail "pom.xml içinde <version> bulunamadı." }

# jpackage --app-version yalnızca N veya N.N veya N.N.N (tam sayı) kabul eder.
# "2.0-dev" -> "2.0.0"
if ($rawVersion -match '^(\d+)(\.(\d+))?(\.(\d+))?') {
    $maj = $Matches[1]; $min = if ($Matches[3]) { $Matches[3] } else { "0" }; $pat = if ($Matches[5]) { $Matches[5] } else { "0" }
    $appVersion = "$maj.$min.$pat"
} else {
    $appVersion = "1.0.0"
}
Info "Proje sürümü: $rawVersion  ->  jpackage app-version: $appVersion"

# ── 3) Derleme (gerekirse) ─────────────────────────────────────────────
if (-not $SkipBuild) {
    Info "mvn clean package çalıştırılıyor..."
    $env:JAVA_HOME = $jdk
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    $mvnCmd = if ($mvn) { $mvn.Source } else {
        $local = Get-ChildItem "$root\build-tools\apache-maven-*" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($local -and (Test-Path "$($local.FullName)\bin\mvn.cmd")) { "$($local.FullName)\bin\mvn.cmd" } else { Fail "Maven bulunamadı. Önce release.ps1 -BuildOnly ile Maven'i indirin." }
    }
    & $mvnCmd -f "$root\pom.xml" clean package
    if ($LASTEXITCODE -ne 0) { Fail "Maven derlemesi başarısız." }
} else {
    Info "-SkipBuild verildi, mevcut target/ kullanılacak."
}

$jar = "$root\target\servicio.jar"
if (-not (Test-Path $jar)) { Fail "target\servicio.jar bulunamadı. Önce derleyin (-SkipBuild vermeyin)." }

# ── 4) jpackage girdi klasörünü hazırla (yalnızca jar + libs) ─────────
$appInput = "$root\packaging\app-input"
if (Test-Path $appInput) { Remove-Item $appInput -Recurse -Force }
New-Item -ItemType Directory -Force -Path $appInput | Out-Null
Copy-Item $jar "$appInput\servicio.jar"
Copy-Item "$root\target\libs" "$appInput\libs" -Recurse

# ── 5) jpackage çalıştır ────────────────────────────────────────────────
$dist = "$root\packaging\dist"
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$icon = "$root\src\main\resources\logo.ico"
$jpackage = "$jdk\bin\jpackage.exe"

$jpArgs = @(
    "--type", $Type,
    "--input", $appInput,
    "--dest", $dist,
    "--name", "Servicio",
    "--app-version", $appVersion,
    "--vendor", "Cabro",
    "--main-jar", "servicio.jar",
    "--main-class", "tr.cabro.servicio.Launcher"
)
if (Test-Path $icon) { $jpArgs += @("--icon", $icon) }
if ($Type -ne "app-image") {
    $jpArgs += @("--win-menu", "--win-shortcut")
}

Info "jpackage çalıştırılıyor (type=$Type)..."
& $jpackage @jpArgs
if ($LASTEXITCODE -ne 0) {
    if ($Type -ne "app-image") {
        Fail "jpackage başarısız. -Type exe/msi WiX Toolset v3 gerektirir: winget install --id WiXToolset.WiXToolset -e (yönetici olarak)."
    }
    Fail "jpackage başarısız (exit $LASTEXITCODE)."
}

Ok "Paket üretildi: $dist"
Get-ChildItem $dist | ForEach-Object { Write-Host "  $($_.Name)" }
