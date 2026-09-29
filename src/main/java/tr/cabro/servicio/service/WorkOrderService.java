package tr.cabro.servicio.service;

import org.jdbi.v3.core.Handle;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.repository.*;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.enums.AllocationTargetType;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.model.enums.ReferenceType;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.dto.TargetPayment;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.model.enums.StockItemKind;
import tr.cabro.servicio.service.exception.ValidationException;

import tr.cabro.servicio.util.Format;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final ServiceItemRepository itemRepository;
    private final PaymentService paymentService;
    private final ServiceNoteRepository noteRepository;
    private final StockService stockService;
    private final DeviceService deviceService;
    private final CustomerRepository customerRepository;
    private final DeviceRepository deviceRepository;
    private final PaymentRepository paymentRepository;

    private static final int IN_CHUNK = 500;

    /** Saat farkı / dakika yuvarlaması için gelecek tarih kontrolünde tanınan pay. */
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            ServiceItemRepository itemRepository,
                            PaymentService paymentService,
                            ServiceNoteRepository noteRepository,
                            StockService stockService,
                            DeviceService deviceService,
                            CustomerRepository customerRepository,
                            DeviceRepository deviceRepository,
                            PaymentRepository paymentRepository) {
        this.workOrderRepository = workOrderRepository;
        this.itemRepository = itemRepository;
        this.paymentService = paymentService;
        this.noteRepository = noteRepository;
        this.stockService = stockService;
        this.deviceService = deviceService;
        this.customerRepository = customerRepository;
        this.deviceRepository = deviceRepository;
        this.paymentRepository = paymentRepository;
    }

    // =========================================================================
    // WORK ORDER CRUD
    // =========================================================================

    /**
     * Servis kaydını ve (gerekiyorsa) bağlı cihazı kaydeder ya da günceller.
     * <p>
     * Cihaz akışı:
     * <ul>
     *   <li>deviceId null → cihaz henüz sistemde yok, önce cihazı kaydet, sonra servisi kaydet.</li>
     *   <li>deviceId dolu → cihaz zaten sistemde; servis insert/update işleminde cihaz dokunulmaz.</li>
     *   <li>update=true ve device nesnesi değişmişse → cihazı da güncelle, sonra servisi güncelle.</li>
     * </ul>
     * Tüm adımlar tek bir async zincirde birbirine bağlıdır; ara adımlar tamamlanmadan
     * bir sonraki adım başlamaz.
     */
    public CompletableFuture<WorkOrder> save(WorkOrder workOrder, boolean update) {
        validateWorkOrder(workOrder);

        if (!update) {
            return saveNew(workOrder);
        } else {
            return saveUpdate(workOrder);
        }
    }

    /**
     * Yeni servis kaydı.
     * Cihaz sistemde yoksa (deviceId == null) önce cihazı kaydeder, sonra servisi ekler.
     * Cihaz zaten sistemdeyse (deviceId dolu) doğrudan servisi ekler.
     */
    private CompletableFuture<WorkOrder> saveNew(WorkOrder workOrder) {
        if (workOrder.getServiceStatus() != null && workOrder.getServiceStatus().isClosed()) {
            throw new ValidationException("Yeni servis kaydı teslim edildi ya da iade durumunda açılamaz.");
        }
        if (workOrder.getReceivedAt() != null) {
            requireNotFuture(workOrder.getReceivedAt(), LocalDateTime.now());
        }
        if (workOrder.getDeviceId() == null) {
            // Önce cihazı kaydet, ID'yi al, sonra servisi ekle
            return deviceService.save(workOrder.getDevice(), false)
                    .thenCompose(savedDevice -> {
                        workOrder.setDeviceId(savedDevice.getId());
                        workOrder.setDevice(savedDevice);
                        return insertWorkOrder(workOrder);
                    });
        } else {
            // Cihaz zaten kayıtlı, direkt servisi ekle
            return insertWorkOrder(workOrder);
        }
    }

    /**
     * Mevcut servis kaydını günceller.
     * Cihaz nesnesi varsa onu da günceller; cihaz yoksa sadece servisi günceller.
     */
    private CompletableFuture<WorkOrder> saveUpdate(WorkOrder workOrder) {
        // Kapalı kayıtta cihaz da güncellenmesin diye kontrol zincirin başında.
        return DbExecutor.run(() -> requireOpen(workOrderRepository, workOrder.getId()))
                .thenCompose(ignored -> {
                    if (workOrder.getDevice() != null && workOrder.getDeviceId() != null) {
                        // Cihazı güncelle, sonra servisi güncelle
                        return deviceService.save(workOrder.getDevice(), true)
                                .thenCompose(updatedDevice -> updateWorkOrder(workOrder));
                    }
                    return updateWorkOrder(workOrder);
                });
    }

    /**
     * Kayıt her zaman "Kabul Edildi" geçmiş satırıyla başlar (teslim alma tarihi). İstenen başlangıç
     * durumu farklıysa (ör. doğrudan tamire alındı) aynı anda ikinci bir geçiş yazılır.
     */
    private CompletableFuture<WorkOrder> insertWorkOrder(WorkOrder workOrder) {
        return DbExecutor.supply(() -> DatabaseManager.inTransaction(handle -> {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime receivedAt = workOrder.getReceivedAt() != null ? workOrder.getReceivedAt() : now;
            ServiceStatus initial = workOrder.getServiceStatus() != null ? workOrder.getServiceStatus() : ServiceStatus.ACCEPTED;
            workOrder.setServiceStatus(initial);

            Long id = handle.attach(WorkOrderRepository.class).insert(workOrder);
            WorkOrderStatusHistoryRepository history = handle.attach(WorkOrderStatusHistoryRepository.class);
            history.insert(id, ServiceStatus.ACCEPTED, receivedAt, now);
            if (initial != ServiceStatus.ACCEPTED) {
                history.insert(id, initial, receivedAt, now);
            }

            workOrder.setId(id);
            workOrder.setReceivedAt(receivedAt);
            workOrder.setStatusChangedAt(receivedAt);
            if (initial == ServiceStatus.UNDER_REPAIR) workOrder.setRepairStartedAt(receivedAt);
            if (initial == ServiceStatus.READY) workOrder.setReadyAt(receivedAt);
            return workOrder;
        }));
    }

    private CompletableFuture<WorkOrder> updateWorkOrder(WorkOrder workOrder) {
        return DbExecutor.supply(() -> {
            workOrderRepository.update(workOrder);
            return workOrder;
        });
    }

    public CompletableFuture<Void> updateStatus(Long id, ServiceStatus newStatus) {
        return updateStatus(id, newStatus, null);
    }

    /**
     * Durumu değiştirir ve geçişi durum geçmişine yazar.
     *
     * @param changedAt geçişin gerçekleştiği an; null ise şimdi. Bir önceki geçişten önce ve
     *                  gelecekte olamaz.
     */
    public CompletableFuture<Void> updateStatus(Long id, ServiceStatus newStatus, LocalDateTime changedAt) {
        if (id == null || newStatus == null) {
            throw new ValidationException("ID ve durum boş olamaz.");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime at = changedAt != null ? changedAt : now;
        requireNotFuture(at, now);

        // "İade" durumunda servisin parçaları stoğa döner; stok yetmezse durum değişmez (tek transaction).
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            WorkOrderRepository repo = handle.attach(WorkOrderRepository.class);
            ServiceStatus current = requireOpen(repo, id);
            if (current == newStatus) return;

            WorkOrderStatusHistoryRepository history = handle.attach(WorkOrderStatusHistoryRepository.class);
            List<WorkOrderStatusHistory> rows = history.findByWorkOrderId(id);
            if (!rows.isEmpty()) {
                requireNotBefore(at, rows.get(rows.size() - 1));
            }
            history.insert(id, newStatus, at, now);
            repo.updateStatus(id, newStatus, now);
            syncStock(handle, id, false);
        }));
    }

    /**
     * Teslimi ya da iadeyi geri alır: kapanış geçişi geçmişten silinir, kayıt bir önceki durumuna
     * döner ve yeniden düzenlenebilir. Yeni bir geçiş yazılmaz; önceki durumun tarihi (ör. hazır
     * olma) olduğu gibi kalır. İadeden dönüşte parçalar yeniden stoktan düşülür; stok yetmezse
     * hiçbir şey değişmez.
     *
     * @return kaydın döndüğü durum
     */
    public CompletableFuture<ServiceStatus> reopen(Long id) {
        if (id == null) {
            throw new ValidationException("ID boş olamaz.");
        }
        return DbExecutor.supply(() -> DatabaseManager.inTransaction(handle -> {
            WorkOrderRepository repo = handle.attach(WorkOrderRepository.class);
            ServiceStatus current = repo.findStatus(id)
                    .orElseThrow(() -> new ValidationException("Servis kaydı bulunamadı."));
            if (!current.isClosed()) {
                throw new ValidationException("Servis kaydı zaten açık.");
            }

            WorkOrderStatusHistoryRepository history = handle.attach(WorkOrderStatusHistoryRepository.class);
            List<WorkOrderStatusHistory> rows = history.findByWorkOrderId(id);
            if (rows.isEmpty() || rows.get(rows.size() - 1).getStatus() != current) {
                throw new ValidationException("Durum geçmişi tutarsız; teslim geri alınamadı.");
            }
            ServiceStatus previous = rows.size() >= 2 ? rows.get(rows.size() - 2).getStatus() : ServiceStatus.ACCEPTED;

            history.delete(rows.get(rows.size() - 1).getId());
            repo.updateStatus(id, previous, LocalDateTime.now());
            syncStock(handle, id, false);
            return previous;
        }));
    }

    /** Servis kaydının durum geçişleri, eskiden yeniye. */
    public CompletableFuture<List<WorkOrderStatusHistory>> getStatusHistory(Long workOrderId) {
        return DbExecutor.supply(() -> DatabaseManager.inTransaction(handle ->
                handle.attach(WorkOrderStatusHistoryRepository.class).findByWorkOrderId(workOrderId)));
    }

    /**
     * Bir durum geçişinin tarihini düzeltir (ör. teslim alma tarihi = "Kabul Edildi" satırı).
     * Yeni tarih komşu geçişlerin arasında kalmalı; geçişlerin sırası değiştirilemez.
     */
    public CompletableFuture<Void> updateStatusDate(Long historyId, LocalDateTime changedAt) {
        if (historyId == null || changedAt == null) {
            throw new ValidationException("Geçmiş kaydı ve tarih boş olamaz.");
        }
        requireNotFuture(changedAt, LocalDateTime.now());

        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            WorkOrderStatusHistoryRepository history = handle.attach(WorkOrderStatusHistoryRepository.class);
            WorkOrderStatusHistory row = history.findById(historyId)
                    .orElseThrow(() -> new ValidationException("Durum geçmişi kaydı bulunamadı."));
            requireOpen(handle.attach(WorkOrderRepository.class), row.getWorkOrderId());

            List<WorkOrderStatusHistory> rows = history.findByWorkOrderId(row.getWorkOrderId());
            int index = -1;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).getId().equals(historyId)) {
                    index = i;
                    break;
                }
            }
            if (index > 0) {
                requireNotBefore(changedAt, rows.get(index - 1));
            }
            if (index >= 0 && index < rows.size() - 1) {
                WorkOrderStatusHistory next = rows.get(index + 1);
                if (changedAt.isAfter(next.getChangedAt())) {
                    throw new ValidationException("Tarih, sonraki durumun (" + next.getStatus().getDisplayName()
                            + ", " + Format.formatDate(next.getChangedAt()) + ") tarihinden sonra olamaz.");
                }
            }
            history.updateChangedAt(historyId, changedAt);
        }));
    }

    public CompletableFuture<Void> updateDetectedFault(Long id, String detectedFault) {
        if (id == null) {
            throw new ValidationException("ID boş olamaz.");
        }
        return DbExecutor.run(() -> {
            requireOpen(workOrderRepository, id);
            workOrderRepository.updateDetectedFault(id, detectedFault, LocalDateTime.now());
        });
    }

    // =========================================================================
    // KİLİT VE TARİH KURALLARI
    // =========================================================================

    /**
     * Kayıt kapalıysa (teslim edildi / iade) {@link ValidationException} fırlatır; açıksa mevcut durumu döner.
     * Ödemeler bu kilidin dışındadır: teslimden sonra tahsilat yapılabilir.
     */
    private static ServiceStatus requireOpen(WorkOrderRepository repo, Long workOrderId) {
        ServiceStatus status = repo.findStatus(workOrderId)
                .orElseThrow(() -> new ValidationException("Servis kaydı bulunamadı."));
        if (status.isClosed()) {
            throw new ValidationException("Teslim edilmiş ya da iade edilmiş servis kaydı değiştirilemez.");
        }
        return status;
    }

    private static void requireNotFuture(LocalDateTime at, LocalDateTime now) {
        if (at.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw new ValidationException("Durum tarihi gelecekte olamaz.");
        }
    }

    private static void requireNotBefore(LocalDateTime at, WorkOrderStatusHistory previous) {
        if (at.isBefore(previous.getChangedAt())) {
            throw new ValidationException("Tarih, önceki durumun (" + previous.getStatus().getDisplayName()
                    + ", " + Format.formatDate(previous.getChangedAt()) + ") tarihinden önce olamaz.");
        }
    }

    /**
     * Servis kaydının temel alanlarını doğrular.
     * Bu kontroller async zincire girmeden önce, çağıran thread'de senkron çalışır;
     * böylece hata mesajı hemen fırlatılır.
     */
    private void validateWorkOrder(WorkOrder workOrder) {
        if (workOrder.getCustomerId() == null || workOrder.getCustomerId() <= 0) {
            throw new ValidationException("Servis kaydı için bir müşteri seçilmiş olmalıdır.");
        }
        // deviceId dolu ama geçersizse hata ver
        if (workOrder.getDeviceId() != null && workOrder.getDeviceId() <= 0) {
            throw new ValidationException("Geçersiz cihaz ID'si.");
        }
        // Ne deviceId ne de device nesnesi yoksa hata ver
        if (workOrder.getDeviceId() == null && workOrder.getDevice() == null) {
            throw new ValidationException("Servis kaydı için bir cihaz girilmiş olmalıdır.");
        }
    }

    // =========================================================================
    // OKUMA İŞLEMLERİ
    // =========================================================================

    /** Servisi siler; kullanılan parçalar stoğa döner. */
    public CompletableFuture<Void> delete(Long id) {
        return delete(id, true);
    }

    /**
     * @param restoreStock true → servisin kullandığı parçalar çıktıkları depoya geri girer.
     *                     false → tüketim defterde "Servis #id" olarak kalır (parça gerçekten kullanıldı).
     */
    public CompletableFuture<Void> delete(Long id, boolean restoreStock) {
        // İş emri hard-delete edildiği için ödeme tahsislerini önce elle temizlemek gerekir
        // (payment_allocations polimorfik olduğundan ON DELETE CASCADE ile bağlanamıyor) —
        // aksi halde yetim tahsis satırları kalır ve cari bakiye şişer.
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            if (restoreStock) syncStock(handle, id, true);
            paymentService.releaseAllocationsForTarget(handle, AllocationTargetType.WORK_ORDER, id);
            handle.attach(WorkOrderRepository.class).delete(id);
        }));
    }

    public CompletableFuture<Optional<WorkOrder>> get(Long id) {
        return DbExecutor.supply(() -> {
            Optional<WorkOrder> opt = workOrderRepository.findById(id);
            opt.ifPresent(s -> hydrateServices(Collections.singletonList(s)));
            return opt;
        });
    }

    public CompletableFuture<List<WorkOrder>> getAll() {
        return DbExecutor.supply(() -> hydrateServices(workOrderRepository.findAll()));
    }

    public CompletableFuture<List<WorkOrder>> getAllSoft() {
        return DbExecutor.supply(workOrderRepository::findAll);
    }

    public CompletableFuture<List<WorkOrder>> getAll(Long customerId) {
        return DbExecutor.supply(() -> hydrateServices(workOrderRepository.findByCustomerId(customerId)));
    }

    public CompletableFuture<List<WorkOrder>> getAllByDevice(Long deviceId) {
        return DbExecutor.supply(() -> hydrateServices(workOrderRepository.findByDeviceId(deviceId)));
    }

    public CompletableFuture<List<WorkOrder>> getAllByPart(Long partId) {
        return DbExecutor.supply(() -> hydrateServices(workOrderRepository.findByPartId(partId)));
    }

    public CompletableFuture<List<WorkOrder>> getAll(String statusStr) {
        if (statusStr == null || statusStr.isEmpty() || statusStr.equalsIgnoreCase("ALL")) {
            return getAll();
        }
        if (statusStr.equalsIgnoreCase("OPEN")) {
            return DbExecutor.supply(() -> {
                List<ServiceStatus> closed = Arrays.asList(ServiceStatus.DELIVERED, ServiceStatus.RETURN);
                return hydrateServices(workOrderRepository.findByStatusesExcluded(closed));
            });
        }
        return DbExecutor.supply(() -> {
            ServiceStatus status = ServiceStatus.of(statusStr);
            return hydrateServices(workOrderRepository.findByStatuses(Collections.singletonList(status)));
        });
    }

    public CompletableFuture<List<WorkOrder>> getServicesWithDebt() {
        return getAll().thenApply(services -> services.stream()
                .filter(s -> s.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList()));
    }

    /**
     * Ana sayfadaki servis hattı: açık iş emirlerinin duruma göre adetleri.
     * Hiç kaydı olmayan açık durumlar da 0 ile döner; sıra {@link ServiceStatus} sırasıdır.
     */
    public CompletableFuture<Map<ServiceStatus, Long>> getOpenStatusCounts() {
        return DbExecutor.supply(() -> {
            Map<ServiceStatus, Long> counts = new EnumMap<>(ServiceStatus.class);
            for (ServiceStatus status : ServiceStatus.values()) {
                if (!status.isClosed()) counts.put(status, 0L);
            }
            workOrderRepository.countOpenGroupedByStatus().forEach(row -> {
                if (row.getLabel() == null || row.getValue() == null) return;
                counts.put(ServiceStatus.of(row.getLabel()), row.getValue().longValue());
            });
            return counts;
        });
    }

    /** Verilen gün açılan iş emri sayısı. */
    public CompletableFuture<Long> countCreatedOn(java.time.LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        return DbExecutor.supply(() -> workOrderRepository.countCreatedBetween(start, start.plusDays(1)));
    }

    public CompletableFuture<Void> setDelivered(Long serviceId) {
        return updateStatus(serviceId, ServiceStatus.DELIVERED);
    }

    public CompletableFuture<List<WorkOrder>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAll();
        return DbExecutor.supply(
                () -> hydrateServices(workOrderRepository.search("%" + searchTerm.trim() + "%")));
    }

    // =========================================================================
    // SAYFALAMA (LIMIT/OFFSET) — DB-tabanlı liste ekranları ve dashboard için
    // =========================================================================

    public CompletableFuture<PageResult<WorkOrder>> getAllPaged(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return DbExecutor.supply(() -> {
            List<WorkOrder> items = hydrateServices(workOrderRepository.findAllPaged(pageSize, offset));
            long total = workOrderRepository.countAll();
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    public CompletableFuture<PageResult<WorkOrder>> getAllPaged(int page, int pageSize, String statusStr) {
        if (statusStr == null || statusStr.isEmpty() || statusStr.equalsIgnoreCase("ALL")) {
            return getAllPaged(page, pageSize);
        }
        int offset = (page - 1) * pageSize;
        if (statusStr.equalsIgnoreCase("OPEN")) {
            return getOpenPaged(page, pageSize);
        }
        ServiceStatus status = ServiceStatus.of(statusStr);
        List<ServiceStatus> statuses = Collections.singletonList(status);
        return DbExecutor.supply(() -> {
            List<WorkOrder> items = hydrateServices(workOrderRepository.findByStatusesPaged(statuses, pageSize, offset));
            long total = workOrderRepository.countByStatuses(statuses);
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    public CompletableFuture<PageResult<WorkOrder>> getOpenPaged(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<ServiceStatus> closed = Arrays.asList(ServiceStatus.DELIVERED, ServiceStatus.RETURN);
        return DbExecutor.supply(() -> {
            List<WorkOrder> items = hydrateServices(workOrderRepository.findByStatusesExcludedPaged(closed, pageSize, offset));
            long total = workOrderRepository.countByStatusesExcluded(closed);
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    public CompletableFuture<PageResult<WorkOrder>> getWithDebtPaged(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return DbExecutor.supply(() -> {
            List<WorkOrder> items = hydrateServices(workOrderRepository.findWithDebtPaged(pageSize, offset));
            long total = workOrderRepository.countWithDebt();
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    public CompletableFuture<PageResult<WorkOrder>> searchPaged(String searchTerm, int page, int pageSize) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAllPaged(page, pageSize);
        int offset = (page - 1) * pageSize;
        String likeTerm = "%" + searchTerm.trim() + "%";
        return DbExecutor.supply(() -> {
            List<WorkOrder> items = hydrateServices(workOrderRepository.searchPaged(likeTerm, pageSize, offset));
            long total = workOrderRepository.countSearch(likeTerm);
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    /** Arama terimi ile durum filtresini (combo) aynı anda uygular. */
    public CompletableFuture<PageResult<WorkOrder>> searchPaged(String searchTerm, int page, int pageSize, String statusStr) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAllPaged(page, pageSize, statusStr);
        if (statusStr == null || statusStr.isEmpty() || statusStr.equalsIgnoreCase("ALL")) return searchPaged(searchTerm, page, pageSize);

        int offset = (page - 1) * pageSize;
        String likeTerm = "%" + searchTerm.trim() + "%";

        if (statusStr.equalsIgnoreCase("OPEN")) {
            List<ServiceStatus> closed = Arrays.asList(ServiceStatus.DELIVERED, ServiceStatus.RETURN);
            return DbExecutor.supply(() -> {
                List<WorkOrder> items = hydrateServices(workOrderRepository.searchPagedByStatusesExcluded(likeTerm, closed, pageSize, offset));
                long total = workOrderRepository.countSearchByStatusesExcluded(likeTerm, closed);
                return new PageResult<>(items, page, pageSize, total);
            });
        }

        ServiceStatus status = ServiceStatus.of(statusStr);
        List<ServiceStatus> statuses = Collections.singletonList(status);
        return DbExecutor.supply(() -> {
            List<WorkOrder> items = hydrateServices(workOrderRepository.searchPagedByStatuses(likeTerm, statuses, pageSize, offset));
            long total = workOrderRepository.countSearchByStatuses(likeTerm, statuses);
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    /** Tablo başlığı filtresi (durum/tarih vb.) + serbest metin arama — sunucu tarafında, tüm kayıtlar üzerinde. */
    public CompletableFuture<PageResult<WorkOrder>> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                                        int page, int pageSize) {
        return searchFilteredPaged(searchTerm, filters, page, pageSize, WorkOrderRepository.Sort.NEWEST, WorkOrderRepository.PayFilter.ALL);
    }

    public CompletableFuture<PageResult<WorkOrder>> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                                        int page, int pageSize, WorkOrderRepository.Sort sort,
                                                                        WorkOrderRepository.PayFilter pay) {
        return DbExecutor.supply(() -> {
            PageResult<WorkOrder> result = workOrderRepository.searchFilteredPaged(searchTerm, filters, page, pageSize, sort, pay);
            hydrateServices(result.getItems());
            return result;
        });
    }

    // =========================================================================
    // HYDRATION
    // =========================================================================

    private List<WorkOrder> hydrateServices(List<WorkOrder> workOrders) {
        if (workOrders == null || workOrders.isEmpty()) return workOrders;

        List<Long> customerIds = workOrders.stream()
                .map(WorkOrder::getCustomerId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());

        List<Long> deviceIds = workOrders.stream()
                .map(WorkOrder::getDeviceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<Long> ids = workOrders.stream().map(WorkOrder::getId).distinct().collect(Collectors.toList());

        // Satır başına sorgu yerine her ilişki için tek (parçalı) IN sorgusu: 50 satırlık sayfada
        // 150+ sorgu yerine 5. Doğrudan repository çağrılır; başka bir future'ı join ile beklemek
        // DB havuzunda thread tutuyordu.
        Map<Long, Customer> customerMap = inChunks(customerIds, customerRepository::findByIds).stream()
                .collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));
        Map<Long, Device> deviceMap = inChunks(deviceIds, deviceRepository::findByIds).stream()
                .collect(Collectors.toMap(Device::getId, d -> d, (a, b) -> a));
        Map<Long, List<WorkOrderItem>> itemMap = inChunks(ids, itemRepository::findByServiceIds).stream()
                .collect(Collectors.groupingBy(WorkOrderItem::getServiceId));
        Map<Long, List<Payment>> paymentMap = inChunks(ids,
                chunk -> paymentRepository.findByTargets(AllocationTargetType.WORK_ORDER, chunk)).stream()
                .collect(Collectors.groupingBy(TargetPayment::getTargetId,
                        Collectors.mapping(p -> (Payment) p, Collectors.toList())));
        Map<Long, List<WorkOrderNote>> noteMap = inChunks(ids, noteRepository::findByServiceIds).stream()
                .collect(Collectors.groupingBy(WorkOrderNote::getServiceId));

        for (WorkOrder s : workOrders) {
            s.setCustomer(customerMap.get(s.getCustomerId()));
            s.setDevice(deviceMap.get(s.getDeviceId()));
            s.setItems(new ArrayList<>(itemMap.getOrDefault(s.getId(), List.of())));
            s.setPayments(new ArrayList<>(paymentMap.getOrDefault(s.getId(), List.of())));
            s.setTechnicianNotes(new ArrayList<>(noteMap.getOrDefault(s.getId(), List.of())));
        }

        return workOrders;
    }

    /** SQLite'ın bağlama değişkeni sınırına takılmamak için IN listesini parçalara bölerek sorgular. */
    private static <T> List<T> inChunks(List<Long> ids, java.util.function.Function<List<Long>, List<T>> query) {
        if (ids.isEmpty()) return List.of();
        List<T> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += IN_CHUNK) {
            result.addAll(query.apply(ids.subList(i, Math.min(ids.size(), i + IN_CHUNK))));
        }
        return result;
    }

    // =========================================================================
    // ITEM
    // =========================================================================

    // Kalem yazımı ve stok hareketi tek transaction'dadır: stok yetmezse kalem de kaydedilmez.
    // Eskiden kalem önce kaydedilip stok ayrı adımda düşülüyordu; yetersiz stokta kalem kalıyor,
    // stok düşmüyordu.

    public CompletableFuture<WorkOrderItem> addItem(WorkOrderItem item) {
        return DbExecutor.supply(() -> DatabaseManager.inTransaction(handle -> {
            requireOpen(handle.attach(WorkOrderRepository.class), item.getServiceId());
            item.setWarehouseId(isStockPart(item)
                    ? stockService.resolveWarehouse(handle, item.getWarehouseId(), true) : null);
            ServiceItemRepository items = handle.attach(ServiceItemRepository.class);
            item.setId(items.insert(item));
            syncStock(handle, item.getServiceId(), false);
            return item;
        }));
    }

    public CompletableFuture<Void> updateItem(WorkOrderItem updatedItem) {
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            ServiceItemRepository items = handle.attach(ServiceItemRepository.class);
            WorkOrderItem oldItem = items.findById(updatedItem.getId())
                    .orElseThrow(() -> new ValidationException("Güncellenecek kalem bulunamadı."));
            requireOpen(handle.attach(WorkOrderRepository.class), oldItem.getServiceId());
            if (isStockPart(updatedItem)) {
                // Depo değişmediyse pasif olması engel değil (eski kalemin adedi düzeltilebilsin).
                boolean warehouseChanged = updatedItem.getWarehouseId() != null
                        && !updatedItem.getWarehouseId().equals(oldItem.getWarehouseId());
                Long requested = updatedItem.getWarehouseId() != null ? updatedItem.getWarehouseId() : oldItem.getWarehouseId();
                updatedItem.setWarehouseId(stockService.resolveWarehouse(handle, requested, warehouseChanged));
            } else {
                updatedItem.setWarehouseId(null);
            }
            items.update(updatedItem);
            syncStock(handle, oldItem.getServiceId(), false);
        }));
    }

    /**
     * @param restoreStock true → parça çıktığı depoya geri girer. false → parça kullanılmış/hasar görmüş
     *                     sayılır: servisten dönüş + fire/kayıp hareketi yazılır (stok değişmez, iz kalır).
     */
    public CompletableFuture<Void> deleteItem(Long itemId, boolean restoreStock) {
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            ServiceItemRepository items = handle.attach(ServiceItemRepository.class);
            WorkOrderItem item = items.findById(itemId)
                    .orElseThrow(() -> new ValidationException("Silinecek kalem bulunamadı."));
            requireOpen(handle.attach(WorkOrderRepository.class), item.getServiceId());
            items.delete(itemId);
            syncStock(handle, item.getServiceId(), false);

            boolean consumed = isStockPart(item) && handle.attach(WorkOrderRepository.class)
                    .findStatus(item.getServiceId()).orElse(null) != ServiceStatus.RETURN;
            if (!restoreStock && consumed) {
                Long warehouseId = stockService.resolveWarehouse(handle, item.getWarehouseId(), false);
                stockService.record(handle, StockItemKind.PART, item.getPartId(), warehouseId, -item.getQuantity(),
                        ReferenceType.LOSS, item.getServiceId(), null,
                        "Servis #" + item.getServiceId() + " kaleminden çıkarıldı, stoğa dönmedi");
            }
        }));
    }

    public CompletableFuture<List<WorkOrderItem>> getItems(Long serviceId) {
        return DbExecutor.supply(() -> itemRepository.findByServiceId(serviceId));
    }

    // =========================================================================
    // NOTE
    // =========================================================================

    public CompletableFuture<WorkOrderNote> addNote(WorkOrderNote note) {
        return DbExecutor.supply(() -> {
            requireOpen(workOrderRepository, note.getServiceId());
            Long id = noteRepository.insert(note);
            note.setId(id);
            return note;
        });
    }

    public CompletableFuture<Void> deleteNote(Long id) {
        return DbExecutor.run(() -> {
            noteRepository.findServiceId(id).ifPresent(serviceId -> requireOpen(workOrderRepository, serviceId));
            noteRepository.delete(id);
        });
    }

    public CompletableFuture<List<WorkOrderNote>> getNotes(Long serviceId) {
        return DbExecutor.supply(() -> noteRepository.findByServiceId(serviceId));
    }

    // =========================================================================
    // PAYMENT
    // =========================================================================

    /** İş emrinin tamamına (bölünmeden) tahsis edilen basit ödeme — WorkOrderPaymentsPanel akışı. */
    public CompletableFuture<Payment> addPayment(Long workOrderId, Long customerId, java.math.BigDecimal amount,
                                                  PaymentType paymentType, String note, java.time.LocalDateTime paymentDate) {
        return paymentService.recordPaymentForTarget(AllocationTargetType.WORK_ORDER, workOrderId, customerId,
                amount, paymentType, note, paymentDate);
    }

    /** Dikkat: parametre {@code paymentId}'dir, {@code serviceId} DEĞİL (eski WorkOrderPayment API'sinden farkı). */
    public CompletableFuture<Void> deletePayment(Long paymentId) {
        return paymentService.deletePayment(paymentId);
    }

    public CompletableFuture<List<Payment>> getPayments(Long serviceId) {
        return paymentService.getPaymentsForTarget(AllocationTargetType.WORK_ORDER, serviceId);
    }

    // =========================================================================
    // STOK EŞİTLEME
    // =========================================================================

    private static boolean isStockPart(WorkOrderItem item) {
        return item.getItemType() == ItemType.PART && item.getPartId() != null;
    }

    private record StockKey(Long partId, Long warehouseId) {}

    /**
     * Servisin defterdeki net parça tüketimini kalemleriyle eşitler (çağıranın transaction'ında).
     * Olması gereken: parça kalemlerinin adetleri, depo bazında; servis "İade" durumundaysa ya da
     * {@code releaseAll} ise hiçbiri. Fark kadar hareket yazılır — önce dönüşler, sonra çıkışlar,
     * böylece adet azaltma/depo değiştirme önce stoğu serbest bırakır. Çıkışta depo yetmezse
     * {@link ValidationException} ile tüm işlem geri alınır.
     * <p>
     * Kalem bazında +/- yazmak yerine eşitleme: adet, parça, depo, durum değişiklikleri ve silme
     * aynı yoldan geçer; defter servisle her zaman tutarlı kalır.
     */
    private void syncStock(Handle handle, Long workOrderId, boolean releaseAll) {
        Map<StockKey, Integer> desired = new HashMap<>();
        boolean returned = handle.attach(WorkOrderRepository.class).findStatus(workOrderId).orElse(null) == ServiceStatus.RETURN;
        if (!releaseAll && !returned) {
            Long defaultWarehouse = null;
            for (WorkOrderItem item : handle.attach(ServiceItemRepository.class).findByServiceId(workOrderId)) {
                if (!isStockPart(item) || item.getQuantity() == null || item.getQuantity() <= 0) continue;
                Long warehouseId = item.getWarehouseId();
                if (warehouseId == null) {
                    if (defaultWarehouse == null) defaultWarehouse = stockService.resolveWarehouse(handle, null, false);
                    warehouseId = defaultWarehouse;
                }
                desired.merge(new StockKey(item.getPartId(), warehouseId), item.getQuantity(), Integer::sum);
            }
        }

        Map<StockKey, Integer> actual = new HashMap<>();
        for (StockLevel level : handle.attach(StockLedgerRepository.class).findWorkOrderConsumption(workOrderId)) {
            actual.put(new StockKey(level.getItemId(), level.getWarehouseId()), level.getQuantity());
        }

        Set<StockKey> keys = new LinkedHashSet<>(actual.keySet());
        keys.addAll(desired.keySet());

        // Önce stoğa dönenler
        for (StockKey key : keys) {
            int diff = desired.getOrDefault(key, 0) - actual.getOrDefault(key, 0);
            if (diff < 0) {
                stockService.record(handle, StockItemKind.PART, key.partId(), key.warehouseId(), -diff,
                        ReferenceType.WORK_ORDER_CANCEL, workOrderId, null, null);
            }
        }
        // Sonra servise çıkanlar
        for (StockKey key : keys) {
            int diff = desired.getOrDefault(key, 0) - actual.getOrDefault(key, 0);
            if (diff > 0) {
                stockService.requireAvailable(handle, StockItemKind.PART, key.partId(), key.warehouseId(), diff);
                stockService.record(handle, StockItemKind.PART, key.partId(), key.warehouseId(), -diff,
                        ReferenceType.WORK_ORDER, workOrderId, null, null);
            }
        }
    }
}
