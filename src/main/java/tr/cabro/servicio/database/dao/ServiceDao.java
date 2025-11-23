package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.database.DatabaseManager; // getByCustomerId için gerekli
import tr.cabro.servicio.database.exception.DataAccessException;
import tr.cabro.servicio.model.PaymentType;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceDao extends BaseDao<Service, Integer> {

    @Override
    protected String getTableName() { return "services"; }

    @Override
    protected String getPrimaryKeyColumn() { return "id"; }

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
    protected void fillStatement(PreparedStatement stmt, Service entity, boolean isUpdate) throws SQLException {
        int i = 1;
        stmt.setInt(i++, entity.getCustomerId());
        stmt.setString(i++, dateToStr(entity.getCreatedAt()));
        stmt.setString(i++, dateToStr(entity.getDeliveryAt()));
        stmt.setString(i++, entity.getDeviceType());
        stmt.setString(i++, entity.getDeviceBrand());
        stmt.setString(i++, entity.getDeviceModel());
        stmt.setString(i++, entity.getDeviceSerial());
        stmt.setString(i++, entity.getDevicePassword());
        stmt.setString(i++, entity.getDeviceAccessory());
        stmt.setDouble(i++, entity.getLaborCost());
        stmt.setDouble(i++, entity.getPaid());
        stmt.setString(i++, entity.getPaymentType().getDisplayName());
        stmt.setString(i++, dateToStr(entity.getWarrantyDate()));
        stmt.setString(i++, dateToStr(entity.getMaintenanceDate()));
        stmt.setString(i++, entity.getReportedFault());
        stmt.setString(i++, entity.getDetectedFault());
        stmt.setString(i++, entity.getActionTaken());
        stmt.setString(i++, entity.getUrgencyStatus());
        stmt.setString(i++, entity.getServiceStatus().getDisplayName());
        stmt.setString(i++, entity.getNotes());

        if (isUpdate) {
            stmt.setInt(i++, entity.getId());
        }
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
        service.setCreatedAt(strToDate(rs.getString("created_at")));
        service.setDeliveryAt(strToDate(rs.getString("delivery_at")));
        service.setDeviceSerial(rs.getString("device_serial"));
        service.setDevicePassword(rs.getString("device_password"));
        service.setDeviceAccessory(rs.getString("device_accessory"));
        service.setLaborCost(rs.getDouble("labor_cost"));
        service.setPaid(rs.getDouble("paid"));

        try {
            service.setPaymentType(PaymentType.of(rs.getString("payment_type")));
        } catch (Exception e) { service.setPaymentType(PaymentType.CASH); }

        service.setWarrantyDate(strToDate(rs.getString("warranty_date")));
        service.setMaintenanceDate(strToDate(rs.getString("maintenance_date")));
        service.setReportedFault(rs.getString("reported_fault"));
        service.setDetectedFault(rs.getString("detected_fault"));
        service.setActionTaken(rs.getString("action_taken"));
        service.setUrgencyStatus(rs.getString("urgency_status"));

        try {
            service.setServiceStatus(ServiceStatus.of(rs.getString("service_status")));
        } catch (Exception e) { service.setServiceStatus(ServiceStatus.UNDER_REPAIR); }

        service.setNotes(rs.getString("Notes"));
        return service;
    }

    @Override
    protected void setGeneratedId(Service entity, int id) {
        entity.setId(id);
    }

    // Özel Metod: Hata fırlatır
    public List<Service> getByCustomerId(int customerId) {
        List<Service> services = new ArrayList<>();
        String sql = "SELECT * FROM " + getTableName() + " WHERE customer_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, customerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) services.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Müşteri servisleri getirilemedi: " + customerId, e);
        }
        return services;
    }
}