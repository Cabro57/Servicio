# Servicio — Kurumsal Mimari Dönüşüm Planı

**Rol:** Kıdemli Yazılım Mimarı değerlendirmesi
**Kapsam:** `tr.cabro.servicio` — Java 21 / Swing (FlatLaf, MigLayout) masaüstü Teknik Servis Kayıt uygulaması
**Yöntem:** Sıfırdan yazım değil — mevcut kod tabanı üzerinde adım adım (incremental) refactoring

---

## 0. Mevcut Durum Tespiti (Codebase Analizi)

Planı somutlaştırmadan önce gerçek kod tabanından çıkarılan tespitler:

| Alan | Bulgu | Değerlendirme |
|---|---|---|
| Katmanlar | `model/`, `database/repository/` (JDBI3 SqlObject arayüzleri), `service/`, `application/` (UI) paketleri zaten ayrılmış | ✅ İyi bir temel var — sıfırdan başlamaya gerek yok |
| Repository | `WorkOrderRepository` gibi arayüzler JDBI3 `@SqlObject` ile tanımlı, dinamik proxy üretiliyor | ✅ Repository Pattern zaten fiilen uygulanmış |
| DI / Bağımlılık Yönetimi | `ServiceManager` (`service/ServiceManager.java`) tüm servisleri `static` alanlarda tutuyor; UI, `ServiceManager.getWorkOrderService()` ile doğrudan erişiyor (`FormWorkOrder:69`) | ❌ Statik Service Locator anti-pattern — test edilemez, gizli bağımlılık |
| Veritabanı erişimi | `DatabaseManager` tamamen `static` metodlarla HikariCP + JDBI kurulumu yapıyor | ❌ Statik singleton, IoC container yok |
| Transaction yönetimi | `WorkOrderService.saveNew()` cihazı ve iş emrini **iki ayrı** `CompletableFuture` zincirinde, **tek transaction olmadan** kaydediyor | ❌ Kısmi hata durumunda tutarsız veri riski (orphan device) |
| Servis arayüzleri | Servisler (`WorkOrderService`, `CustomerService`, …) somut sınıf; arayüz yok | ❌ DIP ihlali, mock'lanamaz |
| Kimlik/Yetki | `User` modeli tek "işletme sahibi" profili; `UserService.authenticate(String pin)` — PIN tabanlı **tek kullanıcı** girişi | ❌ Çoklu kullanıcı / rol kavramı yok |
| Menü yetkilendirme | `MyMenuValidation` — üçüncü parti UI şablonundan kalma, index bazlı hardcoded gizleme (`{2,0}`, `{2,1}`…) | ❌ Gerçek yetkilendirme değil, demo kod artığı |
| Migration | Flyway zaten kullanılıyor, 17 versiyonlu SQL migration mevcut | ✅ İyi pratik, korunmalı |
| Loglama | slf4j + logback bağımlılığı var, ama UI'daki her `Form`'da tekrar eden `.exceptionally(...)` + `Toast.show()` + `log.error()` bloğu | ⚠️ Altyapı var, merkezi politika yok |
| Audit / Denetim izi | Migration dosyalarında `audit_log` benzeri bir tablo yok | ❌ Kim-ne-zaman-ne-değiştirdi izlenemiyor |
| Arama | `WorkOrderRepository.search()` çoklu `LEFT JOIN` + `LIKE '%...%'` — tam metin indeksi yok | ⚠️ Kayıt sayısı büyüdükçe performans riski |
| UI ayrıştırması | `FormWorkOrder` zaten 1254 satırlık god-class'tan `WorkOrderInfoPanel/ItemsPanel/PaymentsPanel/NotesPanel`'e bölünmüş (yorum satırı bunu doğruluyor) | ✅ Ekip zaten doğru yönde bir refactor başlatmış |
| Test | `src/test` altında tek dosya (`SplashScreenTest`) | ❌ Refactoring için güvenlik ağı yok |

**Sonuç:** Servicio "spagetti" değil, "büyümüş monolit" — iskelet doğru ama omurga (DI, transaction sınırları, yetkilendirme, denetim) eksik. Aşağıdaki plan bu iskeleti kırmadan güçlendirmeyi hedefler.

---

## 1. Mimarinin Yeniden Yapılandırılması (Backend & Core)

### 1.1 Hedef Katman Modeli

Tam DDD/Clean Architecture (ayrı modüller, port/adapter'lar) bir masaüstü uygulaması için gereğinden ağırdır. Bunun yerine **"Clean Architecture prensipleri, pragmatik paket sınırları"** öneriyorum: aynı Maven modülünde kalıp, paket görünürlüğü (`package-private`) ve bağımlılık yönü kurallarıyla katmanları zorlamak; olgunlaştıkça (Faz 6) çok modüllü Maven'a geçmek.

```mermaid
flowchart TB
    subgraph UI["tr.cabro.servicio.ui  (Presentation)"]
        Forms["Forms / Panels (View)"]
        Presenters["Presenters (MVP)"]
    end
    subgraph APP["tr.cabro.servicio.application.service  (Use-Case / Business)"]
        Services["Servisler (arayüz + impl)"]
        UoW["UnitOfWork"]
    end
    subgraph DOMAIN["tr.cabro.servicio.domain  (Core)"]
        Entities["Entity / Value Object"]
        Rules["İş kuralları, Domain Exceptions"]
    end
    subgraph INFRA["tr.cabro.servicio.infrastructure  (Data Access)"]
        Repos["Repository impl (JDBI3)"]
        DB["DatabaseManager / Flyway"]
    end

    Forms --> Presenters --> Services
    Services --> Entities
    Services --> UoW --> Repos
    Repos --> DB
    Services -.->|sadece arayüz bilir| Repos

    style DOMAIN fill:#2b2f36,color:#fff
    style APP fill:#1f3a5f,color:#fff
    style INFRA fill:#3a2f1f,color:#fff
    style UI fill:#3a1f2f,color:#fff
```

**Bağımlılık kuralı:** Ok yönü tek yönlü olmalı — `UI → Application → Domain`, `Infrastructure → Domain`. Domain hiçbir üst katmanı (Swing, JDBI) import etmemeli. Bugün `WorkOrderService` içinde `database.filter.ColumnFilterValue` (JDBI'ye özgü) tipinin servis imzasında görünmesi bu kuralın ihlalidir — bunu Faz 3'te temizliyoruz.

### 1.2 Uygulanacak Tasarım Kalıpları ve Gerekçeleri

| Kalıp | Neden gerekli | Servicio'daki somut uygulama noktası |
|---|---|---|
| **Dependency Injection (constructor injection)** | `ServiceManager.getX()` çağrılarını UI'dan söküp test edilebilirlik kazanmak | `ServiceManager`'ı statik alan tutan bir sınıftan, bir **Composition Root**'a (`ApplicationContext`/`ServiceRegistry`) dönüştürmek; ağır bir framework (Spring) yerine [Google Guice](https://github.com/google/guice) gibi hafif bir DI container ya da elle yazılmış composition root |
| **Repository** | Zaten var (JDBI3 `@SqlObject`) | Servis katmanına **arayüz** olarak enjekte edilmeye devam; sadece JDBI-özel tipler (`ColumnFilterValue`) servis API'sinden domain tipine soyutlanmalı |
| **Unit of Work** | `WorkOrderService.saveNew()` gibi çoklu-repository yazımlarını atomik yapmak | `jdbi.inTransaction(handle -> {...})` ile sarmalayan bir `UnitOfWork` arayüzü |
| **Service Interface + Impl** | Servisleri mock'layabilmek, UI'ı somut sınıfa değil soyutlamaya bağlamak | `WorkOrderService` → `interface WorkOrderService` + `WorkOrderServiceImpl` |
| **Factory** | Form/Panel üretimini `new FormX(...)` saçılmasından kurtarmak | `FormFactory` — presenter'ları ve bağımlılıklarını enjekte ederek Form üretir |
| **Strategy** | Zaten örtük var: `documents/*FormGenerator` sınıfları | Ortak `DocumentGenerator` arayüzüyle formalize edilip `Map<ServiceFormType, DocumentGenerator>` üzerinden seçilmeli (switch-case yerine) |
| **Observer / Event Bus** | Modüller arası gizli bağımlılığı azaltmak (örn. ödeme eklenince dashboard'un haberdar olması) | Basit bir in-process `ApplicationEventBus` (Guava EventBus ya da 40 satırlık kendi implementasyonu) |
| **Decorator** | Audit log ve merkezi hata yönetimini iş mantığına karıştırmadan eklemek | Servis arayüzlerini saran `AuditingServiceDecorator` |

### 1.3 Composition Root Örneği (ServiceManager'ın yerini alacak)

```java
// tr.cabro.servicio.infrastructure.ApplicationContext
public final class ApplicationContext {

    private final Jdbi jdbi;
    private final Map<Class<?>, Object> registry = new HashMap<>();

    public ApplicationContext(Jdbi jdbi) {
        this.jdbi = jdbi;
        wireRepositories();
        wireServices();
    }

    private void wireRepositories() {
        register(CustomerRepository.class, jdbi.onDemand(CustomerRepository.class));
        register(WorkOrderRepository.class, jdbi.onDemand(WorkOrderRepository.class));
        // ...
    }

    private void wireServices() {
        UnitOfWork uow = new JdbiUnitOfWork(jdbi);
        CustomerService customerService = new CustomerServiceImpl(get(CustomerRepository.class));
        DeviceService deviceService = new DeviceServiceImpl(get(DeviceRepository.class));
        register(WorkOrderService.class, new WorkOrderServiceImpl(
                uow, get(WorkOrderRepository.class), get(ServiceItemRepository.class),
                deviceService, /* ... */));
        register(CustomerService.class, customerService);
    }

    private <T> void register(Class<T> type, T instance) { registry.put(type, instance); }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) { return (T) registry.get(type); }
}
```

`Servicio` başlatma sınıfı artık `ApplicationContext ctx = new ApplicationContext(jdbi)` üretir, `MainUI`'a enjekte eder; her `Form` kendi bağımlılığını `ctx.get(WorkOrderService.class)` yerine **constructor parametresi** olarak alır (bkz. Bölüm 3).

### 1.4 Unit of Work Örneği (mevcut transaction açığını kapatan somut çözüm)

```java
public interface UnitOfWork {
    <T> T execute(Function<Handle, T> work);
}

public final class JdbiUnitOfWork implements UnitOfWork {
    private final Jdbi jdbi;
    public JdbiUnitOfWork(Jdbi jdbi) { this.jdbi = jdbi; }

    @Override
    public <T> T execute(Function<Handle, T> work) {
        return jdbi.inTransaction(TransactionIsolationLevel.SERIALIZABLE, work::apply);
    }
}
```

`WorkOrderServiceImpl.saveNew` bugünkü hâliyle (cihaz + iş emri iki ayrı `CompletableFuture`) şu şekilde atomikleşir:

```java
public CompletableFuture<WorkOrder> saveNew(WorkOrder workOrder) {
    return CompletableFuture.supplyAsync(() -> unitOfWork.execute(handle -> {
        WorkOrderRepository woRepo = handle.attach(WorkOrderRepository.class);
        DeviceRepository devRepo = handle.attach(DeviceRepository.class);

        if (workOrder.getDeviceId() == null) {
            Long deviceId = devRepo.insert(workOrder.getDevice());
            workOrder.setDeviceId(deviceId);
        }
        Long id = woRepo.insert(workOrder);
        workOrder.setId(id);
        return workOrder;
    }));
}
```

Artık cihaz eklenip iş emri eklenemezse **tüm transaction geri sarılır** — bugünkü kodda bu garanti yok.

---

## 2. Veri Tabanı Mimarisi ve İyileştirme

### 2.1 Kurumsal İlişki Modeli (mevcut + önerilen yeni varlıklar)

```mermaid
erDiagram
    USER ||--o{ WORK_ORDER : "teknisyen olarak atanır"
    USER }o--|| ROLE : "sahip olur"
    ROLE ||--o{ ROLE_PERMISSION : içerir
    PERMISSION ||--o{ ROLE_PERMISSION : tanımlar

    CUSTOMER ||--o{ DEVICE : sahiptir
    CUSTOMER ||--o{ WORK_ORDER : talep_eder
    DEVICE ||--o{ WORK_ORDER : ilişkilidir
    DEVICE ||--o{ DEVICE_TRANSACTION : hareket_gecmisi
    DEVICE ||--o| DEVICE_ACCESS_CREDENTIAL : erisim_bilgisi

    WORK_ORDER ||--o{ WORK_ORDER_ITEM : icerir
    WORK_ORDER ||--o{ WORK_ORDER_PAYMENT : tahsilat
    WORK_ORDER ||--o{ WORK_ORDER_NOTE : not

    WORK_ORDER_ITEM }o--o| PART : kullanir
    WORK_ORDER_ITEM }o--o| LABOR : iscilik

    PART }o--|| PART_CATEGORY : ait
    PART ||--o{ STOCK_MOVEMENT : stok_hareketi
    PART }o--o| SUPPLIER : tedarikci

    AUDIT_LOG }o--|| USER : "islemi_yapan"

    WORK_ORDER {
        long id PK
        long customer_id FK
        long device_id FK
        long technician_id FK "-> USER.id (YENİ)"
        string service_status
        datetime created_at
    }
    AUDIT_LOG {
        long id PK
        long user_id FK
        string entity_name
        long entity_id
        string action
        text old_value_json
        text new_value_json
        datetime created_at
    }
    ROLE {
        long id PK
        string name "TEKNISYEN | MALI_ISLER | YONETICI"
    }
```

**Not:** `technician_id` alanı `WorkOrderRepository` içinde zaten var (`insert` sorgusunda geçiyor) fakat `USER` tablosu bugün tek kişilik bir "işletme profili"; bu FK'nin anlamlı olması için Bölüm 4.1'deki çoklu-kullanıcı modeli önce hayata geçmeli.

### 2.2 Performans

1. **İndeksleme:** V9 migration'ında performans indexleri zaten eklenmiş — iyi. Şunlar eklenmeli:
   - `work_orders(service_status, created_at)` bileşik index — durum filtreli sayfalama sorguları için (`findByStatusesPaged`).
   - `work_order_items(service_id)`, `work_order_payments(service_id)` — zaten FK ama SQLite'da FK otomatik index oluşturmaz, açıkça eklenmeli.
2. **Arama (kritik):** `WorkOrderRepository.search()` her aramada `customers`/`devices`/`device_brands` join'i + 6 ayrı `LIKE '%...%'` çalıştırıyor — index kullanamaz, tam tablo taraması yapar. 5.000+ iş emrinde gözle görülür yavaşlama beklenir.
   - **Öneri:** SQLite **FTS5 sanal tablosu** (`work_order_search_fts`) — müşteri adı, telefon, cihaz marka/model, arıza metni tek bir FTS index'inde birleştirilir; trigger'larla senkron tutulur (`AFTER INSERT/UPDATE/DELETE`).
3. **Transaction yönetimi:** Bölüm 1.4'teki `UnitOfWork` deseni + `journal_mode=WAL` (zaten aktif) — okuma/yazma paralelliği korunur.
4. **ORM seçimi:** Mevcut **JDBI3 + SQL** yaklaşımı **korunmalı**. Tam bir JPA/Hibernate'e geçiş, tek kullanıcı/tek bağlantılı (`maximumPoolSize=1`) SQLite masaüstü senaryosunda gereksiz karmaşıklık (N+1, lazy-loading, session yönetimi) getirir; JDBI zaten SQL üzerinde tam kontrol + tip-güvenli mapping sağlıyor. Sadece **repository arayüzlerinin servis katmanına sızdırdığı JDBI-özel tipler** (Bölüm 1) temizlenmeli.
5. **Migration disiplini:** Flyway sürüm dosyaları (`V1`…`V17`) korunsun; artık `checksum` doğrulaması CI'da (Faz 7) otomatik çalıştırılmalı, "repeatable migration" (`R__`) view/trigger tanımları için ayrılmalı.

---

## 3. Kullanıcı Arayüzü Mimarisi (UI/UX Layer)

### 3.1 Desen Seçimi: MVVM değil, **MVP (Model-View-Presenter)**

Swing, WPF/JavaFX'teki gibi native `Binding`/`ObservableProperty` mekanizmasına sahip değil; MVVM'i zorlamak ekstra bir binding kütüphanesi (JGoodies Binding, Swing property change desteği) gerektirir ve masaüstü Swing dünyasında karşılığı zayıf kalır. **MVP** ise Swing'in event-listener doğasıyla birebir örtüşür ve bugünkü `Form`/`Panel` ayrımına doğal olarak oturur:

```mermaid
sequenceDiagram
    participant V as FormWorkOrder (View)
    participant P as WorkOrderPresenter
    participant S as WorkOrderService (interface)
    participant R as WorkOrderRepository

    V->>P: onStatusChanged(newStatus)
    P->>S: updateStatus(id, newStatus)
    S->>R: updateStatus(...)  [UnitOfWork içinde]
    R-->>S: void
    S-->>P: CompletableFuture<Void>
    P->>P: .thenAccept(...) sonucu al
    P->>V: SwingUtilities.invokeLater(() -> view.showUpdatedStatus(...))
    P->>V: (hata ise) view.showError(mesaj)
```

### 3.2 View / Presenter Ayrıştırma Örneği

Bugün `FormWorkOrder` doğrudan `ServiceManager.getWorkOrderService()` çağırıyor ve `.exceptionally()` içinde Toast gösteriyor. Hedef:

```java
// tr.cabro.servicio.ui.workorder.WorkOrderView  (arayüz — Form bunu implemente eder)
public interface WorkOrderView {
    void showWorkOrder(WorkOrder workOrder);
    void showError(String message);
    void showLoading(boolean loading);
}

// tr.cabro.servicio.ui.workorder.WorkOrderPresenter — Swing/JDBI'den habersiz, saf Java
public final class WorkOrderPresenter {
    private final WorkOrderService workOrderService; // arayüz, DI ile gelir
    private final WorkOrderView view;

    public WorkOrderPresenter(WorkOrderService workOrderService, WorkOrderView view) {
        this.workOrderService = workOrderService;
        this.view = view;
    }

    public void loadWorkOrder(Long id) {
        view.showLoading(true);
        workOrderService.get(id)
            .thenAccept(opt -> SwingUiRunner.run(() -> {
                view.showLoading(false);
                opt.ifPresentOrElse(view::showWorkOrder,
                        () -> view.showError("Servis kaydı bulunamadı."));
            }))
            .exceptionally(ex -> {
                SwingUiRunner.run(() -> { view.showLoading(false); view.showError(ex.getMessage()); });
                return null;
            });
    }
}

// FormWorkOrder artık sadece View implementasyonu
public class FormWorkOrder extends Form implements WorkOrderView {
    private final WorkOrderPresenter presenter;

    public FormWorkOrder(WorkOrderService workOrderService, Long workOrderId) {
        this.presenter = new WorkOrderPresenter(workOrderService, this);
        initComponent();
        presenter.loadWorkOrder(workOrderId);
    }

    @Override public void showWorkOrder(WorkOrder wo) { hydrateHeader(wo); infoPanel.refresh(wo); }
    @Override public void showError(String message) { Toast.show(this, Toast.Type.ERROR, message); }
    @Override public void showLoading(boolean loading) { /* progress göster/gizle */ }
}
```

**Kazanım:** `WorkOrderPresenter` Swing'e hiç bağımlı değil → JUnit ile Toast/Swing mock'lamadan test edilebilir. `FormWorkOrder` artık `ServiceManager`'ı bilmez, servis bağımlılığı `FormFactory` (Bölüm 1.2) tarafından enjekte edilir.

### 3.3 Tekrarlanan Async Boilerplate'in Standardizasyonu

Bugün her Form'da tekrar eden `SwingUtilities.invokeLater(...)` + `.exceptionally(...)` + `Toast.show(...)` bloğu, tek bir yardımcıya taşınmalı:

```java
public final class SwingUiRunner {
    private SwingUiRunner() {}

    public static void run(Runnable onEdt) {
        if (SwingUtilities.isEventDispatchThread()) onEdt.run();
        else SwingUtilities.invokeLater(onEdt);
    }

    /** CompletableFuture sonucunu EDT'de işler, hatada globalExceptionHandler'a devreder. */
    public static <T> void handle(CompletableFuture<T> future, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        future.whenComplete((result, ex) -> run(() -> {
            if (ex != null) onError.accept(unwrap(ex)); else onSuccess.accept(result);
        }));
    }
}
```

### 3.4 Modülerlik & Bileşenler

Zaten var olan yeniden kullanılabilir bileşenler (`GenericTableModel`, `AbstractTableForm`, `StatCard`, `Badge`, `PaginationBar`) **korunmalı ve resmileştirilmeli**:

- `application/component/` → `ui/components/` altında bir **"UI Kit"** paketi olarak belgelenmeli (her bileşen için tek satır javadoc + kullanım örneği).
- `AbstractTableForm` Template Method desenini zaten uyguluyor — yeni liste ekranları (`FormWorkOrders`, `FormCustomers` gibi) bunu miras almaya devam etmeli, kopyala-yapıştır yerine.
- Yeni asenkron standart: her Presenter, `SwingUiRunner.handle(...)` kullanmalı; ham `CompletableFuture`/`SwingUtilities` kodu Form içinde **yasaklanmalı** (code review kuralı).

---

## 4. Kurumsal Özellikler ve Entegrasyonlar

### 4.1 Yetkilendirme & Rol Yönetimi (RBAC)

Bugün: tek `User` (işletme profili) + PIN girişi, `MyMenuValidation` demo kodu. Hedef model:

```mermaid
flowchart LR
    U[User / Personel] -->|N:1| R[Role]
    R -->|1:N| RP[RolePermission]
    RP -->|N:1| P[Permission]
    U -.çalışma anında.-> AC["AccessContext (aktif kullanıcı + izinleri)"]
    AC --> Menu["MyMenuValidation → PermissionAwareMenuFilter"]
    AC --> UseCase["Servis metodları (ör. WorkOrderService.delete)"]
```

- **Roller (öneri):** `TEKNISYEN` (kendi iş emirlerini görür/günceller, fiyat göremez), `MALI_ISLER` (ödeme/fatura, fiyat, rapor), `YONETICI` (tam yetki + kullanıcı yönetimi).
- **Uygulama noktası:** `PermissionService.hasPermission(User, Permission)` — hem UI'da menü/aksiyon gizlemek için (`MyMenuValidation`'ın gerçek implementasyonu) hem de **servis katmanında** son karar noktası olarak (UI gizlemesi atlatılsa bile backend reddeder — *defense in depth*).
- **Parola/PIN güvenliği:** `jbcrypt` bağımlılığı zaten mevcut — `PasswordUtil` üzerinden tüm kullanıcı şifreleri (yeni çoklu-kullanıcı modelinde) bcrypt ile hash'lenmeli, düz metin asla DB'ye yazılmamalı (bugünkü tek-kullanıcı PIN akışı da bu kontrolden geçmeli).

```java
public interface PermissionService {
    boolean can(User user, Permission permission);
    default void require(User user, Permission permission) {
        if (!can(user, permission)) throw new AccessDeniedException(permission);
    }
}

// Servis katmanında kullanım — UI'dan bağımsız, atlatılamaz güvenlik sınırı
public CompletableFuture<Void> delete(Long id, User actingUser) {
    permissionService.require(actingUser, Permission.WORK_ORDER_DELETE);
    return CompletableFuture.runAsync(() -> workOrderRepository.delete(id));
}
```

### 4.2 Loglama & Hata Yönetimi

- **Merkezi UI hata yakalama:** `Thread.setDefaultUncaughtExceptionHandler` (EDT dışı) + `SwingUtilities.invokeLater` içine giren `RepaintManager`/`Toolkit` tabanlı EDT exception handler (EDT'de fırlatılan ama yakalanmayan hatalar için) — tek bir `GlobalExceptionHandler` sınıfı; her Form'un kendi `.exceptionally()` kopyasını yazmasına gerek kalmaz, `SwingUiRunner.handle()` zaten `onError` için bu handler'a düşer.
- **Loglama:** slf4j+logback altyapısı korunur; öneriler:
  - Her kullanıcı işlemi öncesi `MDC.put("userId", ...)`, `MDC.put("actionId", UUID)` — log satırlarını iş akışına bağlamak için.
  - `logback.xml`'de rotasyon (`RollingFileAppender`, günlük + boyut bazlı) ve hata seviyesinde ayrı bir dosya (`error.log`) — destek taleplerinde hızlı teşhis için.
  - Kritik hatalar (DB bağlantı kaybı, migration hatası) için isteğe bağlı bir "hata raporu paketle" özelliği (zaten `AppLock`/`DataDirResolver` gibi altyapı var, log+backup dosyasını zip'leyip kullanıcıya sunmak kolay).

### 4.3 Raporlama & Audit Log

- Mevcut `ReportManager`/`ReportRepository` iş/finans raporları için korunur.
- **Audit Trail (yeni):** `audit_log` tablosu + `AuditingServiceDecorator` (Decorator Pattern, Bölüm 1.2):

```sql
-- V20__create_audit_log.sql  (V18/V19 RBAC tabloları için ayrılmış, bkz. 4.1)
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    entity_name TEXT NOT NULL,
    entity_id INTEGER,
    action TEXT NOT NULL,           -- CREATE | UPDATE | DELETE | STATUS_CHANGE
    old_value_json TEXT,
    new_value_json TEXT,
    created_at TEXT NOT NULL
);
CREATE INDEX idx_audit_entity ON audit_log(entity_name, entity_id);
CREATE INDEX idx_audit_user ON audit_log(user_id, created_at);
```

```java
public final class AuditingWorkOrderService implements WorkOrderService {
    private final WorkOrderService delegate;
    private final AuditLogRepository auditRepo;
    private final CurrentUserProvider currentUser;

    @Override
    public CompletableFuture<Void> updateStatus(Long id, ServiceStatus newStatus) {
        return delegate.updateStatus(id, newStatus)
            .thenRun(() -> auditRepo.log(currentUser.get().getId(), "WorkOrder", id,
                    "STATUS_CHANGE", null, newStatus.name()));
    }
    // diğer metotlar delegate + audit...
}
```

Bu sayede iş kuralı kodu (`WorkOrderServiceImpl`) audit ile hiç kirlenmez; `ApplicationContext` sarmalamayı (wiring) yapar.

### 4.4 Tahsilat ve Cari Hesap Mimarisi (Accounts Receivable)

**Bugünkü durum:** `work_order_payments` tablosu doğrudan `service_id`'ye kilitli (`WorkOrderPayment.serviceId`); bir ödeme yalnızca tek bir iş emrinin borcunu kapatabiliyor ve `ServicePaymentRepository.deletePayment()` kaydı **fiziksel olarak siliyor** (`DELETE FROM work_order_payments`). Küçük ölçekte çalışır, ama iki noktada kurumsal muhasebe standardının (ERP'lerdeki Cari Hesap / Accounts Receivable modeli — Logo, Netsis, SAP FI-AR mantığı) gerisinde kalıyor: müşteri bazlı bakiye görünümü yok, finansal kayıtlar geri döndürülemez şekilde yok edilebiliyor.

```mermaid
erDiagram
    CUSTOMER ||--o{ WORK_ORDER : "borçlanır (iş bazlı)"
    CUSTOMER ||--o{ PAYMENT : "öder (müşteri bazlı)"
    PAYMENT ||--o{ PAYMENT_ALLOCATION : dağıtılır
    PAYMENT_ALLOCATION }o--|| WORK_ORDER : "borcu kapatır"
    PAYMENT }o--|| USER : "tahsil_eden / iptal_eden"

    PAYMENT {
        long id PK
        long customer_id FK "ana bağlantı — artık müşteriye ait"
        decimal amount
        string payment_type
        string currency
        string status "ACTIVE | VOIDED"
        long voided_by_user_id FK "iptal edildiyse"
        string void_reason
        datetime payment_date
    }
    PAYMENT_ALLOCATION {
        long id PK
        long payment_id FK
        long work_order_id FK
        decimal allocated_amount
    }
```

| İlke | Bugün | Kurumsal karşılığı |
|---|---|---|
| Ödeme kime ait? | `service_id` (iş emrine kilitli) | `customer_id` — ödeme önce **müşteriye** girilir |
| Bir ödeme birden fazla borcu kapatabilir mi? | Hayır, 1 ödeme = 1 servis | `payment_allocation` ara tablosu — bir ödeme birden çok açık iş emrine bölüştürülebilir |
| Avans / ön ödeme | Desteklenmiyor (iş emri şart) | Allocation'sız `payment` kaydı → müşteri bakiyesinde kredi/avans olarak durur, sonraki işe düşülür |
| Ödeme iptali | `DELETE FROM work_order_payments` | **Hard delete yok.** `status=VOIDED` + `void_reason` + `voided_by_user_id` — 4.3'teki audit log ile birebir örtüşür |
| "Müşterinin toplam borcu ne?" | Tüm `work_order`'lar client-side toplanır | Tek sorgu: `SUM(work_order borcu) - SUM(payment.amount WHERE status='ACTIVE')` |
| Para birimi | `WorkOrderPayment`'ta yok (V17 kur desteği sadece `Part`/iş kalemlerinde) | `payment.currency` + tahsilat anındaki kur — `ExchangeRateManager` ile tutarlı |

```java
public interface PaymentService {
    // Müşteri bazlı — artık iş emrinden bağımsız girilebilir (avans dahil)
    CompletableFuture<Payment> collect(Long customerId, BigDecimal amount, PaymentType type,
                                        List<PaymentAllocationRequest> allocations); // boş liste = avans

    // Silme değil, iptal — UnitOfWork içinde allocation'lar geri alınır, status=VOIDED yapılır
    CompletableFuture<Void> voidPayment(Long paymentId, String reason, User actingUser);

    CompletableFuture<CustomerBalance> getCustomerBalance(Long customerId);
}
```

```sql
-- V21__create_payment_and_allocation.sql
CREATE TABLE IF NOT EXISTS payments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER NOT NULL REFERENCES customers(id),
    amount DECIMAL NOT NULL,
    payment_type TEXT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'TRY',
    status TEXT NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | VOIDED
    voided_by_user_id INTEGER REFERENCES users(id),
    void_reason TEXT,
    payment_date TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS payment_allocations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    payment_id INTEGER NOT NULL REFERENCES payments(id),
    work_order_id INTEGER NOT NULL REFERENCES work_orders(id),
    allocated_amount DECIMAL NOT NULL
);
CREATE INDEX idx_payment_customer ON payments(customer_id, status);
CREATE INDEX idx_allocation_work_order ON payment_allocations(work_order_id);
```

**Geçiş notu:** `WorkOrderService.addPayment`/`deletePayment` çağrıları kaldırılmaz, `PaymentService`'e delege edilir — iş emri ekranındaki "ödeme ekle" butonu kullanıcı için aynı yerde durur, arkada tek allocation'lı bir `PaymentService.collect(customerId, amount, ..., List.of(new Allocation(workOrderId, amount)))` çağrısına döner. Mevcut `work_order_payments` verisi tek seferlik bir migration script'iyle `payments`+`payment_allocations`'a taşınır (her satır → 1 payment + 1 allocation).

### 4.5 Parça / İşçilik Ekleme — Stok Rezervasyonu ve Onay Akışı

**Bugünkü durum, güçlü yönleriyle:** `WorkOrderItem` zaten iyi tasarlanmış bir **snapshot modeli** kullanıyor (`itemName`, `purchasePrice`, `unitPrice` o anki değerleriyle donuyor — sonradan `Part` fiyatı değişse bile geçmiş iş emri etkilenmiyor); `StockMovement` de `referenceType` (WORK_ORDER, WORK_ORDER_CANCEL, PURCHASE, RETURN…) ile hareket sebebini izliyor. Bunlar korunmalı. Ancak üç gerçek eksik var:

1. **Anlık düşüm, rezervasyon yok:** `WorkOrderService.addItem()` parçayı iş emrine eklerken stoktan **hemen ve kalıcı olarak** düşüyor (`reduceStockForItem`), müşteri teklifi onaylamadan önce bile. Sistemde zaten bir `RepairQuoteApprovalFormGenerator` (teklif onay belgesi) var — yani "teklif" kavramı belgesel düzeyde mevcut ama stok akışına hiç bağlanmamış.
2. **Transaction sınırı yok:** `addItem()` iki ayrı adımda çalışıyor — kalem insert edilir, *sonra* ayrı bir `CompletableFuture` zincirinde stok düşülür (Bölüm 1'deki `WorkOrder`+`Device` sorunuyla birebir aynı desen).
3. **Depo sabit kodlanmış:** `StockService.addStock/removeStock` içinde `movement.setWarehouseId(1L)` — `Warehouse` modeli/repository'si var olmasına rağmen fiilen tek depo destekleniyor.

```mermaid
stateDiagram-v2
    [*] --> PENDING: Teknisyen parçayı işe ekler\n(stok REZERVE edilir, StockMovement OLUŞMAZ)
    PENDING --> APPROVED: Müşteri teklifi onaylar\n(RepairQuoteApprovalFormGenerator)
    PENDING --> REJECTED: Müşteri reddeder\n(rezervasyon serbest kalır)
    APPROVED --> CONSUMED: Teknisyen parçayı fiilen takar\n(StockMovement OUT yazılır, referenceType=WORK_ORDER)
    CONSUMED --> RETURNED: İptal/iade\n(StockMovement OUT ile aynı miktarda ters kayıt, referenceType=WORK_ORDER_CANCEL)
    REJECTED --> [*]
    RETURNED --> [*]
```

- **Rezervasyon:** `stock_reservations` tablosu (`part_id`, `work_order_item_id`, `quantity`) — "kullanılabilir stok" artık `part.stock_quantity - Σaçık rezervasyonlar` olarak hesaplanır. Teklif onaylanmadan fiziksel `stock_movements`'a hiç dokunulmaz; bugünkü "ekle → sil → geri yükle" (restoreStockForItem) akışına göre daha temiz bir audit izi bırakır (hiç çıkmamış, çıkıp-geri-girmiş değil).
- **Atomiklik:** kalem insert + rezervasyon/düşüm, Bölüm 1.4'teki `UnitOfWork` ile tek transaction'a alınır.
- **Yetki entegrasyonu:** `SourceType.MANUAL` (elle fiyat girişi/iskonto) yalnızca `PermissionService.require(user, Permission.PART_PRICE_OVERRIDE)` geçen roller (4.1'deki `MALI_ISLER`/`YONETICI`) için açık olmalı — bir teknisyenin serbestçe fiyat kırabilmesi kurumsal iç kontrol açığıdır.
- **Çoklu depo:** `StockService` imzasına `warehouseId` parametresi eklenir (varsayılan: iş emrinin bağlı olduğu şube/depo ayarı); `1L` hardcode kaldırılır.

```java
public interface InventoryReservationService {
    CompletableFuture<Integer> getAvailable(Long partId, Long warehouseId); // stok - açık rezervasyonlar
    CompletableFuture<Void> reserve(Long workOrderItemId, Long partId, int quantity);
    CompletableFuture<Void> release(Long workOrderItemId);                  // red/iptal
    CompletableFuture<Void> consume(Long workOrderItemId);                  // onay sonrası fiili düşüm → StockMovement
}
```

---

## 5. Adım Adım Dönüşüm Yol Haritası (Roadmap)

> İlke: Her faz **çalışır ve deploy edilebilir** durumda biter; büyük-patlama (big-bang) rewrite yok.

### Faz 0 — Güvenlik Ağı (1 sprint)
- Kritik servisler (`WorkOrderService`, `StockService`, `PartService`) için karakterizasyon testleri yazılır (mevcut davranışı sabitleyen JUnit testleri).
- Test veritabanı için in-memory/temp SQLite + Flyway kurulumu (`@BeforeEach` ile temiz şema).
- CI'a `mvn test` adımı eklenir (bugün muhtemelen yok).

### Faz 1 — Composition Root & Servis Arayüzleri (1-2 sprint)
- Her servis için arayüz çıkarılır (`WorkOrderService` → interface + `WorkOrderServiceImpl`).
- `ApplicationContext` (Bölüm 1.3) yazılır; `ServiceManager` **silinmez**, geçiş süresince `ApplicationContext`'i sarmalayan bir facade'e dönüştürülür (geriye dönük uyumluluk).
- Yeni yazılan her kod `ServiceManager.getX()` yerine constructor injection kullanır — kural code review'da zorunlu kılınır.

### Faz 2 — Transaction Sınırları (`UnitOfWork`) (1 sprint)
- `JdbiUnitOfWork` eklenir.
- Çoklu-repository yazan tüm servis metotları taranır (`WorkOrderService.saveNew/saveUpdate`, stok azaltma/artırma akışları) ve transaction'a alınır — bu, en yüksek veri-bütünlüğü riskini taşıyan alan olduğu için öncelikli.

### Faz 3 — UI Ayrıştırması: MVP Geçişi (2-3 sprint, kademeli)
- `WorkOrderView`/`WorkOrderPresenter` gibi çiftler, en çok kullanılan 3-4 Form için pilot olarak uygulanır (`FormWorkOrder`, `FormCustomer`, `FormPart`).
- `SwingUiRunner` yardımcı sınıfı eklenir, yeni Form'larda zorunlu kılınır.
- `AbstractTableForm` alt sınıfları tek tek MVP'ye taşınır (liste ekranları benzer olduğu için hızlı ilerler).
- `ServiceManager` çağrıları UI'dan tamamen kalkana kadar devam eder (paralel olarak Faz 4-5 ile birlikte yürütülebilir).

### Faz 4 — RBAC ve Çoklu Kullanıcı (2 sprint)
- `role`, `permission`, `role_permission` tabloları (Flyway `V18`/`V19`).
- `User` modeline `roleId` eklenir; mevcut tek-PIN girişi "ilk kullanıcı = Yönetici" olarak migrate edilir (geriye dönük uyumlu).
- `PermissionService` yazılır, kritik servis metotlarına `require(...)` çağrıları eklenir.
- `MyMenuValidation` gerçek `PermissionAwareMenuFilter` ile değiştirilir.

### Faz 5 — Merkezi Hata Yönetimi, Loglama, Audit (1-2 sprint)
- `GlobalExceptionHandler` + EDT uncaught exception handler kurulur.
- MDC bazlı log zenginleştirme, log rotasyonu.
- `audit_log` tablosu + `AuditingServiceDecorator`'lar kritik servislere (WorkOrder, Payment, User, Stock) uygulanır.

### Faz 6 — Cari Hesap (Tahsilat) ve Stok Rezervasyonu (2-3 sprint)
- `payments`/`payment_allocations` tabloları (Bölüm 4.4, `V21`); mevcut `work_order_payments` verisi tek seferlik script ile taşınır.
- `PaymentService` yazılır; `WorkOrderService.addPayment/deletePayment` bu servise delege eder — UI akışı değişmez.
- `stock_reservations` tablosu + `InventoryReservationService` (Bölüm 4.5); `WorkOrderService.addItem` PENDING/APPROVED/CONSUMED akışına geçirilir, fiziksel stok düşümü yalnızca `CONSUMED` adımında olur.
- `StockService`'teki `warehouseId=1L` hardcode'u kaldırılır, parametrik hale getirilir.
- Bu fazın tamamı Faz 2'deki `UnitOfWork` ve Faz 4'teki `PermissionService`'e (fiyat override yetkisi) bağımlıdır — o yüzden onlardan sonra planlanmıştır.

### Faz 7 — Performans & Ölçeklenebilirlik (1-2 sprint, opsiyonel/uzun vade)
- FTS5 tabanlı arama (`work_order_search_fts`), mevcut `LIKE` sorgularının yerini alır.
- İhtiyaç halinde çok-modüllü Maven'a geçiş (`servicio-domain`, `servicio-application`, `servicio-infrastructure`, `servicio-ui`) — paket sınırları Faz 1-3'te zaten netleştiği için bu adım mekanik bir taşıma olur.

### Faz 8 — Sürdürülebilirlik
- Test kapsamı servis katmanında %70+ hedeflenir.
- ArchUnit ile katman bağımlılık kurallarının (Bölüm 1.1) statik olarak CI'da doğrulanması (`Domain katmanı Swing import edemez` gibi kurallar otomatik denetlenir).

---

## 6. Hedef Paket Yapısı (Tek Modül, Sıkı Sınırlarla)

```
tr.cabro.servicio/
├── domain/                     # Framework'ten bağımsız çekirdek
│   ├── model/                  # Entity/VO (mevcut model/ paketinin taşınmış hali)
│   └── exception/              # ValidationException, AccessDeniedException...
├── application/
│   ├── service/                # Servis ARAYÜZLERİ (WorkOrderService, CustomerService...)
│   ├── service/impl/           # Servis implementasyonları
│   ├── security/                # PermissionService, CurrentUserProvider
│   └── audit/                  # AuditingServiceDecorator'lar
├── infrastructure/
│   ├── db/                     # DatabaseManager, JdbiUnitOfWork, Flyway config
│   ├── repository/             # Mevcut database/repository/ (JDBI3 SqlObject arayüzleri)
│   └── context/                # ApplicationContext (composition root)
├── ui/
│   ├── forms/                  # View implementasyonları (mevcut application/forms/)
│   ├── presenters/              # MVP presenter'ları
│   ├── components/             # Yeniden kullanılabilir Swing bileşenleri (mevcut application/component/)
│   └── theming/                 # FlatLaf tema yönetimi (mevcut application/themes/)
└── shared/
    ├── async/                   # SwingUiRunner
    └── util/                    # Format, Validator, PhoneHelper vb. (mevcut util/)
```

Bu yapı, mevcut `application/`, `service/`, `database/`, `model/`, `util/` paketlerinin **yeniden adlandırılıp taşınmasıyla** elde edilir; sınıf içerikleri Faz 1-5 boyunca kademeli olarak güncellenir. Hiçbir aşamada "her şeyi durdurup yeniden yaz" gerekmez.

---

## Özet Öncelik Sırası

1. **En yüksek risk / en düşük çaba:** Faz 2 (Transaction) — veri bütünlüğü açığını kapatır, dar kapsamlı.
2. **En yüksek uzun-vade değer:** Faz 1 (DI/Composition Root) — sonraki her fazı kolaylaştırır.
3. **En görünür kurumsal eksik:** Faz 4 (RBAC) — şu an teknik olarak yok, çok kullanıcılı kullanım için engelleyici.
4. **Sürdürülebilirliğin garantisi:** Faz 0 ve Faz 7 (test) — refactoring'in kendisini güvenli kılan katman.
