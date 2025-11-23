package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.RepairRepository;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;
import tr.cabro.servicio.reports.ServiceFinanceRecord;
import tr.cabro.servicio.reports.ServiceFinanceReport;
import tr.cabro.servicio.util.Validator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RepairService {

    private final RepairRepository repository;
    private static String DASHBOARD_SQL = null;

    public RepairService(RepairRepository repository) {
        this.repository = repository;
        loadDashboardSql();
    }

    public Service save(Service service, boolean update, List<AddedPart> parts) {
        // --- Validasyon ---
        if (Validator.isEmpty(service.getDeviceBrand()) || Validator.isEmpty(service.getDeviceModel())) {
            throw new ValidationException("Cihaz marka ve modeli zorunludur.");
        }

        // İşçilik veya ödenen tutar negatif olamaz
        if (service.getLaborCost() < 0) throw new ValidationException("İşçilik ücreti negatif olamaz.");
        if (service.getPaid() < 0) throw new ValidationException("Ödenen tutar negatif olamaz.");

        // Transactional Kayıt (Repository içindeki @Transaction metodu çağrılır)
        return repository.saveFullService(service, parts, update);
    }

    public void delete(int id) {
        // Eğer veritabanında ON DELETE CASCADE tanımlı değilse,
        // önce parçaları silmek gerekebilir.
        // repository.deletePartsByServiceId(id);
        repository.deleteService(id);
    }

    public Optional<Service> get(int id) {
        return repository.findServiceById(id);
    }

    public List<Service> getAll() {
        return repository.findAllServices();
    }

    public List<Service> getAll(int customerId) {
        return repository.findServicesByCustomerId(customerId);
    }

    // Duruma göre filtreleme
    public List<Service> getAll(String statusStr) {
        List<Service> services = getAll();

        if (statusStr == null || statusStr.isEmpty() || statusStr.equalsIgnoreCase("ALL")) {
            return services;
        }

        if (statusStr.equalsIgnoreCase("OPEN")) {
            return services.stream()
                    .filter(s -> s.getServiceStatus() != ServiceStatus.DELIVERED && s.getServiceStatus() != ServiceStatus.RETURN)
                    .collect(Collectors.toList());
        }

        try {
            // Gelen string'i (örn: "Tamirde" veya "UNDER_REPAIR") Enum'a çevir
            ServiceStatus status = ServiceStatus.of(statusStr);
            return services.stream()
                    .filter(s -> s.getServiceStatus() == status)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // --- Parça İşlemleri ---

    public void addPart(AddedPart part) {
        if (part.getServiceId() <= 0) throw new ValidationException("Parça bir servise ait olmalıdır.");
        repository.insertParts(Collections.singletonList(part));
    }

    public void addParts(List<AddedPart> parts) {
        if (parts == null || parts.isEmpty()) return;
        repository.insertParts(parts);
    }

    public void removePart(int partId) {
        repository.deletePart(partId);
    }

    public List<AddedPart> getParts(int serviceId) {
        return repository.findPartsByServiceId(serviceId);
    }

    public double getTotalPartsCostForService(int serviceId) {
        return getParts(serviceId).stream()
                .mapToDouble(AddedPart::getTotal)
                .sum();
    }

    public void setDelivered(int serviceId) {
        Optional<Service> opt = repository.findServiceById(serviceId);
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
    }

    public List<Service> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }
        return repository.searchServices("%" + searchTerm.trim() + "%");
    }

    // --- Raporlama ---

    public ServiceFinanceReport getDashboardStats() {
        // SQL dosyası yüklenemediyse boş rapor dön
        if (DASHBOARD_SQL == null || DASHBOARD_SQL.isEmpty()) {
            return new ServiceFinanceReport();
        }

        // JDBI Handle ile manuel mapping ve SQL çalıştırma
        return DatabaseManager.getJdbi().withHandle(handle -> {
            ServiceFinanceReport report = new ServiceFinanceReport();

            handle.createQuery(DASHBOARD_SQL)
                    .map((rs, ctx) -> {
                        ServiceFinanceRecord record = new ServiceFinanceRecord();
                        record.setMonth(rs.getString("month"));

                        // HATA DÜZELTMESİ:
                        // rs.getObject(..., Double.class) yerine rs.getDouble(...) kullanıyoruz.
                        // Bu metod null değerler için 0.0 döndürür ve SQLite sürücüsündeki
                        // "Bad value for type Double" hatasını engeller.

                        record.setServiceCount(rs.getInt("service_count"));

                        record.setServiceChangeRate(rs.getDouble("service_change_rate"));
                        record.setTotalRevenue(rs.getDouble("total_revenue"));
                        record.setRevenueChangeRate(rs.getDouble("revenue_change_rate"));
                        record.setTotalExpense(rs.getDouble("total_expense"));
                        record.setExpenseChangeRate(rs.getDouble("expense_change_rate"));
                        record.setTotalProfit(rs.getDouble("total_profit"));
                        record.setProfitChangeRate(rs.getDouble("profit_change_rate"));

                        return record;
                    })
                    .forEach(report::add);

            return report;
        });
    }

    private void loadDashboardSql() {
        if (DASHBOARD_SQL != null) return;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/queries/service_summary.sql")) {
            if (in != null) {
                try (java.util.Scanner scanner = new java.util.Scanner(in, StandardCharsets.UTF_8.name())) {
                    DASHBOARD_SQL = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                }
            }
        } catch (IOException e) {
            Servicio.getLogger().error("SQL dosyası yüklenirken hata: {}", e.getMessage());
        }
    }
}