package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.*;
import tr.cabro.servicio.model.*;
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

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            ServiceItemRepository itemRepository,
                            ServicePaymentRepository paymentRepo,
                            ServiceNoteRepository noteRepository) {
        this.workOrderRepository = workOrderRepository;
        this.itemRepository = itemRepository;
        this.paymentRepository = paymentRepo;
        this.noteRepository = noteRepository;
    }

    // =========================================================================
    // SERVICE CRUD
    // =========================================================================

    public CompletableFuture<WorkOrder> save(WorkOrder workOrder, boolean update) {
        if (workOrder.getDeviceId() <= 0) {
            throw new ValidationException("Servis için bir cihaz seçilmiş olmalıdır.");
        }

        return CompletableFuture.supplyAsync(() -> {
            if (!update) {
                Long id = workOrderRepository.insert(workOrder);
                workOrder.setId(id);
            } else {
                workOrderRepository.update(workOrder);
            }
            return workOrder;
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return CompletableFuture.runAsync(() -> workOrderRepository.delete(id));
    }

    public CompletableFuture<Optional<WorkOrder>> get(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<WorkOrder> optService = workOrderRepository.findById(id);
            optService.ifPresent(s -> hydrateServices(Collections.singletonList(s)));
            return optService;
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
                List<ServiceStatus> closedStatuses = Arrays.asList(ServiceStatus.DELIVERED, ServiceStatus.RETURN);
                return hydrateServices(workOrderRepository.findByStatusesExcluded(closedStatuses));
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
        return CompletableFuture.runAsync(() -> {
            Optional<WorkOrder> opt = workOrderRepository.findById(serviceId);
            if (opt.isPresent()) {
                WorkOrder workOrder = opt.get();
                workOrder.setServiceStatus(ServiceStatus.DELIVERED);
                if (workOrder.getDeliveryDate() == null) {
                    workOrder.setDeliveryDate(LocalDateTime.now());
                }
                workOrderRepository.update(workOrder);
            } else {
                throw new ValidationException("Servis bulunamadı ID: " + serviceId);
            }
        });
    }

    public CompletableFuture<List<WorkOrder>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAll();
        return CompletableFuture.supplyAsync(
                () -> hydrateServices(workOrderRepository.search("%" + searchTerm.trim() + "%")));
    }

    // =========================================================================
    // DEVICE COUNT TODO: Bu işlemi daha optimize şekilde yazalım.
    // =========================================================================

//    public CompletableFuture<Map<Long, Integer>> getDeviceCountsByCustomerIds(List<Long> customerIds) {
//        if (customerIds == null || customerIds.isEmpty()) {
//            return CompletableFuture.completedFuture(Collections.emptyMap());
//        }
//        // DÜZELTME: Artık WorkOrderRepository değil, mantıken DeviceRepository üzerinden çekiyoruz
//        return CompletableFuture.supplyAsync(() -> deviceRepo.getDeviceCountsByCustomerIds(customerIds));
//    }


    // =========================================================================
    // HYDRATION — Servisleri ilişkisel verilerle doldur
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
                .distinct()
                .collect(Collectors.toList());

        CustomerService customerService = ServiceManager.getCustomerService();
        Map<Long, Customer> customerMap = customerIds.isEmpty()
                ? Collections.emptyMap()
                : customerService.getAll(customerIds).join().stream()
                  .collect(Collectors.toMap(Customer::getId, c -> c));

        // 2. Cihazları çek
        DeviceService deviceService = ServiceManager.getDeviceService();
        Map<Long, Device> deviceMap = deviceIds.isEmpty()
                ? Collections.emptyMap()
                : deviceService.getAll(deviceIds).join().stream()
                  .collect(Collectors.toMap(Device::getId, d -> d));

        // 3. ServiceItems ve Payments yükle (itemsTotalMap tamamen SİLİNDİ)
        for (WorkOrder s : workOrders) {
            s.setCustomer(customerMap.get(s.getCustomerId()));
            s.setDevice(deviceMap.get(s.getDeviceId()));

            s.setItems(itemRepository.findByServiceId(s.getId()));
            s.setPayments(paymentRepository.findPaymentsByServiceId(s.getId()));
        }

        return workOrders;
    }

    // ITEM methods
    public CompletableFuture<WorkOrderItem> addItem(WorkOrderItem item) {
        return CompletableFuture.supplyAsync(() -> {
            Long id = itemRepository.insert(item);
            item.setId(id);
            return item;
        });
    }

    public CompletableFuture<Void> updateItem(WorkOrderItem item) {
        return CompletableFuture.runAsync(() -> {
            itemRepository.update(item);
        });
    }

    // TODO silme işleminde stok a geri ekleme işlemi olup olmayacağı işlemleri servis katmanı üzerinden çek
    public CompletableFuture<Void> deleteItem(Long id) {
        return CompletableFuture.runAsync(() -> {

        });
    }

    public CompletableFuture<List<WorkOrderItem>> getItems(Long serviceId) {
        return CompletableFuture.supplyAsync(() -> itemRepository.findByServiceId(serviceId));
    }

    // Note Methods
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

    // Payment Methods
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
}