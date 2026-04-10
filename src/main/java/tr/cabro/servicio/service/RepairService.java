package tr.cabro.servicio.service;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.CustomerRepository;
import tr.cabro.servicio.database.repository.PartRepository;
import tr.cabro.servicio.database.repository.ServiceRepository;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.dto.DashboardStats;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.enums.ServiceStatus;
import tr.cabro.servicio.reports.ServiceFinanceRecord;
import tr.cabro.servicio.reports.ServiceFinanceReport;
import tr.cabro.servicio.util.Validator;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RepairService {

    private final ServiceRepository repository;
    private final CustomerRepository customerRepo;

    public RepairService(ServiceRepository repository) {
        this.repository = repository;
        this.customerRepo = DatabaseManager.getJdbi().onDemand(CustomerRepository.class);
    }

    public CompletableFuture<Service> save(Service service, boolean update) {
        // --- Validasyon ---
        if (Validator.isEmpty(service.getDeviceBrand()) || Validator.isEmpty(service.getDeviceModel())) {
            throw new ValidationException("Cihaz marka ve modeli zorunludur.");
        }

        // İşçilik veya ödenen tutar negatif olamaz
        if (service.getLaborCost() < 0) throw new ValidationException("İşçilik ücreti negatif olamaz.");
        if (service.getPaid() < 0) throw new ValidationException("Ödenen tutar negatif olamaz.");

        // Transactional Kayıt (Repository içindeki @Transaction metodu çağrılır)
        return CompletableFuture.supplyAsync(() -> {
            if (!update) {
                int id = repository.insertService(service);
                service.setId(id);
            } else {
                repository.updateService(service);
            }
            return service;
        });
    }

    public CompletableFuture<Void> delete(int id) {
        // Eğer veritabanında ON DELETE CASCADE tanımlı değilse,
        // önce parçaları silmek gerekebilir.
        // repository.deletePartsByServiceId(id);
        return CompletableFuture.runAsync(() -> repository.deleteService(id));
    }

    public CompletableFuture<Optional<Service>> get(int id) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Service> optService = repository.findById(id);
            optService.ifPresent(s -> hydrateServices(Collections.singletonList(s)));
            return optService;
        });
    }

    public CompletableFuture<List<Service>> getAll() {
        return CompletableFuture.supplyAsync(() -> hydrateServices(repository.findServices()));
    }

    public CompletableFuture<List<Service>> getAll(int customerId) {
        return CompletableFuture.supplyAsync(() -> hydrateServices(repository.findByCustomerId(customerId)));
    }

    // Duruma göre filtreleme
    public CompletableFuture<List<Service>> getAll(String statusStr) {
        if (statusStr == null || statusStr.isEmpty() || statusStr.equalsIgnoreCase("ALL")) {
            return getAll();
        }

        if (statusStr.equalsIgnoreCase("OPEN")) {
            return CompletableFuture.supplyAsync(() -> {
                List<ServiceStatus> statuses = Arrays.asList(ServiceStatus.DELIVERED, ServiceStatus.RETURN);
                return hydrateServices(repository.findByServiceStatusesExcluded(statuses));
            });
        }

        return CompletableFuture.supplyAsync(() -> {
            ServiceStatus status = ServiceStatus.of(statusStr);
            return hydrateServices(repository.findByServiceStatuses(Collections.singletonList(status)));
        });
    }

    public CompletableFuture<Void> setDelivered(int serviceId) {

        return CompletableFuture.runAsync(() -> {
            Optional<Service> opt = repository.findById(serviceId);
            if (opt.isPresent()) {
                Service service = opt.get();
                service.setServiceStatus(ServiceStatus.DELIVERED);
                // Teslim tarihi daha önce set edilmemişse şu anı ata
                if (service.getDeliveryAt() == null) {
                    service.setDeliveryAt(LocalDateTime.now());
                }
                repository.updateService(service);
            } else {
                throw new ValidationException("Servis bulunamadı ID: " + serviceId);
            }
        });

    }

    public CompletableFuture<List<Service>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }
        return CompletableFuture.supplyAsync(() -> hydrateServices(repository.search("%" + searchTerm.trim() + "%")));
    }

    // --- Parça İşlemleri ---

    public CompletableFuture<AddedPart> addServicePart(AddedPart part) {
        return CompletableFuture.supplyAsync(() -> {
            if (part.getServiceId() <= 0) throw new ValidationException("Parça eklemek için önce servis kaydedilmiş olmalıdır.");
            if (part.getAmount() <= 0) throw new ValidationException("Miktar 0 veya negatif olamaz.");

            // 1. Stok Düşümü (Transaction Mantığı)
            if (part.isStockTracked() && part.getPartBarcode() != null) {
                PartRepository partRepo = DatabaseManager.getJdbi().onDemand(PartRepository.class);

                // Atomik olarak stoğu düşür. Stok yetersizse 0 döner.
                int updatedRows = partRepo.decreaseStockAtomically(part.getPartBarcode(), part.getAmount());

                if (updatedRows == 0) {
                    throw new IllegalStateException("Stok yetersiz veya parça bulunamadı: " + part.getName());
                }
            }

            // 2. Stok başarıyla düştüyse (veya manuel bir parçaysa), servise kaydet
            int addedPartId = repository.insertPart(part);
            part.setId(addedPartId); // UI tablosuna geri dönecek ID

            return part;
        });
    }

    public CompletableFuture<AddedPart> updateServicePart(AddedPart part) {
        return CompletableFuture.supplyAsync(() -> {
            if (part.getId() <= 0) {
                throw new ValidationException("Güncellenecek parça ID'si bulunamadı.");
            }
            if (part.getAmount() <= 0) {
                throw new ValidationException("Miktar 0 veya negatif olamaz.");
            }

            // Not: Stok takipli bir ürünün ADETİ sonradan değiştiriliyorsa, eski adet ile yeni adet
            // arasındaki fark kadar stoktan düşme/artırma yapılması gereken karmaşık bir süreç başlar.
            // Şimdilik sadece parçanın fiyat, isim vb. bilgilerinin güncellendiğini varsayarak DB'ye yazıyoruz.
            repository.updatePart(part);

            return part;
        });
    }

    public CompletableFuture<Void> removeServicePart(AddedPart part) {
        return CompletableFuture.runAsync(() -> {

            // 1. İade Kontrolü (Sadece stok takipli ise ve kullanıcı "İade Et" dediyse)
            if (part.isStockTracked() && part.getPartBarcode() != null && part.isReturnToStockOnDelete()) {
                PartRepository partRepo = DatabaseManager.getJdbi().onDemand(PartRepository.class);
                partRepo.increaseStockAtomically(part.getPartBarcode(), part.getAmount());
            }

            // 2. Parçayı servisten sil
            repository.deletePart(part.getId());
        });
    }

    public CompletableFuture<List<AddedPart>> getServiceParts(int serviceId) {
        return CompletableFuture.supplyAsync(() -> repository.findPartsByServiceId(serviceId));
    }

    public CompletableFuture<Double> getTotalPartsCostForService(int serviceId) {
        return CompletableFuture.supplyAsync(() -> repository.calculateTotalPartsCost(serviceId));
    }


    // --- Raporlama ---

    public CompletableFuture<ServiceFinanceReport> getFinanceReport() {
        return CompletableFuture.supplyAsync(() -> {
            List<ServiceFinanceRecord> records = repository.financeRecords();

            ServiceFinanceReport report = new ServiceFinanceReport();
            records.forEach(report::add);

            return report;
        });
    }

    public CompletableFuture<DashboardStats> getDashboardStats() {
        return CompletableFuture.supplyAsync(repository::dashboardStats);
    }

    // --- Helper ---
    private List<Service> hydrateServices(List<Service> services) {
        if (services == null || services.isEmpty()) return services;

        List<Integer> serviceIds = services.stream()
                .map(Service::getId)
                .collect(Collectors.toList());

        List<Integer> customerIds = services.stream()
                .map(Service::getCustomerId)
                .distinct()
                .collect(Collectors.toList());

        // 1. Müşterileri TEK SORGUYLA çek
        Map<Integer, Customer> customerMap = customerRepo.findByIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, c -> c));

        // 2. Parça maliyetlerini TEK SORGUYLA çek
        Map<Integer, Double> partsCostMap = repository.getPartsCostsByServiceIds(serviceIds);

        // 3. Servisleri Müşteri ve Parça Maliyetiyle zenginleştir
        for (Service s : services) {
            s.setCustomer(customerMap.get(s.getCustomerId()));

            // Eğer o servise ait parça yoksa 0.0 ata
            s.setTotalPartsCost(partsCostMap.getOrDefault(s.getId(), 0.0));
        }

        return services;
    }
}