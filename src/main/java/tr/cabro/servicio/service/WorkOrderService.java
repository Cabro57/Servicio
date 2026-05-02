package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.*;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.ReferenceType;
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
    private final ServicePaymentRepository paymentRepository;
    private final ServiceNoteRepository noteRepository;
    private final PartService partService;
    private final StockService stockService;
    private final DeviceService deviceService;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            ServiceItemRepository itemRepository,
                            ServicePaymentRepository paymentRepo,
                            ServiceNoteRepository noteRepository,
                            PartService partService,
                            StockService stockService,
                            DeviceService deviceService) {
        this.workOrderRepository = workOrderRepository;
        this.itemRepository = itemRepository;
        this.paymentRepository = paymentRepo;
        this.noteRepository = noteRepository;
        this.partService = partService;
        this.stockService = stockService;
        this.deviceService = deviceService;
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
        return CompletableFuture.supplyAsync(() -> {
            Long id = workOrderRepository.insert(workOrder);
            workOrder.setId(id);
            return workOrder;
        });
    }

    private CompletableFuture<WorkOrder> updateWorkOrder(WorkOrder workOrder) {
        return CompletableFuture.supplyAsync(() -> {
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

        return CompletableFuture.runAsync(() ->
                workOrderRepository.updateStatus(id, newStatus, deliveryDate, LocalDateTime.now())
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
        return CompletableFuture.runAsync(() -> workOrderRepository.delete(id));
    }

    public CompletableFuture<Optional<WorkOrder>> get(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<WorkOrder> opt = workOrderRepository.findById(id);
            opt.ifPresent(s -> hydrateServices(Collections.singletonList(s)));
            return opt;
        });
    }

    public CompletableFuture<List<WorkOrder>> getAll() {
        return CompletableFuture.supplyAsync(() -> hydrateServices(workOrderRepository.findAll()));
    }

    public CompletableFuture<List<WorkOrder>> getAllSoft() {
        return CompletableFuture.supplyAsync(workOrderRepository::findAll);
    }

    public CompletableFuture<List<WorkOrder>> getAll(Long customerId) {
        return CompletableFuture.supplyAsync(() -> hydrateServices(workOrderRepository.findByCustomerId(customerId)));
    }

    public CompletableFuture<List<WorkOrder>> getAllByDevice(Long deviceId) {
        return CompletableFuture.supplyAsync(() -> hydrateServices(workOrderRepository.findByDeviceId(deviceId)));
    }

    public CompletableFuture<List<WorkOrder>> getAll(String statusStr) {
        if (statusStr == null || statusStr.isEmpty() || statusStr.equalsIgnoreCase("ALL")) {
            return getAll();
        }
        if (statusStr.equalsIgnoreCase("OPEN")) {
            return CompletableFuture.supplyAsync(() -> {
                List<ServiceStatus> closed = Arrays.asList(ServiceStatus.DELIVERED, ServiceStatus.RETURN);
                return hydrateServices(workOrderRepository.findByStatusesExcluded(closed));
            });
        }
        return CompletableFuture.supplyAsync(() -> {
            ServiceStatus status = ServiceStatus.of(statusStr);
            return hydrateServices(workOrderRepository.findByStatuses(Collections.singletonList(status)));
        });
    }

    public CompletableFuture<List<WorkOrder>> getServicesWithDebt() {
        return getAll().thenApply(services -> services.stream()
                .filter(s -> s.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList()));
    }

    public CompletableFuture<Void> setDelivered(Long serviceId) {
        return updateStatus(serviceId, ServiceStatus.DELIVERED);
    }

    public CompletableFuture<List<WorkOrder>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAll();
        return CompletableFuture.supplyAsync(
                () -> hydrateServices(workOrderRepository.search("%" + searchTerm.trim() + "%")));
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

        CustomerService customerService = ServiceManager.getCustomerService();
        Map<Long, Customer> customerMap = customerIds.isEmpty()
                ? Collections.emptyMap()
                : customerService.getAll(customerIds).join().stream()
                  .collect(Collectors.toMap(Customer::getId, c -> c));

        Map<Long, Device> deviceMap = deviceIds.isEmpty()
                ? Collections.emptyMap()
                : deviceService.getAll(deviceIds).join().stream()
                  .collect(Collectors.toMap(Device::getId, d -> d));

        for (WorkOrder s : workOrders) {
            s.setCustomer(customerMap.get(s.getCustomerId()));
            s.setDevice(deviceMap.get(s.getDeviceId()));
            s.setItems(itemRepository.findByServiceId(s.getId()));
            s.setPayments(paymentRepository.findPaymentsByServiceId(s.getId()));
            s.setTechnicianNotes(noteRepository.findByServiceId(s.getId()));
        }

        return workOrders;
    }

    // =========================================================================
    // ITEM
    // =========================================================================

    public CompletableFuture<WorkOrderItem> addItem(WorkOrderItem item) {
        return CompletableFuture.supplyAsync(() -> {
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

    public CompletableFuture<Void> updateItem(WorkOrderItem updatedItem) {
        return CompletableFuture.runAsync(() -> {
            Optional<WorkOrderItem> oldItemOpt = itemRepository.findById(updatedItem.getId());
            if (!oldItemOpt.isPresent()) {
                throw new ValidationException("Güncellenecek item bulunamadı.");
            }
            WorkOrderItem oldItem = oldItemOpt.get();
            itemRepository.update(updatedItem);

            if (updatedItem.getItemType() == ItemType.PART) {
                boolean partChanged = !Objects.equals(oldItem.getPartId(), updatedItem.getPartId());
                boolean quantityChanged = !oldItem.getQuantity().equals(updatedItem.getQuantity());
                if (partChanged || quantityChanged) {
                    if (oldItem.getPartId() != null) restoreStockForItem(oldItem).join();
                    if (updatedItem.getPartId() != null) reduceStockForItem(updatedItem).join();
                }
            }
        });
    }

    public CompletableFuture<Void> deleteItem(Long itemId, boolean stockUpdate) {
        return CompletableFuture.runAsync(() -> {
            Optional<WorkOrderItem> itemOpt = itemRepository.findById(itemId);
            if (!itemOpt.isPresent()) {
                throw new ValidationException("Silinecek item bulunamadı.");
            }
            WorkOrderItem item = itemOpt.get();
            if (item.getItemType() == ItemType.PART && item.getPartId() != null && stockUpdate) {
                restoreStockForItem(item).join();
            }
            itemRepository.delete(itemId);
        });
    }

    public CompletableFuture<List<WorkOrderItem>> getItems(Long serviceId) {
        return CompletableFuture.supplyAsync(() -> itemRepository.findByServiceId(serviceId));
    }

    // =========================================================================
    // NOTE
    // =========================================================================

    public CompletableFuture<WorkOrderNote> addNote(WorkOrderNote note) {
        return CompletableFuture.supplyAsync(() -> {
            Long id = noteRepository.insert(note);
            note.setId(id);
            return note;
        });
    }

    public CompletableFuture<Void> deleteNote(Long id) {
        return CompletableFuture.runAsync(() -> noteRepository.delete(id));
    }

    public CompletableFuture<List<WorkOrderNote>> getNotes(Long serviceId) {
        return CompletableFuture.supplyAsync(() -> noteRepository.findByServiceId(serviceId));
    }

    // =========================================================================
    // PAYMENT
    // =========================================================================

    public CompletableFuture<WorkOrderPayment> addPayment(WorkOrderPayment payment) {
        return CompletableFuture.supplyAsync(() -> {
            Long id = paymentRepository.insertPayment(payment);
            payment.setId(id);
            return payment;
        });
    }

    public CompletableFuture<Void> deletePayment(Long serviceId) {
        return CompletableFuture.runAsync(() -> paymentRepository.deletePayment(serviceId));
    }

    public CompletableFuture<List<WorkOrderPayment>> getPayments(Long serviceId) {
        return CompletableFuture.supplyAsync(() -> paymentRepository.findPaymentsByServiceId(serviceId));
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