package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.dao.ServiceDao;
import tr.cabro.servicio.database.dao.AddedPartDao;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;
import tr.cabro.servicio.reports.ServiceFinanceRecord;
import tr.cabro.servicio.reports.ServiceFinanceReport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
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
    }

    public boolean save(Service service, boolean update) {
        try {
            if (!update) {
                serviceDao.create(service);
            } else {
                serviceDao.update(service);
            }
            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [SAVE SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean delete(int id) {
        try {
            serviceDao.delete(id);
            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public Optional<Service> get(int id) {
        return serviceDao.getByKey(id);
    }

    public List<Service> getAll() {
        try {
            return serviceDao.getAll();
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET ALL SERVICES] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public List<Service> getAll(int customerId) {
        try {
            return serviceDao.getByCustomerId(customerId);
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET BY CUSTOMER ID] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public List<Service> getAll(String statusStr) {
        List<Service> services = serviceDao.getAll();

        if (statusStr == null || statusStr.isEmpty() || statusStr.equalsIgnoreCase("ALL")) {
            return services;
        } else if (statusStr.equalsIgnoreCase("OPEN")) {
            return services.stream()
                    .filter(service -> service.getService_status() != ServiceStatus.DELIVERED
                            && service.getService_status() != ServiceStatus.RETURN)
                    .collect(Collectors.toList());
        }

        ServiceStatus status;
        try {
            status = ServiceStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }

        return services.stream()
                .filter(service -> service.getService_status() == status)
                .collect(Collectors.toList());
    }

    // Service Part Methods

    public boolean addPart(AddedPart part) {
        try {
            boolean b = addedPartDao.create(part);
            if(!b) return false;

            return b;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [ADD PART TO SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean addParts(List<AddedPart> parts) {
        if (parts == null || parts.isEmpty()) {
            return true;
        }

        try {
            return addedPartDao.create(parts);
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [ADD PARTS BATCH TO SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean removePart(AddedPart part) {
        try {

            boolean b;

            b = addedPartDao.delete(part.getId());
            if (!b) return false;

            return b;

        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE ADDED PART] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean removeParts(int serviceId) {
        try {
            boolean success = addedPartDao.deleteByServiceId(serviceId);
            if (!success) {
                return true;
            }

            return true;

        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE PARTS BY SERVICE ID] {}", String.valueOf(e));
            return false;
        }
    }

    public double getTotalPartsCostForService(int serviceId) {
        try {
            List<AddedPart> addedParts = addedPartDao.getByServiceId(serviceId);
            double total = 0;
            for (AddedPart ap : addedParts) {
                total += ap.getTotal(); // fiyat * adet
            }
            return total;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET TOTAL PARTS COST] {}", String.valueOf(e));
            return 0.0;
        }
    }

    public List<AddedPart> getParts(int serviceId) {
        try {
            return addedPartDao.getByServiceId(serviceId);
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET PARTS BY SERVICE ID] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public ServiceFinanceReport getDashboardStats() {
        if (DASHBOARD_SQL == null || DASHBOARD_SQL.isEmpty()) {
            Servicio.getLogger().error("Dashboard SQL yüklenemediği için rapor oluşturulamadı.");
            return new ServiceFinanceReport();
        }

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
            Servicio.getLogger().error("DB ERROR [DASHBOARD STATS]: {}", e.getMessage());
        }
        return report;
    }

    public boolean setDelivered(int serviceId) {
        try {
            Optional<Service> opt = serviceDao.getByKey(serviceId);
            if (!opt.isPresent()) {
                Servicio.getLogger().warn("SERVICE NOT FOUND [SET DELIVERED] id={}", serviceId);
                return false;
            }

            Service service = opt.get();
            service.setService_status(ServiceStatus.DELIVERED);
            service.setDelivery_at(LocalDateTime.now());

            boolean updated = serviceDao.update(service);

            if (!updated) {
                Servicio.getLogger().error("SERVICE UPDATE FAILED [SET DELIVERED] id={}", serviceId);
                return false;
            }

            return true;

        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [SET DELIVERED] {}", String.valueOf(e));
            return false;
        }
    }

    public List<Service> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }

        String[] searchableColumns = {"device_serial", "device_brand", "device_model"};

        try {
            // NOT: ServiceDao sınıfında bu metotun (search) implementasyonu gereklidir.
            return serviceDao.search(searchTerm.trim(), searchableColumns);
        } catch (Exception ex) {
            Servicio.getLogger().error("SERVICE ERROR [SEARCH]: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private void loadDashboardSql() {
        if (DASHBOARD_SQL != null) return;

        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/queries/service_summary.sql")) {
            if (in == null) {
                Servicio.getLogger().error("Kaynak bulunamadı: db/queries/service_summary.sql");
                return;
            }
            byte[] bytes = new byte[in.available()];
            int read = in.read(bytes);
            if (read <= 0) {
                Servicio.getLogger().error("Kaynak boş veya okunamadı: db/queries/service_summary.sql");
                return;
            }
            DASHBOARD_SQL = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Servicio.getLogger().error("SQL dosyası yüklenirken hata: {}", e.getMessage());
        }
    }

}
