@echo off
REM IntelliJ "4) Package: Linux .deb (WSL Ubuntu)" run configuration bu dosyayi cagirir.
REM Onkosul: WSL uzerinde "Ubuntu" dagitimi kurulu olmali (bkz. CLAUDE.md / packaging-native-installers.md).
REM Windows'ta zaten derlenmis target\servicio.jar'i (platform bagimsiz bytecode) tekrar kullanir.
wsl -d Ubuntu -u root -- bash -c "cd /mnt/c/Projelerim/Servicio && SKIP_BUILD=1 ./packaging/jpackage-linux.sh deb"
