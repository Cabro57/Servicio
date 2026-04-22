package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.CustomerRepository;
import tr.cabro.servicio.database.repository.DeviceRepository;
import tr.cabro.servicio.database.repository.ServiceItemRepository;
import tr.cabro.servicio.database.repository.ServicePaymentRepository;
import tr.cabro.servicio.database.repository.ServiceRepository;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.service.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RepairService {

    private final ServiceRepository serviceRepository;
    private final CustomerRepository customerRepo;
    private final DeviceRepository deviceRepo;
    private final ServiceItemRepository itemRepo;
    private final ServicePaymentRepository paymentRepo;

    public RepairService(ServiceRepository serviceRepository,
                         CustomerRepository customerRepo,
                         DeviceRepository deviceRepo,
                         ServiceItemRepository itemRepo,
                         ServicePaymentRepository paymentRepo) {
        this.serviceRepository = serviceRepository;
        this.customerRepo = customerRepo;
        this.deviceRepo = deviceRepo;
        this.itemRepo = itemRepo;
        this.paymentRepo = paymentRepo;
    }

    // =========================================================================
    // SERVICE CRUD
    // =========================================================================

    public CompletableFuture<Service> save(Service service, boolean update) {
        if (service.getDeviceId() <= 0) {
            throw new ValidationException("Servis için bir cihaz seçilmiş olmalıdır.");
        }

        return CompletableFuture.supplyAsync(() -> {
            if (!update) {
                int id = serviceRepository.insertService(service);
                service.setId(id);
            } else {
                serviceRepository.updateService(service);
            }
            return service;
        });
    }

    public CompletableFuture<Void> delete(int id) {
        return CompletableFuture.runAsync(() -> serviceRepository.deleteById(id));
    }

    public CompletableFuture<Optional<Service>> get(int id) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Service> optService = serviceRepository.findById(id);
            optService.ifPresent(s -> hydrateServices(Collections.singletonList(s)));
            return optService;
        });
    }

    public CompletableFuture<List<Service>> getAll() {
        return CompletableFuture.supplyAsync(() -> hydrateServices(serviceRepository.findAll()));
    }

    public CompletableFuture<List<Service>> getAll(int customerId) {
        return CompletableFuture.supplyAsync(() -> hydrateServices(serviceRepository.findByCustomerId(customerId)));
    }

    public CompletableFuture<List<Service>> getAllByDevice(int deviceId) {
        return CompletableFuture.supplyAsync(() -> hydrateServices(serviceRepository.findByDeviceId(deviceId)));
    }

    public CompletableFuture<List<Service>> getAll(String statusStr) {
        if (statusStr == null || statusStr.isEmpty() || statusStr.equalsIgnoreCase("ALL")) {
            return getAll();
        }

        if (statusStr.equalsIgnoreCase("OPEN")) {
            return CompletableFuture.supplyAsync(() -> {
                List<ServiceStatus> closedStatuses = Arrays.asList(ServiceStatus.DELIVERED, ServiceStatus.RETURN);
                return hydrateServices(serviceRepository.findByStatusesExcluded(closedStatuses));
            });
        }

        return CompletableFuture.supplyAsync(() -> {
            ServiceStatus status = ServiceStatus.of(statusStr);
            return hydrateServices(serviceRepository.findByStatuses(Collections.singletonList(status)));
        });
    }

    public CompletableFuture<List<Service>> getServicesWithDebt() {
        return getAll().thenApply(services -> services.stream()
                .filter(s -> s.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList()));
    }

    public CompletableFuture<Void> setDelivered(int serviceId) {
        return CompletableFuture.runAsync(() -> {
            Optional<Service> opt = serviceRepository.findById(serviceId);
            if (opt.isPresent()) {
                Service service = opt.get();
                service.setServiceStatus(ServiceStatus.DELIVERED);
                if (service.getDeliveryDate() == null) {
                    service.setDeliveryDate(LocalDateTime.now());
                }
                serviceRepository.updateService(service);
            } else {
                throw new ValidationException("Servis bulunamadı ID: " + serviceId);
            }
        });
    }

    public CompletableFuture<List<Service>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAll();
        return CompletableFuture.supplyAsync(
                () -> hydrateServices(serviceRepository.search("%" + searchTerm.trim() + "%")));
    }

    // =========================================================================
    // DEVICE COUNT
    // =========================================================================

    public CompletableFuture<Map<Integer, Integer>> getDeviceCountsByCustomerIds(List<Integer> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
        // DÜZELTME: Artık ServiceRepository değil, mantıken DeviceRepository üzerinden çekiyoruz
        return CompletableFuture.supplyAsync(() -> deviceRepo.getDeviceCountsByCustomerIds(customerIds));
    }


    // =========================================================================
    // HYDRATION — Servisleri ilişkisel verilerle doldur
    // =========================================================================

    private List<Service> hydrateServices(List<Service> services) {
        if (services == null || services.isEmpty()) return services;

        List<Integer> customerIds = services.stream()
                .map(Service::getCustomerId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());

        List<Integer> deviceIds = services.stream()
                .map(Service::getDeviceId)
                .distinct()
                .collect(Collectors.toList());

        // 1. Müşterileri tek sorguda çek
        Map<Integer, Customer> customerMap = customerIds.isEmpty()
                ? Collections.emptyMap()
                : customerRepo.findByIds(customerIds).stream()
                  .collect(Collectors.toMap(Customer::getId, c -> c));

        // 2. Cihazları çek
        Map<Integer, Device> deviceMap = new HashMap<>();
        for (int deviceId : deviceIds) {
            deviceRepo.findById(deviceId).ifPresent(d -> deviceMap.put(d.getId(), d));
        }

        // 3. ServiceItems ve Payments yükle (itemsTotalMap tamamen SİLİNDİ)
        for (Service s : services) {
            s.setCustomer(customerMap.get(s.getCustomerId()));
            s.setDevice(deviceMap.get(s.getDeviceId()));

            s.setItems(itemRepo.findByServiceId(s.getId()));
            s.setPayments(paymentRepo.findPaymentsByServiceId(s.getId()));
        }

        return services;
    }
}