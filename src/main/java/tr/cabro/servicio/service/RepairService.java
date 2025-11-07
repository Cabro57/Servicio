package tr.cabro.servicio.service;

import org.jfree.data.time.TimeTableXYDataset;
import org.jfree.data.xy.TableXYDataset;
import org.jfree.data.time.Month;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.dao.ServiceDao;
import tr.cabro.servicio.database.dao.ServicePartDao;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;
import tr.cabro.servicio.reports.ServiceFinanceRecord;
import tr.cabro.servicio.reports.ServiceFinanceReport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RepairService {

    private final ServiceDao serviceDao;
    private final ServicePartDao servicePartDao;

    public RepairService() {
        this.serviceDao = new ServiceDao();
        this.servicePartDao = new ServicePartDao();
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

    public boolean addPart(int serviceId, AddedPart part) {
        try {
            part.setServiceId(serviceId);
            boolean b = servicePartDao.create(part);
            if(!b) return false;

            return b;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [ADD PART TO SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean removePart(AddedPart part) {
        try {

            boolean b;

            b = servicePartDao.delete(part.getId());
            if (!b) return false;

            return b;

        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE ADDED PART] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean removeParts(int serviceId) {
        try {
            List<AddedPart> parts = servicePartDao.getByServiceId(serviceId);
            if (parts == null || parts.isEmpty()) {
                return true; // silinecek parça yok
            }

            boolean allSuccess = true;

            for (AddedPart part : parts) {
                boolean deleted = servicePartDao.delete(part.getId());
                if (!deleted) {
                    Servicio.getLogger().warn("FAILED TO DELETE ADDED PART [ServiceId: {}, PartId: {}]",
                            serviceId, part.getId());
                    allSuccess = false;
                }

            }

            return allSuccess;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE PARTS BY SERVICE ID] {}", String.valueOf(e));
            return false;
        }
    }

    public double getTotalPartsCostForService(int serviceId) {
        try {
            List<AddedPart> addedParts = servicePartDao.getByServiceId(serviceId);
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
            return servicePartDao.getByServiceId(serviceId);
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET PARTS BY SERVICE ID] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public ServiceFinanceReport getDashboardStats() {
        String sql = "";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/queries/service_summary.sql")) {
            if (in == null) {
                throw new IOException("Kaynak bulunamadı: db/queries/service_summary.sql");
            }
            byte[] bytes = new byte[in.available()];
            int read = in.read(bytes);
            if (read <= 0) {
                throw new IOException("Kaynak okunamadı: db/queries/service_summary.sql");
            }
            sql = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Servicio.getLogger().error("SQL dosyası okunamadı: {}", e.getMessage());
            return null;
        }

        ServiceFinanceReport report = new ServiceFinanceReport();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
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
            e.printStackTrace();
        }
        return report;
    }

    public TableXYDataset getTimeSeriesDataset() {
        TimeTableXYDataset dataset = new TimeTableXYDataset();
        String seriesIncome = "Gelir";
        String seriesExpense = "Gider";

        String sql =
                "SELECT ay, toplam_gelir, toplam_gider " +
                        "FROM (" +
                        "  SELECT strftime('%Y-%m', created_at) AS ay, SUM(labor_cost) AS toplam_gelir, 0 AS toplam_gider " +
                        "  FROM services GROUP BY strftime('%Y-%m', created_at) " +
                        "  UNION ALL " +
                        "  SELECT strftime('%Y-%m', created_at) AS ay, " +
                        "         SUM(sale_price * amount) AS toplam_gelir, " +
                        "         SUM(purchase_price * amount) AS toplam_gider " +
                        "  FROM added_part GROUP BY strftime('%Y-%m', created_at)" +
                        ") GROUP BY ay ORDER BY ay;";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String[] parts = rs.getString("ay").split("-");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);

                double income = rs.getDouble("toplam_gelir");
                double expense = rs.getDouble("toplam_gider");

                dataset.add(new Month(month, year), income, seriesIncome);
                dataset.add(new Month(month, year), expense, seriesExpense);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Zaman serisi verisi okunamadı: " + e.getMessage(), e);
        }

        return dataset;
    }
}
