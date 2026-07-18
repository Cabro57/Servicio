# Servicio — Proje Rehberi (CLAUDE.md)

Bu dosya Claude Code'un projeyi hızlıca anlaması için özet rehberdir. Türkçe yazılmıştır çünkü proje ve iletişim dili Türkçedir.

## Proje Nedir?

**Servicio**, teknik servis / tamir atölyesi yönetim masaüstü uygulamasıdır (Java Swing).
Müşteri, cihaz, iş emri (work order), parça/stok, tedarikçi, işçilik, ödeme takibi yapar.
Tek kullanıcılı, yerel çalışan bir masaüstü uygulaması olarak tasarlanmıştır; verisini yerel **SQLite** veritabanında tutar.
Hedef platform: **Windows ve Linux** (macOS kapsam dışı). `ServicioBackend` adında ayrı bir Spring Boot projesi de var — bu repo'yla karıştırılmamalı, farklı bir codebase.

## Teknoloji Yığını

- **Dil / Runtime:** Java 21 (`maven.compiler.release=21`). Derleme **JetBrains JDK 21** (`C:\Users\samet\.jdks\jbr-21.0.10`) ile yapılır.
- **UI:** Swing + FlatLaf (tema), MigLayout, SwingX, jfreechart (grafikler), swing-datetime-picker, modal-dialog (dj-raven).
- **Veritabanı:** SQLite (`sqlite-jdbc`) + JDBI3 (repository katmanı) + Flyway (migration, `flyway-database-nc-sqlite` modülü ile).
- **Bağlantı havuzu:** HikariCP.
- **Config:** okaeri-configs (JSON/Gson) → `.servicio/config.json`.
- **Şifre:** jbcrypt (bcrypt).
- **Build:** Maven (`pom.xml`). `mvn` PATH'de değil — `release.ps1` otomatik indirir/bulur.
- **Native paketleme:** `jpackage` (JDK ile gelir) — `packaging/` altında Windows (`.exe`/`.msi`, WiX Toolset v3 gerekir) ve Linux (`.deb`/`.rpm`) script'leri.
- **Git remote:** `https://github.com/Cabro57/Servicio.git`, ana branch `master`. **gh CLI** kurulu.

## Sert Kurallar

- **Build JDK'sı JBR 21 olmalı, sistem varsayılanı DEĞİL.** Sistem varsayılanı JDK 25 — Lombok 1.18.38 JDK 25 ile ÇALIŞMAZ (`@Getter`/`@Data` "cannot find symbol" hatası verir). Her build öncesi `JAVA_HOME`'u JBR 21'e ayarla; `release.ps1` ve `packaging/*.ps1` bunu otomatik yapıyor.
- **Veri klasörü asla çalışma dizinine göre (`new File(".")`) çözülmez.** `tr.cabro.servicio.Launcher` (pom.xml `mainClass`) uygulamanın giriş noktasıdır, `Servicio.main()` değil — `DataDirResolver` ile veri klasörünü (Windows: `%LOCALAPPDATA%\.servicio`, Linux: `~/.local/share/.servicio`) herhangi bir logger sınıfı yüklenmeden ÖNCE belirler ve `servicio.baseDir` sistem özelliğine yazar. Bu sırayı bozma — `Servicio`/`ApplicationBootstrap` gibi sınıfların statik `Logger` alanları erken tetiklenirse logback kendi varsayılan göreli yolunu kullanıp veri klasörü tespitini yanıltır. Sebep: kurulu (Program Files/`/opt`) bir uygulama, admin olmayan kullanıcıyla oraya yazamaz.
- **Tüm kod yorumları ve log mesajları Türkçe.**
- **`ManifestGenerator` JAR'a dahil edilmez** (pom'da exclude'lu, yalnızca derleme-zamanı aracı).
- Windows konsolunda UTF-8 encoding'e dikkat (antrun'da `-Dfile.encoding=UTF-8` veriliyor).

## Kullanmadıklarımız / Bilinçli Tercihler

- **macOS hedeflenmiyor** — paketleme script'i yok, kod macOS'a özel dallanma içermiyor.
- **SwingX ve jbcrypt güncellenmiyor** — ikisi de bakımı bırakılmış kütüphaneler, güncel/aktif alternatif yok, olduğu gibi kullanılıyor.
- **PasswordUtil'de "açık" yok** — düz-metin şifre fallback'i var ama `UserService.authenticate()` girişte otomatik bcrypt'e migrate ediyor, bu tasarım gereği.
- **Native WiX kurulum sihirbazına özel dialog eklenmiyor** — jpackage'ın UI akışı JDK'nın kapalı iç sınıflarında üretiliyor, güvenilir şekilde genişletilemiyor. Kaldırma sırasında veri silme sorusu istenirse ayrı bir companion script ile yapılmalı, native sihirbaz entegrasyonu değil.
- **Linux paketleme testi bu makinede WSL2 (Ubuntu) üzerinden yapılıyor** — gerçek Linux ortamı yok, gerektiğinde `wsl --install -d Ubuntu` ile kurulur.

## Paket Yapısı (`src/main/java/tr/cabro/servicio/`)

- `Launcher.java` — Gerçek giriş noktası (veri klasörü çözümü, logger'lardan önce).
- `Servicio.java` — Çekirdek yaşam döngüsü (init, run, shutdown). `getInstance()` singleton.
- `application/` — Tüm UI: `forms/`, `panels/`, `component/`, `system/` (MainForm, FormManager), `themes/`, `MainUI.java`, `ApplicationBootstrap.java` (launch akışı, Look&Feel).
- `database/` — `DatabaseManager`, `repository/` (JDBI repo'ları), `mapper/`, `migration/` SQL'leri.
- `model/` — Domain modelleri (Customer, Device, WorkOrder, Part, Supplier, Labor...).
- `service/` — İş mantığı servisleri, `ServiceManager` ile başlatılır.
- `settings/` — `Settings` (okaeri config).
- `updater/` — Otomatik güncelleme sistemi (manifest tabanlı, GitHub Releases).
- `build/` — `ManifestGenerator.java` (derleme-zamanı aracı).
- `util/` — `DataDirResolver`, `PasswordUtil`, `AppLock` vb.
- `reports/`.

## Sürüm / Versiyon

- Tek kaynak: `pom.xml` `<version>`.
- `src/main/resources/version.properties` Maven filtering ile `${project.version}` alır.

## Dağıtım / Yayınlama akışı

1. `pom.xml`'de version güncelle.
2. `mvn clean package` → `target/servicio.jar`, `target/libs/`, kökte `update-manifest.json`.
3. `.\release.ps1` — tek komutla derler, GitHub'da `v<version>` release açar/günceller, JAR'ı yükler, `update-manifest.json`'u `master`'a push eder. Bayraklar: `-BuildOnly`, `-NoPush`, `-Yes`, `-Notes`.
4. Native installer için `packaging/jpackage-windows.ps1` / `packaging/jpackage-linux.sh` — ayrı, `release.ps1` akışının parçası değil.

Detaylı geçmiş/gerekçeler için proje belleğine (`roadmap.md` ve ilgili memory dosyaları) bakılabilir.
