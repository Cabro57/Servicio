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
        stmt.setInt(i++, entity.getCustomer_id());
        stmt.setString(i++, dateToStr(entity.getCreated_at()));
        stmt.setString(i++, dateToStr(entity.getDelivery_at()));
        stmt.setString(i++, entity.getDevice_type());
        stmt.setString(i++, entity.getDevice_brand());
        stmt.setString(i++, entity.getDevice_model());
        stmt.setString(i++, entity.getDevice_serial());
        stmt.setString(i++, entity.getDevice_password());
        stmt.setString(i++, entity.getDevice_accessory());
        stmt.setDouble(i++, entity.getLabor_cost());
        stmt.setDouble(i++, entity.getPaid());
        stmt.setString(i++, entity.getPayment_type().getDisplayName());
        stmt.setString(i++, dateToStr(entity.getWarranty_date()));
        stmt.setString(i++, dateToStr(entity.getMaintenance_date()));
        stmt.setString(i++, entity.getReported_fault());
        stmt.setString(i++, entity.getDetected_fault());
        stmt.setString(i++, entity.getAction_taken());
        stmt.setString(i++, entity.getUrgency_status());
        stmt.setString(i++, entity.getService_status().getDisplayName());
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
        service.setCreated_at(strToDate(rs.getString("created_at")));
        service.setDelivery_at(strToDate(rs.getString("delivery_at")));
        service.setDevice_serial(rs.getString("device_serial"));
        service.setDevice_password(rs.getString("device_password"));
        service.setDevice_accessory(rs.getString("device_accessory"));
        service.setLabor_cost(rs.getDouble("labor_cost"));
        service.setPaid(rs.getDouble("paid"));

        try {
            service.setPayment_type(PaymentType.of(rs.getString("payment_type")));
        } catch (Exception e) { service.setPayment_type(PaymentType.CASH); }

        service.setWarranty_date(strToDate(rs.getString("warranty_date")));
        service.setMaintenance_date(strToDate(rs.getString("maintenance_date")));
        service.setReported_fault(rs.getString("reported_fault"));
        service.setDetected_fault(rs.getString("detected_fault"));
        service.setAction_taken(rs.getString("action_taken"));
        service.setUrgency_status(rs.getString("urgency_status"));

        try {
            service.setService_status(ServiceStatus.of(rs.getString("service_status")));
        } catch (Exception e) { service.setService_status(ServiceStatus.UNDER_REPAIR); }

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