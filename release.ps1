<#
=====================================================================
 Servicio — Tek komutlu derleme + yayınlama script'i
=====================================================================
 Ne yapar:
   1. pom.xml'den sürümü okur (v<version> tag adı buradan gelir)
   2. Maven ile derler (mvn clean package) → target/servicio.jar + update-manifest.json
   3. update-manifest.json'u Ed25519 ile imzalar (update-manifest.json.sig)
   4. GitHub'a yayınlar:
        - v<version> release'i YOKSA  -> yeni release açar; JAR + manifest + imzayı ekler
        - v<version> release'i VARSA  -> bu ekleri değiştirir (--clobber)
      Ardından tüm sürüm notlarını release-notes.json olarak release'e ekler.
      İstemciler (2.14.0+) üçünü de releases/latest/download/ altından okur.
   5. update-manifest.json'u master'a commit + push eder
      (2.14.0 öncesi sürümler hâlâ raw master manifestini okuyor)

 Kullanım:
   .\release.ps1                # derle + yayınla (soru sorar)
   .\release.ps1 -BuildOnly     # sadece derle, yayınlama yok
   .\release.ps1 -NoPush        # yayınla ama manifest'i push etme
   .\release.ps1 -Yes           # onay sormadan devam et
   .\release.ps1 -Notes "..."   # release açılırken açıklama metni
   .\release.ps1 -NotesFile release-notes.md   # açıklama dosyadan (çok satırlı notlar için tercih et)
   .\release.ps1 -Prerelease    # beta: yalnızca "Beta sürümleri al" açık kurulumlara gider
                                # (herkese açmak: gh release edit v<sürüm> --prerelease=false --latest)

 Gereksinimler: gh CLI (giriş yapılmış), git. Maven yoksa otomatik indirilir.
 İmza anahtarı: $env:SERVICIO_SIGNING_KEY ya da %USERPROFILE%\.servicio-release\update-signing.key
   (yalnızca bu makinede durur, repoya girmez; kaybolursa kurulu sürümler yeni
   manifestleri reddeder — yedeğini güvenli bir yerde tut).
=====================================================================
#>
param(
    [switch]$BuildOnly,
    [switch]$NoPush,
    [switch]$Yes,
    [switch]$Prerelease,
    [string]$Notes = "",
    [string]$NotesFile = "",
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

# Native komut (git/gh/mvn) çalıştırıcı. Windows PowerShell 5.1'de
# $ErrorActionPreference = "Stop" altında native bir komutun stderr'e yazdığı HER satır
# NativeCommandError olarak yükselip script'i durdurur — komut başarılı olsa bile. git
# CRLF uyarısını ve `git push` ilerleme satırlarını ("To https://...") stderr'e yazdığı
# için v2.4.0 yayınında script manifest commit adımında kesilmişti. Burada tercih
# geçici olarak Continue yapılır, stderr satırları düz metin olarak basılır; başarı
# yalnızca $LASTEXITCODE ile değerlendirilir (çağıran kontrol eder).
function Invoke-Native {
    # Dizi olarak alınmalı: tek argümanda (`git push`) çoklu atama $rest'i düz string
    # yapar ve @rest onu karakterlerine böler ("git p u s h").
    $command = $args[0]
    [object[]]$rest = @($args | Select-Object -Skip 1)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $command @rest 2>&1 | ForEach-Object {
            if ($_ -is [System.Management.Automation.ErrorRecord]) { Write-Host $_.Exception.Message }
            else { Write-Host $_ }
        }
    } finally {
        $ErrorActionPreference = $previous
    }
}

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
# Proje Java 21 hedefliyor (release 21 → JDK 21+ zorunlu). JetBrains JDK 21
# (JBR) tercih edilir. Lombok 1.18.38 JDK 21 ile uyumlu; JDK 25 ile DEĞİL.
function Resolve-Jdk {
    param([string]$Explicit)
    if ($Explicit) {
        if (Test-Path "$Explicit\bin\javac.exe") { return $Explicit }
        Fail "-JdkHome geçersiz (javac yok): $Explicit"
    }
    $jdksRoot = "$env:USERPROFILE\.jdks"
    # Öncelik: JetBrains Runtime 21 → temurin 21 → herhangi bir JDK 21
    # Tercih edilen kesin sürüm en başta.
    $candidates = @("$jdksRoot\jbr-21.0.10")
    $candidates += (Get-ChildItem $jdksRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^jbr-21' } | Sort-Object Name -Descending | Select-Object -Expand FullName)
    $candidates += (Get-ChildItem $jdksRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'temurin-21' } | Select-Object -Expand FullName)
    $candidates += (Get-ChildItem $jdksRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '-21\.' -or $_.Name -match '21\.0' } | Select-Object -Expand FullName)
    foreach ($c in $candidates) {
        if ($c -and (Test-Path "$c\bin\javac.exe")) { return $c }
    }
    Warn "JDK 21 bulunamadı; sistem varsayılanı kullanılacak (uyumsuz olabilir)."
    return $null
}

$jdk = Resolve-Jdk $JdkHome
if ($jdk) {
    $env:JAVA_HOME = $jdk
    Info "JAVA_HOME: $jdk"
}

# ── 3) Derle ───────────────────────────────────────────────────────────
Info "Derleniyor: mvn clean package ..."
Invoke-Native $mvn -f "$root\pom.xml" clean package
if ($LASTEXITCODE -ne 0) { Fail "Maven derlemesi başarısız (exit $LASTEXITCODE)." }

$jar = "$root\target\servicio.jar"
$manifest = "$root\update-manifest.json"
if (-not (Test-Path $jar))      { Fail "Derleme çıktısı yok: $jar" }
if (-not (Test-Path $manifest)) { Fail "Manifest üretilmedi: $manifest" }
Ok "Derleme tamam: servicio.jar + update-manifest.json"

if ($BuildOnly) { Info "-BuildOnly verildi, yayınlama atlandı."; exit 0 }

# ── 3b) Manifest'i imzala ──────────────────────────────────────────────
# İstemci imzasız ya da imzası tutmayan manifesti reddeder (ManifestSignature).
$signingKey = if ($env:SERVICIO_SIGNING_KEY) { $env:SERVICIO_SIGNING_KEY } else { "$env:USERPROFILE\.servicio-release\update-signing.key" }
if (-not (Test-Path $signingKey)) { Fail "İmza anahtarı yok: $signingKey (yedekten geri yükle; yenisini üretmek kurulu sürümleri kilitler)" }
$signature = "$manifest.sig"
$java = if ($jdk) { "$jdk\bin\java.exe" } else { "java" }
Invoke-Native $java -cp "$root\target\classes" tr.cabro.servicio.build.ManifestSigner sign $signingKey $manifest $signature
if ($LASTEXITCODE -ne 0) { Fail "Manifest imzalanamadı." }
Ok "Manifest imzalandı: update-manifest.json.sig"

# ── 4) gh kontrolü ─────────────────────────────────────────────────────
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Fail "gh CLI kurulu değil." }
& gh auth status 1>$null 2>$null
if ($LASTEXITCODE -ne 0) { Fail "gh oturumu yok. Önce: gh auth login" }

# Release var mı? (gh release yoksa stderr'e yazıp exit 1 döner; $ErrorActionPreference=Stop
# altında bu native-command hatası olarak script'i durdurur — try/catch ile yutuyoruz.)
$releaseExists = $false
try {
    & gh release view $tag 1>$null 2>$null
    $releaseExists = ($LASTEXITCODE -eq 0)
} catch {
    $releaseExists = $false
}

if ($Prerelease) { Info "Beta (pre-release) olarak yayınlanacak; kararlı kanaldaki kurulumlar görmez." }
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
    Invoke-Native gh release upload $tag $jar $manifest $signature --clobber
    if ($LASTEXITCODE -ne 0) { Fail "gh release upload başarısız." }
    Ok "servicio.jar + manifest + imza '$tag' release'inde güncellendi."
    if ($Prerelease) {
        Invoke-Native gh release edit $tag --prerelease
        if ($LASTEXITCODE -ne 0) { Fail "Release pre-release yapılamadı." }
    }
} else {
    if (-not $Notes) { $Notes = "Servicio $version" }
    # Çok satırlı/tırnaklı notlar native argüman olarak geçerken PS 5.1'de bozulabiliyor; dosya güvenli.
    if ($NotesFile) {
        if (-not (Test-Path $NotesFile)) { Fail "Not dosyası yok: $NotesFile" }
        $createArgs = @($tag, $jar, $manifest, $signature, "--title", $tag, "--notes-file", (Resolve-Path $NotesFile).Path)
    } else {
        $createArgs = @($tag, $jar, $manifest, $signature, "--title", $tag, "--notes", $Notes)
    }
    if ($Prerelease) { $createArgs += "--prerelease" }
    Invoke-Native gh release create @createArgs
    if ($LASTEXITCODE -ne 0) { Fail "gh release create başarısız." }
    Ok "Yeni release '$tag' oluşturuldu; JAR + manifest + imza yüklendi."
}

# ── 5b) Sürüm notları dosyası ──────────────────────────────────────────
# Güncelleme penceresi atlanan sürümlerin notlarını bu dosyadan okur; GitHub API'sinin
# girişsiz istek sınırına takılmaz. Release oluşturulduktan sonra üretilir ki yeni sürüm
# de listede olsun. Başarısız olursa istemci API'ye düşer; yayın durdurulmaz.
$notesFile = "$root\target\release-notes.json"
$previous = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$notesJson = (& gh api "repos/{owner}/{repo}/releases?per_page=100" --jq '[.[] | {tag_name, name, published_at, body, draft, prerelease}]' 2>$null) -join "`n"
$notesExit = $LASTEXITCODE
$ErrorActionPreference = $previous
if ($notesExit -eq 0 -and $notesJson) {
    # PS 5.1'in Out-File/Set-Content'i BOM ekler; BOM'suz UTF-8 doğrudan yazılır.
    [System.IO.File]::WriteAllText($notesFile, $notesJson, (New-Object System.Text.UTF8Encoding $false))
    Invoke-Native gh release upload $tag $notesFile --clobber
    if ($LASTEXITCODE -eq 0) { Ok "release-notes.json yüklendi." }
    else { Warn "release-notes.json yüklenemedi; istemciler GitHub API'sine düşer." }
    # İstemciler dosyayı releases/latest/download/ altından okur; latest bir ön sürüm olamaz.
    # Beta yayınında dosya son kararlı sürüme de konur ki beta kanalı yeni ön sürümü görsün.
    $latestTag = $null
    try { $latestTag = (& gh release view --json tagName -q .tagName 2>$null) } catch { }
    if ($latestTag -and $latestTag -ne $tag) {
        Invoke-Native gh release upload $latestTag $notesFile --clobber
        if ($LASTEXITCODE -eq 0) { Ok "release-notes.json son kararlı sürüme ($latestTag) de yüklendi." }
        else { Warn "release-notes.json $latestTag sürümüne yüklenemedi; beta kanalı GitHub API'sine düşer." }
    }
} else {
    Warn "Sürüm notları alınamadı; release-notes.json yüklenmedi."
}

# ── 6) Manifest'i push et (2.14.0 öncesi sürümler için) ──────────────────────────────────────────────
if ($NoPush) { Info "-NoPush verildi, manifest push atlandı."; exit 0 }
# Eski sürümler master manifestini okur ve beta kanalı bilmez; ön sürüm onlara gitmemeli.
if ($Prerelease) { Info "Beta yayını: master manifesti push edilmedi."; Ok "Yayınlama tamamlandı: $tag (beta)"; exit 0 }

Invoke-Native git add "$manifest"
Invoke-Native git diff --cached --quiet
if ($LASTEXITCODE -eq 0) {
    Info "update-manifest.json değişmedi, commit gerekmiyor."
} else {
    Invoke-Native git commit -m "release: update-manifest.json guncellendi ($tag)"
    if ($LASTEXITCODE -ne 0) { Fail "git commit başarısız." }
    Invoke-Native git push
    if ($LASTEXITCODE -ne 0) { Fail "git push başarısız." }
    Ok "update-manifest.json master'a push edildi."
}

Ok "Yayınlama tamamlandı: $tag"