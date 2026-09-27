package tr.cabro.servicio.service;

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
import tr.cabro.servicio.service.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final ServiceItemRepository itemRepository;
    private final PaymentService paymentService;
    private final ServiceNoteRepository noteRepository;
    private final PartService partService;
    private final StockService stockService;
    private final DeviceService deviceService;
    private final CustomerRepository customerRepository;
    private final DeviceRepository deviceRepository;
    private final PaymentRepository paymentRepository;

    private static final int IN_CHUNK = 500;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            ServiceItemRepository itemRepository,
                            PaymentService paymentService,
                            ServiceNoteRepository noteRepository,
                            PartService partService,
                            StockService stockService,
                            DeviceService deviceService,
                            CustomerRepository customerRepository,
                            DeviceRepository deviceRepository,
                            PaymentRepository paymentRepository) {
        this.workOrderRepository = workOrderRepository;
        this.itemRepository = itemRepository;
        this.paymentService = paymentService;
        this.noteRepository = noteRepository;
        this.partService = partService;
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
        if (workOrder.getDevice() != null && workOrder.getDeviceId() != null) {
            // Cihazı güncelle, sonra servisi güncelle
            return deviceService.save(workOrder.getDevice(), true)
                    .thenCompose(updatedDevice -> updateWorkOrder(workOrder));
        } else {
            return updateWorkOrder(workOrder);
        }
    }

    private CompletableFuture<WorkOrder> insertWorkOrder(WorkOrder workOrder) {
        return DbExecutor.supply(() -> {
            Long id = workOrderRepository.insert(workOrder);
            workOrder.setId(id);
            return workOrder;
        });
    }

    private CompletableFuture<WorkOrder> updateWorkOrder(WorkOrder workOrder) {
        return DbExecutor.supply(() -> {
            workOrderRepository.update(workOrder);
            return workOrder;
        });
    }

    public CompletableFuture<Void> updateStatus(Long id, ServiceStatus newStatus) {
        if (id == null || newStatus == null) {
            throw new ValidationException("ID ve durum boş olamaz.");
        }

        LocalDateTime deliveryDate = (newStatus == ServiceStatus.DELIVERED || newStatus == ServiceStatus.RETURN)
                ? LocalDateTime.now()
                : null;

        return DbExecutor.run(() ->
                workOrderRepository.updateStatus(id, newStatus, deliveryDate, LocalDateTime.now())
        );
    }

    public CompletableFuture<Void> updateDetectedFault(Long id, String detectedFault) {
        if (id == null) {
            throw new ValidationException("ID boş olamaz.");
        }
        return DbExecutor.run(() ->
                workOrderRepository.updateDetectedFault(id, detectedFault, LocalDateTime.now())
        );
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

    public CompletableFuture<Void> delete(Long id) {
        // İş emri hard-delete edildiği için ödeme tahsislerini önce elle temizlemek gerekir
        // (payment_allocations polimorfik olduğundan ON DELETE CASCADE ile bağlanamıyor) —
        // aksi halde yetim tahsis satırları kalır ve cari bakiye şişer.
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
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
                if (status != ServiceStatus.DELIVERED && status != ServiceStatus.RETURN) counts.put(status, 0L);
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

    public CompletableFuture<WorkOrderItem> addItem(WorkOrderItem item) {
        return DbExecutor.supply(() -> {
            Long id = itemRepository.insert(item);
            item.setId(id);
            return item;
        }).thenCompose(savedItem -> {
            if (savedItem.getItemType() == ItemType.PART && savedItem.getPartId() != null) {
                return reduceStockForItem(savedItem).thenApply(v -> savedItem);
            }
            return CompletableFuture.completedFuture(savedItem);
        });
    }

    // Stok adımları join ile beklenmez, zincirlenir: DB thread'i başka bir DB işini beklerken
    // tutulursa sınırlı havuzda kilitlenme olur.
    public CompletableFuture<Void> updateItem(WorkOrderItem updatedItem) {
        return DbExecutor.supply(() -> {
            WorkOrderItem oldItem = itemRepository.findById(updatedItem.getId())
                    .orElseThrow(() -> new ValidationException("Güncellenecek item bulunamadı."));
            itemRepository.update(updatedItem);
            return oldItem;
        }).thenCompose(oldItem -> {
            if (updatedItem.getItemType() != ItemType.PART) return CompletableFuture.completedFuture(null);
            boolean partChanged = !Objects.equals(oldItem.getPartId(), updatedItem.getPartId());
            boolean quantityChanged = !oldItem.getQuantity().equals(updatedItem.getQuantity());
            if (!partChanged && !quantityChanged) return CompletableFuture.completedFuture(null);

            CompletableFuture<Void> restore = oldItem.getPartId() != null
                    ? restoreStockForItem(oldItem) : CompletableFuture.completedFuture(null);
            return restore.thenCompose(v -> updatedItem.getPartId() != null
                    ? reduceStockForItem(updatedItem) : CompletableFuture.completedFuture(null));
        });
    }

    public CompletableFuture<Void> deleteItem(Long itemId, boolean stockUpdate) {
        return DbExecutor.supply(() -> itemRepository.findById(itemId)
                .orElseThrow(() -> new ValidationException("Silinecek item bulunamadı.")))
                .thenCompose(item -> {
                    CompletableFuture<Void> restore = item.getItemType() == ItemType.PART && item.getPartId() != null && stockUpdate
                            ? restoreStockForItem(item) : CompletableFuture.completedFuture(null);
                    return restore.thenRun(() -> itemRepository.delete(itemId));
                });
    }

    public CompletableFuture<List<WorkOrderItem>> getItems(Long serviceId) {
        return DbExecutor.supply(() -> itemRepository.findByServiceId(serviceId));
    }

    // =========================================================================
    // NOTE
    // =========================================================================

    public CompletableFuture<WorkOrderNote> addNote(WorkOrderNote note) {
        return DbExecutor.supply(() -> {
            Long id = noteRepository.insert(note);
            note.setId(id);
            return note;
        });
    }

    public CompletableFuture<Void> deleteNote(Long id) {
        return DbExecutor.run(() -> noteRepository.delete(id));
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
    // STOK YARDIMCILARI
    // =========================================================================

    private CompletableFuture<Void> reduceStockForItem(WorkOrderItem item) {
        return partService.getById(item.getPartId()).thenCompose(partOpt -> {
            if (!partOpt.isPresent()) {
                throw new ValidationException("Parça bulunamadı: " + item.getPartId());
            }
            Part part = partOpt.get();
            if (part.getStockQuantity() < item.getQuantity()) {
                throw new ValidationException(
                        "Yetersiz stok! " + part.getName() +
                                " için mevcut: " + part.getStockQuantity() +
                                ", gerekli: " + item.getQuantity());
            }
            StockMovement movement = new StockMovement();
            movement.setPartId(item.getPartId());
            movement.setQuantity(item.getQuantity());
            movement.setReferenceType(ReferenceType.WORK_ORDER);
            movement.setReferenceId(item.getServiceId());
            return stockService.removeStock(movement);
        });
    }

    private CompletableFuture<Void> restoreStockForItem(WorkOrderItem item) {
        StockMovement movement = new StockMovement();
        movement.setPartId(item.getPartId());
        movement.setQuantity(item.getQuantity());
        movement.setReferenceType(ReferenceType.WORK_ORDER_CANCEL);
        movement.setReferenceId(item.getServiceId());
        return stockService.addStock(movement);
    }
}