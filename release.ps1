<#
=====================================================================
 Servicio — Tek komutlu derleme + yayınlama script'i
=====================================================================
 Ne yapar:
   1. pom.xml'den sürümü okur (v<version> tag adı buradan gelir)
   2. Maven ile derler (mvn clean package) → target/servicio.jar + update-manifest.json
   3. GitHub'a yayınlar:
        - v<version> release'i YOKSA  -> yeni release açar ve JAR'ı yükler
        - v<version> release'i VARSA  -> mevcut JAR asset'ini değiştirir (--clobber)
   4. Güncellenen update-manifest.json'u master'a commit + push eder
      (yeni JAR hash'i istemcilere ulaşsın diye)

 Kullanım:
   .\release.ps1                # derle + yayınla (soru sorar)
   .\release.ps1 -BuildOnly     # sadece derle, yayınlama yok
   .\release.ps1 -NoPush        # yayınla ama manifest'i push etme
   .\release.ps1 -Yes           # onay sormadan devam et
   .\release.ps1 -Notes "..."   # release açılırken açıklama metni

 Gereksinimler: gh CLI (giriş yapılmış), git. Maven yoksa otomatik indirilir.
=====================================================================
#>
param(
    [switch]$BuildOnly,
    [switch]$NoPush,
    [switch]$Yes,
    [string]$Notes = "",
    [string]$JdkHome = ""
)

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
$root = $PSScriptRoot
Set-Location $root

function Info($m)  { Write-Host "==> $m" -ForegroundColor Cyan }
function Ok($m)    { Write-Host "OK  $m"  -ForegroundColor Green }
function Warn($m)  { Write-Host "!!  $m"  -ForegroundColor Yellow }
function Fail($m)  { Write-Host "HATA $m" -ForegroundColor Red; exit 1 }

# ── 1) Sürümü pom.xml'den oku ──────────────────────────────────────────
[xml]$pom = Get-Content "$root\pom.xml" -Encoding UTF8
$version = $pom.project.version
if (-not $version) { Fail "pom.xml içinde <version> bulunamadı." }
$tag = "v$version"
Info "Sürüm: $version   (release tag: $tag)"

# ── 2) Maven'i bul (yoksa indir) ───────────────────────────────────────
function Resolve-Maven {
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    foreach ($env in @($env:MAVEN_HOME, $env:M2_HOME)) {
        if ($env -and (Test-Path "$env\bin\mvn.cmd")) { return "$env\bin\mvn.cmd" }
    }
    $globs = @(
        "C:\apache-maven-*", "C:\Program Files\apache-maven-*",
        "$env:USERPROFILE\apache-maven-*", "$env:USERPROFILE\scoop\apps\maven\current"
    )
    foreach ($g in $globs) {
        $d = Get-ChildItem $g -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($d -and (Test-Path "$($d.FullName)\bin\mvn.cmd")) { return "$($d.FullName)\bin\mvn.cmd" }
    }

    # Yerel önbellek (daha önce bu script indirdiyse)
    $local = Get-ChildItem "$root\build-tools\apache-maven-*" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($local -and (Test-Path "$($local.FullName)\bin\mvn.cmd")) { return "$($local.FullName)\bin\mvn.cmd" }

    # İndir
    $mvnVer = "3.9.9"
    Warn "Maven bulunamadı → taşınabilir Maven $mvnVer indiriliyor (tek seferlik)..."
    $toolsDir = "$root\build-tools"
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zip = "$toolsDir\apache-maven-$mvnVer-bin.zip"
    $url = "https://archive.apache.org/dist/maven/maven-3/$mvnVer/binaries/apache-maven-$mvnVer-bin.zip"
    Invoke-WebRequest -Uri $url -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
    Remove-Item $zip -Force
    $mvnPath = "$toolsDir\apache-maven-$mvnVer\bin\mvn.cmd"
    if (-not (Test-Path $mvnPath)) { Fail "Maven indirildi ama mvn.cmd bulunamadı: $mvnPath" }
    Ok "Maven hazır: $mvnPath"
    return $mvnPath
}

$mvn = Resolve-Maven
Info "Maven: $mvn"

# ── 2b) Derleme için uyumlu JDK seç ────────────────────────────────────
# Proje Java 8 hedefliyor. Lombok 1.18.38 JDK 25 ile çalışmaz (tüm
# @Getter/@Data metotları "cannot find symbol" verir). JDK 8 tercih edilir.
function Resolve-Jdk {
    param([string]$Explicit)
    if ($Explicit) {
        if (Test-Path "$Explicit\bin\javac.exe") { return $Explicit }
        Fail "-JdkHome geçersiz (javac yok): $Explicit"
    }
    $candidates = @(
        "$env:USERPROFILE\.jdks\temurin-1.8.0_492",
        "$env:USERPROFILE\.jdks\temurin-1.8.0_482"
    )
    # .jdks içindeki herhangi bir JDK 8 (temurin/corretto 1.8)
    $candidates += (Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '1\.8\.0' } | Select-Object -Expand FullName)
    # Sonra JDK 21 (Lombok destekli)
    $candidates += (Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'temurin-21' } | Select-Object -Expand FullName)
    foreach ($c in $candidates) {
        if ($c -and (Test-Path "$c\bin\javac.exe")) { return $c }
    }
    Warn "Uyumlu JDK (8 veya 21) bulunamadı; sistem varsayılanı kullanılacak (Lombok hatası olabilir)."
    return $null
}

$jdk = Resolve-Jdk $JdkHome
if ($jdk) {
    $env:JAVA_HOME = $jdk
    Info "JAVA_HOME: $jdk"
}

# ── 3) Derle ───────────────────────────────────────────────────────────
Info "Derleniyor: mvn clean package ..."
& $mvn -f "$root\pom.xml" clean package
if ($LASTEXITCODE -ne 0) { Fail "Maven derlemesi başarısız (exit $LASTEXITCODE)." }

$jar = "$root\target\servicio.jar"
$manifest = "$root\update-manifest.json"
if (-not (Test-Path $jar))      { Fail "Derleme çıktısı yok: $jar" }
if (-not (Test-Path $manifest)) { Fail "Manifest üretilmedi: $manifest" }
Ok "Derleme tamam: servicio.jar + update-manifest.json"

if ($BuildOnly) { Info "-BuildOnly verildi, yayınlama atlandı."; exit 0 }

# ── 4) gh kontrolü ─────────────────────────────────────────────────────
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Fail "gh CLI kurulu değil." }
& gh auth status 1>$null 2>$null
if ($LASTEXITCODE -ne 0) { Fail "gh oturumu yok. Önce: gh auth login" }

# Release var mı?
& gh release view $tag 1>$null 2>$null
$releaseExists = ($LASTEXITCODE -eq 0)

if ($releaseExists) {
    Info "Release '$tag' zaten var → JAR asset'i değiştirilecek (--clobber)."
    $action = "mevcut release güncellenecek"
} else {
    Info "Release '$tag' yok → yeni release açılacak."
    $action = "YENİ release açılacak"
}

if (-not $Yes) {
    $ans = Read-Host "Devam edilsin mi? ($action) [E/h]"
    if ($ans -and $ans -notmatch '^(e|E|y|Y)$') { Warn "İptal edildi."; exit 0 }
}

# ── 5) Yayınla ─────────────────────────────────────────────────────────
if ($releaseExists) {
    & gh release upload $tag $jar --clobber
    if ($LASTEXITCODE -ne 0) { Fail "gh release upload başarısız." }
    Ok "servicio.jar '$tag' release'inde güncellendi."
} else {
    if (-not $Notes) { $Notes = "Servicio $version" }
    & gh release create $tag $jar --title $tag --notes $Notes
    if ($LASTEXITCODE -ne 0) { Fail "gh release create başarısız." }
    Ok "Yeni release '$tag' oluşturuldu ve JAR yüklendi."
}

# ── 6) Manifest'i push et ──────────────────────────────────────────────
if ($NoPush) { Info "-NoPush verildi, manifest push atlandı."; exit 0 }

& git add "$manifest"
& git diff --cached --quiet
if ($LASTEXITCODE -eq 0) {
    Info "update-manifest.json değişmedi, commit gerekmiyor."
} else {
    & git commit -m "release: update-manifest.json guncellendi ($tag)"
    if ($LASTEXITCODE -ne 0) { Fail "git commit başarısız." }
    & git push
    if ($LASTEXITCODE -ne 0) { Fail "git push başarısız." }
    Ok "update-manifest.json master'a push edildi."
}

Ok "Yayınlama tamamlandı: $tag"