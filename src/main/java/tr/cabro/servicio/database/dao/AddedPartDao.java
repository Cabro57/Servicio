package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.exception.DataAccessException;
import tr.cabro.servicio.model.AddedPart;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AddedPartDao extends BaseDao<AddedPart, Integer> {

    @Override
    protected String getTableName() { return "added_part"; }
    @Override
    protected String getPrimaryKeyColumn() { return "id"; }

    @Override
    protected String getInsertSQL() {
        return "INSERT INTO added_part (" +
                "service_id, series_no, brand, name, supplier_id, device_type, model, amount, " +
                "purchase_price, sale_price, warranty_period, purchase_date, description, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateSQL() {
        return "UPDATE added_part SET " +
                "service_id=?, series_no=?, brand=?, name=?, supplier_id=?, device_type=?, model=?, " +
                "amount=?, purchase_price=?, sale_price=?, warranty_period=?, purchase_date=?, description=?, created_at=? " +
                "WHERE id=?";
    }

    @Override
    protected void fillStatement(PreparedStatement stmt, AddedPart entity, boolean isUpdate) throws SQLException {
        int i = 1;
        stmt.setInt(i++, entity.getServiceId());
        stmt.setString(i++, entity.getSerial_no());
        stmt.setString(i++, entity.getBrand());
        stmt.setString(i++, entity.getName());
        stmt.setObject(i++, entity.getSupplier_id(), Types.INTEGER);
        stmt.setString(i++, entity.getDevice_type());
        stmt.setString(i++, entity.getModels());
        stmt.setInt(i++, entity.getAmount());
        stmt.setDouble(i++, entity.getPurchasePrice());
        stmt.setDouble(i++, entity.getSellingPrice());
        stmt.setObject(i++, entity.getWarranty_period(), Types.INTEGER);
        stmt.setString(i++, entity.getPurchase_date() != null ? entity.getPurchase_date().toString() : null);
        stmt.setString(i++, entity.getDescription());
        stmt.setString(i++, dateToStr(entity.getCreated_at()));

        if (isUpdate) {
            stmt.setInt(i++, entity.getId());
        }
    }

    @Override
    protected AddedPart mapRow(ResultSet rs) throws SQLException {
        AddedPart ap = new AddedPart();
        ap.setId(rs.getInt("id"));
        ap.setServiceId(rs.getInt("service_id"));
        ap.setSerial_no(rs.getString("series_no"));
        ap.setBrand(rs.getString("brand"));
        ap.setName(rs.getString("name"));
        ap.setSupplier_id(rs.getInt("supplier_id"));
        ap.setDevice_type(rs.getString("device_type"));
        ap.setModels(rs.getString("model"));
        ap.setAmount(rs.getInt("amount"));
        ap.setPurchasePrice(rs.getDouble("purchase_price"));
        ap.setSellingPrice(rs.getDouble("sale_price"));
        ap.setWarranty_period(rs.getInt("warranty_period"));

        String dateStr = rs.getString("purchase_date");
        if (dateStr != null && !dateStr.isEmpty()) {
            ap.setPurchase_date(LocalDate.parse(dateStr));
        }
        ap.setDescription(rs.getString("description"));

        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null && !createdAtStr.isEmpty()) {
            ap.setCreated_at(LocalDateTime.parse(createdAtStr));
        }
        return ap;
    }

    @Override
    protected void setGeneratedId(AddedPart entity, int id) {
        entity.setId(id);
    }

    // --- Batch İşlem ---
    public void create(List<AddedPart> entities) {
        if (entities == null || entities.isEmpty()) return;

        String sql = getInsertSQL();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (AddedPart entity : entities) {
                fillStatement(stmt, entity, false);
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();

        } catch (SQLException e) {
            logger.error("Toplu parça ekleme hatası", e);
            throw new DataAccessException("Toplu parça eklenemedi", e);
        }
    }

    public List<AddedPart> getByServiceId(int serviceId) {
        List<AddedPart> parts = new ArrayList<>();
        String sql = "SELECT * FROM added_part WHERE service_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serviceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) parts.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Servis parçaları getirilemedi: " + serviceId, e);
        }
        return parts;
    }

    public void deleteByServiceId(int serviceId) {
        String sql = "DELETE FROM " + getTableName() + " WHERE service_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, serviceId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Servis parçaları silinemedi: " + serviceId, e);
        }
    }
}