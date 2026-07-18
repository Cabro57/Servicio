#!/usr/bin/env bash
# =====================================================================
# jpackage-linux.sh — Servicio için Linux native paket üretimi
# =====================================================================
# Bu script YALNIZCA Linux üzerinde çalıştırılabilir (jpackage cross-compile
# yapamaz — her OS kendi native paketini üretir). Windows'ta yazıldı/test
# edilmedi; bir Linux makinede/CI'da doğrulanması gerekir.
#
# Gereksinimler:
#   - JDK 21 (jpackage dahil)
#   - --type deb  için: dpkg-deb (Debian/Ubuntu paket araçları)
#   - --type rpm  için: rpmbuild
#   (yoksa --type app-image kullanın: ek araç gerekmez, taşınabilir klasör üretir)
#
# Kullanım:
#   ./packaging/jpackage-linux.sh                 # app-image (varsayılan)
#   ./packaging/jpackage-linux.sh deb              # .deb paketi
#   ./packaging/jpackage-linux.sh rpm              # .rpm paketi
#   SKIP_BUILD=1 ./packaging/jpackage-linux.sh     # mvn package'ı atla
# =====================================================================
set -euo pipefail

TYPE="${1:-app-image}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Sürüm: $(grep -oPm1 '(?<=<version>)[^<]+' pom.xml)"

if [ "${SKIP_BUILD:-0}" != "1" ]; then
    echo "==> mvn clean package çalıştırılıyor..."
    mvn -f "$ROOT/pom.xml" clean package
fi

JAR="$ROOT/target/servicio.jar"
[ -f "$JAR" ] || { echo "HATA: target/servicio.jar yok. Önce derleyin."; exit 1; }

RAW_VERSION=$(grep -oPm1 '(?<=<version>)[^<]+' pom.xml)
# jpackage --app-version yalnızca N veya N.N veya N.N.N (tam sayı) kabul eder.
# "2.0-dev" -> "2.0.0"
BASE_VERSION=$(echo "$RAW_VERSION" | grep -oP '^\d+(\.\d+){0,2}' || true)
[ -n "$BASE_VERSION" ] || BASE_VERSION="1.0.0"
MAJ=$(echo "$BASE_VERSION" | cut -d. -f1)
MIN=$(echo "$BASE_VERSION" | cut -d. -f2)
PAT=$(echo "$BASE_VERSION" | cut -d. -f3)
APP_VERSION="${MAJ:-1}.${MIN:-0}.${PAT:-0}"
echo "==> Proje sürümü: $RAW_VERSION  ->  jpackage app-version: $APP_VERSION"

APP_INPUT="$ROOT/packaging/app-input"
rm -rf "$APP_INPUT"
mkdir -p "$APP_INPUT"
cp "$JAR" "$APP_INPUT/servicio.jar"
cp -r "$ROOT/target/libs" "$APP_INPUT/libs"

DIST="$ROOT/packaging/dist"
mkdir -p "$DIST"

ICON="$ROOT/src/main/resources/logo.png"
ICON_ARGS=()
[ -f "$ICON" ] && ICON_ARGS=(--icon "$ICON")

# --linux-shortcut yalnızca gerçek paket tiplerinde (deb/rpm) geçerli,
# app-image'de (kurulum yapılmadığı için) kısayol oluşturulamaz.
SHORTCUT_ARGS=()
[ "$TYPE" != "app-image" ] && SHORTCUT_ARGS=(--linux-shortcut)

echo "==> jpackage çalıştırılıyor (type=$TYPE)..."
jpackage \
    --type "$TYPE" \
    --input "$APP_INPUT" \
    --dest "$DIST" \
    --name Servicio \
    --app-version "$APP_VERSION" \
    --vendor "Cabro" \
    --main-jar servicio.jar \
    --main-class tr.cabro.servicio.Launcher \
    "${SHORTCUT_ARGS[@]}" \
    "${ICON_ARGS[@]}"

echo "OK  Paket üretildi: $DIST"
ls -la "$DIST"
