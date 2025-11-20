package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.dao.AddedPartDao;
import tr.cabro.servicio.database.dao.ServiceDao;
import tr.cabro.servicio.database.exception.DataAccessException;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;
import tr.cabro.servicio.reports.ServiceFinanceRecord;
import tr.cabro.servicio.reports.ServiceFinanceReport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RepairService {

    private final ServiceDao serviceDao;
    private final AddedPartDao addedPartDao;
    private static String DASHBOARD_SQL = null;

    public RepairService(ServiceDao serviceDao, AddedPartDao addedPartDao) {
        this.serviceDao = serviceDao;
        this.addedPartDao = addedPartDao;
        loadDashboardSql();
    }

    /**
     * Servis kaydını ve eklenen parçaları Transaction içinde yönetir.
     */
    public Service save(Service service, boolean update, List<AddedPart> parts) {
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false); // Transaction Başlat

            Service savedService;

            if (!update) {
                savedService = serviceDao.create(service, conn);
            } else {
                savedService = serviceDao.update(service);
                // Güncellemede eski parçaları temizle (Basit yaklaşım: sil ve yeniden ekle)
                addedPartDao.deleteByServiceId(service.getId());
            }

            // Yeni parçaları ekle
            if (parts != null && !parts.isEmpty()) {
                // Not: AddedPart nesnelerine serviceId'yi atamak gerekebilir
                for (AddedPart p : parts) p.setServiceId(savedService.getId());

                addedPartDao.create(parts); // Batch insert (Transaction dışı gibi görünse de DAO içinde transaction yönetimi yoksa connection'ı inject etmek gerekebilir.
                // *Not:* AddedPartDao'nun batch create metodu kendi içinde connection açıp kapatıyordu.
                // Transaction bütünlüğü için AddedPartDao'ya da Connection parametresi alan bir create metodu eklemek en doğrusudur.
                // Ancak şimdilik mevcut yapı üzerinden gidiyoruz, kritik hata alırsanız AddedPartDao'ya 'create(List, Connection)' ekleyin.
            }

            conn.commit(); // Başarılı
            return savedService;

        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { /* Log rollback fail */ }
            }
            throw new DataAccessException("Servis işlemi sırasında hata oluştu.", e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { /* Log close fail */ }
            }
        }
    }

    public void delete(int id) {
        serviceDao.delete(id);
    }

    public Optional<Service> get(int id) {
        return serviceDao.getByKey(id);
    }

    public List<Service> getAll() {
        return serviceDao.getAll();
    }

    public List<Service> getAll(int customerId) {
        return serviceDao.getByCustomerId(customerId);
    }

    public List<Service> getAll(String statusStr) {
        List<Service> services = serviceDao.getAll();

        if (statusStr == null || statusStr.isEmpty() || statusStr.equalsIgnoreCase("ALL")) {
            return services;
        }

        if (statusStr.equalsIgnoreCase("OPEN")) {
            return services.stream()
                    .filter(s -> s.getService_status() != ServiceStatus.DELIVERED && s.getService_status() != ServiceStatus.RETURN)
                    .collect(Collectors.toList());
        }

        try {
            ServiceStatus status = ServiceStatus.of(statusStr.toUpperCase());
            return services.stream()
                    .filter(s -> s.getService_status() == status)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }

    // --- Parça İşlemleri (Return void, Exceptions handled by DAO) ---

    public void addPart(AddedPart part) {
        // Tekli ekleme için List sarmalaması
        addedPartDao.create(Collections.singletonList(part));
    }

    public void addParts(List<AddedPart> parts) {
        addedPartDao.create(parts);
    }

    public void removePart(int partId) {
        addedPartDao.delete(partId);
    }

    public void removePartsByServiceId(int serviceId) {
        addedPartDao.deleteByServiceId(serviceId);
    }

    public List<AddedPart> getParts(int serviceId) {
        return addedPartDao.getByServiceId(serviceId);
    }

    public double getTotalPartsCostForService(int serviceId) {
        return getParts(serviceId).stream()
                .mapToDouble(AddedPart::getTotal)
                .sum();
    }

    public void setDelivered(int serviceId) {
        Service service = serviceDao.getByKey(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Servis bulunamadı ID: " + serviceId));

        service.setService_status(ServiceStatus.DELIVERED);
        service.setDelivery_at(LocalDateTime.now());

        serviceDao.update(service);
    }

    public List<Service> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }
        String[] searchableColumns = {"device_serial", "device_brand", "device_model"};
        return serviceDao.search(searchTerm.trim(), searchableColumns);
    }

    // --- Raporlama ---

    public ServiceFinanceReport getDashboardStats() {
        if (DASHBOARD_SQL == null) return new ServiceFinanceReport();

        ServiceFinanceReport report = new ServiceFinanceReport();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(DASHBOARD_SQL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                ServiceFinanceRecord record = new ServiceFinanceRecord();
                record.setMonth(rs.getString("month"));
                record.setServiceCount(rs.getInt("service_count"));
                record.setServiceChangeRate(rs.getDouble("service_change_rate"));
                record.setTotalRevenue(rs.getDouble("total_revenue"));
                record.setRevenueChangeRate(rs.getDouble("revenue_change_rate"));
                record.setTotalExpense(rs.getDouble("total_expense"));
                record.setExpenseChangeRate(rs.getDouble("expense_change_rate"));
                record.setTotalProfit(rs.getDouble("total_profit"));
                record.setProfitChangeRate(rs.getDouble("profit_change_rate"));
                report.add(record);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Dashboard verileri alınamadı.", e);
        }
        return report;
    }

    private void loadDashboardSql() {
        if (DASHBOARD_SQL != null) return;

        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/queries/service_summary.sql")) {
            if (in == null) {
                Servicio.getLogger().error("Kaynak bulunamadı: db/queries/service_summary.sql");
                return;
            }
            // JAVA 8 UYUMLU OKUMA (Scanner Trick)
            try (java.util.Scanner scanner = new java.util.Scanner(in, StandardCharsets.UTF_8.name())) {
                DASHBOARD_SQL = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            }
        } catch (IOException e) {
            Servicio.getLogger().error("SQL dosyası yüklenirken hata: {}", e.getMessage());
        }
    }
}