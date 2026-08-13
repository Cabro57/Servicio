<div align="center">

# 🔧 Servicio

**Teknik Servis / Tamir Atölyesi Yönetim Sistemi**

<!--
  TODO: Buraya proje logosu/banner görseli eklenecek.
  Örnek: <img src="docs/assets/banner.png" alt="Servicio banner" width="720">
-->

Müşteriden cihaza, iş emrinden ödemeye — bir teknik servis atölyesinin tüm günlük işleyişini
tek bir masaüstü uygulamasında toplayan, yerelde çalışan ve verinizin sizde kaldığı bir yönetim sistemi.

<!--
  TODO: Sürüm/lisans/build rozetleri buraya eklenecek, örnek:
  ![version](https://img.shields.io/github/v/release/Cabro57/Servicio)
  ![platform](https://img.shields.io/badge/platform-Windows%20%7C%20Linux-blue)
-->

[Özellikler](#-öne-çıkan-özellikler) ·
[Ekran Görüntüleri](#-ekran-görüntüleri) ·
[Kurulum](#-kurulum-son-kullanıcı) ·
[Geliştirici Rehberi](#-geliştirici-kurulumu) ·
[Yol Haritası](#-yol-haritası)

</div>

---

## 📖 İçindekiler

- [Vizyon ve Misyon](#-vizyon-ve-misyon)
- [Proje Hakkında](#-proje-hakkında)
- [Öne Çıkan Özellikler](#-öne-çıkan-özellikler)
- [Ekran Görüntüleri](#-ekran-görüntüleri)
- [Teknoloji Yığını](#-teknoloji-yığını)
- [Mimari ve Proje Yapısı](#-mimari-ve-proje-yapısı)
- [Kurulum (Son Kullanıcı)](#-kurulum-son-kullanıcı)
- [Geliştirici Kurulumu](#-geliştirici-kurulumu)
- [Yapılandırma ve Veri Klasörü](#-yapılandırma-ve-veri-klasörü)
- [Dağıtım / Sürüm Yayınlama](#-dağıtım--sürüm-yayınlama)
- [Yol Haritası](#-yol-haritası)
- [Katkıda Bulunma](#-katkıda-bulunma)
- [Lisans](#-lisans)
- [Teşekkürler](#-teşekkürler)

---

## 🎯 Vizyon ve Misyon

**Vizyonumuz:** Küçük ve orta ölçekli teknik servis atölyelerinin, kurumsal ERP sistemlerinin
karmaşıklığına ve maliyetine katlanmadan; müşteri, cihaz, iş emri ve stok yönetimini tek bir
çatı altında, hızlı ve güvenilir şekilde yürütebildiği referans masaüstü uygulaması olmak.

**Misyonumuz:**
- **Sadelik:** Atölye sahibinin veya teknisyenin bilgisayar uzmanı olmasını gerektirmeyen,
  günlük iş akışına birebir oturan bir arayüz sunmak.
- **Veri sahipliği:** Bulut aboneliği zorunluluğu olmadan, verinin tamamen kullanıcının kendi
  cihazında, yerel bir veritabanında güvenle saklanmasını sağlamak.
- **Sürdürülebilirlik:** Modern, bakımı yapılabilir bir teknoloji temeli (Java 21, aktif olarak
  güncellenen kütüphaneler) üzerinde, uzun vadede geliştirilebilir kalmak.
- **Erişilebilirlik:** Windows ve Linux üzerinde aynı şekilde çalışan, kurulumu tek adımlık
  native paketlerle (`.exe`/`.msi`, `.deb`/`.rpm`) dağıtılan bir uygulama olmak.

Bu doğrultuda Servicio; bir telefon/bilgisayar tamir atölyesinin ihtiyaç duyduğu müşteri kaydı,
cihaz takibi, servis (iş emri) süreci, parça/stok yönetimi, tedarikçi ilişkileri, tahsilat takibi,
belge (PDF) üretimi ve müşteri iletişimi (WhatsApp) gibi tüm temel işlevleri; ek bir sunucuya veya
internet bağlantısına bağımlı olmadan sunar.

---

## 📋 Proje Hakkında

**Servicio**, Java/Swing ile geliştirilmiş, tek kullanıcılı ve yerel çalışan bir masaüstü
uygulamasıdır. Verisini bilgisayarınızdaki bir **SQLite** veritabanında tutar; internet
bağlantısı yalnızca güncelleme kontrolü, WhatsApp Web entegrasyonu ve (varsa) TCMB döviz kuru
çekimi gibi isteğe bağlı özellikler için kullanılır — uygulamanın temel işlevleri çevrimdışı da
çalışır.

Hedef kitlesi; telefon/bilgisayar/elektronik tamiri yapan küçük-orta ölçekli teknik servis
atölyeleri, bayiler ve bağımsız teknisyenlerdir.

> **Not:** `ServicioBackend` adında ayrı bir Spring Boot projesi de mevcuttur — bu, farklı bir
> codebase'dir ve bu repo ile karıştırılmamalıdır.

---

## ✨ Öne Çıkan Özellikler

### 👤 Müşteri Yönetimi
- Bireysel / Kurumsal müşteri tipleri (Kurumsal için firma ismi, vergi no, vergi dairesi alanları).
- Çoklu telefon numarası, adres, e-posta, not alanları.
- "Sorunlu Müşteri" bayrağı ile dikkat edilmesi gereken müşterilerin işaretlenmesi.
- Müşteri detay sayfasında geçmiş servis kayıtları ve toplam harcama özeti.

### 📱 Cihaz Yönetimi
- Cihaz türü / marka sözlükleri (Ayarlar üzerinden yönetilebilir, genişletilebilir).
- Cihaz bazlı geçmiş: servis kayıtları, 2.el alım-satım hareketleri tek ekranda birleşik.
- **Cihaz erişim bilgisi**: ekran kilidi PIN / şifre / desen bilgisi, ayrı şifrelenmiş bir
  tabloda saklanır ve tanımlı bir süre sonra otomatik olarak temizlenir.

### 🛠️ Servis (İş Emri) Süreci
- Uçtan uca servis kaydı: arıza tespiti → teklif/onarım onayı → teslimat.
- Durum takibi (beklemede, onarımda, teslim edildi, iade vb.).
- Kullanılan parça ve işçiliklerin kayda eklenmesi, otomatik tutar hesaplama.
- Servis akışına özel PDF belgeleri: **Arıza Tespit Formu**, **Teklif/Onarım Onayı**,
  **Cihaz Kabul Formu**, **Servis Teslim Formu** — imza alanlı, işletme logosu antetli.
- WhatsApp üzerinden müşteriye şablonlu mesaj gönderimi (durum bildirimi, hatırlatma vb.).

### 📦 Parça / Stok Yönetimi
- Parça kategorileri sözlüğü, uyumlu model filtresi, barkod desteği (üretme + tarama).
- Stok giriş/çıkış takibi, kritik stok uyarısı, envanter değeri raporu.
- **Çoklu döviz desteği**: parça alış fiyatı USD/EUR girilebilir, TCMB günlük kurundan veya
  manuel kurdan TL karşılığı hesaplanır.

### 🤝 Tedarikçi Yönetimi
- Tedarikçi kartları ve tedarik ettiği parçaların listesi.

### 💰 Ödeme / Ön Muhasebe
- Servis bazlı kısmi/tam tahsilat kaydı, kalan bakiye takibi.
- Tahsilat fişi PDF olarak üretilip yazdırılabilir.

### 🔁 2.El Cihaz Alım-Satım Modülü
- Cihaz merkezli tasarım: her cihazın alım ve satım hareketleri birlikte izlenir.
- Alım/Satım/Garanti Belgesi/Cihaz Ekspertiz Raporu gibi 4 ayrı PDF belge türü.

### 🌍 Çok Dilli Arayüz (i18n)
- Ayarlar > Dil ve Bölge üzerinden **arayüz dili** (Türkçe/İngilizce), **tarih-sayı biçimi**
  (bölge) ve **gösterim para birimi** birbirinden bağımsız olarak seçilebilir.
- İlk açılışta işletim sistemi diline göre otomatik belirlenir, değişiklik anında
  (yeniden başlatmadan) uygulanır.

### 🔐 Güvenlik
- PIN ile kilit ekranı, kullanıcı oturumu.
- Hassas veriler (cihaz erişim bilgisi, kullanıcı şifresi) bcrypt/şifreli olarak saklanır.

### 🔄 Otomatik Güncelleme
- Uygulama içinden GitHub Releases üzerinden manifest tabanlı otomatik güncelleme kontrolü,
  indirme ve kurulum.

### 💾 Yedekleme
- Otomatik/manuel veritabanı yedekleme, yedekten geri yükleme.

### 📊 Raporlama / Gösterge Paneli
- Servis kayıtları, parçalar, 2.el stok gibi ekranlarda özet istatistik kartları
  (toplam kayıt, aktif işlemler, ciro vb.).

---

## 🖼️ Ekran Görüntüleri

> 🚧 **Yakında eklenecek.** Bu bölüme uygulamanın gösterge paneli, servis kaydı formu, cihaz
> detay ekranı ve Ayarlar ekranlarından görseller eklenecektir.

<!--
  TODO: Ekran görüntüleri buraya eklenecek. Önerilen format:

  | Gösterge Paneli | Servis Kaydı |
  |---|---|
  | ![dashboard](docs/assets/dashboard.png) | ![work-order](docs/assets/work-order.png) |
-->

---

## 🧰 Teknoloji Yığını

| Katman | Teknoloji |
|---|---|
| Dil / Runtime | Java 21 (derleme: JetBrains JDK 21 — `jbr-21.0.10`) |
| Arayüz (UI) | Swing, [FlatLaf](https://www.formdev.com/flatlaf/) (tema), MigLayout, SwingX, JFreeChart (grafikler) |
| Modal / Bildirim | [raven-modal](https://github.com/DJ-Raven/modal-dialog) (onay/hata/bilgi diyalogları, toast bildirimleri) |
| Veritabanı | SQLite (`sqlite-jdbc`) + JDBI3 (repository katmanı) + Flyway (migration) |
| Bağlantı Havuzu | HikariCP |
| Yapılandırma | Özel JSON tabanlı config store (Gson) → `config.json` |
| Şifreleme | jBCrypt (bcrypt) |
| PDF Üretimi | OpenPDF |
| Loglama | SLF4J + Logback |
| Derleme | Maven (`pom.xml`) |
| Native Paketleme | `jpackage` — Windows (`.exe`/`.msi`, WiX Toolset v3) ve Linux (`.deb`/`.rpm`) |

---

## 🏗️ Mimari ve Proje Yapısı

Uygulama, klasik bir katmanlı masaüstü uygulama mimarisi izler:

```
src/main/java/tr/cabro/servicio/
├── Launcher.java           # Gerçek giriş noktası (veri klasörü çözümü, logger'lardan önce)
├── Servicio.java           # Çekirdek yaşam döngüsü (init, run, shutdown)
├── application/            # Tüm arayüz katmanı
│   ├── forms/               # Ana ekranlar (liste + detay formları)
│   ├── panels/               # Formlar içindeki alt paneller ve edit panelleri
│   ├── component/            # Yeniden kullanılabilir UI bileşenleri
│   ├── system/                # FormManager, MainForm — form yaşam döngüsü ve navigasyon
│   └── themes/                # Görsel tema yönetimi
├── i18n/                    # Çok dillilik altyapısı (Messages, AppLocale, DateFormats)
├── database/                # DatabaseManager, repository katmanı, mapper'lar, migration SQL'leri
├── model/                   # Domain modelleri (Customer, Device, WorkOrder, Part, Supplier ...)
├── service/                 # İş mantığı servisleri (ServiceManager ile başlatılır)
├── settings/                # Uygulama ayarları (config.json erişimi)
├── documents/                # PDF belge üreticileri (servis, satış, garanti formları vb.)
├── updater/                  # Otomatik güncelleme sistemi (GitHub Releases tabanlı)
├── util/                     # Yardımcı sınıflar (DialogHelper, Format, Validator ...)
└── build/                    # Derleme zamanı araçları (ManifestGenerator — JAR'a dahil değil)
```

Veri klasörü (config, veritabanı, yedekler, loglar) işletim sistemine göre otomatik seçilir:
- **Windows:** `%LOCALAPPDATA%\.servicio`
- **Linux:** `~/.local/share/.servicio` (veya `$XDG_DATA_HOME` tanımlıysa oradan)

---

## 💻 Kurulum (Son Kullanıcı)

En güncel sürüm için [**Releases**](https://github.com/Cabro57/Servicio/releases) sayfasından
işletim sisteminize uygun kurulum paketini indirin:

- **Windows:** `.exe` veya `.msi` kurulum dosyası.
- **Linux:** `.deb` (Debian/Ubuntu tabanlı) veya `.rpm` (Fedora/RHEL tabanlı) paketi.

Kurulumdan sonra uygulama, güncellemeleri kendisi kontrol eder ve bildirir — elle JAR indirip
değiştirmenize gerek yoktur.

> macOS şu an için hedef platform değildir.

---

## 🛠️ Geliştirici Kurulumu

### Gereksinimler
- **JetBrains JDK 21** (`jbr-21.0.10` veya uyumlu bir sürüm) — ⚠️ sistem JDK'sı farklı bir
  sürümdeyse (ör. JDK 25) Lombok annotation işleme hata verebilir, mutlaka JBR 21 kullanılmalı.
- Git.
- Maven'e elle ihtiyaç yok — `release.ps1` gerekmesi halinde taşınabilir bir Maven sürümünü
  otomatik indirir.

### Kurulum Adımları

```bash
git clone https://github.com/Cabro57/Servicio.git
cd Servicio
```

`JAVA_HOME`'u JBR 21'e ayarlayıp derleyin:

```powershell
$env:JAVA_HOME = "<JBR 21 kurulum yolu>"
mvn clean package
```

Bu komut `target/servicio.jar`, `target/libs/` (bağımlılıklar) ve kökte `update-manifest.json`
üretir.

Uygulamanın giriş noktası `tr.cabro.servicio.Launcher` sınıfıdır (pom.xml `mainClass`) —
`Servicio.main()` DEĞİL. Veri klasörü çözümü, herhangi bir logger yüklenmeden önce burada yapılır.

### Tek Komutla Derle + Yayınla

```powershell
.\release.ps1               # derler, GitHub'da release açar/günceller, JAR'ı yükler
.\release.ps1 -BuildOnly    # sadece derler, yayınlamaz
.\release.ps1 -NoPush       # yayınlar ama update-manifest.json'u push etmez
.\release.ps1 -Yes          # onay sormadan devam eder
.\release.ps1 -Notes "..."  # release açıklaması ile birlikte
```

### Native Kurulum Paketi Üretme

```powershell
packaging\jpackage-windows.ps1   # Windows: app-image + .exe/.msi
```
```bash
packaging/jpackage-linux.sh      # Linux: app-image + .deb/.rpm
```

---

## ⚙️ Yapılandırma ve Veri Klasörü

Uygulama tercihleri (tema, pencere durumu, dil/bölge ayarı, tablo sayfa boyutları vb.)
veri klasöründeki `config.json` dosyasında; işletme politikaları (barkod öneki, kilit süresi
gibi) ise veritabanındaki `app_settings` tablosunda tutulur — bu ayrım, yedekten geri yüklemede
işletme verisinin korunmasını, makineye özel tercihlerin ise korunmamasını sağlar.

| Veri | Konum |
|---|---|
| Veritabanı, config, loglar | `%LOCALAPPDATA%\.servicio` (Windows) / `~/.local/share/.servicio` (Linux) |
| Yedekler | Veri klasörü altında `backups/` (varsayılan, değiştirilebilir) |

---

## 🚀 Dağıtım / Sürüm Yayınlama

1. `pom.xml` içindeki `<version>` etiketi güncellenir.
2. (İsteğe bağlı) `release-notes.md` dosyasına o sürümün notları yazılır — bu dosya repoya
   commit edilmez, yalnızca GitHub release açıklaması kaynağı olarak kullanılır.
3. `.\release.ps1` çalıştırılır: derler, `v<version>` etiketiyle GitHub release açar/günceller,
   JAR'ı yükler, `update-manifest.json`'u `master`'a push eder (mevcut kurulumların yeni sürümü
   fark etmesi için).
4. Native installer gerekiyorsa `packaging/` altındaki script'ler ayrıca çalıştırılır (release
   akışının otomatik bir parçası değildir).

---

## 🗺️ Yol Haritası

### ✅ Tamamlananlar
- Java 21'e yükseltme ve kütüphane modernizasyonu.
- Çapraz platform desteği (Windows + Linux native paketleme).
- Ayar altyapısının merkezileştirilmesi (`config.json` + `app_settings`).
- Cihaz erişim bilgisi (PIN/şifre/desen) — ayrı şifreli tabloda, otomatik temizlenen.
- Servis akışı PDF belgeleri ve WhatsApp şablon entegrasyonu.
- 2.el cihaz alım-satım modülü.
- Parça için çoklu döviz desteği (TCMB kur entegrasyonu).
- Çok dilli arayüz (Türkçe/İngilizce), bölge/para birimi ayarları.
- Uygulama genelinde modern modal/bildirim sistemine geçiş.

### 🚧 Planlanan / Değerlendirilen
> Bu bölüm proje geliştikçe güncellenecektir.
- Linux için otomatik yayınlama script'i (`release.sh`) — şu an yalnızca paketleme var, GitHub'a
  yayınlama Windows'a özel.
- Daha fazla dil desteği (altyapı hazır, yeni bir `messages_<dil>.properties` dosyası eklemek
  yeterli).
- Kaldırma (uninstall) sırasında veri klasörünü silme seçeneği sunan companion script.
- Raporlama ekranlarının genişletilmesi.
- <!-- TODO: yeni planlanan maddeler buraya eklenecek -->

---

## 🤝 Katkıda Bulunma

Bu proje şu an tek geliştirici tarafından, kapalı/özel bir kod tabanı olarak yürütülmektedir.
Hata bildirimi veya öneri için lütfen bir [Issue](https://github.com/Cabro57/Servicio/issues)
açın.

---

## 📄 Lisans

Lisans modeli henüz belirlenmemiştir. Aksi açıkça belirtilmedikçe tüm hakları saklıdır.

---

## 🙏 Teşekkürler

Bu proje aşağıdaki açık kaynak kütüphaneler sayesinde mümkün oldu:
[FlatLaf](https://www.formdev.com/flatlaf/), [raven-modal](https://github.com/DJ-Raven/modal-dialog),
[MigLayout](https://www.miglayout.com/), [JDBI](https://jdbi.org/), [Flyway](https://flywaydb.org/),
[HikariCP](https://github.com/brettwooldridge/HikariCP), [OpenPDF](https://github.com/LibrePDF/OpenPDF),
[SQLite JDBC](https://github.com/xerial/sqlite-jdbc) ve daha fazlası.

---

<div align="center">

**Servicio** — teknik servis atölyeniz için, verinizin sizde kaldığı yönetim sistemi.

</div>
