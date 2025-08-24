package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.model.PaymentType;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ServiceDao extends BaseDao<Service, Integer> {

    @Override
    protected String getTableName() {
        return "services";
    }

    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }

    @Override
    protected String getInsertSQL() {
        return "INSERT INTO services (" +
                "customer_id, created_at, delivery_at, device_type, device_brand, device_model, " +
                "device_serial, device_password, device_accessory, labor_cost, paid, payment_type, " +
                "warranty_date, maintenance_date, reported_fault, detected_fault, action_taken, " +
                "urgency_status, service_status, Notes" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateSQL() {
        return "UPDATE services SET " +
                "customer_id=?, created_at=?, delivery_at=?, device_type=?, device_brand=?, device_model=?, " +
                "device_serial=?, device_password=?, device_accessory=?, labor_cost=?, paid=?, payment_type=?, " +
                "warranty_date=?, maintenance_date=?, reported_fault=?, detected_fault=?, action_taken=?, " +
                "urgency_status=?, service_status=?, Notes=? " +
                "WHERE id=?";
    }

    @Override
    protected void fillInsertStatement(PreparedStatement stmt, Service entity) throws SQLException {
        fillCommonFields(stmt, entity);
    }

    @Override
    protected void fillUpdateStatement(PreparedStatement stmt, Service entity) throws SQLException {
        fillCommonFields(stmt, entity);
        stmt.setInt(21, entity.getId());
    }

    private void fillCommonFields(PreparedStatement stmt, Service entity) throws SQLException {
        stmt.setInt(1, entity.getCustomer_id());
        stmt.setString(2, dateToStr(entity.getCreated_at()));
        stmt.setString(3, dateToStr(entity.getDelivery_at()));
        stmt.setString(4, entity.getDevice_type());
        stmt.setString(5, entity.getDevice_brand());
        stmt.setString(6, entity.getDevice_model());
        stmt.setString(7, entity.getDevice_serial());
        stmt.setString(8, entity.getDevice_password());
        stmt.setString(9, entity.getDevice_accessory());
        stmt.setDouble(10, entity.getLabor_cost());
        stmt.setDouble(11, entity.getPaid());
        stmt.setString(12, entity.getPayment_type().getDisplayName());
        stmt.setString(13, dateToStr(entity.getWarranty_date()));
        stmt.setString(14, dateToStr(entity.getMaintenance_date()));
        stmt.setString(15, entity.getReported_fault());
        stmt.setString(16, entity.getDetected_fault());
        stmt.setString(17, entity.getAction_taken());
        stmt.setString(18, entity.getUrgency_status());
        stmt.setString(19, entity.getService_status().getDisplayName());
        stmt.setString(20, entity.getNotes());
    }

    @Override
    protected Service mapRow(ResultSet rs) throws SQLException {
        Service service = new Service(
                rs.getInt("customer_id"),
                rs.getString("device_type"),
                rs.getString("device_brand"),
                rs.getString("device_model")
        );
        service.setId(rs.getInt("id"));
        service.setCreated_at(strToDate(rs.getString("created_at")));
        service.setDelivery_at(strToDate(rs.getString("delivery_at")));
        service.setDevice_serial(rs.getString("device_serial"));
        service.setDevice_password(rs.getString("device_password"));
        service.setDevice_accessory(rs.getString("device_accessory"));
        service.setLabor_cost(rs.getDouble("labor_cost"));
        service.setPaid(rs.getDouble("paid"));
        service.setPayment_type(PaymentType.of(rs.getString("payment_type")));
        service.setWarranty_date(strToDate(rs.getString("warranty_date")));
        service.setMaintenance_date(strToDate(rs.getString("maintenance_date")));
        service.setReported_fault(rs.getString("reported_fault"));
        service.setDetected_fault(rs.getString("detected_fault"));
        service.setAction_taken(rs.getString("action_taken"));
        service.setUrgency_status(rs.getString("urgency_status"));
        service.setService_status(ServiceStatus.of(rs.getString("service_status")));
        service.setNotes(rs.getString("Notes"));
        return service;
    }

    @Override
    protected void setGeneratedId(Service entity, int id) {
        entity.setId(id);
    }

    @Override
    protected void setKey(PreparedStatement stmt, int index, Integer key) throws SQLException {
        stmt.setInt(index, key);
    }

    private String dateToStr(LocalDateTime date) {
        return date != null ? date.toString() : null;
    }

    private LocalDateTime strToDate(String dateStr) {
        return (dateStr != null) ? LocalDateTime.parse(dateStr) : null;
    }

    public List<Service> getByCustomerId(int customerId) {
        List<Service> services = new ArrayList<>();
        String sql = "SELECT id FROM " + getTableName() + " WHERE customer_id = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int serviceId = rs.getInt("id");
                    getByKey(serviceId).ifPresent(services::add);
                }
            }
        } catch (SQLException e) {
            Servicio.getLogger().error(e.getMessage());
        }
        return services;
    }

    public List<Service> getServicesByStatus(String status) {
        String sql = "SELECT * FROM services WHERE service_status = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, status);
            ResultSet rs = ps.executeQuery();
            List<Service> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs)); // mapRow senin Service nesnesini oluşturan metodun
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Servis durumu sorgulanırken hata oluştu: " + status, e);
        }
    }
}
